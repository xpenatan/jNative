package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class NativeCrashWorkflowTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void releasedPlayerReportsDecodeWithPrivateSymbols(BuildType type) throws Exception {
        Path source = temporary.resolve("Player.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        package game;
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        import com.github.xpenatan.jnative.interop.NativeInclude;
                        @NativeInclude("jnative_imports.h")
                        public class Player {
                            @NativeImport("crash_player") static int nativeCrash(int mode) { return 0; }
                            static { nativeCrash(3); }
                            static void fail() { throw new IllegalArgumentException("player failure"); }
                            public static void main(String[] args) throws Exception {
                                if (args.length == 0) { System.out.println(7); return; }
                                if (args[0].equals("exception")) { try { fail(); } catch (Exception e) { e.printStackTrace(); } return; }
                                if (args[0].equals("worker")) { Thread t = new Thread(() -> nativeCrash(0), "render"); t.start(); t.join(); return; }
                                if (args[0].equals("terminate")) { nativeCrash(1); return; }
                                if (args[0].equals("overflow")) { nativeCrash(2); return; }
                                nativeCrash(0);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        Path nativeFile = temporary.resolve("player_native.cpp");
        Files.writeString(
                nativeFile,
                """
                        #include <cstdint>
                        #include <exception>
                        #include <cstdlib>
                        static volatile int* bad_address = nullptr;
                        __attribute__((noinline)) static int overflow(int n) {
                            volatile unsigned char buffer[8192]{};
                            buffer[n & 8191] = static_cast<unsigned char>(n);
                            return overflow(n + 1) + buffer[n & 8191];
                        }
                        extern "C" __attribute__((noinline)) std::int32_t crash_player(std::int32_t mode) {
                            if (mode == 3 && !std::getenv("JNATIVE_TEST_STARTUP_CRASH")) return 0;
                            if (mode == 1) std::terminate();
                            if (mode == 2) return overflow(0);
                            *bad_address = 42;
                            return 0;
                        }
                        """);
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("game.Player")
                        .nativeFile(nativeFile)
                        .buildRoot(temporary.resolve("build"))
                        .buildType(type)
                        .sourceLayout(
                                type == BuildType.DEBUG
                                        ? SourceLayout.PACKAGE_DIRECTORIES
                                        : SourceLayout.PACKAGE_FILENAME)
                        .nativeSymbols(NativeSymbols.SEPARATE);
        var built = builder.build();
        assertNotNull(built.diagnostics());
        var bundle = NativeDiagnostics.openBundle(built.diagnostics().directory());
        Path project = built.generation().request().buildRoot().resolve("native"),
                store = temporary.resolve("private");
        var retained = NativeDiagnostics.archive(project, type, store);
        assertEquals(bundle.buildId(), retained.buildId());
        assertThrows(
                CompilerException.class,
                () -> NativeDiagnostics.archive(project, type, project.resolve("archive")));
        String cpp =
                Files.readString(
                        built.generation()
                                .request()
                                .generatedSourcesDirectory()
                                .resolve(
                                        type == BuildType.DEBUG
                                                ? "classes/game/Player.cpp"
                                                : "classes/game.Player.cpp"));
        assertFalse(cpp.contains("JavaFrame") || cpp.contains("java_frame.line"), cpp);
        Path distribution = Files.createDirectory(temporary.resolve("player installation"));
        try(var files = Files.list(built.executable().getParent())) {
            for(Path file : files.filter(Files::isRegularFile).toList())
                Files.copy(file, distribution.resolve(file.getFileName()));
        }
        Path executable = distribution.resolve(built.executable().getFileName());
        Path reports = temporary.resolve("reports");
        Map<String, String> environment =
                Map.of("JNATIVE_REPORT_DIR", reports.toString(), "JNATIVE_GC_INTERVAL", "1");
        assertEquals(
                new ProcessHarness.Output(0, "7\n"),
                ProcessHarness.run(
                        temporary,
                        List.of(executable.toString()),
                        Duration.ofSeconds(30),
                        environment));
        assertTrue(NativeDiagnostics.pendingReports(reports).isEmpty());
        var failure =
                ProcessHarness.run(
                        temporary,
                        List.of(executable.toString(), "exception"),
                        Duration.ofSeconds(30),
                        environment);
        assertEquals(0, failure.exitCode(), failure.text());
        Path exception = NativeDiagnostics.pendingReports(reports).getFirst();
        var decoded = NativeDiagnostics.decode(exception, store);
        assertTrue(
                decoded.text().contains("Player::fail") && decoded.text().contains("Player.cpp"),
                decoded.text());
        assertTrue(decoded.json().contains("verified"), decoded.json());
        var damaged = Json.object(Json.read(Files.readString(exception)));
        damaged.put("schema", 99);
        Path badReport = temporary.resolve("damaged-report.json");
        Files.writeString(badReport, Json.write(damaged));
        assertThrows(CompilerException.class, () -> NativeDiagnostics.decode(badReport, store));
        damaged = Json.object(Json.read(Files.readString(exception)));
        for(Object frame : Json.array(damaged.get("frames"))) {
            var module = Json.object(Json.object(frame).getOrDefault("module", Map.of()));
            if(built.executable().getFileName().toString().equals(module.get("name"))) {
                module.put("sha256", "0".repeat(64));
                break;
            }
        }
        Files.writeString(badReport, Json.write(damaged));
        assertTrue(
                assertThrows(
                        CompilerException.class,
                        () -> NativeDiagnostics.decode(badReport, store))
                        .getMessage()
                        .contains("checksum"));
        Path submitted = NativeDiagnostics.exportReport(exception, temporary.resolve("submitted"));
        assertEquals(decoded.text(), NativeDiagnostics.decode(submitted, store).text());
        boolean windows =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        if(windows) {
            for(String mode : List.of("crash", "worker", "terminate", "overflow")) {
                Path crashDir = temporary.resolve("reports-" + mode);
                var crash =
                        ProcessHarness.run(
                                temporary,
                                List.of(executable.toString(), mode),
                                Duration.ofSeconds(30),
                                Map.of("JNATIVE_REPORT_DIR", crashDir.toString()));
                assertNotEquals(0, crash.exitCode(), crash.text());
                Path report = findDumpReport(crashDir);
                var nativeTrace = NativeDiagnostics.decode(report, store);
                assertTrue(
                        nativeTrace.text().contains("player_native.cpp"),
                        mode + "\n" + nativeTrace.text());
                var dumpTrace = NativeDiagnostics.decodeDump(report, store, "addr2line");
                assertTrue(
                        dumpTrace.text().contains("player_native.cpp"),
                        mode + "\n" + dumpTrace.text());
                assertTrue(
                        Files.size(
                                NativeDiagnostics.exportReport(
                                        report, temporary.resolve("submit-" + mode)))
                                > 0);
            }
            Path startup = temporary.resolve("startup-reports");
            assertNotEquals(
                    0,
                    ProcessHarness.run(
                                    temporary,
                                    List.of(executable.toString()),
                                    Duration.ofSeconds(30),
                                    Map.of(
                                            "JNATIVE_REPORT_DIR",
                                            startup.toString(),
                                            "JNATIVE_TEST_STARTUP_CRASH",
                                            "1"))
                            .exitCode());
            assertTrue(
                    NativeDiagnostics.decode(findDumpReport(startup), store)
                            .text()
                            .contains("player_native.cpp"));
            Path blocked = temporary.resolve("unwritable");
            Files.writeString(blocked, "A file cannot be used as a report directory");
            assertNotEquals(
                    0,
                    ProcessHarness.run(
                                    temporary,
                                    List.of(executable.toString(), "crash"),
                                    Duration.ofSeconds(30),
                                    Map.of("JNATIVE_REPORT_DIR", blocked.toString()))
                            .exitCode());
            assertEquals("A file cannot be used as a report directory", Files.readString(blocked));
            Path noDump = temporary.resolve("without-dump");
            assertNotEquals(
                    0,
                    ProcessHarness.run(
                                    temporary,
                                    List.of(executable.toString(), "crash"),
                                    Duration.ofSeconds(30),
                                    Map.of(
                                            "JNATIVE_REPORT_DIR",
                                            noDump.toString(),
                                            "JNATIVE_INCLUDE_DUMP",
                                            "0"))
                            .exitCode());
            try(var files = Files.list(noDump)) {
                assertTrue(files.noneMatch(path -> path.toString().endsWith(".dmp")));
            }
            assertTrue(
                    NativeDiagnostics.pendingReports(noDump).stream()
                            .anyMatch(
                                    path -> {
                                        try {
                                            return Files.readString(path).contains("dump-disabled");
                                        } catch(Exception error) {
                                            throw new AssertionError(error);
                                        }
                                    }));
            Path helper = distribution.resolve("jnative-diagnostics.exe");
            Files.move(helper, distribution.resolve("helper-unavailable.exe"));
            Path missing = temporary.resolve("without-helper");
            var crash =
                    ProcessHarness.run(
                            temporary,
                            List.of(executable.toString(), "crash"),
                            Duration.ofSeconds(30),
                            Map.of("JNATIVE_REPORT_DIR", missing.toString()));
            assertNotEquals(0, crash.exitCode());
            Path fallback = NativeDiagnostics.pendingReports(missing).getFirst();
            assertTrue(
                    NativeDiagnostics.decode(fallback, store).text().contains("player_native.cpp"));
        }
        // The archive remains useful when the original build tree is unavailable.
        Files.move(project, project.resolveSibling("retired-native"));
        assertEquals(decoded.text(), NativeDiagnostics.decode(exception, store).text());
        Path symbols =
                retained.directory()
                        .resolve("symbols")
                        .resolve(built.executable().getFileName() + ".debug");
        Files.write(symbols, new byte[]{0}, StandardOpenOption.APPEND);
        assertTrue(
                assertThrows(
                        CompilerException.class,
                        () -> NativeDiagnostics.decode(exception, store))
                        .getMessage()
                        .contains("checksum"));
    }

    private static Path findDumpReport(Path directory) throws Exception {
        for(Path path : NativeDiagnostics.pendingReports(directory)) {
            var data = Json.object(Json.read(Files.readString(path)));
            if("minidump".equals(data.get("status"))) return path;
        }
        throw new AssertionError(
                "No minidump report: " + NativeDiagnostics.pendingReports(directory));
    }
}
