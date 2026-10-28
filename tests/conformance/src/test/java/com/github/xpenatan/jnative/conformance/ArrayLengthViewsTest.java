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
import static org.junit.jupiter.api.Assertions.*;

class ArrayLengthViewsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void preservesDeferredFailuresReassignmentAndCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("ArrayLengthViews.java");
        try (var input = getClass().getResourceAsStream("/fixtures/ArrayLengthViews.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ArrayLengthViews"), Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("ArrayLengthViews")
                .buildRoot(temporary.resolve("out")).buildType(buildType).timeout(Duration.ofMinutes(5));
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(), "ArrayLengthViews");
        assertTrue(cpp.contains("values_elements.length()"), cpp);
        var result = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
