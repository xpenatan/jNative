package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReferenceStoreProofTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void exactStoresPreserveCovarianceExceptionOrderAndRootLiveness(BuildType buildType) throws Exception {
        Path source = temporary.resolve("ReferenceStoreProof.java");
        try(var input = getClass().getResourceAsStream("/fixtures/ReferenceStoreProof.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ReferenceStoreProof"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("ReferenceStoreProof")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generated = builder.generate();
        String cpp = Files.readString(generated.request().generatedSourcesDirectory()
                .resolve("classes/ReferenceStoreProof.cpp"));
        assertTrue(cpp.contains("reference_set_exact("), cpp);
        assertTrue(cpp.contains("reference_set("), cpp);
        int putStart = cpp.indexOf("void ReferenceStoreProof::put(");
        assertTrue(putStart >= 0, cpp);
        String put = cpp.substring(putStart, cpp.indexOf("\n}", putStart));
        assertTrue(put.contains("reference_set_exact("), put);
        assertFalse(put.contains("RootFrame<"), put);
        var result = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
