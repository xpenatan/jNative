package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Explicit registrations read from one artifact without executing provider code. */
public record SubstitutionProvider(String id, Path artifact,
        List<ClassSubstitution> classes, List<MethodSubstitution> methods) {
    public SubstitutionProvider {
        id = SubstitutionNames.providerId(id);
        artifact = BuildPaths.absolute(artifact, "substitution artifact");
        classes = List.copyOf(Objects.requireNonNull(classes, "classes"));
        methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
    }
}
