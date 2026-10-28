package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.compiler.MethodResolver;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

/** Proves array arguments do not escape through any reachable dispatch target. */
final class ArrayBorrowing {
    private final Program program;
    private final Map<MethodId, BitSet> summaries = new HashMap<>();
    private final Set<MethodId> visiting = new HashSet<>();
    private final Map<MethodId, Set<MethodId>> virtualTargets = new HashMap<>();

    ArrayBorrowing(Program program) { this.program = program; }

    boolean borrows(AbstractInsnNode instruction, int operand) {
        Set<MethodId> targets = callTargets(instruction);
        return !targets.isEmpty() && targets.stream().allMatch(target -> summary(target).get(operand));
    }

    Set<MethodId> callTargets(AbstractInsnNode instruction) {
        if(!(instruction instanceof MethodInsnNode call)) return Set.of();
        MethodId id = new MethodId(call.owner, call.name, call.desc);
        return call.getOpcode() == INVOKESTATIC || call.getOpcode() == INVOKESPECIAL
                ? Set.of(resolve(id)) : virtualTargets.computeIfAbsent(id, this::targets);
    }

    private MethodId resolve(MethodId id) {
        MethodId resolved = MethodResolver.resolve(id.owner(), id.name(), id.descriptor(),
                program.classes()::get, program::platform, program.bindings());
        if(resolved == null) resolved = id;
        var binding = program.bindings().find(resolved);
        return binding == null ? resolved : binding.helper();
    }

    private Set<MethodId> targets(MethodId id) {
        Set<MethodId> result = new LinkedHashSet<>();
        for(String type : program.instantiatedClasses())
            if(subtype(type, id.owner())) result.add(resolve(new MethodId(type, id.name(), id.descriptor())));
        for(MethodId bridge : program.bindings().interfaceImplementations(id)) result.add(resolve(bridge));
        MethodId declared = resolve(id);
        var method = program.methods().get(declared);
        if(result.isEmpty() || method != null && (method.bytecode().access & ACC_ABSTRACT) == 0)
            result.add(declared);
        return result;
    }

    private boolean subtype(String type, String parent) {
        if(type.equals(parent)) return true;
        var owner = program.classes().get(type);
        if(owner == null) return false;
        if(owner.superName != null && subtype(owner.superName, parent)) return true;
        return owner.interfaces.stream().anyMatch(value -> subtype(value, parent));
    }

    private BitSet summary(MethodId id) {
        BitSet known = summaries.get(id);
        if(known != null) return known;
        var method = program.methods().get(id);
        if(method == null || !visiting.add(id)) return new BitSet();
        BitSet result = new BitSet();
        try {
            if(method.nativeBinding() != null) {
                method.nativeBinding().borrowedArrays().forEach(result::set);
            } else if(method.nativeSymbol() == null
                    && (method.bytecode().access & (ACC_NATIVE | ACC_ABSTRACT)) == 0
                    && method.bytecode().instructions.size() <= 4096) {
                Flow flow = new Flow(method.bytecode());
                new Analyzer<>(flow).analyze(id.owner(), method.bytecode());
                result.or(flow.parameters);
                result.andNot(flow.escaped);
            }
        } catch(AnalyzerException | RuntimeException failure) {
            result.clear();
        } finally {
            visiting.remove(id);
        }
        summaries.put(id, result);
        return result;
    }

    private final class Flow extends BasicInterpreter {
        final BitSet parameters = new BitSet(), escaped = new BitSet();
        final Map<Integer, Integer> localParameters = new HashMap<>();

        Flow(MethodNode method) {
            super(ASM9);
            int local = 0, operand = 0;
            if((method.access & ACC_STATIC) == 0) {
                localParameters.put(local++, operand);
                parameters.set(operand++);
            }
            for(Type type : Type.getArgumentTypes(method.desc)) {
                if(type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
                    localParameters.put(local, operand);
                    parameters.set(operand);
                }
                local += type.getSize();
                operand++;
            }
        }

        @Override public BasicValue newParameterValue(boolean instance, int local, Type type) {
            Integer parameter = localParameters.get(local);
            if(parameter == null) return super.newParameterValue(instance, local, type);
            BitSet origins = new BitSet();
            origins.set(parameter);
            return new BorrowValue(type, origins);
        }

        void escape(BasicValue value) {
            if(value instanceof BorrowValue borrowed) escaped.or(borrowed.origins);
        }

        @Override public BasicValue copyOperation(AbstractInsnNode instruction, BasicValue value) {
            return value;
        }

        @Override public BasicValue unaryOperation(AbstractInsnNode instruction, BasicValue value)
                throws AnalyzerException {
            BasicValue result = super.unaryOperation(instruction, value);
            int opcode = instruction.getOpcode();
            if(opcode == CHECKCAST && value instanceof BorrowValue borrowed)
                return new BorrowValue(result.getType(), borrowed.origins);
            if(opcode != ARRAYLENGTH && opcode != IFNULL && opcode != IFNONNULL) escape(value);
            return result;
        }

        @Override public BasicValue binaryOperation(AbstractInsnNode instruction, BasicValue left, BasicValue right)
                throws AnalyzerException {
            int opcode = instruction.getOpcode();
            if(opcode < IALOAD || opcode > SALOAD || opcode == AALOAD || opcode == BALOAD) escape(left);
            escape(right);
            return super.binaryOperation(instruction, left, right);
        }

        @Override public BasicValue ternaryOperation(AbstractInsnNode instruction, BasicValue array,
                BasicValue index, BasicValue value) throws AnalyzerException {
            int opcode = instruction.getOpcode();
            if(opcode < IASTORE || opcode > SASTORE || opcode == AASTORE || opcode == BASTORE) escape(array);
            escape(index);
            escape(value);
            return super.ternaryOperation(instruction, array, index, value);
        }

        @Override public BasicValue naryOperation(AbstractInsnNode instruction, List<? extends BasicValue> values)
                throws AnalyzerException {
            for(int i = 0; i < values.size(); ++i)
                if(values.get(i) instanceof BorrowValue && !borrows(instruction, i)) escape(values.get(i));
            return super.naryOperation(instruction, values);
        }

        @Override public void returnOperation(AbstractInsnNode instruction, BasicValue value, BasicValue expected) {
            escape(value);
        }

        @Override public BasicValue merge(BasicValue left, BasicValue right) {
            if(left.equals(right)) return left;
            BasicValue merged = super.merge(left, right);
            BitSet origins = new BitSet();
            if(left instanceof BorrowValue first) origins.or(first.origins);
            if(right instanceof BorrowValue second) origins.or(second.origins);
            return origins.isEmpty() ? merged : new BorrowValue(merged.getType(), origins);
        }
    }

    private static final class BorrowValue extends BasicValue {
        final BitSet origins;
        BorrowValue(Type type, BitSet origins) {
            super(type);
            this.origins = (BitSet)origins.clone();
        }
        @Override public boolean equals(Object other) {
            return other instanceof BorrowValue value && super.equals(other) && origins.equals(value.origins);
        }
        @Override public int hashCode() { return 31 * super.hashCode() + origins.hashCode(); }
    }
}
