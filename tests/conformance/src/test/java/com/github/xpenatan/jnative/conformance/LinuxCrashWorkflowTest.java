package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "jnative.linux", matches = "true")
class LinuxCrashWorkflowTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void fallbackReportsWithArchivedRelease(BuildType type) throws Exception {
        Path source = temporary.resolve("Player.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        package game;
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        import com.github.xpenatan.jnative.interop.NativeInclude;
                        @NativeInclude("jnative_imports.h")
                        public class Player {
                            @NativeImport("crash_player") static int crash() { return 0; }
                            public static void main(String[] args) throws Exception {
                                if (args.length > 0 && args[0].equals("worker")) {
                                    Thread t = new Thread(() -> crash(), "render"); t.start(); t.join();
                                } else { crash(); }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        Path nativeFile = temporary.resolve("player_native.cpp");
        Files.writeString(
                nativeFile,
                """
                        #include <cstdint>
                        #include <cstdlib>
                        #include <sys/prctl.h>
                        static volatile int* invalid_address = nullptr;
                        extern "C" __attribute__((noinline)) std::int32_t crash_player() {
                            if(std::getenv("JNATIVE_TEST_NO_CORE")) prctl(PR_SET_DUMPABLE, 0);
                            *invalid_address = 42;
                            return 0;
                        }
                        """);
        var generation =
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
                        .nativeSymbols(NativeSymbols.SEPARATE)
                        .generate();
        Path project = generation.request().buildRoot().resolve("native");
        if(type == BuildType.RELEASE) {
            Path cmake = project.resolve("jnative-sources.cmake");
            String sources = Files.readString(cmake);
            String separate = sources.replace("    \"user/player_native.cpp\"\n", "");
            assertNotEquals(
                    sources,
                    separate,
                    "Move the native crash implementation into its separate library");
            Files.writeString(cmake, separate);
            Files.writeString(
                    project.resolve("user/CMakeLists.txt"),
                    """
                            add_library(player_library SHARED "${CMAKE_CURRENT_LIST_DIR}/player_native.cpp")
                            target_compile_options(player_library PRIVATE -g "-fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
                            target_link_libraries(jnative_app PRIVATE player_library)
                            """);
        }
        Path binary = project.resolve("b/linux"), store = temporary.resolve("private");
        String config = type == BuildType.DEBUG ? "Debug" : "Release";
        successful(
                "cmake",
                "-S",
                linux(project),
                "-B",
                linux(binary),
                "-G",
                "Ninja",
                "-DCMAKE_BUILD_TYPE=" + config,
                "-DJNATIVE_ARCHIVE_STORE=" + linux(store));
        successful(
                "cmake",
                "--build",
                linux(binary),
                "--target",
                "jnative_archive",
                "--parallel",
                "2");
        var bundle = NativeDiagnostics.latest(project, type);
        assertTrue(Files.isRegularFile(store.resolve(bundle.buildId()).resolve("manifest.json")));
        Path executable =
                project.resolve(type.name().toLowerCase(Locale.ROOT))
                        .resolve(generation.request().targetFileName());
        for(String mode : List.of("main", "worker")) {
            Path reports = temporary.resolve("reports-" + mode);
            var crash = wsl("bash", "-c", """
                    ulimit -c 0
                    export JNATIVE_REPORT_DIR="$1"
                    export JNATIVE_TEST_NO_CORE=1
                    shift
                    exec "$@"
                    """, "jnative-crash", linux(reports), linux(executable), mode);
            assertNotEquals(0, crash.exitCode(), crash.text());
            Path report = NativeDiagnostics.pendingReports(reports).getFirst();
            var raw = Json.object(Json.read(Files.readString(report)));
            assertTrue(raw.get("status").toString().contains("context-only"));
            assertEquals(bundle.buildId(), raw.get("buildId"));
            var fallback = NativeDiagnostics.decode(report, store);
            if(type == BuildType.DEBUG)
                assertTrue(fallback.text().contains("player_native.cpp"), fallback.text());
            else assertTrue(fallback.text().contains("symbols unavailable"), fallback.text());
            var unavailable = assertThrows(
                    CompilerException.class,
                    () -> NativeDiagnostics.decodeCore(
                            report, temporary.resolve(mode + ".core"), store, "gdb"));
            assertTrue(unavailable.getMessage().contains("core decoding is unavailable"));
        }
    }

    private void successful(String... command) throws Exception {
        var result = wsl(command);
        assertEquals(0, result.exitCode(), result.text());
    }

    private ProcessHarness.Output wsl(String... command) throws Exception {
        var args = new ArrayList<>(List.of("wsl", "-d", "Ubuntu", "--exec"));
        args.addAll(List.of(command));
        return ProcessHarness.run(temporary, args, Duration.ofSeconds(120));
    }

    private static String linux(Path path) {
        String text = path.toAbsolutePath().toString().replace('\\', '/');
        return "/mnt/" + Character.toLowerCase(text.charAt(0)) + text.substring(2);
    }
}
