package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

/** Storage facts for existing allocations; this analysis never makes an escaping array confined. */
final class ArrayStorage extends BasicInterpreter {
    enum Mode { UNKNOWN, ORDINARY, CONFINED }
    record Facts(Set<AbstractInsnNode> ordinary, Set<AbstractInsnNode> confined) {}
    private final Set<AbstractInsnNode> confined;
    private final OwnedArrays fields;
    private final Map<Integer, Mode> parameters = new HashMap<>();
    private final Map<AbstractInsnNode, Mode> reads = new IdentityHashMap<>();
    private final Map<MethodInsnNode, List<Mode>> calls = new IdentityHashMap<>();

    private ArrayStorage(MethodNode method, Set<AbstractInsnNode> confined, OwnedArrays fields, List<Mode> inputs) {
        super(ASM9);
        this.confined = confined;
        this.fields = fields;
        if((method.access & ACC_STATIC) != 0) {
            int local = 0, argument = 0;
            for(Type type : Type.getArgumentTypes(method.desc)) {
                if(argument < inputs.size()) parameters.put(local, inputs.get(argument));
                local += type.getSize();
                argument++;
            }
        }
    }

    static Map<MethodId, List<Mode>> inputs(Program program, OwnedArrays fields, ArrayBorrowing borrowing) {
        Map<MethodId, List<Mode>> result = new HashMap<>();
        Set<MethodId> external = new HashSet<>(program.reflection().methods());
        external.add(program.entry());
        for(Program.Method method : program.methods().values()) {
            if(exported(method.bytecode().visibleAnnotations) || exported(method.bytecode().invisibleAnnotations))
                external.add(method.id());
            if(method.nativeBinding() != null) {
                for(var callback : method.nativeBinding().callbacks()) {
                    MethodId id = callback.method();
                    external.addAll(borrowing.callTargets(new MethodInsnNode(INVOKESTATIC,
                            id.owner(), id.name(), id.descriptor(), false)));
                }
            }
        }
        // One conservative pass: only facts established in each caller itself.
        // Forwarded unknown arguments are not guessed or made known by traversal order.
        for(Program.Method method : program.methods().values()) {
            if(method.nativeSymbol() != null) continue;
            Set<AbstractInsnNode> confined = new HashSet<>(fields.instructions(method.id()));
            confined.addAll(ConfinedArrays.analyze(method.id().owner(), method.bytecode(), borrowing));
            ArrayStorage flow = analyze(method.id().owner(), method.bytecode(), confined, fields, List.of());
            for(AbstractInsnNode instruction : method.bytecode().instructions) {
                if(!(instruction instanceof MethodInsnNode call) || call.getOpcode() != INVOKESTATIC) continue;
                List<Mode> modes = flow == null ? null : flow.calls.get(call);
                if(flow != null && modes == null) continue; // Unreachable bytecode.
                if(modes == null) modes = Collections.nCopies(Type.getArgumentTypes(call.desc).length, Mode.UNKNOWN);
                for(MethodId target : borrowing.callTargets(call)) {
                    Program.Method callee = program.methods().get(target);
                    if(callee == null || callee.nativeSymbol() != null || (callee.bytecode().access & ACC_STATIC) == 0) continue;
                    result.merge(target, modes, ArrayStorage::mergeInputs);
                }
            }
        }
        for(MethodId id : external) result.remove(id);
        return result;
    }

    private static boolean exported(List<AnnotationNode> annotations) {
        return annotations != null && annotations.stream().anyMatch(annotation -> annotation.desc.equals(
                "Lcom/github/xpenatan/jnative/interop/NativeExport;"));
    }

    private static List<Mode> mergeInputs(List<Mode> first, List<Mode> second) {
        List<Mode> result = new ArrayList<>();
        for(int i = 0; i < first.size(); i++)
            result.add(i < second.size() && first.get(i) == second.get(i) ? first.get(i) : Mode.UNKNOWN);
        return List.copyOf(result);
    }

    static Facts facts(String owner, MethodNode method, Set<AbstractInsnNode> confined,
            OwnedArrays fields, List<Mode> inputs) {
        ArrayStorage flow = analyze(owner, method, confined, fields, inputs);
        Set<AbstractInsnNode> ordinaryReads = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AbstractInsnNode> confinedReads = Collections.newSetFromMap(new IdentityHashMap<>());
        if(flow != null) flow.reads.forEach((instruction, mode) -> {
            if(mode == Mode.ORDINARY) ordinaryReads.add(instruction);
            else if(mode == Mode.CONFINED) confinedReads.add(instruction);
        });
        return new Facts(ordinaryReads, confinedReads);
    }

    private static ArrayStorage analyze(String owner, MethodNode method, Set<AbstractInsnNode> confined,
            OwnedArrays fields, List<Mode> inputs) {
        if(method.instructions.size() > 4096) return null;
        ArrayStorage flow = new ArrayStorage(method, confined, fields, inputs);
        try {
            new Analyzer<>(flow).analyze(owner, method);
            return flow;
        } catch(AnalyzerException | RuntimeException failure) {
            return null;
        }
    }

    private static Mode mode(BasicValue value) {
        return value instanceof Storage storage ? storage.mode : Mode.UNKNOWN;
    }
    private void read(AbstractInsnNode instruction, BasicValue value) {
        reads.merge(instruction, mode(value), (first, second) -> first == second ? first : Mode.UNKNOWN);
    }
    @Override public BasicValue newParameterValue(boolean instance, int local, Type type) {
        Mode mode = parameters.getOrDefault(local, Mode.UNKNOWN);
        return mode == Mode.UNKNOWN ? super.newParameterValue(instance, local, type) : new Storage(type, mode);
    }
    @Override public BasicValue copyOperation(AbstractInsnNode instruction, BasicValue value) {
        read(instruction, value);
        return value;
    }
    @Override public BasicValue unaryOperation(AbstractInsnNode instruction, BasicValue value) throws AnalyzerException {
        BasicValue result = super.unaryOperation(instruction, value);
        if(instruction.getOpcode() == NEWARRAY)
            result = new Storage(result.getType(), confined.contains(instruction) ? Mode.CONFINED : Mode.ORDINARY);
        else if(instruction instanceof FieldInsnNode field && field.getOpcode() == GETFIELD) {
            if(confined.contains(instruction)) result = new Storage(result.getType(), Mode.CONFINED);
            else if(fields.ordinaryArray(field)) result = new Storage(result.getType(), Mode.ORDINARY);
        } else if(instruction.getOpcode() == CHECKCAST && value instanceof Storage storage)
            result = new Storage(result.getType(), storage.mode);
        if(result != null) read(instruction, result);
        return result;
    }
    @Override public BasicValue binaryOperation(AbstractInsnNode instruction, BasicValue first, BasicValue second)
            throws AnalyzerException {
        if(instruction.getOpcode() >= IALOAD && instruction.getOpcode() <= SALOAD) read(instruction, first);
        return super.binaryOperation(instruction, first, second);
    }
    @Override public BasicValue ternaryOperation(AbstractInsnNode instruction, BasicValue first, BasicValue second,
            BasicValue third) throws AnalyzerException {
        read(instruction, first);
        return super.ternaryOperation(instruction, first, second, third);
    }
    @Override public BasicValue naryOperation(AbstractInsnNode instruction, List<? extends BasicValue> values)
            throws AnalyzerException {
        if(instruction instanceof MethodInsnNode call && call.getOpcode() == INVOKESTATIC)
            calls.merge(call, values.stream().map(ArrayStorage::mode).toList(), ArrayStorage::mergeInputs);
        return super.naryOperation(instruction, values);
    }
    @Override public BasicValue merge(BasicValue first, BasicValue second) {
        if(first.equals(second)) return first;
        BasicValue merged = super.merge(first, second);
        return mode(first) != Mode.UNKNOWN && mode(first) == mode(second)
                ? new Storage(merged.getType(), mode(first)) : new BasicValue(merged.getType());
    }
    private static final class Storage extends BasicValue {
        final Mode mode;
        Storage(Type type, Mode mode) { super(type); this.mode = mode; }
        @Override public boolean equals(Object other) {
            return other instanceof Storage value && mode == value.mode && super.equals(other);
        }
        @Override public int hashCode() { return super.hashCode() * 31 + mode.ordinal(); }
    }
}