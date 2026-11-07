package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

/** Finds candidate lengths for a checked fixed-size copy, without assuming the caller's value. */
final class PrimitiveCopyCounts implements Opcodes {
    private final Map<MethodInsnNode, Integer> preferred = new HashMap<>();

    PrimitiveCopyCounts(Program program, Predicate<MethodInsnNode> dynamicCopy,
                        Function<MethodInsnNode, MethodId> directTarget) {
        Map<MethodId, Integer> countSlots = new HashMap<>();
        Set<String> signatures = new HashSet<>();
        for(Program.Method method : program.methods().values()) {
            Type[] arguments = Type.getArgumentTypes(method.id().descriptor());
            if(arguments.length == 0 || !arguments[arguments.length - 1].equals(Type.INT_TYPE)
                    || method.bytecode().instructions.size() > 256)
                continue;
            for(AbstractInsnNode instruction : method.bytecode().instructions) {
                if(!(instruction instanceof MethodInsnNode call) || !dynamicCopy.test(call)) continue;
                int slot = (method.bytecode().access & ACC_STATIC) == 0 ? 1 : 0;
                for(int i = 0; i < arguments.length - 1; i++) slot += arguments[i].getSize();
                countSlots.put(method.id(), slot);
                signatures.add(method.id().name() + method.id().descriptor());
                break;
            }
        }

        Map<MethodId, Integer> callerCounts = new HashMap<>();
        for(Program.Method caller : program.methods().values()) {
            for(AbstractInsnNode instruction : caller.bytecode().instructions) {
                if(!(instruction instanceof MethodInsnNode call) || !signatures.contains(call.name + call.desc))
                    continue;
                int count = CppEmitter.trailingIntLiteral(call);
                if(count < 1 || count > 32) continue;
                MethodId target = directTarget.apply(call);
                if(countSlots.containsKey(target))
                    callerCounts.merge(target, count, (left, right) -> left.equals(right) ? left : 0);
            }
        }
        callerCounts.forEach((target, count) -> {
            if(count != 0) trace(program.methods().get(target), countSlots.get(target), count, dynamicCopy);
        });
    }

    int preferred(MethodInsnNode copy) {
        return preferred.getOrDefault(copy, 0);
    }

    private void trace(Program.Method method, int countSlot, int count, Predicate<MethodInsnNode> dynamicCopy) {
        SourceValue parameter = new SourceValue(1, new InsnNode(NOP));
        var flow = new SourceInterpreter(ASM9) {
            @Override public SourceValue newParameterValue(boolean instance, int local, Type type) {
                return local == countSlot ? parameter : super.newParameterValue(instance, local, type);
            }

            @Override public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
                return value;
            }
        };
        Frame<SourceValue>[] frames;
        try {
            frames = new Analyzer<>(flow).analyze(method.id().owner(), method.bytecode());
        } catch(AnalyzerException ignored) {
            return;
        }
        var instructions = method.bytecode().instructions;
        for(AbstractInsnNode instruction : instructions) {
            if(!(instruction instanceof MethodInsnNode copy) || !dynamicCopy.test(copy)) continue;
            Frame<SourceValue> frame = frames[instructions.indexOf(copy)];
            if(frame == null) continue;
            var producers = frame.getStack(frame.getStackSize() - 1).insns;
            if(producers.size() != 1 || !(producers.iterator().next() instanceof MethodInsnNode min)
                    || min.getOpcode() != INVOKESTATIC || !min.owner.equals("java/lang/Math")
                    || !min.name.equals("min") || !min.desc.equals("(II)I"))
                continue;
            Frame<SourceValue> operands = frames[instructions.indexOf(min)];
            int size = operands.getStackSize();
            if(parameter.equals(operands.getStack(size - 1)) || parameter.equals(operands.getStack(size - 2)))
                preferred.put(copy, count);
        }
    }
}
