package com.github.xpenatan.jnative;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CBuildResultTest {
    @TempDir
    Path temporary;

    @Test
    void resultsOwnImmutableNormalizedFileSnapshots() {
        var request =
                NativeBuilder.create()
                        .classpath(temporary)
                        .mainClass("example.Main")
                        .buildRoot(temporary.resolve("output"))
                        .request();
        var files = new HashSet<>(Set.of(Path.of("build/generated/../app.cpp")));
        var projects = new HashSet<>(Set.of(Path.of("build/CMakeLists.txt")));
        var fallback =
                new ReadabilityFallback(
                        "example.Main.loop(I)I",
                        Path.of("build/generated/../app.cpp"),
                        "control flow re-enters a structured region");
        var fallbacks = new ArrayList<>(List.of(fallback));
        var generated = new NativeGenerationResult(request, files, projects, fallbacks);
        files.clear();
        projects.clear();
        fallbacks.clear();

        Path executable = Path.of("build/native/../app.bin");
        var artifacts = new HashSet<>(Set.of(executable));
        var compiled = new NativeCompilationResult(generated, executable, artifacts);
        artifacts.clear();

        assertEquals(Set.of(Path.of("build/app.cpp").toAbsolutePath()), generated.generatedFiles());
        assertEquals(
                Set.of(Path.of("build/CMakeLists.txt").toAbsolutePath()), generated.projectFiles());
        assertEquals(Set.of(Path.of("build/app.bin").toAbsolutePath()), compiled.outputFiles());
        assertThrows(UnsupportedOperationException.class, () -> generated.generatedFiles().clear());
        assertThrows(UnsupportedOperationException.class, () -> generated.projectFiles().clear());
        assertThrows(UnsupportedOperationException.class, () -> compiled.outputFiles().clear());
        assertEquals(List.of(fallback), generated.readabilityFallbacks());
        assertEquals(Path.of("build/app.cpp").toAbsolutePath(), fallback.cppFile());
        assertEquals(
                generated.readabilityFallbacks(), compiled.generation().readabilityFallbacks());
        assertThrows(
                UnsupportedOperationException.class,
                () -> generated.readabilityFallbacks().clear());
        assertTrue(
                new NativeGenerationResult(
                        request, generated.generatedFiles(), generated.projectFiles())
                        .readabilityFallbacks()
                        .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> new NativeGenerationResult(request, Set.of(), Set.of(), List.of(fallback)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new NativeCompilationResult(generated, executable, Set.of()));
    }
}
