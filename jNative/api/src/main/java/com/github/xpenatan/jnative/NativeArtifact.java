package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Primary target artifact and its CMake configuration directory, when available.
 */
public record NativeArtifact(NativeArtifactKind kind, Path path, Path buildDirectory) {
    public NativeArtifact {
        Objects.requireNonNull(kind, "kind");
        path = BuildPaths.absolute(path, "path");
        if(buildDirectory != null)
            buildDirectory = BuildPaths.absolute(buildDirectory, "buildDirectory");
    }

    public Path executable() {
        if(kind != NativeArtifactKind.EXECUTABLE)
            throw new IllegalStateException("Native output is " + kind + "; use artifact().path()");
        return path;
    }
}
