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
    private final Predicate<MethodInsnNode> cooperativeRuntime;
    private final Map<MethodId, Summary> summaries = new LinkedHashMap<>();
    private final Map<MethodId, Summary> returningSummaries = new LinkedHashMap<>();
    private final Map<MethodId, Set<AbstractInsnNode>> returnPaths = new HashMap<>();
    private final Map<MethodId, List<MethodId>> virtualTargets = new HashMap<>();
    private final Set<MethodId> fallback = new HashSet<>();
    private final Map<MethodId, List<MethodId>> tailCalls = new LinkedHashMap<>();
    private final Set<MethodId> delegatedRoots = new HashSet<>();
    private final Map<MethodId, Set<String>> delegationInitializers = new HashMap<>();
    private final Set<MethodId> unprotectedTailEntries = new HashSet<>();
    private final Map<MethodId, Summary> cooperativeSummaries = new LinkedHashMap<>();
    private final Set<MethodId> argumentProtection = new HashSet<>();

    MethodEffects(Program program, Predicate<MethodInsnNode> runtimeLeaf) {
        this(program, runtimeLeaf, ignored -> false);
    }

    record TerminalArgumentProtection(boolean entryPoll, boolean lazyBoundaries) {
        static final TerminalArgumentProtection NONE = new TerminalArgumentProtection(false, false);

        boolean protects() {
            return entryPoll || lazyBoundaries;
        }
    }

    MethodEffects(Program program, Predicate<MethodInsnNode> runtimeLeaf,
                  Predicate<MethodInsnNode> cooperativeRuntime) {
        this.program = program;
        this.runtimeLeaf = runtimeLeaf;
        this.cooperativeRuntime = cooperativeRuntime;
        analyze();
    }

    /** Complete returning work proof; collecting operations require a published arm. */
    Summary cooperativeWork(MethodId id, Set<AbstractInsnNode> scoped) {
        return inspect(program.methods().get(id), true, null, scoped, true);
    }

    void validatedCooperative(MethodId id, Summary summary) {
        if(summary != null && summary.bounded() && !fallback.contains(id))
            cooperativeSummaries.put(id, summary);
    }

    boolean cooperative(MethodId id) {
        return cooperativeSummaries.containsKey(id);
    }

    /** An actual successful lowering publishes parameters before every collecting boundary. */
    void validatedArgumentProtection(MethodId id, boolean protectsArguments) {
        Program.Method callee = program.methods().get(id);
        if(protectsArguments && cooperative(id) && !fallback.contains(id)
                && !delegatedRoots.contains(id) && callee.nativeSymbol() == null
                && (callee.bytecode().access & (ACC_STATIC | ACC_NATIVE | ACC_ABSTRACT | ACC_SYNCHRONIZED))
                == ACC_STATIC && Type.getReturnType(id.descriptor()).equals(Type.VOID_TYPE))
            argumentProtection.add(id);
    }

    boolean protectsArguments(MethodId id) {
        return argumentProtection.contains(id);
    }

    boolean protectsCallArguments(MethodInsnNode call) {
        if(call.getOpcode() != INVOKESTATIC || !Type.getReturnType(call.desc).equals(Type.VOID_TYPE))
            return false;
        List<MethodId> targets = generatedCallTargets(call);
        return targets.size() == 1 && argumentProtection.contains(targets.getFirst());
    }

    /** This admits polling/guards only; it never establishes a noncollecting call. */
    int cooperativeCallWork(MethodId caller, MethodInsnNode call) {
        if(!directCall(call)) return 0;
        List<MethodId> targets = generatedCallTargets(call);
        if(targets.size() != 1) return 0;
        Summary summary = cooperativeSummaries.get(targets.getFirst());
        if(summary == null) return 0;
        Set<String> initialized = new HashSet<>(summary.initialized());
        if(call.getOpcode() == INVOKESTATIC) initialized.add(targets.getFirst().owner());
        if((program.methods().get(caller).bytecode().access & ACC_STATIC) != 0)
            initialized.remove(caller.owner());
        return initialized.isEmpty() ? summary.work() : 0;
    }

    private boolean directCall(MethodInsnNode call) {
        if(call.getOpcode() == INVOKESTATIC || call.getOpcode() == INVOKESPECIAL) return true;
        List<MethodId> targets = generatedCallTargets(call);
        return targets.size() == 1 && (program.methods().get(targets.getFirst()).bytecode().access
                & (ACC_FINAL | ACC_PRIVATE)) != 0;
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

    /**
     * A caller may leave references unpublished only in the bounded, noncollecting
     * surroundings of lexical regions that publish them before their first operation.
     * Cycles, handlers and monitors remain disallowed even inside those regions.
     */
    boolean boundedOutsideRegions(MethodId id, Set<AbstractInsnNode> scoped) {
        Summary summary = inspect(program.methods().get(id), false, null, scoped);
        return summary.bounded() && summary.initialized().isEmpty();
    }

    /**
     * Only structured, complete published loops may exempt a backedge. This proof
     * bounds unpublished surroundings; it never certifies a bounded/cooperative callee.
     * Headers remain inspected because their first test precedes publication.
     */
    boolean boundedOutsideLazyRegions(MethodId id, Set<AbstractInsnNode> scoped,
                                     Set<MethodInsnNode> guardedCopies,
                                     List<Set<AbstractInsnNode>> publishedLoops) {
        Summary summary = inspect(program.methods().get(id), false, null, scoped, false,
                new LazyRegions(guardedCopies, publishedLoops));
        return summary.bounded() && summary.initialized().isEmpty();
    }

    private record LazyRegions(Set<MethodInsnNode> guardedCopies,
                               List<Set<AbstractInsnNode>> publishedLoops) {
        boolean containsBackedge(AbstractInsnNode source, LabelNode target) {
            AbstractInsnNode operation = target;
            while(operation != null && operation.getOpcode() < 0) operation = operation.getNext();
            for(Set<AbstractInsnNode> loop : publishedLoops)
                if(loop.contains(source) && loop.contains(operation)) return true;
            return false;
        }
    }

    boolean delegatesRoots(MethodId id) {
        return delegatedRoots.contains(id);
    }

    Set<String> delegationInitializationDependencies(MethodId id) {
        return delegationInitializers.getOrDefault(id, Set.of());
    }

    /** Revoke candidates lacking either actual entry or complete lazy-boundary protection. */
    boolean validateTailArguments(Map<MethodId, TerminalArgumentProtection> protection) {
        boolean changed = false;
        for(List<MethodId> targets : tailCalls.values())
            for(MethodId target : targets)
                if(!protection.getOrDefault(target, TerminalArgumentProtection.NONE).protects())
                    changed |= unprotectedTailEntries.add(target);
        if(changed) analyzeTailCalls();
        return changed;
    }

    boolean tailArgumentsProtected(Map<MethodId, TerminalArgumentProtection> protection) {
        for(MethodId caller : delegatedRoots)
            for(MethodId target : tailCalls.get(caller))
                if(!protection.getOrDefault(target, TerminalArgumentProtection.NONE).protects()) return false;
        return true;
    }

    // Entry-only validation remains available for tests of the original certificate.
    boolean validateTailEntries(Map<MethodId, Boolean> entries) {
        return validateTailArguments(entryArgumentProtection(entries));
    }

    boolean tailEntriesProtected(Map<MethodId, Boolean> entries) {
        return tailArgumentsProtected(entryArgumentProtection(entries));
    }

    private static Map<MethodId, TerminalArgumentProtection> entryArgumentProtection(
            Map<MethodId, Boolean> entries) {
        Map<MethodId, TerminalArgumentProtection> result = new HashMap<>();
        entries.forEach((id, entry) -> result.put(id, new TerminalArgumentProtection(entry, false)));
        return result;
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
        argumentProtection.clear();
        cooperativeSummaries.clear();
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
        return inspect(method, returning, delegatedCall, Set.of());
    }

    private Summary inspect(
            Program.Method method, boolean returning, AbstractInsnNode delegatedCall,
            Set<AbstractInsnNode> scoped) {
        return inspect(method, returning, delegatedCall, scoped, false);
    }

    private Summary inspect(
            Program.Method method, boolean returning, AbstractInsnNode delegatedCall,
            Set<AbstractInsnNode> scoped, boolean cooperative) {
        return inspect(method, returning, delegatedCall, scoped, cooperative, null);
    }

    private Summary inspect(
            Program.Method method, boolean returning, AbstractInsnNode delegatedCall,
            Set<AbstractInsnNode> scoped, boolean cooperative, LazyRegions lazy) {
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
        if(lazy == null && code.instructions.size() > MAX_WORK) return unknown("body work bound");
        if(returning
                && returnPaths.computeIfAbsent(method.id(), key -> returnPaths(code)).isEmpty())
            return unknown("no normal return");
        int work = 1;
        Set<String> initialized = new TreeSet<>();
        for(AbstractInsnNode instruction : code.instructions) {
            int opcode = instruction.getOpcode();
            if(opcode < 0) continue;
            int position = code.instructions.indexOf(instruction);
            if(lazy != null && scoped.contains(instruction)) {
                if(instruction instanceof JumpInsnNode jump
                        && code.instructions.indexOf(jump.label) <= position
                        && !lazy.containsBackedge(instruction, jump.label))
                    return unknown("unverified published cycle");
                if(instruction instanceof TableSwitchInsnNode
                        || instruction instanceof LookupSwitchInsnNode
                        || opcode == MONITORENTER || opcode == MONITOREXIT)
                    return unknown("unsupported published region");
                continue;
            }
            if(instruction instanceof JumpInsnNode jump
                    && code.instructions.indexOf(jump.label) <= position)
                return unknown("control-flow cycle");
            if(instruction instanceof TableSwitchInsnNode selection
                    && backward(code, position, selection.dflt, selection.labels))
                return unknown("switch cycle");
            if(instruction instanceof LookupSwitchInsnNode selection
                    && backward(code, position, selection.dflt, selection.labels))
                return unknown("switch cycle");
            if(!scoped.isEmpty() && (opcode == MONITORENTER || opcode == MONITOREXIT))
                return unknown("monitor");
            if(returning && !returnsThrough(method.id(), instruction)) continue;
            work++;
            if(work > MAX_WORK) return unknown("transitive work bound");
            if(lazy != null && instruction instanceof MethodInsnNode call
                    && lazy.guardedCopies().contains(call)) {
                // The successful unpublished arm performs at most 64 copy units.
                // Its negative/large-count adapter arm publishes and polls first.
                work += 64;
                if(work > MAX_WORK) return unknown("guarded copy work bound");
                continue;
            }
            if(!cooperative && scoped.contains(instruction)) continue;
            if(instruction == delegatedCall) continue;
            if(instruction instanceof MethodInsnNode call) {
                if(cooperative && scoped.contains(instruction) && cooperativeRuntime.test(call))
                    continue;
                // Do not establish cyclic cooperative proofs or assume a dispatch target.
                if(cooperative && !runtimeLeaf.test(call) && !directCall(call))
                    return unknown("unproven cooperative dispatch");
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
                if(cooperative && scoped.contains(instruction) && opcode == NEWARRAY) {
                    int length = constantInt(instruction.getPrevious());
                    if(length < 0 || length > 64) return unknown("unbounded primitive allocation");
                    // Allocation polls before initializing this fixed-size primitive payload.
                    // Charge its entire initialization, not just the allocating bytecode.
                    work += length;
                    if(work > MAX_WORK) return unknown("transitive work bound");
                    continue;
                }
                boolean staticField = opcode == GETSTATIC || opcode == PUTSTATIC;
                if(staticField) {
                    FieldInsnNode field = (FieldInsnNode)instruction;
                    if(opcode == GETSTATIC
                            && program.primitiveClassField(
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
                cooperative ? "bounded returning work with published cooperative boundaries"
                        : "bounded body and all reachable call targets; failure unwinds",
                Set.copyOf(initialized));
    }

    private static int constantInt(AbstractInsnNode instruction) {
        while(instruction instanceof LineNumberNode || instruction instanceof FrameNode)
            instruction = instruction.getPrevious();
        // Labels can merge a different stack value from another predecessor.
        if(instruction == null || instruction instanceof LabelNode) return -1;
        int opcode = instruction.getOpcode();
        if(opcode >= ICONST_0 && opcode <= ICONST_5) return opcode - ICONST_0;
        if(instruction instanceof IntInsnNode value && (opcode == BIPUSH || opcode == SIPUSH))
            return value.operand;
        if(instruction instanceof LdcInsnNode value && value.cst instanceof Integer integer)
            return integer;
        return -1;
    }

    /**
     * A straight-line prefix may pass its live references directly to a final generated call. The
     * callee publishes its own arguments before its entry poll or every verified lazy boundary,
     * and nothing in the caller uses them afterwards. This does not make the call noncollecting.
     */
    private void analyzeTailCalls() {
        tailCalls.clear();
        delegatedRoots.clear();
        delegationInitializers.clear();
        Map<MethodId, Set<String>> prefixInitializers = new HashMap<>();
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
            // Prefix failures cannot resume here: callers with handlers are excluded.
            // Returning summaries include the initialization dependencies of prefix callees.
            Summary prefix = inspect(method, true, call);
            if(!prefix.bounded()) continue;
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
                                && !unprotectedTailEntries.contains(target)
                                && !boundedAfterInitialization(target);
            }
            if(entryPoll) {
                tailCalls.put(method.id(), targets);
                prefixInitializers.put(method.id(), prefix.initialized());
            }
        }
        // Never eliminate consecutive entry polls. In particular, mutually recursive
        // wrappers must not remove each other's only opportunity to stop for GC.
        tailCalls.forEach(
                (caller, targets) -> {
                    if(targets.stream().noneMatch(tailCalls::containsKey)) {
                        delegatedRoots.add(caller);
                        delegationInitializers.put(caller, prefixInitializers.get(caller));
                    }
                });
    }

    private List<MethodId> generatedCallTargets(MethodInsnNode call) {
        if(program.platform(call.owner)
                || RuntimeLibrary.intrinsic(call.owner, call.name, call.desc)) return List.of();
        MethodId declaration =
                MethodResolver.resolve(call.owner, call.name, call.desc, program.classes()::get, program::platform, program.bindings());
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
        return boundedCallWork(caller, call) > 0;
    }

    /** Maximum transitive returning work, or zero when collection or initialization is unresolved. */
    int boundedCallWork(MethodId caller, MethodInsnNode call) {
        Set<String> initialized = new HashSet<>();
        int work = callWork(caller, call, true, initialized);
        if((program.methods().get(caller).bytecode().access & ACC_STATIC) != 0)
            initialized.remove(caller.owner());
        return initialized.isEmpty() ? work : 0;
    }

    boolean boundedDispatch(MethodId call) {
        // Runtime-owned implementations are not enumerated by instantiatedClasses.
        // Their adapter contracts are checked separately by the runtime lowering.
        Set<String> initialized = new HashSet<>();
        return !program.platform(call.owner())
                && dispatchWork(call, true, initialized) > 0
                && initialized.isEmpty();
    }

    private int callWork(
            MethodId caller, MethodInsnNode call, boolean returning, Set<String> initialized) {
        if(runtimeLeaf.test(call)) return 1;
        if(program.platform(call.owner)
                || RuntimeLibrary.intrinsic(call.owner, call.name, call.desc)) return 0;
        MethodId declaration =
                MethodResolver.resolve(call.owner, call.name, call.desc, program.classes()::get, program::platform, program.bindings());
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
                            type, call.name(), call.descriptor(), program.classes()::get, program::platform, program.bindings());
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
                                + "\tinitialization-dependencies\tdelegates-final-call-roots"
                                + "\tcooperative-return-work\n");
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
                                .append('\t')
                                .append(cooperativeSummaries.containsKey(id)
                                        ? cooperativeSummaries.get(id).work() : 0)
                                .append('\n'));
        return result.toString();
    }
}
