package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Exact platform API routes derived exclusively from packaged NativeImport annotations. */
public final class PlatformBindings {
    public record Target(Program.MethodId api, Program.MethodId helper, boolean instance, NativeBinding binding) {}
    public record StaticField(String owner, String name, String descriptor) {}
    public record FieldTarget(StaticField field, Program.MethodId helper, NativeBinding binding) {}
    private static final Map<Program.MethodId, Target> TARGETS = read();
    private static final Map<StaticField, FieldTarget> FIELDS = readFields();

    private PlatformBindings() {}

    public static Map<Program.MethodId, Target> targets() { return TARGETS; }

    public static Map<StaticField, FieldTarget> fields() { return FIELDS; }
    public static FieldTarget field(String owner, String name, String descriptor) {
        return FIELDS.get(new StaticField(owner, name, descriptor));
    }

    public static Target find(Program.MethodId api) {
        // Arrays share Object.clone's exact contract; their component type remains runtime metadata.
        if(api.owner().startsWith("[") && api.name().equals("clone") && api.descriptor().equals("()Ljava/lang/Object;"))
            api = new Program.MethodId("java/lang/Object", "clone", api.descriptor());
        return TARGETS.get(api);
    }

    /** Runtime representation adapters required by ordinary Java interface dispatch. */
    public static List<Program.MethodId> interfaceImplementations(Program.MethodId call) {
        if(call.owner().equals("java/lang/CharSequence")) {
            String name = call.name().equals("subSequence") ? "substring" : call.name();
            String descriptor = call.descriptor().replace("Ljava/lang/CharSequence;", "Ljava/lang/String;");
            var target = new Program.MethodId("java/lang/String", name, descriptor);
            return find(target) == null ? List.of() : List.of(target);
        }
        if(call.owner().equals("java/lang/Comparable") && call.name().equals("compareTo")
                && call.descriptor().equals("(Ljava/lang/Object;)I"))
            return List.of(new Program.MethodId("java/lang/String", "compareTo", "(Ljava/lang/String;)I"),
                    new Program.MethodId("java/nio/file/Path", "compareTo", "(Ljava/nio/file/Path;)I"));
        return List.of();
    }

    public static List<Target> read(ClassNode owner, MethodNode method) {
        var annotations = Annotations.all(method.visibleAnnotations, method.invisibleAnnotations);
        Object values = Annotations.property(annotations, "NativeImport", "targets");
        if(values == null) return List.of();
        NativeBinding binding = NativeBinding.read(owner, method);
        var helper = new Program.MethodId(owner.name, method.name, method.desc);
        if(binding == null || !binding.managed()) throw error(helper, "Platform targets require a managed NativeImport");
        boolean instance = Annotations.flag(annotations, "NativeImport", "instance");
        var result = new ArrayList<Target>();
        for(Object encoded : (List<?>)values) {
            String value = (String)encoded;
            int start = value.indexOf('('), separator = value.lastIndexOf('.', start);
            if(start < 0 || separator <= 0 || separator + 1 == start)
                throw error(helper, "Invalid platform target: " + value);
            String targetOwner = value.substring(0, separator), name = value.substring(separator + 1, start), descriptor = value.substring(start);
            if(!targetOwner.matches("[A-Za-z_$][A-Za-z_0-9$]*(/[A-Za-z_$][A-Za-z_0-9$]*)*")
                    || !name.matches("(?:[A-Za-z_$][A-Za-z_0-9$]*|<init>)"))
                throw error(helper, "Invalid platform target: " + value);
            if(!NativeBinding.validMethodDescriptor(descriptor))
                throw error(helper, "Invalid platform target descriptor: " + value);
            Type[] arguments;
            Type resultType;
            try {
                arguments = Type.getArgumentTypes(descriptor);
                resultType = Type.getReturnType(descriptor);
                if(!Type.getMethodDescriptor(resultType, arguments).equals(descriptor)) throw new IllegalArgumentException();
            } catch(RuntimeException error) { throw error(helper, "Invalid platform target descriptor: " + value); }
            if(name.equals("<init>") && (!instance || !resultType.equals(Type.VOID_TYPE)))
                throw error(helper, "Constructor target requires instance=true and void result: " + value);
            Type[] parameters = Type.getArgumentTypes(method.desc);
            int offset = instance ? 1 : 0;
            if(parameters.length != arguments.length + offset
                    || instance && !compatible(Type.getObjectType(targetOwner), parameters[0]))
                throw error(helper, "Platform receiver/parameter mismatch: " + value);
            for(int i = 0; i < arguments.length; ++i)
                if(!compatible(arguments[i], parameters[i + offset]))
                    throw error(helper, "Platform parameter " + i + " mismatch: " + value);
            if(!compatible(resultType, Type.getReturnType(method.desc)))
                throw error(helper, "Platform result mismatch: " + value);
            result.add(new Target(new Program.MethodId(targetOwner, name, descriptor), helper, instance, binding));
        }
        return List.copyOf(result);
    }

    public static List<FieldTarget> readStaticFields(ClassNode owner, MethodNode method) {
        var annotations = Annotations.all(method.visibleAnnotations, method.invisibleAnnotations);
        Object values = Annotations.property(annotations, "NativeImport", "staticFields");
        if(values == null) return List.of();
        var helper = new Program.MethodId(owner.name, method.name, method.desc);
        NativeBinding binding = NativeBinding.read(owner, method);
        if(binding == null || !binding.managed() || Annotations.flag(annotations, "NativeImport", "instance")
                || Type.getArgumentTypes(method.desc).length != 0)
            throw error(helper, "Platform static fields require a managed zero-argument static getter");
        var result = new ArrayList<FieldTarget>();
        for(Object encoded : (List<?>)values) {
            String value = (String)encoded;
            int separator = value.indexOf(':'), member = value.lastIndexOf('.', separator);
            if(separator < 0 || member <= 0 || member + 1 == separator)
                throw error(helper, "Invalid static field; expected internal/Owner.field:descriptor: " + value);
            String fieldOwner = value.substring(0, member), name = value.substring(member + 1, separator);
            String descriptor = value.substring(separator + 1);
            if(!fieldOwner.matches("[A-Za-z_$][A-Za-z_0-9$]*(/[A-Za-z_$][A-Za-z_0-9$]*)*")
                    || !name.matches("[A-Za-z_$][A-Za-z_0-9$]*")
                    || !NativeBinding.validValueDescriptor(descriptor)
                    || descriptor.equals("V") || !compatible(Type.getType(descriptor), Type.getReturnType(method.desc)))
                throw error(helper, "Invalid platform static field type: " + value);
            result.add(new FieldTarget(new StaticField(fieldOwner, name, descriptor), helper, binding));
        }
        return List.copyOf(result);
    }

    private static Map<StaticField, FieldTarget> readFields() {
        var result = new LinkedHashMap<StaticField, FieldTarget>();
        for(String name : new TreeSet<>(ClassLibrary.classes())) {
            var owner = new ClassNode();
            new ClassReader(ClassLibrary.read(name)).accept(owner, ClassReader.SKIP_CODE);
            for(MethodNode method : owner.methods)
                for(FieldTarget target : readStaticFields(owner, method)) {
                    FieldTarget previous = result.putIfAbsent(target.field(), target);
                    if(previous != null) throw error(target.helper(), "Duplicate platform static field " + target.field());
                }
        }
        return Collections.unmodifiableMap(result);
    }

    private static boolean compatible(Type target, Type helper) {
        return target.equals(helper) || (target.getSort() == Type.OBJECT || target.getSort() == Type.ARRAY)
                && helper.equals(Type.getType(Object.class));
    }

    private static Map<Program.MethodId, Target> read() {
        var result = new LinkedHashMap<Program.MethodId, Target>();
        for(String name : new TreeSet<>(ClassLibrary.classes())) {
            var owner = new ClassNode();
            new ClassReader(ClassLibrary.read(name)).accept(owner, ClassReader.SKIP_CODE);
            for(MethodNode method : owner.methods)
                for(Target target : read(owner, method)) {
                    Target previous = result.putIfAbsent(target.api(), target);
                    if(previous != null) throw error(target.helper(), "Duplicate platform target " + target.api() + " on " + previous.helper());
                }
        }
        return Collections.unmodifiableMap(result);
    }

    private static CompilerException error(Program.MethodId helper, String detail) {
        return new CompilerException("JN2001 " + detail + ": " + helper);
    }
}
