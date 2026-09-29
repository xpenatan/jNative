package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Lowers standard LambdaMetafactory sites to traced objects and ordinary methods.
 */
final class LambdaLowering {
    private static final Map<Integer, String> WRAPPERS =
            Map.of(
                    Type.BOOLEAN,
                    "Boolean",
                    Type.BYTE,
                    "Byte",
                    Type.CHAR,
                    "Character",
                    Type.SHORT,
                    "Short",
                    Type.INT,
                    "Integer",
                    Type.LONG,
                    "Long",
                    Type.FLOAT,
                    "Float",
                    Type.DOUBLE,
                    "Double");

    static Map<String, ClassNode> lower(ClassNode owner) {
        var generated = new LinkedHashMap<String, ClassNode>();
        int ordinal = 0;
        for(MethodNode method : owner.methods) {
            for(AbstractInsnNode instruction : method.instructions.toArray()) {
                if(!(instruction instanceof InvokeDynamicInsnNode site)
                        || !site.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                        || !site.bsm.getName().equals("metafactory")
                        || site.bsmArgs.length != 3) continue;
                if(!(site.bsmArgs[0] instanceof Type sam)
                        || !(site.bsmArgs[1] instanceof Handle target)
                        || !(site.bsmArgs[2] instanceof Type instantiated)) continue;
                String name = owner.name + "$jNativeLambda$" + ordinal++;
                ClassNode lambda = create(name, owner.sourceFile, site, sam, target, instantiated);
                generated.put(name, lambda);
                method.instructions.set(
                        site, new MethodInsnNode(INVOKESTATIC, name, "create", site.desc, false));
            }
        }
        return generated;
    }

    private static ClassNode create(
            String name,
            String source,
            InvokeDynamicInsnNode site,
            Type sam,
            Handle target,
            Type instantiated) {
        if(!Set.of(
                        H_INVOKESTATIC,
                        H_INVOKEVIRTUAL,
                        H_INVOKEINTERFACE,
                        H_INVOKESPECIAL,
                        H_NEWINVOKESPECIAL)
                .contains(target.getTag()))
            throw new CompilerException(
                    "JN1005 Unsupported lambda implementation handle: " + target);
        Type[] captures = Type.getArgumentTypes(site.desc), supplied = sam.getArgumentTypes();
        Type[] typed = instantiated.getArgumentTypes();
        var expected = new ArrayList<Type>();
        boolean constructor = target.getTag() == H_NEWINVOKESPECIAL;
        boolean receiver = target.getTag() != H_INVOKESTATIC && !constructor;
        if(receiver) expected.add(Type.getObjectType(target.getOwner()));
        expected.addAll(List.of(Type.getArgumentTypes(target.getDesc())));
        if(captures.length + supplied.length != expected.size() || supplied.length != typed.length)
            throw new CompilerException("JN1004 Invalid lambda arity: " + name);
        ClassNode node = new ClassNode();
        node.version = V17;
        node.access = ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC;
        node.name = name;
        node.superName = "java/lang/Object";
        node.sourceFile = source;
        node.interfaces.add(Type.getReturnType(site.desc).getInternalName());
        for(int i = 0; i < captures.length; ++i)
            node.fields.add(
                    new FieldNode(
                            ACC_PRIVATE | ACC_FINAL,
                            "capture" + i,
                            captures[i].getDescriptor(),
                            null,
                            null));
        String constructorType = Type.getMethodDescriptor(Type.VOID_TYPE, captures);
        MethodNode init = method(ACC_PUBLIC, "<init>", constructorType);
        init.instructions.add(new VarInsnNode(ALOAD, 0));
        init.instructions.add(
                new MethodInsnNode(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        int local = 1;
        for(int i = 0; i < captures.length; ++i) {
            init.instructions.add(new VarInsnNode(ALOAD, 0));
            init.instructions.add(new VarInsnNode(captures[i].getOpcode(ILOAD), local));
            init.instructions.add(
                    new FieldInsnNode(PUTFIELD, name, "capture" + i, captures[i].getDescriptor()));
            local += captures[i].getSize();
        }
        init.instructions.add(new InsnNode(RETURN));
        node.methods.add(init);
        MethodNode factory = method(ACC_PUBLIC | ACC_STATIC, "create", site.desc);
        if(receiver && captures.length > 0) {
            factory.instructions.add(new VarInsnNode(ALOAD, 0));
            factory.instructions.add(
                    new MethodInsnNode(
                            INVOKESTATIC,
                            "java/util/Objects",
                            "requireNonNull",
                            "(Ljava/lang/Object;)Ljava/lang/Object;",
                            false));
            factory.instructions.add(new InsnNode(POP));
        }
        factory.instructions.add(new TypeInsnNode(NEW, name));
        factory.instructions.add(new InsnNode(DUP));
        local = 0;
        for(Type capture : captures) {
            factory.instructions.add(new VarInsnNode(capture.getOpcode(ILOAD), local));
            local += capture.getSize();
        }
        factory.instructions.add(
                new MethodInsnNode(INVOKESPECIAL, name, "<init>", constructorType, false));
        factory.instructions.add(new InsnNode(ARETURN));
        node.methods.add(factory);
        MethodNode invoke = method(ACC_PUBLIC | ACC_FINAL, site.name, sam.getDescriptor());
        invoke.maxStack += expected.stream().mapToInt(Type::getSize).sum();
        if(constructor) {
            invoke.instructions.add(new TypeInsnNode(NEW, target.getOwner()));
            invoke.instructions.add(new InsnNode(DUP));
        }
        for(int i = 0; i < captures.length; ++i) {
            invoke.instructions.add(new VarInsnNode(ALOAD, 0));
            invoke.instructions.add(
                    new FieldInsnNode(GETFIELD, name, "capture" + i, captures[i].getDescriptor()));
            adapt(invoke.instructions, captures[i], expected.get(i));
        }
        local = 1;
        for(int i = 0; i < supplied.length; ++i) {
            invoke.instructions.add(new VarInsnNode(supplied[i].getOpcode(ILOAD), local));
            adapt(invoke.instructions, supplied[i], typed[i]);
            adapt(invoke.instructions, typed[i], expected.get(captures.length + i));
            local += supplied[i].getSize();
        }
        int opcode =
                switch(target.getTag()) {
                    case H_INVOKESTATIC -> INVOKESTATIC;
                    case H_INVOKEVIRTUAL -> INVOKEVIRTUAL;
                    case H_INVOKEINTERFACE -> INVOKEINTERFACE;
                    default -> INVOKESPECIAL;
                };
        invoke.instructions.add(
                new MethodInsnNode(
                        opcode,
                        target.getOwner(),
                        target.getName(),
                        target.getDesc(),
                        target.isInterface()));
        Type returned =
                constructor
                        ? Type.getObjectType(target.getOwner())
                        : Type.getReturnType(target.getDesc());
        adapt(invoke.instructions, returned, instantiated.getReturnType());
        adapt(invoke.instructions, instantiated.getReturnType(), sam.getReturnType());
        invoke.instructions.add(new InsnNode(sam.getReturnType().getOpcode(IRETURN)));
        node.methods.add(invoke);
        return node;
    }

    private static MethodNode method(int access, String name, String descriptor) {
        var method = new MethodNode(access, name, descriptor, null, null);
        int size = (access & ACC_STATIC) == 0 ? 1 : 0;
        for(Type argument : Type.getArgumentTypes(descriptor)) size += argument.getSize();
        method.maxLocals = size;
        method.maxStack = size + 32;
        return method;
    }

    private static boolean reference(Type type) {
        return type.getSort() == Type.ARRAY || type.getSort() == Type.OBJECT;
    }

    private static void adapt(InsnList code, Type from, Type to) {
        if(from.equals(to)) return;
        if(to.equals(Type.VOID_TYPE)) {
            if(!from.equals(Type.VOID_TYPE))
                code.add(new InsnNode(from.getSize() == 2 ? POP2 : POP));
            return;
        }
        if(from.equals(Type.VOID_TYPE))
            throw new CompilerException("JN1004 Void lambda result cannot produce " + to);
        if(reference(from) && reference(to)) {
            if(!to.equals(Type.getType(Object.class)))
                code.add(new TypeInsnNode(CHECKCAST, to.getInternalName()));
        }
        else if(!reference(from) && reference(to)) {
            String wrapper = "java/lang/" + WRAPPERS.get(from.getSort());
            code.add(
                    new MethodInsnNode(
                            INVOKESTATIC,
                            wrapper,
                            "valueOf",
                            "(" + from.getDescriptor() + ")L" + wrapper + ";",
                            false));
            if(!to.getInternalName().equals(wrapper) && !to.equals(Type.getType(Object.class)))
                code.add(new TypeInsnNode(CHECKCAST, to.getInternalName()));
        }
        else if(reference(from)) {
            Type primitive = to;
            String wrapper = null;
            for(var entry : WRAPPERS.entrySet())
                if(from.getInternalName().equals("java/lang/" + entry.getValue())) {
                    wrapper = from.getInternalName();
                    primitive = primitive(entry.getKey());
                }
            if(wrapper == null)
                wrapper =
                        to.getSort() == Type.BOOLEAN
                                ? "java/lang/Boolean"
                                : to.getSort() == Type.CHAR
                                ? "java/lang/Character"
                                : "java/lang/Number";
            code.add(new TypeInsnNode(CHECKCAST, wrapper));
            code.add(
                    new MethodInsnNode(
                            INVOKEVIRTUAL,
                            wrapper,
                            primitive.getClassName() + "Value",
                            "()" + primitive.getDescriptor(),
                            false));
            adapt(code, primitive, to);
        }
        else {
            int f = from.getSort(), t = to.getSort();
            if(f <= Type.INT && t <= Type.INT) return;
            int opcode =
                    f <= Type.INT
                            ? (t == Type.LONG
                            ? I2L
                            : t == Type.FLOAT ? I2F : t == Type.DOUBLE ? I2D : -1)
                            : f == Type.LONG
                            ? (t == Type.FLOAT ? L2F : t == Type.DOUBLE ? L2D : -1)
                            : f == Type.FLOAT && t == Type.DOUBLE ? F2D : -1;
            if(opcode < 0)
                throw new CompilerException(
                        "JN1005 Unsupported lambda conversion " + from + " to " + to);
            code.add(new InsnNode(opcode));
        }
    }

    private static Type primitive(int sort) {
        return switch(sort) {
            case Type.BOOLEAN -> Type.BOOLEAN_TYPE;
            case Type.BYTE -> Type.BYTE_TYPE;
            case Type.CHAR -> Type.CHAR_TYPE;
            case Type.SHORT -> Type.SHORT_TYPE;
            case Type.INT -> Type.INT_TYPE;
            case Type.LONG -> Type.LONG_TYPE;
            case Type.FLOAT -> Type.FLOAT_TYPE;
            default -> Type.DOUBLE_TYPE;
        };
    }
}
