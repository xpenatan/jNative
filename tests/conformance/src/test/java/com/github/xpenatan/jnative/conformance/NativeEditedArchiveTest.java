package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeEditedArchiveTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @Test
    void standaloneEditsAndSharedLibraryUseTheirExactPrivateArtifacts() throws Exception {
        Path source = temporary.resolve("Player.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        package game;
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        @com.github.xpenatan.jnative.interop.NativeInclude("jnative_imports.h")
                        public class Player {
                            @NativeImport("library_crash") static int nativeCrash() { return 0; }
                            static void fail() { throw new RuntimeException("saved exception"); }
                            public static void main(String[] args) {
                                if (args.length > 0) { nativeCrash(); return; }
                                try { fail(); } catch (Exception error) { error.printStackTrace(); }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("game.Player")
                        .buildRoot(temporary.resolve("build"))
                        .buildType(BuildType.RELEASE)
                        .sourceLayout(SourceLayout.PACKAGE_FILENAME);
        var generated = builder.generate();
        Path root = generated.request().buildRoot().resolve("native");
        Files.createDirectories(root.resolve("user"));
        Files.writeString(
                root.resolve("user/library.cpp"),
                """
                        #include <cstdint>
                        static volatile int* address = nullptr;
                        __attribute__((always_inline)) inline void inlined_fault() {
                            *address = 1;
                        }
                        extern "C"
                        #ifdef _WIN32
                        __declspec(dllexport)
                        #endif
                        std::int32_t library_crash() {
                            inlined_fault();
                            return 0;
                        }
                        """);
        Files.writeString(
                root.resolve("user/CMakeLists.txt"),
                """
                        add_library(game_library SHARED "${CMAKE_CURRENT_LIST_DIR}/library.cpp")
                        target_compile_options(game_library PRIVATE -g "-fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
                        target_link_libraries(jnative_app PRIVATE game_library)
                        """);
        Path project = builder.exportProject(generated, temporary.resolve("editable")).directory();
        Files.move(classes, temporary.resolve("retired-input"));
        var first = builder.compileProject(project);
        Path store = temporary.resolve("private");
        var saved = NativeDiagnostics.archive(project, BuildType.RELEASE, store);
        Path firstReports = temporary.resolve("first-reports");
        assertEquals(
                0,
                ProcessHarness.run(
                                temporary,
                                List.of(first.executable().toString()),
                                Duration.ofSeconds(30),
                                Map.of("JNATIVE_REPORT_DIR", firstReports.toString()))
                        .exitCode());
        Path firstReport = NativeDiagnostics.pendingReports(firstReports).getFirst();
        assertTrue(NativeDiagnostics.decode(firstReport, store).json().contains("verified"));
        Path cpp = project.resolve("src/classes/game.Player.cpp");
        Files.writeString(cpp, "\n".repeat(17) + Files.readString(cpp));
        var second = builder.compileProject(project);
        var savedSecond = NativeDiagnostics.archive(project, BuildType.RELEASE, store);
        assertNotEquals(saved.buildId(), savedSecond.buildId());
        assertThrows(
                CompilerException.class,
                () -> NativeDiagnostics.decode(firstReport, savedSecond.directory()));
        Path secondReports = temporary.resolve("second-reports");
        assertEquals(
                0,
                ProcessHarness.run(
                                temporary,
                                List.of(second.executable().toString()),
                                Duration.ofSeconds(30),
                                Map.of("JNATIVE_REPORT_DIR", secondReports.toString()))
                        .exitCode());
        var changed =
                NativeDiagnostics.decode(
                        NativeDiagnostics.pendingReports(secondReports).getFirst(), store);
        assertTrue(changed.json().contains("stale-after-native-edit"), changed.json());
        assertTrue(changed.text().contains("game.Player.cpp"), changed.text());
        try(var zip = new java.util.zip.ZipFile(savedSecond.sources().toFile())) {
            assertEquals(
                    Files.readString(cpp),
                    new String(
                            zip.getInputStream(zip.getEntry("src/classes/game.Player.cpp"))
                                    .readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8));
        }
        Path extracted =
                NativeDiagnostics.extractSources(savedSecond, temporary.resolve("snapshot"));
        assertEquals(
                Files.readString(cpp),
                Files.readString(extracted.resolve("src/classes/game.Player.cpp")));
        Path installation = Files.createDirectory(temporary.resolve("player"));
        try(var files = Files.list(second.executable().getParent())) {
            for(Path file : files.filter(Files::isRegularFile).toList())
                Files.copy(file, installation.resolve(file.getFileName()));
        }
        Files.move(project, project.resolveSibling("retired-native"));
        Path crashReports = temporary.resolve("library-reports");
        assertNotEquals(
                0,
                ProcessHarness.run(
                                temporary,
                                List.of(
                                        installation
                                                .resolve(second.executable().getFileName())
                                                .toString(),
                                        "crash"),
                                Duration.ofSeconds(30),
                                Map.of("JNATIVE_REPORT_DIR", crashReports.toString()))
                        .exitCode());
        if(System.getProperty("os.name").startsWith("Windows")) {
            Path report =
                    NativeDiagnostics.pendingReports(crashReports).stream()
                            .filter(
                                    path -> {
                                        try {
                                            return "minidump"
                                                    .equals(
                                                            Json.object(
                                                                            Json.read(
                                                                                    Files
                                                                                            .readString(
                                                                                                    path)))
                                                                    .get("status"));
                                        } catch(Exception error) {
                                            throw new AssertionError(error);
                                        }
                                    })
                            .findFirst()
                            .orElseThrow();
            var decoded = NativeDiagnostics.decode(report, store);
            assertTrue(decoded.text().contains("library.cpp"));
            var fault =
                    Json.object(
                            Json.array(Json.object(Json.read(decoded.json())).get("decodedFrames"))
                                    .getFirst());
            var locations = Json.array(fault.get("locations"));
            assertTrue(locations.size() >= 2, decoded.json());
            var inner = Json.object(locations.getFirst());
            var outer = Json.object(locations.getLast());
            assertTrue(inner.get("function").toString().contains("inlined_fault"), decoded.json());
            assertEquals(true, inner.get("inline"));
            assertEquals("library_crash", outer.get("function"));
            assertEquals(false, outer.get("inline"));
            assertEquals("user/library.cpp", inner.get("sourceFile"), decoded.json());
            assertTrue(
                    NativeDiagnostics.decodeDump(report, store, "addr2line")
                            .text()
                            .contains("library.cpp"));
            Path helper = installation.resolve("jnative-diagnostics.exe");
            var export =
                    ProcessHarness.run(
                            temporary,
                            List.of(
                                    helper.toString(),
                                    "--export",
                                    report.toString(),
                                    temporary.resolve("attachment").toString()),
                            Duration.ofSeconds(30));
            assertEquals(0, export.exitCode(), export.text());
            assertTrue(
                    Files.isRegularFile(
                            temporary.resolve("attachment").resolve(report.getFileName())));
            var minimal =
                    NativeDiagnostics.exportReport(report, temporary.resolve("minimal"), false);
            assertEquals("", Json.object(Json.read(Files.readString(minimal))).get("dump"));
        }
        assertTrue(NativeDiagnostics.decode(firstReport, store).text().contains("game.Player.cpp"));
    }
}
