package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Verifies a static sparse store followed by a fixed float array copy. */
record PackedFloatCopy(int count, List<Integer> lanes) {
    private static final Type FLOAT_ARRAY = Type.getType("[F");

    String guard(List<String> arguments) {
        return "::jnative::try_packed_float_copy<" + count + ", "
                + String.join(", ", lanes.stream().map(String::valueOf).toList()) + ">("
                + String.join(", ", arguments) + ")";
    }

    static PackedFloatCopy match(Program program, Program.Method method, Predicate<MethodInsnNode> arraycopy) {
        Type[] arguments = Type.getArgumentTypes(method.id().descriptor());
        if (arguments.length < 5 || arguments.length > 36 || !staticBody(method)
                || !Type.getReturnType(method.id().descriptor()).equals(Type.VOID_TYPE)
                || !arguments[0].equals(FLOAT_ARRAY) || !arguments[1].equals(Type.INT_TYPE)
                || !arguments[2].equals(FLOAT_ARRAY) || !arguments[3].equals(Type.INT_TYPE)) return null;
        for (int i = 4; i < arguments.length; ++i) {
            if (!arguments[i].equals(Type.FLOAT_TYPE)) return null;
        }

        Cursor wrapper = new Cursor(method);
        if (!wrapper.load(ALOAD, 0) || !wrapper.load(ILOAD, 1)) return null;
        for (int i = 4; i < arguments.length; ++i) {
            if (!wrapper.load(FLOAD, i)) return null;
        }
        if (!(wrapper.next() instanceof MethodInsnNode producer)
                || producer.getOpcode() != INVOKESTATIC || !producer.owner.equals(method.id().owner())
                || !producer.desc.equals("([FI" + "F".repeat(arguments.length - 4) + ")V")
                || !wrapper.load(ALOAD, 0) || !wrapper.load(ILOAD, 1)
                || !wrapper.load(ALOAD, 2) || !wrapper.load(ILOAD, 3)) return null;
        int count = wrapper.literal();
        if (count < 4 || count > 32 || count % 4 != 0
                || !(wrapper.next() instanceof MethodInsnNode copy) || !arraycopy.test(copy)
                || !wrapper.finish()) return null;

        Program.Method stores = program.methods().get(new MethodId(producer.owner, producer.name, producer.desc));
        if (stores == null || !staticBody(stores)) return null;
        Cursor body = new Cursor(stores);
        List<Integer> lanes = new ArrayList<>();
        for (int slot = 2; slot < arguments.length - 2; ++slot) {
            if (!body.load(ALOAD, 0) || !body.load(ILOAD, 1)) return null;
            int lane = 0;
            if (body.opcode() != FLOAD) {
                lane = body.literal();
                if (lane < 0 || !body.take(IADD)) return null;
            }
            if (lane >= count || (!lanes.isEmpty() && lane <= lanes.getLast())
                    || !body.load(FLOAD, slot) || !body.take(FASTORE)) return null;
            lanes.add(lane);
        }
        return body.finish() ? new PackedFloatCopy(count, List.copyOf(lanes)) : null;
    }

    private static boolean staticBody(Program.Method method) {
        return (method.bytecode().access & (ACC_STATIC | ACC_NATIVE | ACC_ABSTRACT | ACC_SYNCHRONIZED)) == ACC_STATIC
                && method.nativeBinding() == null && method.bytecode().tryCatchBlocks.isEmpty();
    }

    private static final class Cursor {
        private AbstractInsnNode position;

        Cursor(Program.Method method) {
            position = executable(method.bytecode().instructions.getFirst());
        }

        private static AbstractInsnNode executable(AbstractInsnNode instruction) {
            while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
            return instruction;
        }

        AbstractInsnNode next() {
            AbstractInsnNode result = position;
            if (result != null) position = executable(result.getNext());
            return result;
        }

        int opcode() {
            return position == null ? -1 : position.getOpcode();
        }

        boolean take(int opcode) {
            return opcode() == opcode && next() != null;
        }

        boolean load(int opcode, int slot) {
            return next() instanceof VarInsnNode load && load.getOpcode() == opcode && load.var == slot;
        }

        int literal() {
            AbstractInsnNode instruction = next();
            if (instruction == null) return -1;
            int opcode = instruction.getOpcode();
            if (opcode >= ICONST_0 && opcode <= ICONST_5) return opcode - ICONST_0;
            if (instruction instanceof IntInsnNode value && (opcode == BIPUSH || opcode == SIPUSH))
                return value.operand;
            return instruction instanceof LdcInsnNode value && value.cst instanceof Integer number ? number : -1;
        }

        boolean finish() {
            return take(RETURN) && position == null;
        }
    }
}
