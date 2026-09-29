package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Successfully generated C++ sources and native project files.
 * Paths are absolute, normalized, immutable snapshots. Constructing this value does
 * not inspect the filesystem; the backend must report only files it successfully produced.
 * The caller owns these files and must retain them until compilation finishes.
 *
 * @param request              configuration used during generation
 * @param generatedFiles       emitted C++ sources, headers, and runtime files
 * @param projectFiles         emitted native build files, such as {@code CMakeLists.txt}
 * @param readabilityFallbacks immutable details for methods emitted as low-level C++
 */
public record NativeGenerationResult(
        NativeBuildRequest request,
        Set<Path> generatedFiles,
        Set<Path> projectFiles,
        List<ReadabilityFallback> readabilityFallbacks) {
    /**
     * Creates a result without reported readability fallbacks.
     */
    public NativeGenerationResult(
            NativeBuildRequest request, Set<Path> generatedFiles, Set<Path> projectFiles) {
        this(request, generatedFiles, projectFiles, List.of());
    }

    /**
     * Creates an immutable result; all arguments and collection entries are required.
     */
    public NativeGenerationResult {
        Objects.requireNonNull(request, "request");
        generatedFiles = BuildPaths.files(generatedFiles, "generatedFiles");
        projectFiles = BuildPaths.files(projectFiles, "projectFiles");
        readabilityFallbacks =
                List.copyOf(Objects.requireNonNull(readabilityFallbacks, "readabilityFallbacks"));
        for(ReadabilityFallback fallback : readabilityFallbacks) {
            if(!generatedFiles.contains(fallback.cppFile())) {
                throw new IllegalArgumentException(
                        "Readability fallback must reference a generated file: "
                                + fallback.cppFile());
            }
        }
    }
}
