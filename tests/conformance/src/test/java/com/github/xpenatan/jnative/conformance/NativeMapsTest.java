package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class NativeMapsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void mapsPreserveManagedEntriesCallbacksAndCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("NativeMaps.java");
        try(var input = getClass().getResourceAsStream("/fixtures/NativeMaps.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "NativeMaps"), Duration.ofSeconds(90));
        assertEquals(new ProcessHarness.Output(0, "maps-ok\n"), expected);
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("NativeMaps")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeDefine("CMAKE_CXX_STANDARD", buildType == BuildType.DEBUG ? "11" : "17")
                .cmakeDefine("CMAKE_CXX_STANDARD_REQUIRED", "ON")
                .cmakeDefine("CMAKE_CXX_EXTENSIONS", "OFF")
                .cmakeDefine("JNATIVE_FEATURES", buildType == BuildType.DEBUG ? "PORTABLE" : "AUTO").timeout(Duration.ofMinutes(5));
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String hash = Files.readString(generated.resolve("classes/java.util.HashMap.cpp"));
        String tree = Files.readString(generated.resolve("classes/java.util.TreeMap.cpp"));
        for(String helper : List.of("hashmap_capacity", "hashmap_find", "hashmap_put", "hashmap_remove", "hashmap_clear"))
            assertTrue(hash.contains("::jnative::" + helper + "("), helper);
        for(String helper : List.of("treemap_find", "treemap_put", "treemap_remove", "treemap_higher", "treemap_extreme", "treemap_clear"))
            assertTrue(tree.contains("::jnative::" + helper + "("), helper);
        assertTrue(hash.contains("NativeFieldAccess<"));
        assertTrue(tree.contains("NativeFieldAccess<"));
        assertFalse(hash.contains("advanceBucket"));
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(120), Map.of("JNATIVE_GC_INTERVAL", "1")));
        assertEquals(new ProcessHarness.Output(0, "map-hooks-ok\n"),
                ProcessHarness.run(temporary, List.of(result.executable().toString(), "hooks"),
                        Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
