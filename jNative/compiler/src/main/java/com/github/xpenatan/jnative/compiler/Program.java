package com.github.xpenatan.jnative.compiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/**
 * Linked closed-world input and typed control-flow graphs, independent of C++ syntax.
 */
public record Program(
        MethodId entry,
        Map<String, ClassNode> classes,
        Map<MethodId, Method> methods,
        Set<String> instantiatedClasses,
        ReflectionPlan reflection,
        Map<String, InputOrigin> origins,
        SubstitutionRegistry substitutions) {
    public Program(MethodId entry, Map<String, ClassNode> classes, Map<MethodId, Method> methods,
            Set<String> instantiatedClasses, ReflectionPlan reflection, Map<String, InputOrigin> origins) {
        this(entry, classes, methods, instantiatedClasses, reflection, origins, null);
    }

    public boolean platform(String owner) {
        return substitutions == null ? RuntimeLibrary.platform(owner) : substitutions.platform(owner);
    }

    public PlatformBindings.Table bindings() {
        return substitutions == null ? PlatformBindings.defaults() : substitutions.platformBindings();
    }

    public boolean primitiveClassField(String owner, String name, String descriptor) {
        if(!RuntimeLibrary.primitiveClassField(owner, name, descriptor)) return false;
        var rule = substitutions == null ? null : substitutions.classReplacement(owner);
        return rule == null || rule.builtin();
    }

    public Program(
            MethodId entry,
            Map<String, ClassNode> classes,
            Map<MethodId, Method> methods,
            Set<String> instantiatedClasses,
            ReflectionPlan reflection) {
        this(entry, classes, methods, instantiatedClasses, reflection, Map.of());
    }

    public Program(
            MethodId entry,
            Map<String, ClassNode> classes,
            Map<MethodId, Method> methods,
            Set<String> instantiatedClasses) {
        this(entry, classes, methods, instantiatedClasses, ReflectionPlan.empty());
    }

    public Program {
        classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
        methods = Collections.unmodifiableMap(new LinkedHashMap<>(methods));
        instantiatedClasses = Set.copyOf(instantiatedClasses);
        origins = Map.copyOf(origins);
    }

    public record InputOrigin(
            String artifact, String member, String classSha256, String generatedFrom, String substitutionDonor) {
        public InputOrigin(String artifact, String member, String classSha256, String generatedFrom) {
            this(artifact, member, classSha256, generatedFrom, "");
        }
    }

    public record MethodId(String owner, String name, String descriptor) {
        @Override
        public String toString() {
            return owner.replace('/', '.') + "." + name + descriptor;
        }
    }

    public record Block(int start, int end, List<Integer> successors) {
    }

    public record Method(
            MethodId id,
            MethodNode bytecode,
            Frame<BasicValue>[] frames,
            List<Block> blocks,
            NativeBinding nativeBinding,
            List<MethodId> reachabilityPath) {
        public String nativeSymbol() {
            return nativeBinding == null ? null : nativeBinding.symbol();
        }
    }
}
