package com.github.xpenatan.jnative.compiler;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** Artifact-scoped bundled provider utility; compilation choices belong to SubstitutionRegistry. */
public final class ClassLibrary {
    private ClassLibrary() {}

    private static final class Builtin {
        static final SubstitutionProviderIndex INDEX = SubstitutionProviderIndex.builtin();
        static final Map<String, String> DONORS = donors();
        static final Map<String, String> ALIASES = aliases();
        private static Map<String, String> donors() {
            var result = new TreeMap<String, String>();
            for(String declaration : INDEX.artifact().classNames()) result.put(declaration, declaration);
            for(String declaration : INDEX.declarations()) {
                ClassNode node = INDEX.artifact().classNode(declaration);
                String target = null;
                for(var annotation : Annotations.all(node.visibleAnnotations, node.invisibleAnnotations)) {
                    if(annotation.desc.equals("Lcom/github/xpenatan/jnative/substitution/SubstituteClass;") && annotation.values != null)
                        for(int i = 0; i < annotation.values.size(); i += 2)
                            if(annotation.values.get(i).equals("value")) target = ((String)annotation.values.get(i + 1)).replace('.', '/');
                }
                if(target != null) {
                    result.remove(declaration);
                    result.put(target, declaration);
                }
            }
            return Map.copyOf(result);
        }
        private static Map<String, String> aliases() {
            var result = new TreeMap<String, String>();
            DONORS.forEach((target, donor) -> { if(!target.equals(donor)) result.put(donor, target); });
            return Map.copyOf(result);
        }
    }

    public static boolean contains(String name) {
        return Builtin.DONORS.containsKey(name);
    }

    public static Set<String> classes() { return Builtin.DONORS.keySet(); }

    /** Returns normalized bundled bytecode for inventory tools, without selecting external providers. */
    public static byte[] read(String name) {
        String donor = Builtin.DONORS.getOrDefault(name, name);
        ClassNode raw = Builtin.INDEX.artifact().classNode(donor);
        ClassNode effective = new SubstitutionRemapper(Builtin.ALIASES).remap(raw);
        var writer = new ClassWriter(0);
        effective.accept(writer);
        return writer.toByteArray();
    }
}
