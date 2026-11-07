package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Plans bounded entry sharing and size-budgeted complete implementations. */
record InliningPlan(Set<MethodId> sharedBodies, Set<MethodId> implementations) {
    static final InliningPlan EMPTY = new InliningPlan(Set.of(), Set.of());
    static final String FILE = "java_inline.hpp";
    private static final int MAX_ENTRY_OPERATIONS = 384;
    private static final int MAX_IMPLEMENTATION_OPERATIONS = 256;
    private static final int MAX_IMPLEMENTATION_CHARACTERS = 12 * 1024;
    private static final int MAX_IMPLEMENTATIONS = 64;
    private static final int MAX_TOTAL_IMPLEMENTATION_OPERATIONS = 8 * 1024;
    private static final int LOOP_CALL_WEIGHT = 16;
    private static final int MAX_IMPORTANCE = 65536;
    private static final int MAX_PROPAGATED_IMPORTANCE = 256;
    private record CallWeight(MethodId target, int weight) {}

    InliningPlan {
        sharedBodies = Set.copyOf(sharedBodies);
        implementations = Set.copyOf(implementations);
    }

    static InliningPlan analyze(Program program, NativeBuildRequest request, Set<MethodId> entries,
            Predicate<MethodId> boundedAfterInitialization) {
        if(request.stackTraces().javaFrames()) return EMPTY;
        Set<MethodId> selected = new HashSet<>();
        for(MethodId id : entries) {
            if(!boundedAfterInitialization.test(id)) continue;
            int operations = 0;
            for(AbstractInsnNode instruction : program.methods().get(id).bytecode().instructions)
                if(instruction.getOpcode() >= 0) operations++;
            // The existing effect proof bounds transitive work. This second
            // limit bounds repeated native expansion of an individual body.
            if(operations <= MAX_ENTRY_OPERATIONS) selected.add(id);
        }
        return new InliningPlan(selected, Set.of());
    }

    InliningPlan withImplementations(Program program, NativeBuildRequest request,
            Map<MethodId, ReadableMethodEmitter.Result> readable, Set<MethodId> initializedEntries,
            Predicate<MethodId> bounded, Function<MethodInsnNode, MethodId> targets) {
        if(request.stackTraces().javaFrames()) return this;
        Map<MethodId, Integer> calls = new HashMap<>();
        Map<MethodId, List<CallWeight>> outgoingCalls = new HashMap<>();
        for(var caller : program.methods().values()) {
            List<CallWeight> outgoing = new ArrayList<>();
            for(MethodInsnNode call : calls(caller)) {
                MethodId target = targets.apply(call);
                if(target == null) continue;
                int weight = loopWeight(caller, call);
                outgoing.add(new CallWeight(target, weight));
                calls.merge(target, weight, InliningPlan::addImportance);
            }
            outgoingCalls.put(caller.id(), outgoing);
        }
        // Static loop placement is a profitability estimate, independent of all
        // safety proofs. Propagate through short wrappers with a fixed budget.
        Map<MethodId, Integer> importance = new HashMap<>(calls);
        for(int pass = 0; pass < 2; pass++) {
            Map<MethodId, Integer> propagated = new HashMap<>(calls);
            for(var caller : outgoingCalls.entrySet()) {
                int weight = Math.min(MAX_PROPAGATED_IMPORTANCE, importance.getOrDefault(caller.getKey(), 1));
                for(CallWeight call : caller.getValue())
                    propagated.merge(call.target(), weight * call.weight(), InliningPlan::addImportance);
            }
            importance = propagated;
        }
        Map<MethodId, Integer> priorities = importance;
        Map<MethodId, Integer> costs = new HashMap<>();
        for(var method : program.methods().values()) {
            MethodId id = method.id();
            var body = readable.get(id);
            if(body == null || body.source() == null || !body.segmentedRoots()
                    || body.source().length() > MAX_IMPLEMENTATION_CHARACTERS
                    || initializedEntries.contains(id) || bounded.test(id)
                    || !calls.containsKey(id) || id.name().startsWith("<") || method.nativeSymbol() != null
                    || (method.bytecode().access & (ACC_NATIVE | ACC_ABSTRACT | ACC_SYNCHRONIZED)) != 0
                    || (program.classes().get(id.owner()).access & ACC_INTERFACE) != 0
                    || !method.bytecode().tryCatchBlocks.isEmpty()) continue;
            int operations = 0;
            for(AbstractInsnNode instruction : method.bytecode().instructions)
                if(instruction.getOpcode() >= 0) operations++;
            if(operations <= MAX_IMPLEMENTATION_OPERATIONS) costs.put(id, operations);
        }
        Map<MethodId, Set<MethodId>> edges = new HashMap<>();
        for(MethodId id : costs.keySet()) {
            Set<MethodId> outgoing = new HashSet<>();
            for(CallWeight call : outgoingCalls.getOrDefault(id, List.of()))
                if(costs.containsKey(call.target())) outgoing.add(call.target());
            edges.put(id, outgoing);
        }
        Set<MethodId> selected = new HashSet<>();
        int remaining = MAX_TOTAL_IMPLEMENTATION_OPERATIONS;
        for(MethodId id : costs.keySet().stream()
                .sorted(Comparator.<MethodId>comparingInt(method -> -priorities.get(method))
                        .thenComparing(MethodId::toString)).toList()) {
            if(selected.size() >= MAX_IMPLEMENTATIONS || costs.get(id) > remaining
                    || reaches(id, id, edges, new HashSet<>())) continue;
            selected.add(id);
            remaining -= costs.get(id);
        }
        return new InliningPlan(sharedBodies, selected);
    }

    private static boolean reaches(MethodId start, MethodId current, Map<MethodId, Set<MethodId>> edges,
            Set<MethodId> visited) {
        if(!visited.add(current)) return false;
        for(MethodId next : edges.getOrDefault(current, Set.of()))
            if(next.equals(start) || reaches(start, next, edges, visited)) return true;
        return false;
    }

    private static List<MethodInsnNode> calls(Program.Method method) {
        if(method.frames() == null) return List.of();
        List<MethodInsnNode> calls = new ArrayList<>();
        for(int index = 0; index < method.frames().length; index++)
            if(method.frames()[index] != null && method.bytecode().instructions.get(index) instanceof MethodInsnNode call)
                calls.add(call);
        return calls;
    }

    private static int addImportance(int left, int right) {
        return (int)Math.min(MAX_IMPORTANCE, (long)left + right);
    }

    private static int loopWeight(Program.Method method, MethodInsnNode call) {
        int position = method.bytecode().instructions.indexOf(call);
        for(var block : method.blocks())
            for(int successor : block.successors())
                if(successor <= block.start() && position >= successor && position < block.end())
                    return LOOP_CALL_WEIGHT;
        return 1;
    }

    boolean implementation(MethodId method) {
        return implementations.contains(method);
    }

    boolean shared(MethodId method) {
        return sharedBodies.contains(method);
    }
}
