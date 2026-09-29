package com.github.xpenatan.jnative;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Verified, exact-build diagnostic artifacts. Build-directory bundles are disposable.
 */
public record NativeDiagnosticsBundle(String buildId, Path directory, Path manifest, Path sources) {
    public NativeDiagnosticsBundle {
        Objects.requireNonNull(buildId, "buildId");
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        manifest = Objects.requireNonNull(manifest, "manifest").toAbsolutePath().normalize();
        sources = Objects.requireNonNull(sources, "sources").toAbsolutePath().normalize();
    }
}
