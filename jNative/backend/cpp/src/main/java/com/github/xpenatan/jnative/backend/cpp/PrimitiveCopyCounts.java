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

/** Finds checked fixed-copy specializations from literal caller operands. */
final class PrimitiveCopyCounts implements Opcodes {
    record Hint(int count, int sourceOffset) {
        static final Hint NONE = new Hint(0, -1);

        Hint merge(Hint other) {
            if(count != other.count) return NONE;
            if(sourceOffset == -1) return other;
            if(other.sourceOffset == -1 || sourceOffset == other.sourceOffset) return this;
            return new Hint(count, -2);
        }
    }

    private record Slots(int count, int offset) {}

    private final Map<MethodInsnNode, Hint> preferred = new HashMap<>();

    PrimitiveCopyCounts(Program program, Predicate<MethodInsnNode> dynamicCopy,
                        Function<MethodInsnNode, MethodId> directTarget) {
        Map<MethodId, Slots> slots = new HashMap<>();
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
                int offset = arguments.length >= 2 && arguments[arguments.length - 2].equals(Type.INT_TYPE)
                        ? slot - 1 : -1;
                slots.put(method.id(), new Slots(slot, offset));
                signatures.add(method.id().name() + method.id().descriptor());
                break;
            }
        }

        Map<MethodId, Hint> callerHints = new HashMap<>();
        for(Program.Method caller : program.methods().values()) {
            for(AbstractInsnNode instruction : caller.bytecode().instructions) {
                if(!(instruction instanceof MethodInsnNode call) || !signatures.contains(call.name + call.desc))
                    continue;
                int count = CppEmitter.trailingIntLiteral(call);
                if(count < 1 || count > 32) continue;
                MethodId target = directTarget.apply(call);
                Slots targetSlots = slots.get(target);
                if(targetSlots == null) continue;
                int offset = targetSlots.offset() < 0 ? -1
                        : CppEmitter.intLiteral(CppEmitter.previousOperand(CppEmitter.previousOperand(call)));
                callerHints.merge(target, new Hint(count, Math.max(-1, offset)), Hint::merge);
            }
        }
        callerHints.forEach((target, hint) -> {
            if(hint.count() != 0) trace(program.methods().get(target), slots.get(target), hint, dynamicCopy);
        });
    }

    Hint preferred(MethodInsnNode copy) {
        return preferred.getOrDefault(copy, Hint.NONE);
    }

    private void trace(Program.Method method, Slots slots, Hint hint, Predicate<MethodInsnNode> dynamicCopy) {
        SourceValue countParameter = new SourceValue(1, new InsnNode(NOP));
        SourceValue offsetParameter = new SourceValue(1, new InsnNode(NOP));
        var flow = new SourceInterpreter(ASM9) {
            @Override public SourceValue newParameterValue(boolean instance, int local, Type type) {
                if(local == slots.count()) return countParameter;
                if(local == slots.offset()) return offsetParameter;
                return super.newParameterValue(instance, local, type);
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
            if(!countParameter.equals(operands.getStack(size - 1))
                    && !countParameter.equals(operands.getStack(size - 2))) continue;
            SourceValue offset = frame.getStack(frame.getStackSize() - 4);
            int from = offset.equals(offsetParameter) ? hint.sourceOffset()
                    : offset.insns.size() == 1 ? CppEmitter.intLiteral(offset.insns.iterator().next()) : -1;
            if(from < 0 || from > Integer.MAX_VALUE - hint.count()) from = -1;
            preferred.put(copy, new Hint(hint.count(), from));
        }
    }
}
