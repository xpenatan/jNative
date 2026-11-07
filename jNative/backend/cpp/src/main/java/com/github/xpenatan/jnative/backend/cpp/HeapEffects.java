package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.ReflectionPlan.FieldId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Memory invalidation facts. Callers separately prove initialization and safepoint safety. */
final class HeapEffects {
    record Summary(Set<FieldId> writes, boolean arraysWritten, boolean barrier) {
        static final Summary UNKNOWN = new Summary(Set.of(), true, true);

        Summary {
            writes = Set.copyOf(writes);
        }
    }

    private final Program program;
    private final Function<MethodInsnNode, MethodId> targets;
    private final Map<MethodId, Summary> summaries = new HashMap<>();
    private final Set<MethodId> active = new HashSet<>();

    HeapEffects(Program program, Function<MethodInsnNode, MethodId> targets) {
        this.program = program;
        this.targets = targets;
    }

    Summary call(MethodInsnNode call) {
        MethodId target = targets.apply(call);
        return target == null ? Summary.UNKNOWN : method(target);
    }

    String report() {
        StringBuilder result = new StringBuilder("java-method\tbarrier\tarray-writes\tfield-writes\n");
        summaries.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(MethodId::toString)))
                .forEach(entry -> result.append(entry.getKey()).append('\t')
                        .append(entry.getValue().barrier()).append('\t')
                        .append(entry.getValue().arraysWritten()).append('\t')
                        .append(String.join(",", entry.getValue().writes().stream()
                                .map(field -> field.owner() + "." + field.name() + ":" + field.descriptor())
                                .sorted().toList())).append('\n'));
        return result.toString();
    }

    private Summary method(MethodId id) {
        Summary cached = summaries.get(id);
        if(cached != null) return cached;
        Program.Method method = program.methods().get(id);
        if(method == null || method.frames() == null || method.nativeSymbol() != null || id.name().startsWith("<")
                || (method.bytecode().access & (ACC_NATIVE | ACC_ABSTRACT | ACC_SYNCHRONIZED)) != 0
                || method.bytecode().instructions.size() > 4096 || !active.add(id))
            return Summary.UNKNOWN;
        Summary result;
        try {
            result = inspect(method);
        } finally {
            active.remove(id);
        }
        summaries.put(id, result);
        return result;
    }

    private Summary inspect(Program.Method method) {
        Set<FieldId> writes = new HashSet<>();
        boolean arraysWritten = false;
        for(int index = 0; index < method.frames().length; index++) {
            if(method.frames()[index] == null) continue;
            AbstractInsnNode instruction = method.bytecode().instructions.get(index);
            int opcode = instruction.getOpcode();
            if(instruction instanceof FieldInsnNode field) {
                FieldId declaration = ordinaryField(field);
                if(declaration == null) return Summary.UNKNOWN;
                if(opcode == PUTFIELD || opcode == PUTSTATIC) writes.add(declaration);
            } else if(instruction instanceof MethodInsnNode call) {
                Summary nested = call(call);
                if(nested.barrier()) return Summary.UNKNOWN;
                writes.addAll(nested.writes());
                arraysWritten |= nested.arraysWritten();
            } else if(instruction instanceof InvokeDynamicInsnNode
                    || instruction instanceof TypeInsnNode
                    || instruction instanceof MultiANewArrayInsnNode
                    || instruction instanceof LdcInsnNode constant && !(constant.cst instanceof Number)
                    || opcode == NEWARRAY || opcode == MONITORENTER || opcode == MONITOREXIT)
                return Summary.UNKNOWN;
            else if(opcode >= IASTORE && opcode <= SASTORE) arraysWritten = true;
        }
        return new Summary(writes, arraysWritten, false);
    }

    private FieldId ordinaryField(FieldInsnNode field) {
        if(program.bindings().field(field.owner, field.name, field.desc) != null) return null;
        for(String name = field.owner; name != null;) {
            var owner = program.classes().get(name);
            if(owner == null) return null;
            for(var candidate : owner.fields)
                if(candidate.name.equals(field.name) && candidate.desc.equals(field.desc))
                    return (candidate.access & ACC_VOLATILE) != 0 ? null
                            : new FieldId(name, field.name, field.desc);
            name = owner.superName;
        }
        return null;
    }
}
