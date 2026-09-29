package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.compiler.MethodResolver;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.RuntimeLibrary;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.objectweb.asm.Opcodes.*;

/**
 * Resolves a unique generated target using the complete closed program.
 */
final class CallTargets {
    private final Program program;
    private final Map<MethodId, Optional<MethodId>> virtualTargets = new HashMap<>();
    private final Map<MethodId, MethodId> directTargets = new HashMap<>();

    CallTargets(Program program) {
        this.program = program;
    }

    MethodId direct(MethodInsnNode call) {
        if(call.getOpcode() != INVOKEVIRTUAL && call.getOpcode() != INVOKEINTERFACE) return null;
        if(program.platform(call.owner)) return null;
        MethodId declared = resolve(call.owner, call.name, call.desc);
        if(!generated(declared)) return null;
        MethodId requested = new MethodId(call.owner, call.name, call.desc);
        MethodId target =
                (program.methods().get(declared).bytecode().access & (ACC_PRIVATE | ACC_FINAL)) != 0
                        ? declared
                        : virtualTargets
                        .computeIfAbsent(
                                new MethodId(call.owner, call.name, call.desc),
                                this::unique)
                        .orElse(null);
        if(target != null) directTargets.put(requested, target);
        return target;
    }

    String report() {
        StringBuilder report = new StringBuilder("java-invocation\tdirect-target\n");
        directTargets.entrySet().stream()
                .sorted(
                        Map.Entry.comparingByKey(
                                Comparator.comparing(MethodId::toString)))
                .forEach(
                        entry ->
                                report.append(entry.getKey())
                                        .append('\t')
                                        .append(entry.getValue())
                                        .append('\n'));
        return report.toString();
    }

    private Optional<MethodId> unique(MethodId call) {
        MethodId found = null;
        for(String type : program.instantiatedClasses()) {
            if(!subtype(type, call.owner())) continue;
            MethodId target = resolve(type, call.name(), call.descriptor());
            if(!generated(target)
                    || (program.methods().get(target).bytecode().access & ACC_ABSTRACT) != 0)
                return Optional.empty();
            if(found != null && !found.equals(target)) return Optional.empty();
            found = target;
        }
        return Optional.ofNullable(found);
    }

    private MethodId resolve(String owner, String name, String descriptor) {
        return MethodResolver.resolve(owner, name, descriptor, program.classes()::get, program::platform, program.bindings());
    }

    private boolean generated(MethodId method) {
        return method != null
                && !program.platform(method.owner())
                && program.methods().containsKey(method);
    }

    private boolean subtype(String type, String parent) {
        if(type.equals(parent)) return true;
        ClassNode node = program.classes().get(type);
        if(node == null) return false;
        if(node.superName != null && subtype(node.superName, parent)) return true;
        for(String implemented : node.interfaces) if(subtype(implemented, parent)) return true;
        return false;
    }
}
