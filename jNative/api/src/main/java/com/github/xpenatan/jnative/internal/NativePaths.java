package com.github.xpenatan.jnative.internal;

import com.github.xpenatan.jnative.CompilerException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Portable preflight policy; never rewrites or abbreviates application names.
 */
public final class NativePaths {
    public static final int MAX_PATH = 220;

    private NativePaths() {
    }

    public static void validate(Path path) {
        validate(path, MAX_PATH);
    }

    private static void validate(Path path, int limit) {
        Path absolute = path.toAbsolutePath().normalize();
        for(Path part : absolute)
            if(part.toString().length() > 255
                    || part.toString().getBytes(StandardCharsets.UTF_8).length > 255)
                throw new CompilerException(
                        "JN4010 Native filename component exceeds 255 characters/bytes: "
                                + absolute
                                + ". Use PACKAGE_DIRECTORIES if the full package filename is too long.");
        if(absolute.toString().length() > limit)
            throw new CompilerException(
                    "JN4010 Native path has "
                            + absolute.toString().length()
                            + " characters; portable limit is "
                            + limit
                            + ": "
                            + absolute
                            + ". Use a shorter checkout or the owning module's configured build directory.");
    }

    /**
     * Includes future archive staging, symbol artifacts and the minimum hashed CMake object path.
     */
    public static void validateProject(
            Path project, Path sources, Iterable<Path> inputs, String target) {
        validateProject(project, sources, inputs, target, project.resolve("b/release/0000000000"));
    }

    /**
     * Validates the actual isolated native build directory before invoking CMake.
     */
    public static void validateProject(
            Path project, Path sources, Iterable<Path> inputs, String target, Path binary) {
        validateProject(project, sources, inputs, target, binary, true);
    }

    /**
     * Checks archive paths only when native diagnostics are enabled.
     */
    public static void validateProject(
            Path project,
            Path sources,
            Iterable<Path> inputs,
            String target,
            Path binary,
            boolean archive) {
        String identity = "0".repeat(32);
        for(Path path : inputs) {
            validate(path);
            Path relative =
                    path.startsWith(sources)
                            ? Path.of("src").resolve(sources.relativize(path))
                            : project.relativize(path);
            if(archive && !relative.startsWith(".."))
                validate(binary.resolve("a").resolve(identity).resolve(relative));
            if(path.toString().endsWith(".cpp") || path.toString().endsWith(".c")) {
                Path object =
                        binary.resolve("CMakeFiles/jnative_classes.dir").resolve(relative + ".obj");
                if(object.toAbsolutePath().toString().length() > 240)
                    object =
                            binary.resolve("CMakeFiles/jnative_classes.dir")
                                    .resolve(identity)
                                    .resolve(path.getFileName() + ".obj");
                validate(object, 240);
            }
        }
        if(archive)
            validate(
                    project.resolve("diagnostics")
                            .resolve(identity)
                            .resolve("symbols")
                            .resolve(target + ".exe.debug"));
        if(archive) validate(binary.resolve("helper/jnative-diagnostics.exe"));
    }
}
