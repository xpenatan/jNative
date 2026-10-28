package com.github.xpenatan.jnative.conformance;

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
import static org.junit.jupiter.api.Assertions.*;

class IdentityMapStorageTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void identityTablePreservesViewsDeletionAndCollectionWithoutAllocatingEntries(BuildType buildType) throws Exception {
        Path source = temporary.resolve("IdentityMapStorage.java");
        try(var input = getClass().getResourceAsStream("/fixtures/IdentityMapStorage.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "IdentityMapStorage"), Duration.ofSeconds(90));
        assertEquals(new ProcessHarness.Output(0, "identity-storage-ok\n"), expected);
        var result = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("IdentityMapStorage")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5)).build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(120), Map.of("JNATIVE_GC_INTERVAL", "1")));
        long small = allocations(result.executable(), 128), large = allocations(result.executable(), 512);
        // 384 additional keys plus logarithmically many table growths, not one
        // extra managed node per insertion. Allow fixed runtime-output variation.
        assertTrue(large - small >= 384 && large - small <= 400,
                "Unexpected insertion allocations: small=" + small + ", large=" + large);
    }

    private long allocations(Path executable, int size) throws Exception {
        var output = ProcessHarness.run(temporary, List.of(executable.toString(), Integer.toString(size)),
                Duration.ofSeconds(30), Map.of("JNATIVE_STATS", "1"));
        assertEquals(0, output.exitCode(), output.text());
        var match = Pattern.compile("JNATIVE_STATS allocations=(\\d+)").matcher(output.text());
        assertTrue(match.find(), output.text());
        return Long.parseLong(match.group(1));
    }
}
