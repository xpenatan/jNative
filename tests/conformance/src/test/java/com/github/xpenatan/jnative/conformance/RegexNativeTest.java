package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class RegexNativeTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void splitAndReplacementPreserveSupportedContracts(BuildType buildType) throws Exception {
        Path source = temporary.resolve("RegexNative.java");
        try (var input = getClass().getResourceAsStream("/fixtures/RegexNative.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "RegexNative"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("RegexNative")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeDefine("CMAKE_CXX_STANDARD", buildType == BuildType.DEBUG ? "11" : "17")
                .cmakeDefine("CMAKE_CXX_STANDARD_REQUIRED", "ON")
                .cmakeDefine("CMAKE_CXX_EXTENSIONS", "OFF")
                .cmakeDefine("JNATIVE_FEATURES", buildType == BuildType.DEBUG ? "PORTABLE" : "AUTO").timeout(Duration.ofMinutes(5)).build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
        assertEquals(new ProcessHarness.Output(0,
                "group:java.lang.IndexOutOfBoundsException\nprefix:seedz\nretry:seedzzaq\n"
                        + "escape:java.lang.IllegalArgumentException\npartial:zX\n"
                        + "null:java.lang.NullPointerException\nunchanged:zX\nsequence:[a, b, ]:1\n"),
                ProcessHarness.run(temporary, List.of(result.executable().toString(), "partial"),
                        Duration.ofSeconds(30), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
