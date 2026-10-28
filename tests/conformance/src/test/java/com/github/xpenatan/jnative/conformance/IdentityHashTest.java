package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IdentityHashTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void identityIsStableAndIndependentOfOverrides(BuildType buildType) throws Exception {
        Path source = temporary.resolve("IdentityHashBenchmark.java");
        try (var input = getClass().getResourceAsStream("/fixtures/IdentityHashBenchmark.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "IdentityHashBenchmark", "verify"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("IdentityHashBenchmark")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5)).build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString(), "verify"),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
