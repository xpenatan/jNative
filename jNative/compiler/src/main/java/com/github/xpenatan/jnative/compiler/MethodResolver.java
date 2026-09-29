package com.github.xpenatan.jnative.compiler;

import java.util.*;
import java.util.function.Function;
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
        if(name.equals("<init>") || name.equals("<clinit>"))
            return new MethodId(owner, name, descriptor);
        var faces = new LinkedHashSet<String>();
        var visited = new HashSet<String>();
        for(String current = owner; current != null && visited.add(current); ) {
            if(RuntimeLibrary.platform(current)) {
                if(RuntimeLibrary.method(current, name, descriptor))
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
        for(String face : faces) interfaces(face, name, descriptor, classes, seen, matches);
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
                                                    new HashSet<>()));
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
            List<MethodId> matches) {
        if(!seen.add(face)) return;
        if(RuntimeLibrary.platform(face)) {
            if(RuntimeLibrary.method(face, name, desc))
                matches.add(new MethodId(face, name, desc));
            return;
        }
        ClassNode node = classes.apply(face);
        if(node == null) return;
        if(node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(desc)))
            matches.add(new MethodId(face, name, desc));
        for(String parent : node.interfaces)
            interfaces(parent, name, desc, classes, seen, matches);
    }

    private static boolean subinterface(
            String child, String parent, Function<String, ClassNode> classes, Set<String> seen) {
        if(child.equals(parent)) return true;
        if(!seen.add(child) || RuntimeLibrary.platform(child)) return false;
        ClassNode node = classes.apply(child);
        return node != null
                && node.interfaces.stream()
                .anyMatch(face -> subinterface(face, parent, classes, seen));
    }
}
