package com.github.xpenatan.jnative.conformance;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

/**
 * Keeps module-local native test paths within Windows path limits.
 */
public final class NativeTempDirectoryFactory implements TempDirFactory {
    @Override
    public Path createTempDirectory(AnnotatedElementContext element, ExtensionContext context)
            throws IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir"));
        Files.createDirectories(root);
        for(int attempt = 0; attempt < 8; ++attempt) {
            try {
                return Files.createDirectory(
                        root.resolve(UUID.randomUUID().toString().substring(0, 8)));
            } catch(FileAlreadyExistsException collision) {
                /* Retry without touching another test's directory. */
            }
        }
        throw new IOException("Cannot allocate a unique native test directory under " + root);
    }
}
