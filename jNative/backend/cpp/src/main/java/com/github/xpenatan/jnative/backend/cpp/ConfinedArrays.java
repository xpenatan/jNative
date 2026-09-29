package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proves fresh primitive arrays stay in the allocating method, including every alias and use.
 */
final class ConfinedArrays extends BasicInterpreter {
    private final Set<AbstractInsnNode> escaped =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<AbstractInsnNode, AbstractInsnNode> uses = new IdentityHashMap<>();

    private ConfinedArrays() {
        super(ASM9);
    }

    static Set<AbstractInsnNode> analyze(String owner, MethodNode method) {
        if(!method.tryCatchBlocks.isEmpty() || method.instructions.size() > 4096) return Set.of();
        boolean hasArray = false;
        for(AbstractInsnNode instruction : method.instructions)
            if(instruction.getOpcode() == NEWARRAY) {
                hasArray = true;
                break;
            }
        if(!hasArray) return Set.of();
        ConfinedArrays analysis = new ConfinedArrays();
        try {
            new Analyzer<>(analysis).analyze(owner, method);
        } catch(AnalyzerException ignored) {
            return Set.of();
        }
        Set<AbstractInsnNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        analysis.uses.forEach(
                (use, allocation) -> {
                    if(!analysis.escaped.contains(allocation)) result.add(use);
                });
        return result;
    }

    private void escape(BasicValue value) {
        if(value instanceof ArrayValue array) escaped.add(array.allocation);
    }

    private void use(AbstractInsnNode instruction, BasicValue value) {
        if(value instanceof ArrayValue array) {
            AbstractInsnNode previous = uses.put(instruction, array.allocation);
            if(previous != null && previous != array.allocation) {
                escaped.add(previous);
                escaped.add(array.allocation);
            }
        }
    }

    @Override
    public BasicValue copyOperation(AbstractInsnNode instruction, BasicValue value) {
        // Local stores/loads and stack duplication preserve the allocation identity.
        use(instruction, value);
        return value;
    }

    @Override
    public BasicValue unaryOperation(AbstractInsnNode instruction, BasicValue value)
            throws AnalyzerException {
        BasicValue result = super.unaryOperation(instruction, value);
        if(instruction instanceof IntInsnNode array
                && array.getOpcode() == NEWARRAY
                && array.operand != T_BYTE
                && array.operand != T_BOOLEAN) {
            uses.put(instruction, instruction);
            return new ArrayValue(result.getType(), instruction);
        }
        if(instruction.getOpcode() == ARRAYLENGTH) use(instruction, value);
        else escape(value);
        return result;
    }

    @Override
    public BasicValue binaryOperation(
            AbstractInsnNode instruction, BasicValue left, BasicValue right)
            throws AnalyzerException {
        int opcode = instruction.getOpcode();
        if(opcode >= IALOAD && opcode <= SALOAD && opcode != AALOAD && opcode != BALOAD)
            use(instruction, left);
        else escape(left);
        escape(right);
        return super.binaryOperation(instruction, left, right);
    }

    @Override
    public BasicValue ternaryOperation(
            AbstractInsnNode instruction, BasicValue array, BasicValue index, BasicValue value)
            throws AnalyzerException {
        int opcode = instruction.getOpcode();
        if(opcode >= IASTORE && opcode <= SASTORE && opcode != AASTORE && opcode != BASTORE)
            use(instruction, array);
        else escape(array);
        escape(index);
        escape(value);
        return super.ternaryOperation(instruction, array, index, value);
    }

    @Override
    public BasicValue naryOperation(AbstractInsnNode instruction, List<? extends BasicValue> values)
            throws AnalyzerException {
        for(BasicValue value : values) escape(value);
        return super.naryOperation(instruction, values);
    }

    @Override
    public void returnOperation(
            AbstractInsnNode instruction, BasicValue value, BasicValue expected) {
        escape(value);
    }

    @Override
    public BasicValue merge(BasicValue left, BasicValue right) {
        if(left instanceof ArrayValue || right instanceof ArrayValue) {
            if(left instanceof ArrayValue first
                    && right instanceof ArrayValue second
                    && first.allocation == second.allocation) return left;
        }
        else if(left.equals(right)) return left;
        // A mixture of allocations, incoming arrays or null must use the uniform atomic layout.
        escape(left);
        escape(right);
        return super.merge(left, right);
    }

    private static final class ArrayValue extends BasicValue {
        final AbstractInsnNode allocation;

        ArrayValue(Type type, AbstractInsnNode allocation) {
            super(type);
            this.allocation = allocation;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ArrayValue array && array.allocation == allocation;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(allocation);
        }
    }
}
