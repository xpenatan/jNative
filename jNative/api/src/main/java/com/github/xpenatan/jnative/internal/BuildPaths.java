package com.github.xpenatan.jnative.internal;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @hidden
 */
public final class BuildPaths {
    private BuildPaths() {
    }

    public static Path absolute(Path path, String name) {
        if(path == null) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return path.toAbsolutePath().normalize();
    }

    public static String text(String value, String name) {
        if(value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value;
    }

    public static Set<Path> files(Set<Path> paths, String name) {
        Objects.requireNonNull(paths, name);
        var normalized = new LinkedHashSet<Path>();
        for(Path path : paths) {
            normalized.add(absolute(path, name + " entry"));
        }
        return Set.copyOf(normalized);
    }

    public static String fileName(String name) {
        text(name, "targetFileName");
        if(name.equals(".")
                || name.equals("..")
                || name.endsWith(".")
                || name.endsWith(" ")
                || name.chars().anyMatch(c -> c < 32 || "/\\:<>\"|?*".indexOf(c) >= 0)
                || name.split("\\.", 2)[0]
                .toUpperCase(java.util.Locale.ROOT)
                .matches("CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]"))
            throw new IllegalArgumentException(
                    "targetFileName must be a file base name, not a path");
        return name;
    }
}
