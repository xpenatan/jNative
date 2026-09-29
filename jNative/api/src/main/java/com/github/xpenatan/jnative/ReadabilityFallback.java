package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;
import java.nio.file.Path;

/**
 * A method emitted as low-level C++ because readable reconstruction was unavailable.
 * This describes generated source, not a runtime event or a compilation failure.
 *
 * @param javaMethod qualified Java method name followed by its JVM descriptor
 * @param cppFile    generated C++ implementation file; normalized to an absolute path
 * @param reason     explanation of why readable reconstruction was unavailable
 */
public record ReadabilityFallback(String javaMethod, Path cppFile, String reason) {
    /**
     * Validates the description without inspecting the filesystem.
     */
    public ReadabilityFallback {
        javaMethod = BuildPaths.text(javaMethod, "javaMethod");
        cppFile = BuildPaths.absolute(cppFile, "cppFile");
        reason = BuildPaths.text(reason, "reason");
    }
}
