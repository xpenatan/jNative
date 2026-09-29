package com.github.xpenatan.jnative.compiler;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * Expands the JDK record bootstrap into ordinary, inspectable Java operations.
 */
final class RecordLowering {
    private RecordLowering() {
    }

    static void lower(ClassNode owner) {
        if((owner.access & ACC_RECORD) == 0) return;
        for(MethodNode method : owner.methods) {
            boolean generated = false;
            for(AbstractInsnNode instruction : method.instructions) {
                if(instruction instanceof InvokeDynamicInsnNode call
                        && call.bsm.getOwner().equals("java/lang/runtime/ObjectMethods")
                        && call.bsm.getName().equals("bootstrap")) generated = true;
            }
            if(!generated) continue;
            InsnList code = new InsnList();
            switch(method.name + method.desc) {
                case "equals(Ljava/lang/Object;)Z" -> equals(owner, code);
                case "hashCode()I" -> hash(owner, code);
                case "toString()Ljava/lang/String;" -> text(owner, code);
                default -> throw new IllegalArgumentException(
                        "Unsupported record bootstrap method " + method.name);
            }
            method.instructions = code;
            method.tryCatchBlocks.clear();
            if(method.localVariables != null) method.localVariables.clear();
            method.maxStack = 8;
            method.maxLocals = 2;
        }
    }

    private static void field(
            InsnList code, ClassNode owner, RecordComponentNode field, int receiver) {
        code.add(new VarInsnNode(ALOAD, receiver));
        if(receiver != 0) code.add(new TypeInsnNode(CHECKCAST, owner.name));
        code.add(new FieldInsnNode(GETFIELD, owner.name, field.name, field.descriptor));
    }

    private static void equals(ClassNode owner, InsnList code) {
        LabelNode different = new LabelNode();
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new TypeInsnNode(INSTANCEOF, owner.name));
        code.add(new JumpInsnNode(IFEQ, different));
        for(RecordComponentNode field : owner.recordComponents) {
            field(code, owner, field, 0);
            field(code, owner, field, 1);
            switch(Type.getType(field.descriptor).getSort()) {
                case Type.OBJECT, Type.ARRAY -> {
                    code.add(
                            new MethodInsnNode(
                                    INVOKESTATIC,
                                    "java/util/Objects",
                                    "equals",
                                    "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                                    false));
                    code.add(new JumpInsnNode(IFEQ, different));
                }
                case Type.LONG -> {
                    code.add(new InsnNode(LCMP));
                    code.add(new JumpInsnNode(IFNE, different));
                }
                case Type.FLOAT, Type.DOUBLE -> {
                    String wrapper = RuntimeLibrary.wrapper(field.descriptor);
                    code.add(
                            new MethodInsnNode(
                                    INVOKESTATIC,
                                    wrapper,
                                    "compare",
                                    "(" + field.descriptor + field.descriptor + ")I",
                                    false));
                    code.add(new JumpInsnNode(IFNE, different));
                }
                default -> code.add(new JumpInsnNode(IF_ICMPNE, different));
            }
        }
        code.add(new InsnNode(ICONST_1));
        code.add(new InsnNode(IRETURN));
        code.add(different);
        code.add(new InsnNode(ICONST_0));
        code.add(new InsnNode(IRETURN));
    }

    private static void hash(ClassNode owner, InsnList code) {
        code.add(new InsnNode(ICONST_0));
        for(RecordComponentNode field : owner.recordComponents) {
            code.add(new IntInsnNode(BIPUSH, 31));
            code.add(new InsnNode(IMUL));
            field(code, owner, field, 0);
            int sort = Type.getType(field.descriptor).getSort();
            if(sort == Type.OBJECT || sort == Type.ARRAY)
                code.add(
                        new MethodInsnNode(
                                INVOKESTATIC,
                                "java/util/Objects",
                                "hashCode",
                                "(Ljava/lang/Object;)I",
                                false));
            else
                code.add(
                        new MethodInsnNode(
                                INVOKESTATIC,
                                RuntimeLibrary.wrapper(field.descriptor),
                                "hashCode",
                                "(" + field.descriptor + ")I",
                                false));
            code.add(new InsnNode(IADD));
        }
        code.add(new InsnNode(IRETURN));
    }

    private static void append(InsnList code, String value) {
        code.add(new LdcInsnNode(value));
        code.add(
                new MethodInsnNode(
                        INVOKEVIRTUAL,
                        "java/lang/StringBuilder",
                        "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
                        false));
    }

    private static void text(ClassNode owner, InsnList code) {
        String simple = owner.name.substring(owner.name.lastIndexOf('/') + 1);
        for(InnerClassNode nested : owner.innerClasses)
            if(nested.name.equals(owner.name) && nested.innerName != null)
                simple = nested.innerName;
        code.add(new TypeInsnNode(NEW, "java/lang/StringBuilder"));
        code.add(new InsnNode(DUP));
        code.add(
                new MethodInsnNode(
                        INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));
        append(code, simple + "[");
        boolean first = true;
        for(RecordComponentNode field : owner.recordComponents) {
            append(code, (first ? "" : ", ") + field.name + "=");
            first = false;
            field(code, owner, field, 0);
            String descriptor =
                    switch(Type.getType(field.descriptor).getSort()) {
                        case Type.OBJECT, Type.ARRAY -> "Ljava/lang/Object;";
                        case Type.BYTE, Type.SHORT -> "I";
                        default -> field.descriptor;
                    };
            code.add(
                    new MethodInsnNode(
                            INVOKEVIRTUAL,
                            "java/lang/StringBuilder",
                            "append",
                            "(" + descriptor + ")Ljava/lang/StringBuilder;",
                            false));
        }
        append(code, "]");
        code.add(
                new MethodInsnNode(
                        INVOKEVIRTUAL,
                        "java/lang/StringBuilder",
                        "toString",
                        "()Ljava/lang/String;",
                        false));
        code.add(new InsnNode(ARETURN));
    }
}
