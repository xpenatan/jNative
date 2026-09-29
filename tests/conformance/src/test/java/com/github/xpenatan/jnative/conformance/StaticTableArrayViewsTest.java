package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StaticTableArrayViewsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void checkedMetadataMatchesJvmDuringInitializationMutationAndCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("StaticTableArrayViews.java");
        try (var input = getClass().getResourceAsStream("/fixtures/StaticTableArrayViews.java")) {
            assertNotNull(input);
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "StaticTableArrayViews"),
                Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("StaticTableArrayViews").buildRoot(temporary.resolve("out"))
                .buildType(buildType).cmakeBuildArgs("--parallel", "2")
                .timeout(Duration.ofMinutes(5));
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                "StaticTableArrayViews$Reentrant");
        assertTrue(cpp.contains("PrimitiveArrayView<std::int32_t> TABLE_elements"), cpp);
        assertFalse(cpp.contains("TABLE_elements.get_unchecked("), cpp);
        var result = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
