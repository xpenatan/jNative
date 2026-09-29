package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Validated annotation-derived native contract shared by linking and C++ lowering. */
public record NativeBinding(String symbol, String include, boolean managed, boolean leaf,
                            boolean bounded, List<Callback> callbacks, List<Field> fields, List<String> types,
                            boolean boundedAccess, boolean runtimeOnly, boolean callbacksSynchronous, boolean managesRoots,
                            boolean registeredReflection) {
    private static final String OWNER = "[A-Za-z_$][A-Za-z_0-9$]*(/[A-Za-z_$][A-Za-z_0-9$]*)*";
    private static final String VALUE_TYPE = "(?:\\[*[ZBCSIJFD]|\\[*L" + OWNER + ";)";
    private static final String METHOD_TYPE = "\\(" + VALUE_TYPE + "*\\)(?:V|" + VALUE_TYPE + ")";
    public enum Invocation { VIRTUAL, INTERFACE, STATIC, SPECIAL }
    public record Field(String owner, String name, String descriptor) {}
    public record Callback(Program.MethodId method, int receiver, Invocation invocation) {}

    public NativeBinding {
        callbacks = List.copyOf(callbacks);
        fields = List.copyOf(fields);
        types = List.copyOf(types);
    }

    public static NativeBinding read(ClassNode owner, MethodNode method) {
        var annotations = Annotations.all(method.visibleAnnotations, method.invisibleAnnotations);
        String symbol = Annotations.value(annotations, "NativeImport");
        var id = new Program.MethodId(owner.name, method.name, method.desc);
        for(var annotation : annotations)
            if(annotation.desc.equals("Lcom/github/xpenatan/jnative/substitution/TargetField;")
                    || annotation.desc.equals("Lcom/github/xpenatan/jnative/substitution/OriginalMethod;"))
                throw error(id, "Substitution alias requires an active, valid owning method replacement");
        if(annotations.stream().filter(a -> a.desc.equals("Lcom/github/xpenatan/jnative/interop/NativeImport;")).count() > 1)
            throw error(id, "Native method has multiple executable bindings");
        if(symbol == null) {
            if((method.access & Opcodes.ACC_NATIVE) != 0)
                throw error(id, "NativeImport is required for every native method");
            return null;
        }
        if(!validMethodDescriptor(method.desc)) throw error(id, "Invalid native method descriptor");
        if(Annotations.value(annotations, "NativeExport") != null)
            throw error(id, "A method cannot be both an import and export");
        boolean managed = Annotations.flag(annotations, "NativeImport", "managed");
        boolean leaf = Annotations.flag(annotations, "NativeImport", "leaf");
        boolean bounded = Annotations.flag(annotations, "NativeImport", "bounded");
        if((method.access & Opcodes.ACC_STATIC) == 0)
            throw error(id, "NativeImport requires a static method");
        if(managed ? !symbol.matches("[A-Za-z_][A-Za-z_0-9]*(::[A-Za-z_][A-Za-z_0-9]*)*")
                : !symbol.matches("[A-Za-z][A-Za-z_0-9]*") || symbol.startsWith("jn_") || symbol.contains("__"))
            throw error(id, "Invalid native symbol: " + symbol);
        String include = Annotations.value(Annotations.all(owner.visibleAnnotations, owner.invisibleAnnotations), "NativeInclude");
        if(include == null) throw error(id, "NativeInclude is required on the declaring class");
        if(!include.matches("[A-Za-z0-9_./-]+") || include.startsWith("/") || include.contains(".."))
            throw error(id, "Invalid NativeInclude: " + include);
        List<?> values = list(annotations, "callbacks");
        List<?> fieldValues = list(annotations, "fields");
        List<?> receivers = list(annotations, "callbackReceivers");
        List<?> kinds = list(annotations, "callbackKinds");
        if(!receivers.isEmpty() && receivers.size() != values.size()
                || !kinds.isEmpty() && kinds.size() != values.size())
            throw error(id, "Callback metadata must have the same length as callbacks");
        if(!managed && (bounded || !values.isEmpty() || !receivers.isEmpty() || !kinds.isEmpty() || !fieldValues.isEmpty()))
            throw error(id, "Managed effects and callbacks require managed=true");
        if(managed && leaf) throw error(id, "managed=true cannot use the external leaf contract");
        if(bounded && (!values.isEmpty() || !fieldValues.isEmpty()))
            throw error(id, "A bounded import cannot invoke callbacks or access managed fields");
        if(bounded && (method.access & Opcodes.ACC_SYNCHRONIZED) != 0)
            throw error(id, "A bounded import cannot acquire a synchronized method monitor");
        var signature = new ArrayList<Type>(List.of(Type.getArgumentTypes(method.desc)));
        signature.add(Type.getReturnType(method.desc));
        if((leaf || bounded) && signature.stream().anyMatch(NativeBinding::reference))
            throw error(id, "A leaf or bounded import requires primitive parameters and return type");
        var callbacks = new ArrayList<Callback>();
        Type[] arguments = Type.getArgumentTypes(method.desc);
        for(int i = 0; i < values.size(); ++i) {
            String value = (String)values.get(i);
            int descriptor = value.indexOf('('), separator = value.lastIndexOf('.', descriptor);
            if(descriptor < 0 || separator <= 0 || separator + 1 == descriptor)
                throw error(id, "Invalid callback; expected internal/Owner.method(descriptor): " + value);
            String callbackOwner = value.substring(0, separator);
            String name = value.substring(separator + 1, descriptor);
            String desc = value.substring(descriptor);
            if(!callbackOwner.matches(OWNER)
                    || !name.matches("[A-Za-z_$][A-Za-z_0-9$]*"))
                throw error(id, "Invalid callback: " + value);
            if(!validMethodDescriptor(desc)) throw error(id, "Invalid callback descriptor: " + value);
            int receiver = receivers.isEmpty() ? 0 : (Integer)receivers.get(i);
            Invocation kind = Invocation.VIRTUAL;
            if(!kinds.isEmpty()) {
                String[] enumeration = (String[])kinds.get(i);
                if(enumeration.length != 2 || !enumeration[0].equals("Lcom/github/xpenatan/jnative/interop/NativeImport$Invocation;"))
                    throw error(id, "Invalid callback invocation annotation: " + value);
                try { kind = Invocation.valueOf(enumeration[1]); }
                catch(IllegalArgumentException error) { throw error(id, "Invalid callback invocation: " + value); }
            }
            if(receiver < -1 || kind != Invocation.STATIC && receiver >= 0
                    && (receiver >= arguments.length || !reference(arguments[receiver])))
                throw error(id, "Callback receiver must be a reference argument or -1: " + value);
            callbacks.add(new Callback(new Program.MethodId(callbackOwner, name, desc), receiver, kind));
        }
        var fields = new ArrayList<Field>();
        for(Object entry : fieldValues) {
            String value = (String)entry;
            int separator = value.indexOf(':'), member = value.lastIndexOf('.', separator);
            if(separator < 0 || member <= 0 || member + 1 == separator)
                throw error(id, "Invalid field; expected internal/Owner.field:descriptor: " + value);
            String fieldOwner = value.substring(0, member), name = value.substring(member + 1, separator);
            String descriptor = value.substring(separator + 1);
            if(!fieldOwner.matches(OWNER) || !name.matches("[A-Za-z_$][A-Za-z_0-9$]*") || !validValueDescriptor(descriptor))
                throw error(id, "Invalid managed field descriptor: " + value);
            fields.add(new Field(fieldOwner, name, descriptor));
        }
        var types = new ArrayList<String>();
        for(Object type : list(annotations, "types")) {
            String name = (String)type;
            if(!name.matches(OWNER)) throw error(id, "Invalid managed type dependency: " + name);
            types.add(name);
        }
        boolean boundedAccess = Annotations.flag(annotations, "NativeImport", "boundedAccess");
        if(boundedAccess && (!managed || !callbacks.isEmpty() || !fields.isEmpty()
                || list(annotations, "targets").isEmpty() || (method.access & Opcodes.ACC_SYNCHRONIZED) != 0))
            throw error(id, "boundedAccess requires a managed platform target without callbacks, fields or a monitor");
        boolean runtimeOnly = Annotations.flag(annotations, "NativeImport", "runtimeOnly");
        if(runtimeOnly && !managed) throw error(id, "runtimeOnly requires managed=true");
        boolean callbacksSynchronous = Annotations.flag(annotations, "NativeImport", "callbacksSynchronous");
        if(callbacksSynchronous && !managed) throw error(id, "callbacksSynchronous requires managed=true");
        boolean managesRoots = Annotations.flag(annotations, "NativeImport", "managesRoots");
        if(managesRoots && !managed) throw error(id, "managesRoots requires managed=true");
        boolean registeredReflection = Annotations.flag(annotations, "NativeImport", "registeredReflection");
        if(registeredReflection && !managed) throw error(id, "registeredReflection requires managed=true");
        if(registeredReflection && (bounded || boundedAccess))
            throw error(id, "registeredReflection cannot use bounded or boundedAccess promises");
        return new NativeBinding(symbol, include, managed, leaf, bounded, callbacks, fields, types, boundedAccess, runtimeOnly, callbacksSynchronous, managesRoots, registeredReflection);
    }

    /** Conservative proof for bypassing a platform helper's initialization adapter. */
    public static boolean trivialInitialization(ClassNode helper) {
        return helper != null && "java/lang/Object".equals(helper.superName) && helper.interfaces.isEmpty()
                && helper.methods.stream().noneMatch(member -> member.name.equals("<clinit>"));
    }

    public static boolean validMethodDescriptor(String descriptor) {
        if(descriptor == null || !descriptor.matches(METHOD_TYPE) || !validDimensions(descriptor)) return false;
        int units = 0;
        for(Type argument : Type.getArgumentTypes(descriptor)) units += argument.getSize();
        return units <= 255;
    }

    public static boolean validValueDescriptor(String descriptor) {
        return descriptor != null && descriptor.matches(VALUE_TYPE) && validDimensions(descriptor);
    }

    private static boolean validDimensions(String descriptor) {
        int dimensions = 0;
        for(int index = 0; index < descriptor.length(); ++index) {
            if(descriptor.charAt(index) == '[') {
                if(++dimensions > 255) return false;
            } else dimensions = 0;
        }
        return true;
    }

    private static List<?> list(List<AnnotationNode> annotations, String property) {
        Object value = Annotations.property(annotations, "NativeImport", property);
        return value == null ? List.of() : (List<?>)value;
    }

    private static boolean reference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    private static CompilerException error(Program.MethodId id, String detail) {
        return new CompilerException("JN2001 " + detail + ": " + id);
    }
}
