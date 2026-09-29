package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Immutable reflection roots. Metadata retention and executable access are independent.
 */
public record ReflectionPlan(Set<String> classes, Set<MethodId> methods, Set<FieldId> fields) {
    public record FieldId(String owner, String name, String descriptor) {
    }

    public ReflectionPlan {
        classes = Collections.unmodifiableSet(new LinkedHashSet<>(classes));
        methods = Collections.unmodifiableSet(new LinkedHashSet<>(methods));
        fields = Collections.unmodifiableSet(new LinkedHashSet<>(fields));
    }

    public static ReflectionPlan empty() {
        return new ReflectionPlan(Set.of(), Set.of(), Set.of());
    }

    public static ClassNode objectClass() {
        ClassNode node = new ClassNode();
        node.name = "java/lang/Object";
        node.access = Opcodes.ACC_PUBLIC;
        for(String signature :
                List.of(
                        "equals(Ljava/lang/Object;)Z",
                        "hashCode()I",
                        "toString()Ljava/lang/String;",
                        "getClass()Ljava/lang/Class;",
                        "wait()V",
                        "wait(J)V",
                        "wait(JI)V",
                        "notify()V",
                        "notifyAll()V")) {
            int start = signature.indexOf('(');
            String name = signature.substring(0, start);
            int flags = Opcodes.ACC_PUBLIC;
            if(Set.of("getClass", "wait", "notify", "notifyAll").contains(name))
                flags |= Opcodes.ACC_FINAL;
            if(Set.of("getClass", "hashCode", "notify", "notifyAll").contains(name))
                flags |= Opcodes.ACC_NATIVE;
            node.methods.add(new MethodNode(flags, name, signature.substring(start), null, null));
        }
        return node;
    }

    public static ClassNode platformClass(String name) {
        if(name.equals("java/lang/Object")) return objectClass();
        if(!Set.of("java/lang/Runnable", "java/lang/Cloneable", "java/io/Serializable")
                .contains(name))
            throw error("Public member metadata for platform type is not supported: " + name);
        ClassNode node = new ClassNode();
        node.name = name;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT;
        if(name.equals("java/lang/Runnable"))
            node.methods.add(
                    new MethodNode(
                            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "run", "()V", null, null));
        return node;
    }

    public static ReflectionPlan create(
            List<ReflectionRegistration> registrations, Function<String, ClassNode> load) {
        return create(registrations, load, RuntimeLibrary::platform);
    }

    public static ReflectionPlan create(List<ReflectionRegistration> registrations,
            Function<String, ClassNode> load, Predicate<String> platform) {
        var classes = new LinkedHashSet<String>();
        var methods = new LinkedHashSet<MethodId>();
        var fields = new LinkedHashSet<FieldId>();
        for(var registration : registrations) {
            String owner = registration.className().replace('.', '/');
            if(platform.test(owner))
                throw error(
                        "Register application types; platform reflection is limited to inherited Object methods: "
                                + owner);
            ClassNode root = load.apply(owner);
            if((root.access & Opcodes.ACC_PUBLIC) == 0)
                throw error("Reflection requires a public class: " + owner);
            var hierarchy = new LinkedHashMap<String, ClassNode>();
            hierarchy(root, load, hierarchy, platform);
            classes.addAll(hierarchy.keySet());
            if(registration.kind() == ReflectionRegistration.Kind.METHOD) {
                MethodId id =
                        MethodResolver.resolve(
                                owner, registration.name(), registration.descriptor(), load, platform, PlatformBindings.Table.empty());
                ClassNode declaring = hierarchy.get(id.owner());
                if(declaring == null
                        || declaring.methods.stream()
                        .noneMatch(
                                m ->
                                        m.name.equals(id.name())
                                                && m.desc.equals(id.descriptor())
                                                && (m.access & Opcodes.ACC_PUBLIC) != 0))
                    throw error("Public reflection method not found: " + id);
                methods.add(id);
                continue;
            }
            boolean matched =
                    registration.kind() == ReflectionRegistration.Kind.METADATA
                            || registration.kind() == ReflectionRegistration.Kind.PUBLIC_MEMBERS;
            for(ClassNode node : hierarchy.values()) {
                for(MethodNode method : node.methods) {
                    if((method.access & Opcodes.ACC_PUBLIC) == 0 || method.name.equals("<clinit>"))
                        continue;
                    boolean constructor = method.name.equals("<init>");
                    if(constructor && !node.name.equals(owner)) continue;
                    if(!node.name.equals(owner)
                            && (node.access & Opcodes.ACC_INTERFACE) != 0
                            && (method.access & Opcodes.ACC_STATIC) != 0) continue;
                    boolean select =
                            switch(registration.kind()) {
                                case PUBLIC_MEMBERS -> true;
                                case CONSTRUCTOR -> constructor
                                        && method.desc.equals(registration.descriptor());
                                case METHOD -> !constructor
                                        && method.name.equals(registration.name())
                                        && method.desc.equals(registration.descriptor());
                                default -> false;
                            };
                    if(select) {
                        methods.add(new MethodId(node.name, method.name, method.desc));
                        matched = true;
                        // A named member selects the first matching declaration in lookup order.
                        if(registration.kind() != ReflectionRegistration.Kind.PUBLIC_MEMBERS)
                            break;
                    }
                }
                if(registration.kind() == ReflectionRegistration.Kind.FIELD
                        || registration.kind() == ReflectionRegistration.Kind.PUBLIC_MEMBERS)
                    for(FieldNode field : node.fields)
                        if((field.access & Opcodes.ACC_PUBLIC) != 0
                                && (registration.kind()
                                == ReflectionRegistration.Kind.PUBLIC_MEMBERS
                                || field.name.equals(registration.name()))) {
                            fields.add(new FieldId(node.name, field.name, field.desc));
                            matched = true;
                            if(registration.kind() == ReflectionRegistration.Kind.FIELD) break;
                        }
                if(matched
                        && registration.kind() != ReflectionRegistration.Kind.PUBLIC_MEMBERS
                        && registration.kind() != ReflectionRegistration.Kind.METADATA) break;
            }
            if(!matched)
                throw error(
                        "Public reflection member not found: "
                                + owner
                                + "."
                                + registration.name()
                                + registration.descriptor());
        }
        return new ReflectionPlan(classes, methods, fields);
    }

    private static void hierarchy(
            ClassNode node, Function<String, ClassNode> load, Map<String, ClassNode> result,
            Predicate<String> platform) {
        if(result.putIfAbsent(node.name, node) != null) return;
        for(String face : node.interfaces)
            hierarchy(
                    platform.test(face) ? platformClass(face) : load.apply(face),
                    load,
                    result, platform);
        if(node.superName != null && (node.access & Opcodes.ACC_INTERFACE) == 0) {
            if(node.superName.equals("java/lang/Object"))
                result.putIfAbsent(node.superName, objectClass());
            else if(!platform.test(node.superName))
                hierarchy(load.apply(node.superName), load, result, platform);
            else
                throw error(
                        "Public member metadata for platform superclass is not supported: "
                                + node.superName);
        }
    }

    private static CompilerException error(String message) {
        return new CompilerException("JN1101 " + message);
    }
}
