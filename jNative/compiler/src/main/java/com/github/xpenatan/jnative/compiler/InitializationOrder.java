package com.github.xpenatan.jnative.compiler;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

/**
 * The parent-first superinterface initialization order required by class initialization.
 */
public final class InitializationOrder {
    private InitializationOrder() {
    }

    public static List<ClassNode> defaultInterfaces(
            ClassNode owner, Function<String, ClassNode> lookup) {
        return defaultInterfaces(owner, lookup, RuntimeLibrary::platform);
    }

    public static List<ClassNode> defaultInterfaces(ClassNode owner,
            Function<String, ClassNode> lookup, Predicate<String> platform) {
        if((owner.access & Opcodes.ACC_INTERFACE) != 0) return List.of();
        var result = new ArrayList<ClassNode>();
        var seen = new HashSet<String>();
        for(String name : owner.interfaces) visit(name, lookup, seen, result, platform);
        return List.copyOf(result);
    }

    private static void visit(
            String name,
            Function<String, ClassNode> lookup,
            Set<String> seen,
            List<ClassNode> result, Predicate<String> platform) {
        if(!seen.add(name) || platform.test(name)) return;
        ClassNode node = lookup.apply(name);
        for(String parent : node.interfaces) visit(parent, lookup, seen, result, platform);
        if(node.methods.stream()
                .anyMatch(
                        m ->
                                !m.name.startsWith("<")
                                        && (m.access
                                        & (Opcodes.ACC_STATIC
                                        | Opcodes.ACC_ABSTRACT
                                        | Opcodes.ACC_PRIVATE))
                                        == 0)) result.add(node);
    }
}
