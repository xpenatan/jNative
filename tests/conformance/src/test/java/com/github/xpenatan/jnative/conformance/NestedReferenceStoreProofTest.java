package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NestedReferenceStoreProofTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void compatibleStoresPreserveExceptionsAndGcLiveness(BuildType buildType) throws Exception {
        Path source = temporary.resolve("NestedReferenceStoreProof.java");
        try(var input = getClass().getResourceAsStream("/fixtures/NestedReferenceStoreProof.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "NestedReferenceStoreProof"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("NestedReferenceStoreProof")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generated = builder.generate();
        String cpp = Files.readString(generated.request().generatedSourcesDirectory()
                .resolve("classes/NestedReferenceStoreProof.cpp"));
        for(String name : List.of("store", "deep", "leaves", "leafRows", "fieldStore", "nullable", "loop")) {
            assertTrue(body(cpp, name).contains("reference_set_exact("), name + "\n" + cpp);
            assertFalse(body(cpp, name).contains("::jnative::reference_set("), name + "\n" + cpp);
        }
        for(String name : List.of("unknown", "covariant", "base", "clones", "mixedTargets", "mixedValues", "loopMerge"))
            assertTrue(body(cpp, name).contains("::jnative::reference_set("), name + "\n" + cpp);
        assertFalse(body(cpp, "store").contains("RootFrame<"), cpp);
        assertTrue(body(cpp, "loop").contains("loop_safepoints.poll()"), cpp);
        var result = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* NestedReferenceStoreProof::"
                + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        return cpp.substring(declaration.start(), cpp.indexOf("\n}", declaration.end()));
    }
}
