package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;
import java.nio.file.Path;

/**
 * A native project independent of Java input files and generation requests.
 *
 * @param directory      absolute CMake project root
 * @param targetFileName executable base name
 * @param editable       whether the snapshot has transferred source ownership to its developer
 */
public record NativeProject(Path directory, String targetFileName, boolean editable) {
    public NativeProject {
        directory = BuildPaths.absolute(directory, "directory");
        targetFileName = BuildPaths.fileName(targetFileName);
    }
}
