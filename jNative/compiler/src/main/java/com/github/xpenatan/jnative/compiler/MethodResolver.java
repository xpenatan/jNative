package com.github.xpenatan.jnative.compiler;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.tree.ClassNode;
import com.github.xpenatan.jnative.compiler.Program.MethodId;

/**
 * Class declarations take precedence over maximally specific interface declarations.
 */
public final class MethodResolver {
    private MethodResolver() {
    }

    public static MethodId resolve(
            String owner, String name, String descriptor, Function<String, ClassNode> classes) {
        return resolve(owner, name, descriptor, classes, RuntimeLibrary::platform, PlatformBindings.defaults());
    }

    public static MethodId resolve(String owner, String name, String descriptor,
            Function<String, ClassNode> classes, Predicate<String> platform, PlatformBindings.Table bindings) {
        if(name.equals("<init>") || name.equals("<clinit>"))
            return new MethodId(owner, name, descriptor);
        var faces = new LinkedHashSet<String>();
        var visited = new HashSet<String>();
        for(String current = owner; current != null && visited.add(current); ) {
            if(platform.test(current)) {
                if(bindings.find(new MethodId(current, name, descriptor)) != null
                        || RuntimeLibrary.method(current, name, descriptor))
                    return new MethodId(current, name, descriptor);
                current = RuntimeLibrary.parent(current);
            }
            else {
                ClassNode node = classes.apply(current);
                if(node == null) break;
                if(node.methods.stream()
                        .anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)))
                    return new MethodId(current, name, descriptor);
                faces.addAll(node.interfaces);
                current = node.superName;
            }
        }
        var matches = new ArrayList<MethodId>();
        var seen = new HashSet<String>();
        for(String face : faces) interfaces(face, name, descriptor, classes, seen, matches, platform, bindings);
        for(MethodId candidate : matches) {
            boolean shadowed =
                    matches.stream()
                            .anyMatch(
                                    other ->
                                            !other.equals(candidate)
                                                    && subinterface(
                                                    other.owner(),
                                                    candidate.owner(),
                                                    classes,
                                                    new HashSet<>(), platform));
            if(!shadowed) return candidate;
        }
        return new MethodId(owner, name, descriptor);
    }

    private static void interfaces(
            String face,
            String name,
            String desc,
            Function<String, ClassNode> classes,
            Set<String> seen,
            List<MethodId> matches, Predicate<String> platform, PlatformBindings.Table bindings) {
        if(!seen.add(face)) return;
        if(platform.test(face)) {
            if(bindings.find(new MethodId(face, name, desc)) != null || RuntimeLibrary.method(face, name, desc))
                matches.add(new MethodId(face, name, desc));
            return;
        }
        ClassNode node = classes.apply(face);
        if(node == null) return;
        if(node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc)))
            matches.add(new MethodId(face, name, desc));
        for(String parent : node.interfaces)
            interfaces(parent, name, desc, classes, seen, matches, platform, bindings);
    }

    private static boolean subinterface(
            String child, String parent, Function<String, ClassNode> classes, Set<String> seen,
            Predicate<String> platform) {
        if(child.equals(parent)) return true;
        if(!seen.add(child) || platform.test(child)) return false;
        ClassNode node = classes.apply(child);
        return node != null
                && node.interfaces.stream()
                .anyMatch(face -> subinterface(face, parent, classes, seen, platform));
    }
}
