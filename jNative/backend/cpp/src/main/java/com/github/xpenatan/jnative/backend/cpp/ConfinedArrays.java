package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
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
    private final Set<AbstractInsnNode> borrowed =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<AbstractInsnNode, AbstractInsnNode> uses = new IdentityHashMap<>();
    private final Map<AbstractInsnNode, JavaIntRange> bounds = new IdentityHashMap<>();
    private final ArrayBorrowing borrowing;

    record Facts(Map<AbstractInsnNode, AbstractInsnNode> origins, Map<AbstractInsnNode, JavaIntRange> bounds,
                 Set<AbstractInsnNode> borrowed) {
        static final Facts EMPTY = new Facts(Map.of(), Map.of(), Set.of());

        JavaIntRange range(AbstractInsnNode instruction) {
            return bounds.getOrDefault(instruction, JavaIntRange.ANY);
        }

        boolean disjoint(AbstractInsnNode first, AbstractInsnNode second) {
            AbstractInsnNode left = origins.get(first), right = origins.get(second);
            // Unknown/shared storage stays conservative. A distinct fresh site or
            // non-overlapping integer ranges establish disjoint confined elements.
            return left != null && right != null && (left != right || range(first).disjoint(range(second)));
        }
    }

    private ConfinedArrays(ArrayBorrowing borrowing) {
        super(ASM9);
        this.borrowing = borrowing;
    }

    static Set<AbstractInsnNode> analyze(String owner, MethodNode method) {
        return analyze(owner, method, null);
    }

    static Set<AbstractInsnNode> analyze(String owner, MethodNode method, ArrayBorrowing borrowing) {
        return facts(owner, method, borrowing).origins.keySet();
    }

    static Facts facts(String owner, MethodNode method, ArrayBorrowing borrowing) {
        if(!method.tryCatchBlocks.isEmpty() || method.instructions.size() > 4096) return Facts.EMPTY;
        boolean hasArray = false;
        for(AbstractInsnNode instruction : method.instructions)
            if(instruction.getOpcode() == NEWARRAY) {
                hasArray = true;
                break;
            }
        if(!hasArray) return Facts.EMPTY;
        ConfinedArrays analysis = new ConfinedArrays(borrowing);
        try {
            new Analyzer<>(analysis).analyze(owner, method);
        } catch(AnalyzerException ignored) {
            return Facts.EMPTY;
        }
        analysis.uses.values().removeIf(analysis.escaped::contains);
        analysis.bounds.keySet().retainAll(analysis.uses.keySet());
        return new Facts(Map.copyOf(analysis.uses), Map.copyOf(analysis.bounds), Set.copyOf(analysis.borrowed));
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
    public BasicValue newValue(Type type) {
        return intValue(super.newValue(type), JavaIntRange.ANY);
    }

    @Override
    public BasicValue newOperation(AbstractInsnNode instruction) throws AnalyzerException {
        int opcode = instruction.getOpcode();
        JavaIntRange range = JavaIntRange.ANY;
        if (opcode >= ICONST_M1 && opcode <= ICONST_5) range = JavaIntRange.constant(opcode - ICONST_0);
        else if (opcode == BIPUSH || opcode == SIPUSH) range = JavaIntRange.constant(((IntInsnNode)instruction).operand);
        else if (instruction instanceof LdcInsnNode literal && literal.cst instanceof Integer value)
            range = JavaIntRange.constant(value);
        return intValue(super.newOperation(instruction), range);
    }

    private static BasicValue intValue(BasicValue value, JavaIntRange range) {
        return BasicValue.INT_VALUE.equals(value) ? new IntValue(range) : value;
    }

    private static JavaIntRange range(BasicValue value) {
        return value instanceof IntValue integer ? integer.range : JavaIntRange.ANY;
    }

    private void observe(AbstractInsnNode instruction, BasicValue value) {
        bounds.merge(instruction, range(value), JavaIntRange::merge);
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
            observe(instruction, value);
            return new ArrayValue(result.getType(), instruction);
        }
        if(instruction.getOpcode() == ARRAYLENGTH) use(instruction, value);
        else escape(value);
        JavaIntRange range = instruction instanceof IincInsnNode increment
                ? range(value).binary(IADD, JavaIntRange.constant(increment.incr))
                : instruction.getOpcode() == INEG
                ? JavaIntRange.constant(0).binary(ISUB, range(value)) : JavaIntRange.ANY;
        return intValue(result, range);
    }

    @Override
    public BasicValue binaryOperation(
            AbstractInsnNode instruction, BasicValue left, BasicValue right)
            throws AnalyzerException {
        int opcode = instruction.getOpcode();
        if(opcode >= IALOAD && opcode <= SALOAD && opcode != AALOAD && opcode != BALOAD) {
            use(instruction, left);
            observe(instruction, right);
        }
        else escape(left);
        escape(right);
        return intValue(super.binaryOperation(instruction, left, right), range(left).binary(opcode, range(right)));
    }

    @Override
    public BasicValue ternaryOperation(
            AbstractInsnNode instruction, BasicValue array, BasicValue index, BasicValue value)
            throws AnalyzerException {
        int opcode = instruction.getOpcode();
        if(opcode >= IASTORE && opcode <= SASTORE && opcode != AASTORE && opcode != BASTORE) {
            use(instruction, array);
            observe(instruction, index);
        }
        else escape(array);
        escape(index);
        escape(value);
        return super.ternaryOperation(instruction, array, index, value);
    }

    @Override
    public BasicValue naryOperation(AbstractInsnNode instruction, List<? extends BasicValue> values)
            throws AnalyzerException {
        for(int i = 0; i < values.size(); ++i) {
            if (!(values.get(i) instanceof ArrayValue array)) continue;
            if (borrowing != null && borrowing.borrows(instruction, i)) borrowed.add(array.allocation);
            else escape(array);
        }
        return intValue(super.naryOperation(instruction, values), JavaIntRange.ANY);
    }

    @Override
    public void returnOperation(
            AbstractInsnNode instruction, BasicValue value, BasicValue expected) {
        escape(value);
    }

    @Override
    public BasicValue merge(BasicValue left, BasicValue right) {
        if (left instanceof IntValue first && right instanceof IntValue second)
            return new IntValue(first.range.merge(second.range));
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

    private static final class IntValue extends BasicValue {
        final JavaIntRange range;

        IntValue(JavaIntRange range) {
            super(Type.INT_TYPE);
            this.range = range;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IntValue integer && range.equals(integer.range);
        }

        @Override
        public int hashCode() {
            return range.hashCode();
        }
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
