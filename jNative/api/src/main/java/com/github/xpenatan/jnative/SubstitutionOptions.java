package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable explicit provider selection. Classpath membership never activates a provider. */
public record SubstitutionOptions(boolean useBuiltinSubstitutions,
        List<Path> providerPaths, List<Path> dependencyPaths,
        List<SubstitutionProvider> providers, Map<String, String> classPreferences,
        Map<MethodReference, String> methodPreferences) {
    public SubstitutionOptions {
        providerPaths = paths(providerPaths, "providerPaths");
        dependencyPaths = paths(dependencyPaths, "dependencyPaths");
        providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        var classes = new LinkedHashMap<String, String>();
        Objects.requireNonNull(classPreferences, "classPreferences").forEach((target, provider) -> {
            String name = SubstitutionNames.binaryName(target, "class preference target");
            String id = SubstitutionNames.providerId(provider);
            String previous = classes.putIfAbsent(name, id);
            if(previous != null && !previous.equals(id))
                throw new IllegalArgumentException("Conflicting class substitution preferences: " + name);
        });
        classPreferences = Map.copyOf(classes);
        var methods = new LinkedHashMap<MethodReference, String>();
        Objects.requireNonNull(methodPreferences, "methodPreferences").forEach((target, provider) ->
                methods.put(Objects.requireNonNull(target, "method preference target"), SubstitutionNames.providerId(provider)));
        methodPreferences = Map.copyOf(methods);
    }

    public static SubstitutionOptions defaults() {
        return new SubstitutionOptions(true, List.of(), List.of(), List.of(), Map.of(), Map.of());
    }

    private static List<Path> paths(List<Path> values, String label) {
        var paths = new LinkedHashSet<Path>();
        for(Path value : Objects.requireNonNull(values, label)) paths.add(BuildPaths.absolute(value, label + " entry"));
        return List.copyOf(paths);
    }
}
