package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleModeTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void consoleWaitPreservesOutputAndStatusAndCanBeDisabledInAnExistingBuild(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ConsoleExample.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class ConsoleExample {
                            public static void main(String[] args) {
                                System.out.println("Console example");
                                if (args.length != 0) throw new IllegalStateException("console failure");
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 17);
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("ConsoleExample")
                        .buildRoot(temporary.resolve("generated"))
                        .buildType(buildType)
                        .consoleMode(ConsoleMode.PAUSE_ON_EXIT);
        var paused = builder.build();
        var expected = new ProcessHarness.Output(0, "Console example\n");
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(paused.executable().toString()),
                        Duration.ofSeconds(20)));
        var failed =
                ProcessHarness.run(
                        temporary,
                        List.of(paused.executable().toString(), "fail"),
                        Duration.ofSeconds(20));
        assertEquals(1, failed.exitCode());
        assertTrue(failed.text().contains("console failure"), failed.text());
        assertFalse(failed.text().contains("Press any key"), failed.text());
        assertFalse(failed.text().contains("Press Enter"), failed.text());

        Path probe = null;
        if(System.getProperty("os.name").startsWith("Windows")) {
            probe = temporary.resolve("console-probe.exe");
            Path probeSource =
                    Path.of(
                            System.getProperty("jnative.root"),
                            "tests/conformance/src/test/cpp/console_probe.cpp");
            var compile =
                    ProcessHarness.run(
                            temporary,
                            List.of(
                                    "g++",
                                    "-municode",
                                    "-static-libgcc",
                                    "-static-libstdc++",
                                    probeSource.toString(),
                                    "-o",
                                    probe.toString()),
                            Duration.ofSeconds(45));
            assertEquals(0, compile.exitCode(), compile.text());
            verifyConsole(probe, paused.executable(), 0, "pause");
            verifyConsole(probe, paused.executable(), 1, "pause", "fail");
        }
        if(Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, paused, expected, 1);

        if(buildType == BuildType.RELEASE) {
            var exported =
                    builder.exportProject(paused.generation(), temporary.resolve("exported"));
            var rebuilt =
                    NativeBuilder.create()
                            .buildType(BuildType.RELEASE)
                            .compileProject(exported.directory());
            assertEquals(
                    expected,
                    ProcessHarness.run(
                            temporary,
                            List.of(rebuilt.executable().toString()),
                            Duration.ofSeconds(20)));
            if(probe != null) verifyConsole(probe, rebuilt.executable(), 0, "pause");
        }

        var normal = builder.consoleMode(ConsoleMode.NORMAL).build();
        Path cache = normal.artifact().buildDirectory().resolve("CMakeCache.txt");
        assertTrue(Files.readString(cache).contains("JNATIVE_PAUSE_ON_EXIT:BOOL=OFF"));
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(normal.executable().toString()),
                        Duration.ofSeconds(20)));
        if(probe != null) verifyConsole(probe, normal.executable(), 0, "normal");
    }

    private void verifyConsole(
            Path probe, Path executable, int status, String mode, String... arguments)
            throws Exception {
        var command =
                new java.util.ArrayList<>(
                        List.of(
                                probe.toString(),
                                executable.toString(),
                                Integer.toString(status),
                                mode));
        command.addAll(List.of(arguments));
        var result = ProcessHarness.run(temporary, command, Duration.ofSeconds(30));
        assertEquals(0, result.exitCode(), result.text());
    }
}
