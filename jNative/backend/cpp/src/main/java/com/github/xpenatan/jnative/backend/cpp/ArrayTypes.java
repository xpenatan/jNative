package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

/** Reference descriptor flow, independent of debug names and reused local slots. */
final class ArrayTypes {
    private static final BasicValue NULL_VALUE = new BasicValue(BasicInterpreter.NULL_TYPE);

    static Map<AbstractInsnNode, Type> analyze(String owner, MethodNode method) {
        if(method.instructions.size() > 4096) return unchangedParameters(method);
        try {
            var flow = new Flow();
            var frames = new Analyzer<>(flow).analyze(owner, method);
            Map<AbstractInsnNode, Type> result = new IdentityHashMap<>();
            for(int i = 0; i < frames.length; i++) {
                var frame = frames[i];
                if(frame == null) continue;
                AbstractInsnNode instruction = method.instructions.get(i);
                BasicValue value;
                if(instruction instanceof VarInsnNode load && load.getOpcode() == ALOAD)
                    value = frame.getLocal(load.var);
                else if(instruction.getOpcode() == AALOAD)
                    value = flow.binaryOperation(instruction,
                            frame.getStack(frame.getStackSize() - 2), frame.getStack(frame.getStackSize() - 1));
                else continue;
                if(array(value)) result.put(instruction, value.getType());
            }
            return result;
        } catch(AnalyzerException | RuntimeException unsupported) {
            return unchangedParameters(method);
        }
    }

    /** Stores whose destination cannot have a narrower runtime component type. */
    static Set<AbstractInsnNode> compatibleStores(String owner, MethodNode method, Predicate<String> finalClass) {
        if(method.instructions.size() > 4096) return Set.of();
        try {
            var frames = new Analyzer<>(new Flow()).analyze(owner, method);
            Set<AbstractInsnNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
            for(int i = 0; i < frames.length; i++) {
                var frame = frames[i];
                AbstractInsnNode instruction = method.instructions.get(i);
                if(frame == null || instruction.getOpcode() != AASTORE) continue;
                BasicValue target = frame.getStack(frame.getStackSize() - 3);
                BasicValue value = frame.getStack(frame.getStackSize() - 1);
                if(!array(target) || value == null) continue;
                Type targetType = target.getType();
                Type component = Type.getType(targetType.getDescriptor().substring(1));
                if(component.getSort() != Type.ARRAY && component.getSort() != Type.OBJECT) continue;
                Type leaf = targetType.getElementType();
                // Primitive arrays have no subtypes. Arrays of a final class
                // also cannot be narrowed, at any dimension. Object/interface
                // and non-final class leaves may still hide covariant arrays.
                if(leaf.getSort() == Type.OBJECT && !finalClass.test(leaf.getInternalName())) continue;
                if(component.equals(value.getType())) result.add(instruction);
            }
            return result;
        } catch(AnalyzerException | RuntimeException unsupported) {
            return Set.of();
        }
    }

    private static Map<AbstractInsnNode, Type> unchangedParameters(MethodNode method) {
        Map<Integer, Type> parameters = new HashMap<>();
        int slot = (method.access & ACC_STATIC) == 0 ? 1 : 0;
        for(Type type : Type.getArgumentTypes(method.desc)) {
            if(type.getSort() == Type.ARRAY) parameters.put(slot, type);
            slot += type.getSize();
        }
        for(AbstractInsnNode instruction : method.instructions)
            if(instruction instanceof VarInsnNode store && store.getOpcode() == ASTORE)
                parameters.remove(store.var);
        Map<AbstractInsnNode, Type> result = new IdentityHashMap<>();
        for(AbstractInsnNode instruction : method.instructions)
            if(instruction instanceof VarInsnNode load && load.getOpcode() == ALOAD
                    && parameters.containsKey(load.var)) result.put(load, parameters.get(load.var));
        return result;
    }

    private static boolean array(BasicValue value) {
        return value != null && value.getType() != null && value.getType().getSort() == Type.ARRAY;
    }

    private static final class Flow extends BasicInterpreter {
        Flow() {
            super(ASM9);
        }

        @Override
        public BasicValue newValue(Type type) {
            return type != null && (type.getSort() == Type.ARRAY || type.getSort() == Type.OBJECT)
                    ? new BasicValue(type) : super.newValue(type);
        }

        @Override
        public BasicValue newOperation(AbstractInsnNode instruction) throws AnalyzerException {
            return instruction.getOpcode() == ACONST_NULL ? NULL_VALUE : super.newOperation(instruction);
        }

        @Override
        public BasicValue binaryOperation(AbstractInsnNode instruction, BasicValue first, BasicValue second)
                throws AnalyzerException {
            // Strip exactly one dimension: float[][][] loads a float[][], not a float[].
            if(instruction.getOpcode() == AALOAD && array(first))
                return newValue(Type.getType(first.getType().getDescriptor().substring(1)));
            return super.binaryOperation(instruction, first, second);
        }

        @Override
        public BasicValue merge(BasicValue first, BasicValue second) {
            if(first.equals(second)) return first;
            // Null cannot change a successful access's kind. All other mixed
            // descriptors widen to Object; do not infer a kind from one branch.
            if(first.isReference() && NULL_VALUE.equals(second)) return first;
            if(NULL_VALUE.equals(first) && second.isReference()) return second;
            if(first.isReference() && second.isReference()) return BasicValue.REFERENCE_VALUE;
            return super.merge(first, second);
        }
    }
}
