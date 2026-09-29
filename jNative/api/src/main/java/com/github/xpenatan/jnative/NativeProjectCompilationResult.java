package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * Successfully compiled retained project, without launching its output.
 *
 * @param project     retained native project
 * @param artifact    primary artifact reported by the target build
 * @param outputFiles all produced artifacts
 * @param diagnostics exact-build private artifacts, or null when diagnostics are unavailable or disabled
 */
public record NativeProjectCompilationResult(
        NativeProject project,
        NativeArtifact artifact,
        Set<Path> outputFiles,
        NativeDiagnosticsBundle diagnostics) {
    public NativeProjectCompilationResult(
            NativeProject project,
            Path executable,
            Set<Path> outputFiles,
            NativeDiagnosticsBundle diagnostics) {
        this(
                project,
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

    public NativeProjectCompilationResult(
            NativeProject project, Path executable, Set<Path> outputFiles) {
        this(project, executable, outputFiles, null);
    }

    public NativeProjectCompilationResult {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(artifact, "artifact");
        outputFiles = BuildPaths.files(outputFiles, "outputFiles");
        if(!outputFiles.contains(artifact.path()))
            throw new IllegalArgumentException("outputFiles must include the primary artifact");
    }
}
