package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BitCountsNativeTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(value = BuildType.class, names = {"DEBUG", "RELEASE"})
    void canonicalBitCountsMatchJdkForEdgesEveryBitAndRandomValues(BuildType buildType) throws Exception {
        Path source = temporary.resolve("BitCountsNative.java");
        try (var input = getClass().getResourceAsStream("/fixtures/BitCountsNative.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "BitCountsNative"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder()
                .classpath(classes)
                .mainClass("BitCountsNative")
                .buildRoot(temporary.resolve("out"))
                .buildType(buildType)
                .timeout(Duration.ofMinutes(5))
                .build();
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(result.executable().toString()), Duration.ofSeconds(60)));
    }
}
