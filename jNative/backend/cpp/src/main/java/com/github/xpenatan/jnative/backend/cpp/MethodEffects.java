package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.compiler.MethodResolver;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.RuntimeLibrary;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.function.Predicate;

/**
 * Closed-world successful-path effects, including transitive work and actual lowering.
 */
final class MethodEffects {
    // Bound the entire call tree, not just each individual body. Recursive components
    // never acquire a summary, so they keep entry polls even without a bytecode loop.
    private static final int MAX_WORK = 512;

    record Summary(int work, String reason, Set<String> initialized) {
        boolean bounded() {
            return work > 0;
        }
    }

    private final Program program;
    private final Predicate<MethodInsnNode> runtimeLeaf;
    private final Map<MethodId, Summary> summaries = new LinkedHashMap<>();
    private final Map<MethodId, Summary> returningSummaries = new LinkedHashMap<>();
    private final Map<MethodId, Set<AbstractInsnNode>> returnPaths = new HashMap<>();
    private final Map<MethodId, List<MethodId>> virtualTargets = new HashMap<>();
    private final Set<MethodId> fallback = new HashSet<>();
    private final Map<MethodId, List<MethodId>> tailCalls = new LinkedHashMap<>();
    private final Set<MethodId> delegatedRoots = new HashSet<>();

    MethodEffects(Program program, Predicate<MethodInsnNode> runtimeLeaf) {
        this.program = program;
        this.runtimeLeaf = runtimeLeaf;
        analyze();
    }

    boolean bounded(MethodId id) {
        Summary summary = summaries.get(id);
        return summary != null && summary.bounded() && summary.initialized().isEmpty();
    }

    boolean boundedReturn(MethodId id) {
        Summary summary = returningSummaries.get(id);
        return summary != null && summary.bounded() && summary.initialized().isEmpty();
    }

    boolean boundedAfterInitialization(MethodId id) {
        Summary summary = returningSummaries.get(id);
        return summary != null && summary.bounded();
    }

    boolean delegatesRoots(MethodId id) {
        return delegatedRoots.contains(id);
    }

    Set<MethodId> tailLowerings() {
        Set<MethodId> result = new HashSet<>(tailCalls.keySet());
        tailCalls.values().forEach(result::addAll);
        return result;
    }

    Set<String> initializationDependencies(MethodId id) {
        Summary summary = returningSummaries.get(id);
        return summary == null ? Set.of() : summary.initialized();
    }

    boolean returnsThrough(MethodId id, AbstractInsnNode instruction) {
        return returnPaths
                .computeIfAbsent(id, key -> returnPaths(program.methods().get(key).bytecode()))
                .contains(instruction);
    }

    void fallback(Set<MethodId> methods) {
        if(fallback.addAll(methods)) analyze();
    }

    private void analyze() {
        summaries.clear();
        returningSummaries.clear();
        analyze(summaries, false);
        analyze(returningSummaries, true);
        analyzeTailCalls();
    }

    private void analyze(Map<MethodId, Summary> results, boolean returning) {
        boolean changed;
        do {
            changed = false;
            for(Program.Method method : program.methods().values()) {
                Summary previous = results.get(method.id());
                if(previous != null && previous.bounded()) continue;
                Summary summary = inspect(method, returning);
                results.put(method.id(), summary);
                changed |= summary.bounded();
            }
        } while(changed);
    }

    private Summary inspect(Program.Method method, boolean returning) {
        return inspect(method, returning, null);
    }

    private Summary inspect(
            Program.Method method, boolean returning, AbstractInsnNode delegatedCall) {
        MethodNode code = method.bytecode();
        if(fallback.contains(method.id())) return unknown("low-level lowering polls");
        if(method.nativeBinding() != null && method.nativeBinding().bounded()
                && (code.access & ACC_SYNCHRONIZED) == 0)
            return new Summary(1, "validated bounded scalar managed import", Set.of());
        if(method.nativeSymbol() != null || (code.access & (ACC_NATIVE | ACC_ABSTRACT)) != 0)
            return unknown("native or abstract body");
        if((code.access & ACC_SYNCHRONIZED) != 0) return unknown("monitor");
        if(!code.tryCatchBlocks.isEmpty()) {
            if(!returning) return unknown("exception handler");
            for(TryCatchBlockNode handler : code.tryCatchBlocks)
                if(returnsThrough(method.id(), handler.handler))
                    return unknown("exception handler can return");
        }
        if(code.instructions.size() > MAX_WORK) return unknown("body work bound");
        if(returning
                && returnPaths.computeIfAbsent(method.id(), key -> returnPaths(code)).isEmpty())
            return unknown("no normal return");
        int work = 1;
        Set<String> initialized = new TreeSet<>();
        for(AbstractInsnNode instruction : code.instructions) {
            int opcode = instruction.getOpcode();
            if(opcode < 0) continue;
            int position = code.instructions.indexOf(instruction);
            if(instruction instanceof JumpInsnNode jump
                    && code.instructions.indexOf(jump.label) <= position)
                return unknown("control-flow cycle");
            if(instruction instanceof TableSwitchInsnNode selection
                    && backward(code, position, selection.dflt, selection.labels))
                return unknown("switch cycle");
            if(instruction instanceof LookupSwitchInsnNode selection
                    && backward(code, position, selection.dflt, selection.labels))
                return unknown("switch cycle");
            if(returning && !returnsThrough(method.id(), instruction)) continue;
            work++;
            if(instruction == delegatedCall) continue;
            if(instruction instanceof MethodInsnNode call) {
                int cost = callWork(method.id(), call, returning, initialized);
                if(cost == 0)
                    return unknown(
                            "call may collect, initialize, block or recurse: "
                                    + call.owner
                                    + "."
                                    + call.name
                                    + call.desc);
                work += cost;
            }
            else {
                boolean staticField = opcode == GETSTATIC || opcode == PUTSTATIC;
                if(staticField) {
                    FieldInsnNode field = (FieldInsnNode)instruction;
                    if(opcode == GETSTATIC
                            && RuntimeLibrary.primitiveClassField(
                            field.owner, field.name, field.desc)) continue;
                    String owner = fieldOwner((FieldInsnNode)instruction);
                    if(owner == null) return unknown("runtime static field");
                    initialized.add(owner);
                }
                if(opcode == JSR
                        || opcode == RET
                        || opcode == AASTORE
                        || (opcode > RETURN
                        && opcode != GETFIELD
                        && opcode != PUTFIELD
                        && opcode != ARRAYLENGTH
                        && opcode != IFNULL
                        && opcode != IFNONNULL
                        && !staticField))
                    return unknown("operation may collect: " + opcode);
                // Generated string slots are populated before any Java entry.
                // Their later loads cannot allocate, park or collect.
                if(instruction instanceof LdcInsnNode constant
                        && !(constant.cst instanceof Number)
                        && !(constant.cst instanceof String)
                        && !(constant.cst instanceof Type type && type.getSort() != Type.METHOD))
                    return unknown("constant resolution");
            }
            if(work > MAX_WORK) return unknown("transitive work bound");
        }
        // Static entry has already completed initialization, or is reentering it
        // on its owning thread. An instance can be published by <clinit> before
        // initialization finishes, so instance bodies retain this dependency.
        if((code.access & ACC_STATIC) != 0) initialized.remove(method.id().owner());
        return new Summary(
                work,
                "bounded body and all reachable call targets; failure unwinds",
                Set.copyOf(initialized));
    }

    /**
     * A straight-line prefix may pass its live references directly to a final generated call. The
     * callee publishes its own arguments before its entry poll, and nothing in the caller uses them
     * afterwards. This does not make the call noncollecting for its callers.
     */
    private void analyzeTailCalls() {
        tailCalls.clear();
        delegatedRoots.clear();
        for(Program.Method method : program.methods().values()) {
            MethodNode code = method.bytecode();
            if(method.id().name().startsWith("<")
                    || !code.tryCatchBlocks.isEmpty()
                    || boundedAfterInitialization(method.id())) continue;
            List<AbstractInsnNode> operations = new ArrayList<>();
            boolean branches = false;
            for(AbstractInsnNode instruction : code.instructions) {
                branches |=
                        instruction instanceof JumpInsnNode
                                || instruction instanceof TableSwitchInsnNode
                                || instruction instanceof LookupSwitchInsnNode;
                if(instruction.getOpcode() >= 0) operations.add(instruction);
            }
            if(branches || operations.size() < 2) continue;
            AbstractInsnNode result = operations.getLast();
            if(result.getOpcode()
                    != Type.getReturnType(method.id().descriptor()).getOpcode(IRETURN)) continue;
            if(!(operations.get(operations.size() - 2) instanceof MethodInsnNode call)) continue;
            if(result.getOpcode() != Type.getReturnType(call.desc).getOpcode(IRETURN)) continue;
            Summary prefix = inspect(method, false, call);
            if(!prefix.bounded() || !prefix.initialized().isEmpty()) continue;
            List<MethodId> targets = generatedCallTargets(call);
            if(targets.isEmpty()) continue;
            boolean entryPoll = true;
            for(MethodId target : targets) {
                Program.Method callee = program.methods().get(target);
                entryPoll &=
                        callee != null
                                && callee.nativeSymbol() == null
                                && (callee.bytecode().access & (ACC_NATIVE | ACC_ABSTRACT)) == 0
                                && !fallback.contains(target)
                                && !boundedAfterInitialization(target);
            }
            if(entryPoll) tailCalls.put(method.id(), targets);
        }
        // Never eliminate consecutive entry polls. In particular, mutually recursive
        // wrappers must not remove each other's only opportunity to stop for GC.
        tailCalls.forEach(
                (caller, targets) -> {
                    if(targets.stream().noneMatch(tailCalls::containsKey))
                        delegatedRoots.add(caller);
                });
    }

    private List<MethodId> generatedCallTargets(MethodInsnNode call) {
        if(RuntimeLibrary.platform(call.owner)
                || RuntimeLibrary.intrinsic(call.owner, call.name, call.desc)) return List.of();
        MethodId declaration =
                MethodResolver.resolve(call.owner, call.name, call.desc, program.classes()::get);
        if(declaration == null || !program.methods().containsKey(declaration)) return List.of();
        if(call.getOpcode() == INVOKESTATIC
                || call.getOpcode() == INVOKESPECIAL
                || (program.methods().get(declaration).bytecode().access
                & (ACC_FINAL | ACC_PRIVATE))
                != 0) return List.of(declaration);
        return virtualTargets.computeIfAbsent(
                new MethodId(call.owner, call.name, call.desc), this::targets);
    }

    private String fieldOwner(FieldInsnNode field) {
        for(String owner = field.owner; owner != null; ) {
            ClassNode node = program.classes().get(owner);
            if(node == null) return null;
            if(node.fields.stream()
                    .anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)))
                return owner;
            owner = node.superName;
        }
        return null;
    }

    private static boolean backward(
            MethodNode code, int position, LabelNode otherwise, List<LabelNode> labels) {
        if(code.instructions.indexOf(otherwise) <= position) return true;
        for(LabelNode label : labels)
            if(code.instructions.indexOf(label) <= position) return true;
        return false;
    }

    boolean boundedCall(MethodId caller, MethodInsnNode call) {
        Set<String> initialized = new HashSet<>();
        int work = callWork(caller, call, true, initialized);
        if((program.methods().get(caller).bytecode().access & ACC_STATIC) != 0)
            initialized.remove(caller.owner());
        return work > 0 && initialized.isEmpty();
    }

    boolean boundedDispatch(MethodId call) {
        // Runtime-owned implementations are not enumerated by instantiatedClasses.
        // Their adapter contracts are checked separately by the runtime lowering.
        Set<String> initialized = new HashSet<>();
        return !RuntimeLibrary.platform(call.owner())
                && dispatchWork(call, true, initialized) > 0
                && initialized.isEmpty();
    }

    private int callWork(
            MethodId caller, MethodInsnNode call, boolean returning, Set<String> initialized) {
        if(runtimeLeaf.test(call)) return 1;
        if(RuntimeLibrary.platform(call.owner)
                || RuntimeLibrary.intrinsic(call.owner, call.name, call.desc)) return 0;
        MethodId declaration =
                MethodResolver.resolve(call.owner, call.name, call.desc, program.classes()::get);
        if(declaration == null || !program.methods().containsKey(declaration)) return 0;
        if(call.getOpcode() == INVOKESTATIC) {
            initialized.add(declaration.owner());
            return work(declaration, returning, initialized);
        }
        if(call.getOpcode() == INVOKESPECIAL
                || (program.methods().get(declaration).bytecode().access
                & (ACC_FINAL | ACC_PRIVATE))
                != 0) return work(declaration, returning, initialized);
        return dispatchWork(new MethodId(call.owner, call.name, call.desc), returning, initialized);
    }

    private int dispatchWork(MethodId call, boolean returning, Set<String> initialized) {
        List<MethodId> targets = virtualTargets.computeIfAbsent(call, this::targets);
        if(targets.isEmpty()) return 0;
        int maximum = 0;
        for(MethodId target : targets) {
            int cost = work(target, returning, initialized);
            if(cost == 0) return 0;
            maximum = Math.max(maximum, cost);
        }
        return maximum;
    }

    private int work(MethodId id, boolean returning, Set<String> initialized) {
        Summary summary = (returning ? returningSummaries : summaries).get(id);
        if(summary != null) initialized.addAll(summary.initialized());
        return summary == null ? 0 : summary.work();
    }

    private static Set<AbstractInsnNode> returnPaths(MethodNode method) {
        var predecessors = new IdentityHashMap<AbstractInsnNode, List<AbstractInsnNode>>();
        var pending = new ArrayDeque<AbstractInsnNode>();
        for(AbstractInsnNode instruction : method.instructions) {
            int opcode = instruction.getOpcode();
            if(opcode >= IRETURN && opcode <= RETURN) pending.add(instruction);
            List<AbstractInsnNode> next = new ArrayList<>();
            if(instruction instanceof JumpInsnNode jump) {
                next.add(jump.label);
                if(opcode != GOTO && instruction.getNext() != null)
                    next.add(instruction.getNext());
            }
            else if(instruction instanceof TableSwitchInsnNode selection) {
                next.add(selection.dflt);
                next.addAll(selection.labels);
            }
            else if(instruction instanceof LookupSwitchInsnNode selection) {
                next.add(selection.dflt);
                next.addAll(selection.labels);
            }
            else if(!(opcode >= IRETURN && opcode <= RETURN)
                    && opcode != ATHROW
                    && instruction.getNext() != null) next.add(instruction.getNext());
            for(AbstractInsnNode successor : next)
                predecessors
                        .computeIfAbsent(successor, ignored -> new ArrayList<>())
                        .add(instruction);
        }
        Set<AbstractInsnNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        while(!pending.isEmpty()) {
            AbstractInsnNode instruction = pending.removeFirst();
            if(result.add(instruction))
                pending.addAll(predecessors.getOrDefault(instruction, List.of()));
        }
        return result;
    }

    private List<MethodId> targets(MethodId call) {
        Set<MethodId> result = new LinkedHashSet<>();
        for(String type : program.instantiatedClasses()) {
            if(!subtype(type, call.owner())) continue;
            MethodId target =
                    MethodResolver.resolve(
                            type, call.name(), call.descriptor(), program.classes()::get);
            if(target == null) return List.of();
            result.add(target);
        }
        return List.copyOf(result);
    }

    private boolean subtype(String type, String parent) {
        if(type.equals(parent)) return true;
        ClassNode node = program.classes().get(type);
        if(node == null) return false;
        if(node.superName != null && subtype(node.superName, parent)) return true;
        for(String implemented : node.interfaces) if(subtype(implemented, parent)) return true;
        return false;
    }

    private static Summary unknown(String reason) {
        return new Summary(0, reason, Set.of());
    }

    String report() {
        StringBuilder result =
                new StringBuilder(
                        "java-method\tbounded-noncollecting-body\tmaximum-work\treason"
                                + "\tbounded-return-path\tbounded-after-initialization"
                                + "\tinitialization-dependencies\tdelegates-final-call-roots\n");
        summaries.forEach(
                (id, summary) ->
                        result.append(id)
                                .append('\t')
                                .append(bounded(id))
                                .append('\t')
                                .append(summary.work())
                                .append('\t')
                                .append(summary.reason())
                                .append('\t')
                                .append(boundedReturn(id))
                                .append('\t')
                                .append(boundedAfterInitialization(id))
                                .append('\t')
                                .append(
                                        String.join(
                                                ",", new TreeSet<>(initializationDependencies(id))))
                                .append('\t')
                                .append(delegatesRoots(id))
                                .append('\n'));
        return result.toString();
    }
}
