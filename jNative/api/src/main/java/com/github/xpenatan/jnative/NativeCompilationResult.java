package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Successfully compiled native output. Paths are absolute and normalized.
 * Constructing this value does not inspect the filesystem; the backend must verify
 * successful compilation and primary artifact creation before returning it.
 * The caller owns all output files. Creating this result never launches the executable.
 *
 * @param generation  the generated project that was compiled
 * @param artifact    primary artifact reported by the target build
 * @param diagnostics exact-build private artifacts, or null when diagnostics are unavailable or disabled
 * @param outputFiles immutable set of produced artifacts, including dependencies of static libraries
 */
public record NativeCompilationResult(
        NativeGenerationResult generation,
        NativeArtifact artifact,
        Set<Path> outputFiles,
        NativeDiagnosticsBundle diagnostics) {
    public NativeCompilationResult(
            NativeGenerationResult generation,
            Path executable,
            Set<Path> outputFiles,
            NativeDiagnosticsBundle diagnostics) {
        this(
                generation,
                new NativeArtifact(NativeArtifactKind.EXECUTABLE, executable, null),
                outputFiles,
                diagnostics);
    }

    /**
     * Compatibility accessor for executable builds.
     */
    public Path executable() {
        return artifact.executable();
    }

    /**
     * Creates an immutable result.
     *
     * @throws IllegalArgumentException if the executable is absent from the output file set
     */
    public NativeCompilationResult(
            NativeGenerationResult generation, Path executable, Set<Path> outputFiles) {
        this(generation, executable, outputFiles, null);
    }

    public NativeCompilationResult {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(artifact, "artifact");
        outputFiles = BuildPaths.files(outputFiles, "outputFiles");
        if(!outputFiles.contains(artifact.path())) {
            throw new IllegalArgumentException("outputFiles must include the primary artifact");
        }
    }
}
