package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.compiler.Program;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** A size-bounded pure expression from a verified managed static target. */
final class PrimitiveInline {
    private interface Value {
        Type type();
        String render(List<String> arguments);
    }

    private record Parameter(Type type, int index) implements Value {
        public String render(List<String> arguments) {
            return arguments.get(index);
        }
    }

    private record Constant(Type type, String source) implements Value {
        public String render(List<String> arguments) {
            return source;
        }
    }

    private record Binary(JavaArithmetic operation, Value left, Value right) implements Value {
        public Type type() {
            return operation.type();
        }

        public String render(List<String> arguments) {
            return operation.render(left.render(arguments), right.render(arguments));
        }
    }

    private final Value result;

    private PrimitiveInline(Value result) {
        this.result = result;
    }

    String render(List<String> arguments) {
        return result.render(arguments);
    }

    static PrimitiveInline analyze(Program.Method method) {
        if (method == null || method.nativeSymbol() != null || method.nativeBinding() != null) return null;
        var body = method.bytecode();
        if ((body.access & (ACC_STATIC | ACC_SYNCHRONIZED | ACC_NATIVE | ACC_ABSTRACT)) != ACC_STATIC
                || !body.tryCatchBlocks.isEmpty() || method.id().name().startsWith("<")) return null;
        Type result = Type.getReturnType(body.desc);
        if (!numeric(result)) return null;
        Type[] arguments = Type.getArgumentTypes(body.desc);
        Map<Integer, Value> parameters = new HashMap<>();
        for (int index = 0, slot = 0; index < arguments.length; ++index) {
            Type type = arguments[index];
            if (!numeric(type)) return null;
            parameters.put(slot, new Parameter(type, index));
            slot += type.getSize();
        }
        var stack = new ArrayList<Value>();
        Value returned = null;
        int cost = 0;
        for (AbstractInsnNode instruction : body.instructions) {
            int opcode = instruction.getOpcode();
            if (opcode < 0) continue;
            if (++cost > 16 || returned != null) return null;
            if (instruction instanceof VarInsnNode load) {
                Value parameter = parameters.get(load.var);
                if (parameter == null || opcode != parameter.type().getOpcode(ILOAD)) return null;
                stack.add(parameter);
                continue;
            }
            Value constant = constant(instruction);
            if (constant != null) {
                stack.add(constant);
                continue;
            }
            if (opcode == result.getOpcode(IRETURN) && stack.size() == 1) {
                returned = stack.removeLast();
                if (!returned.type().equals(result)) return null;
                continue;
            }
            JavaArithmetic operation = JavaArithmetic.decode(opcode);
            if (operation == null || !operation.effects().pure() || stack.size() < 2) return null;
            Value right = stack.removeLast(), left = stack.removeLast();
            if (!operation.type().equals(left.type()) || !operation.rightType().equals(right.type())) return null;
            stack.add(new Binary(operation, left, right));
        }
        return returned == null ? null : new PrimitiveInline(returned);
    }

    private static boolean numeric(Type type) {
        return type.equals(Type.INT_TYPE) || type.equals(Type.LONG_TYPE)
                || type.equals(Type.FLOAT_TYPE) || type.equals(Type.DOUBLE_TYPE);
    }

    private static Constant constant(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        if (opcode >= ICONST_M1 && opcode <= ICONST_5)
            return new Constant(Type.INT_TYPE, CppEmitter.integer(opcode - ICONST_0));
        if (opcode == BIPUSH || opcode == SIPUSH)
            return new Constant(Type.INT_TYPE, CppEmitter.integer(((IntInsnNode)instruction).operand));
        if (opcode >= LCONST_0 && opcode <= LCONST_1)
            return new Constant(Type.LONG_TYPE, CppEmitter.longInteger(opcode - LCONST_0));
        if (opcode >= FCONST_0 && opcode <= FCONST_2)
            return new Constant(Type.FLOAT_TYPE, CppEmitter.floating(opcode - FCONST_0, true));
        if (opcode >= DCONST_0 && opcode <= DCONST_1)
            return new Constant(Type.DOUBLE_TYPE, CppEmitter.floating(opcode - DCONST_0, false));
        if (instruction instanceof LdcInsnNode literal) {
            if (literal.cst instanceof Integer value) return new Constant(Type.INT_TYPE, CppEmitter.integer(value));
            if (literal.cst instanceof Long value) return new Constant(Type.LONG_TYPE, CppEmitter.longInteger(value));
            if (literal.cst instanceof Float value) return new Constant(Type.FLOAT_TYPE, CppEmitter.floating(value, true));
            if (literal.cst instanceof Double value) return new Constant(Type.DOUBLE_TYPE, CppEmitter.floating(value, false));
        }
        return null;
    }
}
