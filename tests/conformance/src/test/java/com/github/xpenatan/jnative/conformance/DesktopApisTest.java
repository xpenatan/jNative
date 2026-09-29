package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DesktopApisTest {
    @TempDir
    Path temporary;

    @Test
    void desktopLibrariesMatchTheJvmWithFrequentCollection() throws Exception {
        Path source = temporary.resolve("DesktopApis.java");
        Path classes = temporary.resolve("classes");
        try(var input = getClass().getResourceAsStream("/fixtures/DesktopApis.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "DesktopApis"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result =
                NativeBuilder.create()
                        .generator(System.getProperty("jnative.test.generator", ""))
                        .classpath(classes)
                        .mainClass("DesktopApis")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(BuildType.RELEASE)
                        .nativeSymbols(NativeSymbols.NONE)
                        .stackTraces(StackTraceMode.JAVA)
                        .crashReports(CrashReportMode.OFF)
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
