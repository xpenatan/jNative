package com.github.xpenatan.jnative.backend.cpp;

import static com.github.xpenatan.jnative.backend.cpp.CppEmitter.*;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.*;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.ReflectionPlan.FieldId;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;

import java.util.*;
import java.util.function.Function;

/**
 * Reconstructs typed values and structured regions before printing C++. Effectful values are
 * sequenced explicitly. A reference that survives an allocation or call remains rooted, even when a
 * shorter expression is possible.
 */
final class ReadableMethodEmitter {
    record Result(String source, String reason) {
    }

    private static final Type OBJECT = Type.getType(Object.class);
    private final CppEmitter backend;
    private final Program program;
    private final CppNames names;
    private final NativeBuildRequest request;
    private final Program.Method method;
    private final List<AbstractInsnNode> instructions = new ArrayList<>();
    private final Map<AbstractInsnNode, Integer> positions = new IdentityHashMap<>();
    private final Map<Integer, Integer> lines = new HashMap<>();
    private final List<Block> blocks = new ArrayList<>();
    private final Map<Integer, Block> blockAt = new HashMap<>();
    private final Map<String, Local> locals = new LinkedHashMap<>();
    private final List<Local> parameters = new ArrayList<>();
    private final Set<String> usedNames =
            new HashSet<>(
                    List.of(
                            "java_frame",
                            "keep_alive",
                            "synchronized_method",
                            "synchronized_return",
                            "loop_safepoints",
                            "gc_roots",
                            "self"));
    private final List<Value> stack = new ArrayList<>();

    private record FieldRead(String owner, String name, String descriptor, Object receiver) {
    }

    private record ArrayRead(int kind, Object array, Object index) {
    }

    private final Map<FieldRead, Value> fieldReads = new HashMap<>();
    private final Map<FieldId, Boolean> invariantStaticArrays = new HashMap<>();
    private final Map<FieldId, Boolean> invariantInstanceArrays = new HashMap<>();
    private Set<MethodId> initializationMethods;
    private final Map<ArrayRead, Value> arrayReads = new HashMap<>();
    private final Map<Local, Object> localReadIdentities = new HashMap<>();
    private final Map<Block, Loop> loops = new HashMap<>();
    private final Map<Block, Set<Block>> postdominators = new HashMap<>();
    private final List<Value> sharedValues = new ArrayList<>();
    private final Set<Block> handlerEntries = new HashSet<>();
    private final List<Protection> protections = new ArrayList<>();
    private final List<TryCatchBlockNode> exceptionTable = new ArrayList<>();
    private final Map<Integer, MonitorRegion> monitorEntries = new LinkedHashMap<>();
    private final Map<Integer, MonitorRegion> monitorExits = new HashMap<>();
    private final BitSet monitorHandlers = new BitSet();
    private final Map<Block, String> exitLabels = new LinkedHashMap<>();
    private final Map<ArrayViewKey, String> arrayViews = new LinkedHashMap<>();
    private final Set<ArrayViewKey> uncheckedArrayViews = new HashSet<>();
    private final boolean splitExceptionRegions;
    private final boolean rootedReferences;
    private final boolean boundedNormalReturn;
    private final boolean deferredRoots;
    private final Set<Step> exceptionalSteps = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<AbstractInsnNode> confinedArrays;
    private Block block;
    private int sourceLine;
    private int emittedBlocks;
    private int rootSlots;
    private boolean scopedColdRoots;
    private boolean scopedRejoiningRoots;
    private final Set<Node> collectingBranches =
            Collections.newSetFromMap(new IdentityHashMap<>());

    ReadableMethodEmitter(
            CppEmitter backend,
            Program program,
            CppNames names,
            NativeBuildRequest request,
            Program.Method method) {
        this(backend, program, names, request, method, false);
    }

    private ReadableMethodEmitter(
            CppEmitter backend,
            Program program,
            CppNames names,
            NativeBuildRequest request,
            Program.Method method,
            boolean splitExceptionRegions) {
        this.backend = backend;
        this.program = program;
        this.names = names;
        this.request = request;
        this.method = method;
        this.splitExceptionRegions = splitExceptionRegions;
        this.rootedReferences = !boundedLeaf() && !backend.delegatesRoots(method.id());
        this.boundedNormalReturn =
                rootedReferences && backend.boundedAfterInitialization(method.id());
        // A throwing call can collect before entering a handler. Publish handler
        // locals eagerly even if every handler ultimately throws again.
        this.deferredRoots = boundedNormalReturn && method.bytecode().tryCatchBlocks.isEmpty();
        this.confinedArrays =
                new HashSet<>(ConfinedArrays.analyze(method.id().owner(), method.bytecode()));
        this.confinedArrays.addAll(backend.ownedArrayInstructions(method.id()));
    }

    /**
     * The bytecode body cannot collect or wait for collection. A failing runtime check may allocate
     * its exception, but execution cannot resume in this method afterwards. Implicit static class
     * initialization still runs; reference arguments are rooted on its slow path.
     */
    private boolean boundedLeaf() {
        return backend.boundedMethod(method.id());
    }

    /**
     * Runtime operations with no successful allocation, poll or generated override.
     */
    private boolean boundedIntrinsic(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode call && backend.boundedIntrinsic(call);
    }

    Result emit() {
        try {
            if(method.nativeSymbol() != null) fail("native import adapter");
            prepare();
            for(Block current : blocks) lower(current);
            connectValues();
            simplify();
            removeUnusedLocals();
            simplify();
            bypassEmptyJumps();
            analyzeRegions();
            Node body = region(blocks.getFirst(), null, Context.EMPTY, new HashSet<>());
            if(!exitLabels.isEmpty()) {
                // Labeled Java exits must not jump over a C++ initialization. The
                // values keep their evaluation sites, but their storage is hoisted.
                for(Block current : blocks)
                    for(Step step : current.steps)
                        if(step.kind == Kind.LET && !step.value.shared) {
                            step.value.shared = true;
                            sharedValues.add(step.value);
                        }
            }
            analyzeTemporaryRoots();
            analyzeReferenceLiveness();
            if(deferredRoots) {
                for(Block current : blocks)
                    if(!backend.returnsThrough(method.id(), instructions.get(current.start)))
                        exceptionalSteps.addAll(current.steps);
            }
            // Acyclic failure branches cannot resume this method. Keep their
            // registration and new local roots inside the branch itself. Shared
            // exits, switch fallthrough, and initialization dependencies keep the
            // existing method-wide frame until their lexical lifetimes are proven.
            scopedColdRoots =
                    deferredRoots
                            && backend.initializationDependencies(method.id()).isEmpty()
                            && exitLabels.isEmpty()
                            && !method.id().name().equals("<clinit>")
                            && instructions.stream()
                            .noneMatch(
                                    instruction ->
                                            instruction instanceof TableSwitchInsnNode
                                                    || instruction
                                                    instanceof
                                                    LookupSwitchInsnNode);
            scopedRejoiningRoots = analyzeRejoiningRoots(body);
            scopedColdRoots |= scopedRejoiningRoots;
            if(scopedRejoiningRoots) {
                // The new arm-entry poll precedes every operation in the arm,
                // including uses that die before an original collecting expression.
                // Conservatively publish all outer holders at that earlier point.
                // Their RootValue storage remains unregistered on the warm path.
                for(Local local : locals.values())
                    if(reference(local.type)) local.rootedReferences = true;
                for(Value value : sharedValues)
                    if(reference(value.type)) value.rootedReferences = true;
            }
            StringBuilder out = new StringBuilder();
            String owner = method.id().owner();
            var declarations = new ArrayList<String>();
            if(backend.isInterface(owner) && (method.bytecode().access & ACC_STATIC) == 0)
                declarations.add("::jnative::Object* self");
            for(Local parameter : parameters)
                declarations.add(
                        type(parameter.type)
                                + " "
                                + (reference(parameter.type)
                                ? parameter.name + "_argument"
                                : parameter.name));
            out.append(type(Type.getReturnType(method.id().descriptor())))
                    .append(' ')
                    .append(names.definitionName(owner))
                    .append("::")
                    .append(names.method(method.id()))
                    .append('(')
                    .append(String.join(", ", declarations))
                    .append(") {\n");
            String source = program.classes().get(owner).sourceFile;
            if(request.stackTraces().javaFrames())
                out.append("    ::jnative::JavaFrame java_frame(")
                        .append(quote(owner.replace('/', '.') + "." + method.id().name()))
                        .append(", ")
                        .append(
                                quote(
                                        request.javaSourceLocations() && source != null
                                                ? source
                                                : "Unknown Source"))
                        .append(");\n");
            int rootFramePosition = out.length();
            Writer writer = new Writer(out);
            if(rootedReferences && (method.bytecode().access & ACC_STATIC) == 0)
                writer.root(1, "keep_alive", backend.isInterface(owner) ? "self" : "this");
            for(Local local : locals.values()) {
                if(local.receiver) continue;
                if(reference(local.type)) {
                    String initial = local.parameter ? local.name + "_argument" : "nullptr";
                    if(local.rooted()) writer.root(1, local.name, initial);
                    else writer.line(1, "::jnative::Object* " + local.name + " = " + initial + ";");
                }
                else if(!local.parameter)
                    out.append("    ")
                            .append(type(local.type))
                            .append(' ')
                            .append(local.name)
                            .append("{};\n");
            }
            for(Value value : sharedValues) {
                if(value.rooted()) writer.root(1, value.name, "nullptr");
                else writer.line(1, type(value.type) + " " + value.name + "{};");
            }
            String initializationGuard = "";
            if(deferredRoots && !backend.initializationDependencies(method.id()).isEmpty()) {
                List<String> pending =
                        backend.initializationDependencies(method.id()).stream()
                                .sorted()
                                .map(
                                        dependency ->
                                                "!"
                                                        + classReference(dependency)
                                                        + "::initialization_complete.load(std::memory_order_acquire)")
                                .toList();
                initializationGuard =
                        "    if (" + String.join(" || ", pending) + ") gc_roots.activate();\n";
                out.append(initializationGuard);
            }
            // Calls can form unbounded recursion without a loop backedge. Publish
            // receiver/argument roots before polling at method entry as well.
            int entryPollPosition = out.length();
            if(rootedReferences && !boundedNormalReturn && !scopedRejoiningRoots)
                out.append(
                        loops.isEmpty()
                                ? "    ::jnative::safepoint();\n"
                                : "    ::jnative::LoopSafepoint loop_safepoints;\n");
            int entryPollEnd = out.length();
            if((method.bytecode().access & ACC_STATIC) != 0
                    && !method.id().name().equals("<clinit>")) {
                List<Local> initializationRoots =
                        !rootedReferences
                                ? parameters.stream()
                                .filter(parameter -> reference(parameter.type))
                                .toList()
                                : List.of();
                if(deferredRoots || scopedRejoiningRoots) {
                    writer.line(
                            1,
                            "if (!"
                                    + names.simpleName(owner)
                                    + "::initialization_complete.load(std::memory_order_acquire))"
                                    + " {");
                    writer.activateRoots(2);
                    writer.line(2, names.simpleName(owner) + "::ensure_initialized();");
                    writer.line(1, "}");
                    writer.coldRootsActive = false;
                }
                else if(initializationRoots.isEmpty()) {
                    writer.line(1, names.simpleName(owner) + "::ensure_initialized();");
                }
                else {
                    writer.line(
                            1,
                            "if (!"
                                    + names.simpleName(owner)
                                    + "::initialization_complete.load(std::memory_order_acquire))"
                                    + " {");
                    writer.line(
                            2,
                            "// Retain arguments while first-use initialization can collect or"
                                    + " wait.");
                    writer.line(
                            2,
                            "::jnative::RootFrame<"
                                    + initializationRoots.size()
                                    + "> initialization_roots;");
                    for(int i = 0; i < initializationRoots.size(); ++i)
                        writer.line(
                                2,
                                "initialization_roots.slot("
                                        + i
                                        + ") = "
                                        + initializationRoots.get(i).name
                                        + ";");
                    writer.line(2, names.simpleName(owner) + "::ensure_initialized();");
                    writer.line(1, "}");
                }
            }
            if(synchronizedReferenceReturn()) writer.root(1, "synchronized_return", "nullptr");
            if((method.bytecode().access & ACC_SYNCHRONIZED) != 0)
                out.append("    ::jnative::MonitorGuard synchronized_method(")
                        .append(
                                (method.bytecode().access & ACC_STATIC) == 0
                                        ? "keep_alive.get()"
                                        : "::jnative::class_object(" + quote(owner) + ")")
                        .append(");\n");
            writer.beginParameterArrayViews(1);
            body.write(writer, 1);
            if(!writer.labels.equals(exitLabels.keySet()))
                fail(
                        "a named exit has no unique lexical destination: "
                                + exitLabels.keySet().stream()
                                .filter(b -> !writer.labels.contains(b))
                                .map(b -> b.start + ":" + exitLabels.get(b))
                                .toList());
            writer.verifyExits();
            if(rootSlots != 0 && !scopedColdRoots) {
                // The frame already validates and retains this thread's heap.
                // Reuse it instead of looking up and validating TLS a second time.
                if(rootedReferences && !boundedNormalReturn && !scopedRejoiningRoots)
                    out.replace(
                            entryPollPosition,
                            entryPollEnd,
                            loops.isEmpty()
                                    ? "    gc_roots.heap().poll_if_requested();\n"
                                    : "    ::jnative::LoopSafepoint"
                                    + " loop_safepoints(gc_roots.heap());\n");
                out.insert(
                        rootFramePosition,
                        "    ::jnative::"
                                + (deferredRoots ? "DeferredRootFrame" : "RootFrame")
                                + "<"
                                + rootSlots
                                + "> gc_roots;\n");
            }
            else if(deferredRoots && !scopedColdRoots) {
                // No reference survives a collection in this method.
                if(!initializationGuard.isEmpty()) {
                    int guard = out.indexOf(initializationGuard);
                    out.delete(guard, guard + initializationGuard.length());
                }
                String activation = "gc_roots.activate();";
                int next;
                while((next = out.indexOf(activation)) >= 0)
                    out.delete(next, next + activation.length());
            }
            out.append("}\n\n");
            return new Result(out.toString(), null);
        } catch(Unstructured exception) {
            if(!splitExceptionRegions
                    && !method.bytecode().tryCatchBlocks.isEmpty()
                    && method.nativeSymbol() == null)
                return new ReadableMethodEmitter(backend, program, names, request, method, true)
                        .emit();
            return new Result(null, exception.getMessage());
        }
    }

    private boolean synchronizedReferenceReturn() {
        return (method.bytecode().access & ACC_SYNCHRONIZED) != 0
                && reference(Type.getReturnType(method.id().descriptor()));
    }

    /**
     * Publish at the entrance of whole collecting if arms. Their common continuation may
     * use the references after the address frame leaves scope: the proof excludes any
     * collection, initialization, wait or poll there, and bounds its transitive work.
     * Each published arm polls before its body, so recursive collecting calls cannot
     * indefinitely postpone cooperation by repeatedly taking an unpolled method entry.
     */
    private boolean analyzeRejoiningRoots(Node body) {
        if(!rootedReferences || deferredRoots || !loops.isEmpty()
                || !method.bytecode().tryCatchBlocks.isEmpty() || !monitorEntries.isEmpty()
                || (method.bytecode().access & ACC_SYNCHRONIZED) != 0
                || !exitLabels.isEmpty() || method.id().name().startsWith("<")
                || instructions.stream().anyMatch(instruction ->
                instruction instanceof TableSwitchInsnNode
                        || instruction instanceof LookupSwitchInsnNode)) return false;
        Set<Block> scopedBlocks = Collections.newSetFromMap(new IdentityHashMap<>());
        if(!findCollectingBranches(body, scopedBlocks) || collectingBranches.isEmpty()) {
            collectingBranches.clear();
            return false;
        }
        Set<AbstractInsnNode> scoped = Collections.newSetFromMap(new IdentityHashMap<>());
        for(Block current : scopedBlocks)
            scoped.addAll(instructions.subList(current.start, current.end));
        if(!backend.boundedOutsideRegions(method.id(), scoped)) {
            collectingBranches.clear();
            return false;
        }
        return true;
    }

    private boolean findCollectingBranches(Node node, Set<Block> scopedBlocks) {
        if(node == null) return true;
        if(node instanceof Sequence sequence) {
            for(Node child : sequence.nodes)
                if(!findCollectingBranches(child, scopedBlocks)) return false;
            return true;
        }
        if(node instanceof IfNode branch) {
            if(branch.block.condition.expression.collects()) return false;
            for(Node arm : Arrays.asList(branch.yes, branch.no)) {
                if(arm == null) continue;
                if(containsCollection(arm)) {
                    collectingBranches.add(arm);
                    collectRegionBlocks(arm, scopedBlocks);
                }
                else if(!findCollectingBranches(arm, scopedBlocks)) return false;
            }
            return true;
        }
        if(node instanceof StatementsNode statements)
            return statements.steps.stream().noneMatch(step -> step.expression.collects());
        return node instanceof TransferNode || node instanceof ExitLabelNode;
    }

    private boolean containsCollection(Node node) {
        if(node instanceof Sequence sequence)
            return sequence.nodes.stream().anyMatch(this::containsCollection);
        if(node instanceof StatementsNode statements)
            return statements.steps.stream().anyMatch(step -> step.expression.collects());
        if(node instanceof IfNode branch)
            return branch.block.condition.expression.collects()
                    || containsCollection(branch.yes)
                    || containsCollection(branch.no);
        return false;
    }

    private void collectRegionBlocks(Node node, Set<Block> result) {
        if(node instanceof Sequence sequence)
            for(Node child : sequence.nodes) collectRegionBlocks(child, result);
        else if(node instanceof StatementsNode statements) {
            for(Block current : blocks)
                if(current.steps == statements.steps) result.add(current);
        }
        else if(node instanceof IfNode branch) {
            result.add(branch.block);
            collectRegionBlocks(branch.yes, result);
            collectRegionBlocks(branch.no, result);
        }
    }

    /**
     * A block-local temporary only needs a root if a collection can occur before its last use.
     * Throwing checks cannot resume in a method without handlers. Shared values, monitors and
     * handlers stay conservative; their exceptional/control-flow liveness is not block-local.
     */
    private void analyzeTemporaryRoots() {
        if(!rootedReferences
                || !method.bytecode().tryCatchBlocks.isEmpty()
                || !monitorEntries.isEmpty()
                || (method.bytecode().access & ACC_SYNCHRONIZED) != 0) return;
        for(Block current : blocks) {
            Map<Value, Integer> lastUses = new IdentityHashMap<>();
            for(int i = 0; i < current.steps.size(); ++i) {
                Map<Value, Integer> uses = new IdentityHashMap<>();
                current.steps.get(i).expression.uses(uses);
                for(Value value : uses.keySet()) lastUses.put(value, i);
            }
            for(int i = 0; i < current.steps.size(); ++i) {
                Step definition = current.steps.get(i);
                if(definition.kind != Kind.LET
                        || definition.value.shared
                        || !definition.value.rooted()) continue;
                int last = lastUses.getOrDefault(definition.value, i);
                boolean crossesCollection = false;
                for(int j = i + 1; j <= last; ++j)
                    if(current.steps.get(j).expression.collects()) {
                        crossesCollection = true;
                        break;
                    }
                if(!crossesCollection) definition.value.rootedReferences = false;
            }
        }
    }

    /**
     * Root references live through a collecting expression or a loop poll, including handlers.
     */
    private void analyzeReferenceLiveness() {
        if(!rootedReferences
                || !monitorEntries.isEmpty()
                || (method.bytecode().access & ACC_SYNCHRONIZED) != 0) return;
        Map<String, Local> byName = new HashMap<>();
        for(Local local : locals.values()) byName.put(local.name, local);
        Map<Block, Set<Object>> incoming = new IdentityHashMap<>();
        for(Block current : blocks) incoming.put(current, new HashSet<>());
        Set<Object> required = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            // A conservative exceptional join is deliberately shared by protected
            // blocks. It includes the old value of a local whose assignment throws.
            Set<Object> exceptional = new HashSet<>();
            for(Block handler : handlerEntries) exceptional.addAll(incoming.get(handler));
            // A successful-path leaf can allocate its exception before unwinding.
            // Handler-visible references must survive that failure even when the
            // ordinary call expression is classified as noncollecting.
            required.addAll(exceptional);
            for(int blockIndex = blocks.size() - 1; blockIndex >= 0; --blockIndex) {
                Block current = blocks.get(blockIndex);
                Set<Object> live = new HashSet<>();
                for(Block next : current.successors()) live.addAll(incoming.get(next));
                if(!loops.isEmpty()) required.addAll(live);
                for(int i = current.steps.size() - 1; i >= 0; --i) {
                    Step step = current.steps.get(i);
                    if(step.kind == Kind.STORE) live.remove(step.local);
                    if(step.kind == Kind.LET || step.kind == Kind.EDGE) live.remove(step.value);
                    live.addAll(exceptional);
                    Map<Value, Integer> values = new IdentityHashMap<>();
                    step.expression.uses(values);
                    for(Value value : values.keySet()) if(reference(value.type)) live.add(value);
                    for(String name : step.expression.reads()) {
                        Local local = byName.get(name);
                        if(local != null && reference(local.type)) live.add(local);
                    }
                    if(step.expression.collects()) required.addAll(live);
                }
                if(!loops.isEmpty()) required.addAll(live);
                if(!incoming.get(current).equals(live)) {
                    incoming.put(current, live);
                    changed = true;
                }
            }
        } while(changed);
        for(Local local : locals.values())
            if(!local.receiver && !local.parameter)
                local.rootedReferences = required.contains(local);
        for(Block current : blocks)
            for(Step step : current.steps)
                if(step.value != null && reference(step.value.type))
                    step.value.rootedReferences = required.contains(step.value);
        for(Value value : sharedValues) value.rootedReferences = required.contains(value);
    }

    private void prepare() {
        int line = -1;
        for(AbstractInsnNode instruction : method.bytecode().instructions) {
            positions.put(instruction, instructions.size());
            if(instruction instanceof LineNumberNode location) line = location.line;
            if(instruction.getOpcode() >= 0) {
                lines.put(instructions.size(), line);
                instructions.add(instruction);
            }
        }
        if(instructions.isEmpty()) fail("method has no instructions");
        prepareMonitors();
        var starts = new TreeSet<Integer>(Set.of(0));
        for(MonitorRegion monitor : monitorEntries.values()) {
            starts.add(monitor.enter);
            starts.add(monitor.enter + 1);
            starts.add(monitor.end);
        }
        for(int i = 0; i < instructions.size(); ++i) {
            AbstractInsnNode instruction = instructions.get(i);
            if(instruction instanceof JumpInsnNode jump) starts.add(positions.get(jump.label));
            if(instruction instanceof TableSwitchInsnNode selection) {
                starts.add(positions.get(selection.dflt));
                for(LabelNode label : selection.labels) starts.add(positions.get(label));
            }
            if(instruction instanceof LookupSwitchInsnNode selection) {
                starts.add(positions.get(selection.dflt));
                for(LabelNode label : selection.labels) starts.add(positions.get(label));
            }
            int opcode = instruction.getOpcode();
            if((instruction instanceof JumpInsnNode
                    || opcode == TABLESWITCH
                    || opcode == LOOKUPSWITCH
                    || opcode == ATHROW
                    || opcode >= IRETURN && opcode <= RETURN)
                    && i + 1 < instructions.size()) starts.add(i + 1);
        }
        for(TryCatchBlockNode handler : exceptionTable) {
            starts.add(positions.get(handler.start));
            starts.add(positions.get(handler.end));
            starts.add(positions.get(handler.handler));
        }
        var points = new ArrayList<>(starts);
        for(int i = 0; i < points.size(); ++i) {
            int start = points.get(i);
            if(start == instructions.size()) continue; // Exclusive end of a protected interval.
            if(monitorHandlers.get(start)) continue;
            var frame =
                    method.frames()[
                            method.bytecode().instructions.indexOf(instructions.get(start))];
            if(frame == null) continue;
            Block current =
                    new Block(
                            start,
                            i + 1 == points.size() ? instructions.size() : points.get(i + 1));
            for(int n = 0; n < frame.getStackSize(); ++n) {
                Type type = frame.getStack(n).getType();
                if(type == null) fail("control-flow join has an untyped value");
                current.inputs.add(new Value(type, "", null, rootedReferences));
            }
            blocks.add(current);
            blockAt.put(start, current);
        }
        prepareProtections();
        int slot = 0;
        if((method.bytecode().access & ACC_STATIC) == 0)
            local(slot++, OBJECT, true, false, "self");
        Type[] arguments = Type.getArgumentTypes(method.id().descriptor());
        for(int i = 0; i < arguments.length; ++i) {
            Local parameter = local(slot, arguments[i], false, true, "arg" + i);
            parameters.add(parameter);
            slot += arguments[i].getSize();
        }
    }

    private record MonitorRegion(int enter, int handler, int end, int slot, String name) {
        boolean contains(Block block) {
            return block.start >= enter && block.start < handler;
        }
    }

    /**
     * Recognize javac's monitor cleanup handlers before constructing exception regions.
     */
    private void prepareMonitors() {
        exceptionTable.addAll(method.bytecode().tryCatchBlocks);
        for(int i = 0; i < instructions.size(); ++i) {
            if(instructions.get(i).getOpcode() != MONITORENTER) continue;
            if(i < 2
                    || !(instructions.get(i - 1) instanceof VarInsnNode store)
                    || store.getOpcode() != ASTORE
                    || instructions.get(i - 2).getOpcode() != DUP)
                throw new Unstructured("monitor acquisition has no lexical lock variable");
            int enter = i;
            TryCatchBlockNode cleanup =
                    exceptionTable.stream()
                            .filter(h -> h.type == null && positions.get(h.start) == enter + 1)
                            .filter(h -> monitorCleanup(positions.get(h.handler), store.var))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new Unstructured(
                                                    "monitor has no structured cleanup handler"));
            int handler = positions.get(cleanup.handler);
            if(handler + 4 >= instructions.size()
                    || !(instructions.get(handler) instanceof VarInsnNode caught)
                    || caught.getOpcode() != ASTORE
                    || !(instructions.get(handler + 1) instanceof VarInsnNode lock)
                    || lock.getOpcode() != ALOAD
                    || lock.var != store.var
                    || instructions.get(handler + 2).getOpcode() != MONITOREXIT
                    || !(instructions.get(handler + 3) instanceof VarInsnNode rethrow)
                    || rethrow.getOpcode() != ALOAD
                    || rethrow.var != caught.var
                    || instructions.get(handler + 4).getOpcode() != ATHROW)
                fail("monitor cleanup is not a lexical unlock and rethrow");
            MonitorRegion monitor =
                    new MonitorRegion(
                            i,
                            handler,
                            handler + 5,
                            store.var,
                            CppNames.unique("synchronized_block", usedNames));
            monitorEntries.put(i, monitor);
            for(int n = i + 1; n < handler; ++n) {
                AbstractInsnNode instruction = instructions.get(n);
                if(instruction instanceof VarInsnNode write
                        && write.getOpcode() == ASTORE
                        && write.var == store.var)
                    fail("monitor lock variable is reassigned inside its scope");
                if(instruction.getOpcode() == MONITOREXIT
                        && instructions.get(n - 1) instanceof VarInsnNode read
                        && read.getOpcode() == ALOAD
                        && read.var == store.var) monitorExits.put(n, monitor);
            }
            for(TryCatchBlockNode entry : exceptionTable)
                if(entry.handler == cleanup.handler && entry.type != null)
                    fail("monitor cleanup handler has an explicit catch type");
            exceptionTable.removeIf(entry -> entry.handler == cleanup.handler);
            monitorHandlers.set(handler, handler + 5);
        }
    }

    private boolean monitorCleanup(int handler, int slot) {
        return handler + 4 < instructions.size()
                && instructions.get(handler) instanceof VarInsnNode caught
                && caught.getOpcode() == ASTORE
                && instructions.get(handler + 1) instanceof VarInsnNode lock
                && lock.getOpcode() == ALOAD
                && lock.var == slot
                && instructions.get(handler + 2).getOpcode() == MONITOREXIT
                && instructions.get(handler + 3) instanceof VarInsnNode rethrow
                && rethrow.getOpcode() == ALOAD
                && rethrow.var == caught.var
                && instructions.get(handler + 4).getOpcode() == ATHROW;
    }

    private Local local(int slot, Type type, boolean receiver, boolean parameter, String fallback) {
        String key = slot + ":" + CppEmitter.type(type);
        return locals.computeIfAbsent(
                key,
                ignored -> {
                    String name = fallback;
                    if(!receiver && method.bytecode().localVariables != null)
                        for(LocalVariableNode variable : method.bytecode().localVariables)
                            if(variable.index == slot
                                    && CppEmitter.type(Type.getType(variable.desc))
                                    .equals(CppEmitter.type(type))) {
                                name = CppNames.identifier(variable.name);
                                break;
                            }
                    name = CppNames.unique(name, usedNames);
                    if(parameter && reference(type)) usedNames.add(name + "_argument");
                    return new Local(name, type, receiver, parameter, rootedReferences);
                });
    }

    private Local local(VarInsnNode instruction) {
        Type type =
                switch(instruction.getOpcode()) {
                    case ILOAD, ISTORE -> Type.INT_TYPE;
                    case LLOAD, LSTORE -> Type.LONG_TYPE;
                    case FLOAD, FSTORE -> Type.FLOAT_TYPE;
                    case DLOAD, DSTORE -> Type.DOUBLE_TYPE;
                    default -> OBJECT;
                };
        return local(instruction.var, type, false, false, "local" + instruction.var);
    }

    private void lower(Block current) {
        block = current;
        fieldReads.clear();
        arrayReads.clear();
        localReadIdentities.clear();
        stack.clear();
        stack.addAll(current.inputs);
        for(int i = current.start; i < current.end; ++i) {
            AbstractInsnNode instruction = instructions.get(i);
            sourceLine = lines.get(i);
            int opcode = instruction.getOpcode();
            // Reuse ordinary Java loads only inside a straight-line region with no call,
            // publication, class initialization, monitor or receiver reassignment.
            if(instruction instanceof MethodInsnNode
                    || instruction instanceof InvokeDynamicInsnNode
                    || opcode == NEW
                    || opcode == PUTSTATIC
                    || opcode == PUTFIELD
                    || opcode == ASTORE
                    || opcode == MONITORENTER
                    || opcode == MONITOREXIT) {
                fieldReads.clear();
                arrayReads.clear();
            }
            // Primitive payload stores cannot replace entries of an Object[]. Keep
            // those reference snapshots, but invalidate all potentially aliased
            // primitive reads. Reference stores retain the conservative barrier.
            if(opcode >= IASTORE && opcode <= SASTORE) {
                if(opcode == AASTORE) arrayReads.clear();
                else arrayReads.keySet().removeIf(read -> read.kind != 4);
            }
            if((opcode >= ISTORE && opcode <= ASTORE)
                    || opcode == IINC) arrayReads.clear();
            // Recognize the ordinary throw-new idiom without exposing allocation plumbing.
            // Only built-in exceptions with a literal UTF-8-compatible message qualify.
            if(opcode == NEW
                    && i + 4 < current.end
                    && instruction instanceof TypeInsnNode allocation
                    && RuntimeLibrary.throwable(allocation.desc)
                    && program.platform(allocation.desc)
                    && instructions.get(i + 1).getOpcode() == DUP
                    && instructions.get(i + 2) instanceof LdcInsnNode literal
                    && literal.cst instanceof String text
                    && text.indexOf('\0') < 0
                    && text.chars().noneMatch(c -> Character.isSurrogate((char)c))
                    && instructions.get(i + 3) instanceof MethodInsnNode constructor
                    && constructor.owner.equals(allocation.desc)
                    && constructor.name.equals("<init>")
                    && constructor.desc.equals("(Ljava/lang/String;)V")
                    && instructions.get(i + 4).getOpcode() == ATHROW) {
                effect(
                        Expr.text(
                                "::jnative::raise("
                                        + quote(allocation.desc)
                                        + ", "
                                        + quote(text)
                                        + ")",
                                true));
                current.endKind = End.RETURN;
                i += 4;
                continue;
            }
            if(opcode == NOP) continue;
            if(opcode == ACONST_NULL) {
                constant(OBJECT, "nullptr");
                continue;
            }
            if(opcode >= ICONST_M1 && opcode <= ICONST_5) {
                constant(Type.INT_TYPE, Integer.toString(opcode - ICONST_0));
                continue;
            }
            if(opcode >= LCONST_0 && opcode <= LCONST_1) {
                constant(Type.LONG_TYPE, longInteger(opcode - LCONST_0));
                continue;
            }
            if(opcode >= FCONST_0 && opcode <= FCONST_2) {
                constant(Type.FLOAT_TYPE, (opcode - FCONST_0) + ".0f");
                continue;
            }
            if(opcode >= DCONST_0 && opcode <= DCONST_1) {
                constant(Type.DOUBLE_TYPE, (opcode - DCONST_0) + ".0");
                continue;
            }
            if(instruction instanceof IntInsnNode value
                    && (opcode == BIPUSH || opcode == SIPUSH)) {
                constant(Type.INT_TYPE, Integer.toString(value.operand));
                continue;
            }
            if(instruction instanceof LdcInsnNode constant) {
                constant(constant.cst);
                continue;
            }
            if(instruction instanceof VarInsnNode variable) {
                Local local = local(variable);
                if(opcode >= ILOAD && opcode <= ALOAD) {
                    Value value =
                            value(
                                    local.type,
                                    local.name + "_value",
                                    new Expr(
                                            List.of(),
                                            ignored -> local.read(),
                                            false,
                                            Set.of(local.name)));
                    value.receiver = local.receiver;
                    value.expression.directLocal = local;
                    value.expression.readIdentity =
                            localReadIdentities.computeIfAbsent(local, ignored -> new Object());
                    value.expression.confinedArray = confinedArrays.contains(instruction);
                    stack.add(value);
                }
                else if(opcode >= ISTORE && opcode <= ASTORE) {
                    if(local.receiver) fail("bytecode reassigns its receiver slot");
                    block.steps.add(new Step(Kind.STORE, Expr.use(pop()), null, local, sourceLine));
                    localReadIdentities.remove(local);
                }
                else fail("unsupported local instruction " + opcode);
                continue;
            }
            if(instruction instanceof IincInsnNode increment) {
                Local local =
                        local(increment.var, Type.INT_TYPE, false, false, "local" + increment.var);
                Expr expression =
                        new Expr(
                                List.of(),
                                ignored ->
                                        "::jnative::add("
                                                + local.name
                                                + ", "
                                                + increment.incr
                                                + ")",
                                false,
                                Set.of(local.name));
                block.steps.add(new Step(Kind.STORE, expression, null, local, sourceLine));
                expression.incrementLocal = local;
                expression.incrementAmount = increment.incr;
                localReadIdentities.remove(local);
                continue;
            }
            if(opcode >= IADD && opcode <= DREM) {
                Value right = pop(), left = pop();
                int operation = (opcode - IADD) / 4;
                boolean integral =
                        left.type.equals(Type.INT_TYPE) || left.type.equals(Type.LONG_TYPE);
                Expr expression =
                        expr(
                                List.of(left, right),
                                v ->
                                        integral
                                                ? "::jnative::"
                                                + List.of(
                                                        "add",
                                                        "sub",
                                                        "mul",
                                                        "divide",
                                                        "remainder")
                                                .get(operation)
                                                + "("
                                                + v.get(0)
                                                + ", "
                                                + v.get(1)
                                                + ")"
                                                : operation == 4
                                                ? "std::fmod("
                                                + v.get(0)
                                                + ", "
                                                + v.get(1)
                                                + ")"
                                                : "("
                                                + v.get(0)
                                                + " "
                                                + List.of("+", "-", "*", "/")
                                                .get(operation)
                                                + " "
                                                + v.get(1)
                                                + ")",
                                integral && operation >= 3);
                expression.integerOperation = opcode;
                push(left.type, "value", expression);
                continue;
            }
            if(opcode >= INEG && opcode <= DNEG) {
                Value value = pop();
                push(
                        value.type,
                        "value",
                        expr(
                                List.of(value),
                                v ->
                                        reference(value.type)
                                                ? ""
                                                : value.type.equals(Type.INT_TYPE)
                                                || value.type.equals(Type.LONG_TYPE)
                                                ? "::jnative::sub("
                                                + type(value.type)
                                                + "(0), "
                                                + v.getFirst()
                                                + ")"
                                                : "(-" + v.getFirst() + ")",
                                false));
                continue;
            }
            if(opcode >= ISHL && opcode <= LUSHR) {
                Value right = pop(), left = pop();
                push(
                        left.type,
                        "value",
                        expr(
                                List.of(left, right),
                                v ->
                                        "::jnative::"
                                                + List.of(
                                                        "shift_left",
                                                        "shift_right",
                                                        "unsigned_shift")
                                                .get((opcode - ISHL) / 2)
                                                + "("
                                                + v.get(0)
                                                + ", "
                                                + v.get(1)
                                                + ")",
                                false));
                continue;
            }
            if(opcode >= IAND && opcode <= LXOR) {
                Value right = pop(), left = pop();
                push(
                        left.type,
                        "value",
                        expr(
                                List.of(left, right),
                                v ->
                                        "("
                                                + v.get(0)
                                                + " "
                                                + List.of("&", "|", "^").get((opcode - IAND) / 2)
                                                + " "
                                                + v.get(1)
                                                + ")",
                                false));
                stack.getLast().expression.integerOperation = opcode;
                continue;
            }
            if(opcode >= I2L && opcode <= I2S) {
                convert(opcode);
                continue;
            }
            if(opcode >= LCMP && opcode <= DCMPG) {
                Value right = pop(), left = pop();
                // Comparison may mention an operand repeatedly; materialization preserves reads.
                push(
                        Type.INT_TYPE,
                        "comparison",
                        expr(
                                List.of(left, right),
                                v -> {
                                    String a = v.get(0), b = v.get(1);
                                    String comparison =
                                            "("
                                                    + a
                                                    + " > "
                                                    + b
                                                    + " ? 1 : ("
                                                    + a
                                                    + " == "
                                                    + b
                                                    + " ? 0 : -1))";
                                    return opcode == FCMPG || opcode == DCMPG
                                            ? "("
                                            + a
                                            + " < "
                                            + b
                                            + " ? -1 : ("
                                            + a
                                            + " == "
                                            + b
                                            + " ? 0 : 1))"
                                            : comparison;
                                },
                                false)
                                .repeats());
                stack.getLast().expression.comparisonOpcode = opcode;
                continue;
            }
            if(opcode == POP) {
                pop();
                continue;
            }
            if(opcode == POP2) {
                if(pop().type.getSize() == 1) pop();
                continue;
            }
            if(opcode >= DUP && opcode <= SWAP) {
                permute(opcode);
                continue;
            }
            if(instruction instanceof JumpInsnNode jump) {
                branch(jump, i);
                continue;
            }
            if(instruction instanceof TableSwitchInsnNode selection) {
                for(int n = 0; n < selection.labels.size(); ++n)
                    current.cases.put(
                            selection.min + n, blockAt.get(positions.get(selection.labels.get(n))));
                selection(blockAt.get(positions.get(selection.dflt)));
                continue;
            }
            if(instruction instanceof LookupSwitchInsnNode selection) {
                for(int n = 0; n < selection.labels.size(); ++n)
                    current.cases.put(
                            selection.keys.get(n),
                            blockAt.get(positions.get(selection.labels.get(n))));
                selection(blockAt.get(positions.get(selection.dflt)));
                continue;
            }
            if(opcode >= IRETURN && opcode <= ARETURN) {
                block.steps.add(new Step(Kind.RETURN, Expr.use(pop()), null, null, sourceLine));
                current.endKind = End.RETURN;
                continue;
            }
            if(opcode == RETURN) {
                effect(Expr.text("return", false));
                current.endKind = End.RETURN;
                continue;
            }
            if(opcode == ATHROW) {
                effect(
                        expr(
                                List.of(pop()),
                                v -> "::jnative::throw_object(" + v.getFirst() + ")",
                                true));
                current.endKind = End.RETURN;
                continue;
            }
            if(instruction instanceof FieldInsnNode field) {
                field(field);
                continue;
            }
            if(instruction instanceof MethodInsnNode call) {
                call(call);
                continue;
            }
            if(instruction instanceof InvokeDynamicInsnNode dynamic) {
                concatenate(dynamic);
                continue;
            }
            if(instruction instanceof MultiANewArrayInsnNode array) {
                var dimensions = new ArrayList<Value>();
                for(int n = 0; n < array.dims; ++n) dimensions.addFirst(pop());
                push(
                        OBJECT,
                        "array",
                        expr(
                                dimensions,
                                v ->
                                        "::jnative::multi_array("
                                                + quote(array.desc)
                                                + ", {"
                                                + String.join(", ", v)
                                                + "})",
                                true));
                continue;
            }
            if(instruction instanceof TypeInsnNode type) {
                objectInstruction(type);
                continue;
            }
            if(instruction instanceof IntInsnNode value && opcode == NEWARRAY) {
                String descriptor =
                        switch(value.operand) {
                            case T_BOOLEAN -> "[Z";
                            case T_CHAR -> "[C";
                            case T_BYTE -> "[B";
                            case T_SHORT -> "[S";
                            case T_INT -> "[I";
                            case T_LONG -> "[J";
                            case T_FLOAT -> "[F";
                            case T_DOUBLE -> "[D";
                            default -> throw new Unstructured("invalid primitive array type");
                        };
                String storage =
                        switch(value.operand) {
                            case T_CHAR -> "std::uint16_t";
                            case T_SHORT -> "std::int16_t";
                            default -> type(Type.getType(descriptor.substring(1)));
                        };
                push(
                        OBJECT,
                        "array",
                        expr(
                                List.of(pop()),
                                v ->
                                        (confinedArrays.contains(instruction)
                                                ? "::jnative::new_confined_array<"
                                                + storage
                                                + ">("
                                                : "::jnative::new_array(")
                                                + quote(descriptor)
                                                + ", "
                                                + v.getFirst()
                                                + ")",
                                true));
                stack.getLast().expression.confinedArray = confinedArrays.contains(instruction);
                continue;
            }
            if(opcode == ARRAYLENGTH) {
                push(
                        Type.INT_TYPE,
                        "length",
                        expr(
                                List.of(pop()),
                                v -> "::jnative::array_length(" + v.getFirst() + ")",
                                true)
                                .withoutCollection());
                continue;
            }
            if(opcode >= IALOAD && opcode <= SALOAD || opcode >= IASTORE && opcode <= SASTORE) {
                arrayInstruction(instruction);
                continue;
            }
            if(opcode == MONITORENTER) {
                block.steps.add(
                        new Step(Kind.MONITOR_ENTER, Expr.use(pop()), null, null, sourceLine));
                continue;
            }
            if(opcode == MONITOREXIT) {
                MonitorRegion monitor = monitorExits.get(i);
                if(monitor == null) fail("monitor release has no matching lexical scope");
                pop();
                effect(Expr.text(monitor.name + ".unlock()", true));
                continue;
            }
            fail("instruction " + opcode + " has no structured lowering yet");
        }
        current.outputs.addAll(stack);
        if(current.endKind == null) {
            current.endKind = End.JUMP;
            current.yes = blockAt.get(current.end);
        }
    }

    private void selection(Block defaultTarget) {
        block.endKind = End.SWITCH;
        block.yes = defaultTarget;
        block.condition = new Step(Kind.CONDITION, Expr.use(pop()), null, null, sourceLine);
        block.steps.add(block.condition);
    }

    private void constant(Object constant) {
        if(constant instanceof Integer value) constant(Type.INT_TYPE, integer(value));
        else if(constant instanceof Long value) constant(Type.LONG_TYPE, longInteger(value));
        else if(constant instanceof Float value) constant(Type.FLOAT_TYPE, floating(value, true));
        else if(constant instanceof Double value)
            constant(Type.DOUBLE_TYPE, floating(value, false));
        else if(constant instanceof String value)
            push(OBJECT, "text", Expr.text(backend.stringLiteral(value), true).withoutCollection());
        else if(constant instanceof Type value)
            push(
                    OBJECT,
                    "class_type",
                    Expr.text(backend.classLiteral(value), true).withoutCollection());
        else fail("constant has no structured lowering");
    }

    private void field(FieldInsnNode field) {
        int opcode = field.getOpcode();
        boolean put = opcode == PUTFIELD || opcode == PUTSTATIC,
                isStatic = opcode == PUTSTATIC || opcode == GETSTATIC;
        if(opcode == GETSTATIC && program.bindings().field(field.owner, field.name, field.desc) != null) {
            fieldReads.clear();
            arrayReads.clear();
            push(Type.getType(field.desc), "platform_field", Expr.text(backend.platformFieldCall(field, true), true));
            return;
        }
        if(opcode == GETSTATIC
                && program.primitiveClassField(field.owner, field.name, field.desc)) {
            fieldReads.clear();
            arrayReads.clear();
            push(
                    OBJECT,
                    "class_type",
                    Expr.text(
                                    backend.classLiteral(
                                            Type.getType(RuntimeLibrary.primitive(field.owner))),
                                    true)
                            .withoutCollection());
            return;
        }
        String owner = backend.fieldOwner(field.owner, field.name, field.desc);
        Value value = put ? pop() : null;
        Value receiver = isStatic ? null : pop();
        // Static entry checks its owner. An instance may have been published
        // while another thread is still running its class initializer.
        boolean initializedOwner =
                owner.equals(method.id().owner()) && (method.bytecode().access & ACC_STATIC) != 0;
        if(isStatic && !initializedOwner && block.initialized.add(owner)) {
            // Initialization may mutate fields/arrays, publish data or collect.
            // Keep it at this instruction, before considering reuse of the read.
            fieldReads.clear();
            arrayReads.clear();
            effect(Expr.text(classReference(owner) + "::ensure_initialized()", true));
        }
        FieldRead reusable = null;
        if(!put) {
            ClassNode declaring = program.classes().get(owner);
            FieldNode declaration =
                    declaring == null
                            ? null
                            : declaring.fields.stream()
                            .filter(
                                    f ->
                                            f.name.equals(field.name)
                                                    && f.desc.equals(field.desc))
                            .findFirst()
                            .orElse(null);
            if(declaration == null || (declaration.access & ACC_VOLATILE) != 0) {
                fieldReads.clear();
                arrayReads.clear();
            }
            else {
                reusable = new FieldRead(
                        owner, field.name, field.desc, isStatic ? null : readIdentity(receiver));
                Value earlier = fieldReads.get(reusable);
                if(earlier != null) {
                    stack.add(earlier);
                    return;
                }
            }
        }
        String name = names.field(owner, field.name, field.desc);
        Local stableReceiver = !isStatic && receiver.expression != null
                ? receiver.expression.directLocal : null;
        boolean finalInstance = !put && stableReceiver != null
                && stableReceiver.rooted() && (stableReceiver.parameter || stableReceiver.receiver)
                && unchangedReceiver(stableReceiver)
                && field.desc.startsWith("[")
                && invariantInstanceArrays.computeIfAbsent(
                        new FieldId(owner, field.name, field.desc), this::stableInstanceArray)
                && !initializationMethods().contains(method.id());
        boolean finalStatic = !put && isStatic && invariantStaticArray(owner, field.name, field.desc);
        String invariantSource = finalInstance
                ? "(" + stableReceiver.read() + " ? static_cast<" + names.className(owner)
                + "*>(" + stableReceiver.read() + ")->" + name + ".get() : nullptr)"
                : finalStatic ? classReference(owner) + "::" + name + ".get()" : null;
        var operands = new ArrayList<Value>();
        if(receiver != null && !receiver.receiver) operands.add(receiver);
        if(value != null) operands.add(value);
        Expr expression =
                expr(
                        operands,
                        v -> {
                            String access =
                                    isStatic
                                            ? classReference(owner) + "::"
                                            : receiver.receiver
                                            ? owner.equals(method.id().owner())
                                            ? "this->"
                                            : "static_cast<"
                                            + names.className(owner)
                                            + "*>(this)->"
                                            : "static_cast<"
                                            + names.className(owner)
                                            + "*>(::jnative::require_non_null("
                                            + v.getFirst()
                                            + "))->";
                            String ordinary = access
                                    + name
                                    + (put
                                    ? ".set("
                                    + narrow(Type.getType(field.desc), v.getLast())
                                    + ")"
                                    : ".get()");
                            if(invariantSource != null) {
                                for(var cached : arrayViews.entrySet()) {
                                    ArrayViewKey key = cached.getKey();
                                    if(invariantSource.equals(key.field)) {
                                        boolean checkedReceiver = finalInstance && !stableReceiver.receiver
                                                && !uncheckedArrayViews.contains(key);
                                        return (checkedReceiver
                                                ? "(::jnative::require_non_null(" + stableReceiver.read() + "), " : "")
                                                + cached.getValue() + ".object()"
                                                + (checkedReceiver ? ")" : "");
                                    }
                                }
                            }
                            return ordinary;
                        },
                        true);
        if(put && (isStatic || receiver.receiver)) expression.afterArguments = true;
        expression.withoutCollection();
        if(!put) expression.confinedArray = confinedArrays.contains(field);
        if(finalInstance || finalStatic) {
            expression.invariantArray = invariantSource;
            expression.invariantArrayName = name;
        }
        if(!put
                && !finalInstance && !finalStatic && !isStatic
                && receiver.receiver
                && expression.confinedArray
                && !method.id().name().equals("<init>")) {
            expression.invariantArray = expression.code();
            expression.invariantArrayName = name;
        }
        if(backend.isInterface(method.id().owner()) && receiver != null && receiver.receiver)
            fail("interface field receiver needs an adapter");
        if(put) effect(expression);
        else {
            push(Type.getType(field.desc), name + "_value", expression);
            if(reusable != null) fieldReads.put(reusable, stack.getLast());
        }
    }

    private boolean invariantStaticArray(String owner, String name, String descriptor) {
        if(!owner.equals(method.id().owner())
                || (method.bytecode().access & ACC_STATIC) == 0
                || method.id().name().equals("<clinit>")
                || descriptor.length() != 2
                || descriptor.charAt(0) != '['
                || "ZBCSIJFD".indexOf(descriptor.charAt(1)) < 0) return false;
        return invariantStaticArrays.computeIfAbsent(
                new FieldId(owner, name, descriptor), this::stableStaticArray);
    }

    private boolean unchangedReceiver(Local receiver) {
        for(AbstractInsnNode instruction : instructions)
            if(instruction instanceof VarInsnNode store && store.getOpcode() == ASTORE
                    && local(store) == receiver) return false;
        return true;
    }

    private boolean stableInstanceArray(FieldId field) {
        ClassNode declaring = program.classes().get(field.owner());
        FieldNode declaration = declaring == null ? null : declaring.fields.stream()
                .filter(f -> f.name.equals(field.name()) && f.desc.equals(field.descriptor()))
                .findFirst().orElse(null);
        if(declaration == null || (declaration.access & (ACC_STATIC | ACC_FINAL | ACC_VOLATILE)) != ACC_FINAL
                || program.bindings().field(field.owner(), field.name(), field.descriptor()) != null)
            return false;
        for(FieldId reflected : program.reflection().fields())
            if(sameStaticArray(field, reflected.owner(), reflected.name(), reflected.descriptor())) return false;
        for(Program.Method reachable : program.methods().values()) {
            NativeBinding binding = reachable.nativeBinding();
            if(binding != null)
                for(NativeBinding.Field exposed : binding.fields())
                    if(sameStaticArray(field, exposed.owner(), exposed.name(), exposed.descriptor())) return false;
            boolean exported = Annotations.all(reachable.bytecode().visibleAnnotations,
                    reachable.bytecode().invisibleAnnotations).stream().anyMatch(a -> a.desc.equals(
                    "Lcom/github/xpenatan/jnative/interop/NativeExport;"));
            if((binding != null || exported)
                    && (binding == null || (!binding.runtimeOnly() && !binding.registeredReflection()))) return false;
            for(AbstractInsnNode instruction : reachable.bytecode().instructions) {
                if(instruction instanceof MethodInsnNode call && call.name.equals("<init>")
                        && call.owner.equals(field.owner())) {
                    // A normal invokespecial constructor call cannot reinitialize a
                    // published object. Reject malformed explicit constructor calls.
                    if(call.getOpcode() != INVOKESPECIAL) return false;
                }
                if(instruction instanceof FieldInsnNode write && write.getOpcode() == PUTFIELD
                        && sameStaticArray(field, write.owner, write.name, write.desc)
                        && (!reachable.id().owner().equals(field.owner())
                        || !reachable.id().name().equals("<init>"))) return false;
            }
            if(reachable.id().owner().equals(field.owner()) && reachable.id().name().equals("<init>")
                    && !unpublishedBeforeFinalWrite(reachable, field)) return false;
        }
        return true;
    }

    private boolean unpublishedBeforeFinalWrite(Program.Method constructor, FieldId field) {
        MethodNode bytecode = constructor.bytecode();
        int lastWrite = -1;
        for(int i = 0; i < bytecode.instructions.size(); i++)
            if(bytecode.instructions.get(i) instanceof FieldInsnNode write
                    && write.getOpcode() == PUTFIELD
                    && sameStaticArray(field, write.owner, write.name, write.desc)) lastWrite = i;
        if(lastWrite < 0) return true; // This constructor never changes the default null reference.
        if(!bytecode.tryCatchBlocks.isEmpty()) return false;
        try {
            var frames = new Analyzer<>(new SourceInterpreter()).analyze(field.owner(), bytecode);
            for(int i = 0; i <= lastWrite; i++) {
                AbstractInsnNode instruction = bytecode.instructions.get(i);
                int opcode = instruction.getOpcode();
                if(instruction instanceof MethodInsnNode call) {
                    if(opcode != INVOKESPECIAL || !call.owner.equals("java/lang/Object")
                            || !call.name.equals("<init>") || !call.desc.equals("()V")) return false;
                }
                else if(instruction instanceof InvokeDynamicInsnNode
                        || instruction instanceof JumpInsnNode
                        || instruction instanceof TableSwitchInsnNode
                        || instruction instanceof LookupSwitchInsnNode
                        || opcode == PUTSTATIC || opcode == AASTORE || opcode == MONITORENTER
                        || opcode == MONITOREXIT) return false;
                if(opcode == PUTFIELD) {
                    var frame = frames[i];
                    if(frame == null || frame.getStackSize() < 2) return false;
                    var receiver = frame.getStack(frame.getStackSize() - 2);
                    if(receiver.insns.isEmpty() || receiver.insns.stream().anyMatch(source ->
                            !(source instanceof VarInsnNode load) || load.getOpcode() != ALOAD || load.var != 0))
                        return false;
                }
            }
            return true;
        } catch(AnalyzerException invalid) {
            return false;
        }
    }

    private Set<MethodId> initializationMethods() {
        if(initializationMethods != null) return initializationMethods;
        initializationMethods = new HashSet<>();
        Map<String, List<MethodId>> signatures = new HashMap<>();
        ArrayDeque<MethodId> pending = new ArrayDeque<>();
        for(MethodId id : program.methods().keySet()) {
            signatures.computeIfAbsent(id.name() + id.descriptor(), ignored -> new ArrayList<>()).add(id);
            if(id.name().equals("<init>") || id.name().equals("<clinit>")) pending.add(id);
        }
        // Reentrant initialization can observe a final field before its last write.
        // Include every potential virtual implementation, without alias assumptions.
        while(!pending.isEmpty()) {
            MethodId id = pending.removeFirst();
            if(!initializationMethods.add(id)) continue;
            Program.Method reachable = program.methods().get(id);
            if(reachable == null) continue;
            for(AbstractInsnNode instruction : reachable.bytecode().instructions)
                if(instruction instanceof MethodInsnNode call) {
                    if(call.getOpcode() == INVOKEVIRTUAL || call.getOpcode() == INVOKEINTERFACE)
                        pending.addAll(signatures.getOrDefault(call.name + call.desc, List.of()));
                    else pending.add(backend.resolve(call.owner, call.name, call.desc));
                }
        }
        return initializationMethods;
    }

    private boolean stableStaticArray(FieldId field) {
        ClassNode declaring = program.classes().get(field.owner());
        FieldNode declaration = declaring.fields.stream()
                .filter(f -> f.name.equals(field.name()) && f.desc.equals(field.descriptor()))
                .findFirst().orElse(null);
        if(declaration == null
                || (declaration.access & (ACC_STATIC | ACC_FINAL | ACC_VOLATILE))
                != (ACC_STATIC | ACC_FINAL)
                || program.bindings().field(field.owner(), field.name(), field.descriptor()) != null)
            return false;
        for(FieldId reflected : program.reflection().fields())
            if(sameStaticArray(field, reflected.owner(), reflected.name(), reflected.descriptor()))
                return false;
        for(Program.Method reachable : program.methods().values()) {
            NativeBinding binding = reachable.nativeBinding();
            if(binding != null) {
                for(NativeBinding.Field exposed : binding.fields())
                    if(sameStaticArray(field, exposed.owner(), exposed.name(), exposed.descriptor()))
                        return false;
            }
            boolean exported = Annotations.all(reachable.bytecode().visibleAnnotations,
                    reachable.bytecode().invisibleAnnotations).stream().anyMatch(
                    annotation -> annotation.desc.equals(
                            "Lcom/github/xpenatan/jnative/interop/NativeExport;"));
            if((binding != null || exported)
                    && (binding == null || (!binding.runtimeOnly() && !binding.registeredReflection())))
                return false;
            for(AbstractInsnNode instruction : reachable.bytecode().instructions) {
                if(instruction instanceof MethodInsnNode call
                        && call.owner.equals(field.owner()) && call.name.equals("<clinit>"))
                    return false;
                if(instruction instanceof FieldInsnNode write && write.getOpcode() == PUTSTATIC
                        && sameStaticArray(field, write.owner, write.name, write.desc)
                        && (!reachable.id().owner().equals(field.owner())
                        || !reachable.id().name().equals("<clinit>"))) return false;
            }
        }
        // Static entry has initialized this owner, or its same-thread initializer is
        // suspended until return. Its final reference is stable even when still null.
        // The nonthrowing view snapshots metadata only; accesses keep their checks.
        return true;
    }

    private boolean sameStaticArray(FieldId field, String owner, String name, String descriptor) {
        return field.name().equals(name) && field.descriptor().equals(descriptor)
                && field.owner().equals(backend.fieldOwner(owner, name, descriptor));
    }

    private void call(MethodInsnNode original) {
        MethodId declaration = backend.resolve(original.owner, original.name, original.desc);
        // Retain predecessor metadata when resolution leaves the owner unchanged.
        // A detached replacement cannot prove an immediate literal argument.
        MethodInsnNode call =
                program.platform(declaration.owner()) && !declaration.owner().equals(original.owner)
                        ? new MethodInsnNode(
                        original.getOpcode(),
                        declaration.owner(),
                        original.name,
                        original.desc,
                        original.itf)
                        : original;
        Type[] types = Type.getArgumentTypes(call.desc);
        var args = new ArrayList<Value>();
        for(int i = types.length - 1; i >= 0; --i) args.addFirst(pop());
        boolean isStatic = call.getOpcode() == INVOKESTATIC;
        Value receiver = isStatic ? null : pop();
        if(call.owner.equals("java/lang/Object") && call.name.equals("<init>")) {
            if(!receiver.receiver)
                effect(
                        expr(
                                List.of(receiver),
                                v -> "::jnative::require_non_null(" + v.getFirst() + ")",
                                true)
                                .withoutCollection());
            return;
        }
        // The null check follows evaluation of all arguments, as on the JVM.
        // Success cannot collect. A failure unwinds, and handler-visible references
        // are retained separately by exceptional liveness, just like field checks.
        if(receiver != null && !receiver.receiver)
            effect(
                    expr(
                            List.of(receiver),
                            v -> "::jnative::require_non_null(" + v.getFirst() + ")",
                            true)
                            .withoutCollection());
        var operands = new ArrayList<Value>();
        if(receiver != null) operands.add(receiver);
        operands.addAll(args);
        Expr expression =
                expr(
                        operands,
                        rendered -> {
                            String self = isStatic ? null : rendered.getFirst();
                            List<String> values =
                                    rendered.subList(isStatic ? 0 : 1, rendered.size());
                            if(program.platform(call.owner)
                                    || !backend.hasBinding(call) && RuntimeLibrary.intrinsic(call.owner, call.name, call.desc)) {
                                if(!isStatic
                                        && !call.name.equals("<init>")
                                        && !call.owner.equals("java/lang/String")
                                        && !backend.boundedRuntimeCall(call)
                                        && call.getOpcode() != INVOKESPECIAL)
                                    return names.call(
                                            new MethodId(call.owner, call.name, call.desc))
                                            + "("
                                            + String.join(", ", rendered)
                                            + ")";
                                CppEmitter.LibraryExpression library =
                                        backend.lowerLibraryCall(call, self, values,
                                                args.stream().map(value -> value.type).toList());
                                if(library.statements().isEmpty()) return library.expression();
                                String result =
                                        Type.getReturnType(call.desc).equals(Type.VOID_TYPE)
                                                ? library.expression() + ";"
                                                : "return " + library.expression() + ";";
                                return "([&] { "
                                        + String.join(" ", library.statements())
                                        + " "
                                        + result
                                        + " }())";
                            }
                            String owner = declaration.owner(), member = names.method(declaration);
                            if(isStatic)
                                return classReference(owner)
                                        + "::"
                                        + member
                                        + "("
                                        + String.join(", ", values)
                                        + ")";
                            MethodId direct = backend.directTarget(call);
                            if(direct != null) {
                                String target = names.className(direct.owner());
                                if(backend.isInterface(direct.owner()))
                                    return target
                                            + "::"
                                            + names.method(direct)
                                            + "("
                                            + String.join(", ", rendered)
                                            + ")";
                                return "static_cast<"
                                        + target
                                        + "*>("
                                        + self
                                        + ")->"
                                        + names.simpleName(direct.owner())
                                        + "::"
                                        + names.method(direct)
                                        + "("
                                        + String.join(", ", values)
                                        + ")";
                            }
                            if(backend.isInterface(owner) || call.getOpcode() == INVOKEINTERFACE)
                                return (call.getOpcode() == INVOKESPECIAL
                                        || (program.methods()
                                        .get(declaration)
                                        .bytecode()
                                        .access
                                        & ACC_PRIVATE)
                                        != 0
                                        ? names.className(owner) + "::" + member
                                        : names.call(
                                        new MethodId(
                                                call.owner, call.name, call.desc)))
                                        + "("
                                        + String.join(", ", rendered)
                                        + ")";
                            String object =
                                    receiver.receiver && owner.equals(method.id().owner())
                                            ? "this"
                                            : "static_cast<"
                                            + names.className(owner)
                                            + "*>("
                                            + self
                                            + ")";
                            return object
                                    + "->"
                                    + (call.getOpcode() == INVOKESPECIAL
                                    ? names.simpleName(owner) + "::"
                                    : "")
                                    + member
                                    + "("
                                    + String.join(", ", values)
                                    + ")";
                        },
                        true);
        // Library lowering can reuse operands, including arraycopy's count guard.
        // Prevent effectful reads/calls from being duplicated or moved past one
        // another; their existing LET steps preserve Java evaluation order.
        if(program.platform(call.owner)
                || RuntimeLibrary.intrinsic(call.owner, call.name, call.desc)) expression.repeats();
        else expression.afterArguments = true;
        if(backend.boundedCall(method.id(), call)) expression.withoutCollection();
        Type result = Type.getReturnType(call.desc);
        if(result.equals(Type.VOID_TYPE)) effect(expression);
        else push(result, CppNames.identifier(call.name) + "_result", expression);
    }

    private void concatenate(InvokeDynamicInsnNode dynamic) {
        if(!dynamic.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory"))
            fail("invokedynamic has no structured lowering");
        Type[] arguments = Type.getArgumentTypes(dynamic.desc);
        var values = new ArrayList<Value>();
        for(int i = arguments.length - 1; i >= 0; --i) values.addFirst(pop());
        String recipe =
                dynamic.bsm.getName().equals("makeConcat")
                        ? "\u0001".repeat(arguments.length)
                        : (String)dynamic.bsmArgs[0];
        push(
                OBJECT,
                "text",
                expr(
                        values,
                        rendered -> {
                            var parts = new ArrayList<String>();
                            StringBuilder text = new StringBuilder();
                            int argument = 0, constant = 1;
                            for(int i = 0; i < recipe.length(); ++i) {
                                char c = recipe.charAt(i);
                                if(c != 1 && c != 2) {
                                    text.append(c);
                                    continue;
                                }
                                if(!text.isEmpty()) {
                                    parts.add(utf16(text.toString()));
                                    text.setLength(0);
                                }
                                if(c == 1) {
                                    parts.add(
                                            backend.readableTextExpression(
                                                    arguments[argument],
                                                    rendered.get(argument)));
                                    ++argument;
                                }
                                else
                                    parts.add(
                                            utf16(
                                                    String.valueOf(
                                                            dynamic.bsmArgs[constant++])));
                            }
                            if(!text.isEmpty()) parts.add(utf16(text.toString()));
                            return "::jnative::concatenate({"
                                    + String.join(", ", parts)
                                    + "})";
                        },
                        true)
                        .repeats());
    }

    private void objectInstruction(TypeInsnNode instruction) {
        String owner = instruction.desc;
        switch(instruction.getOpcode()) {
            case NEW -> {
                String expression;
                if(!program.platform(owner)) {
                    effect(Expr.text(classReference(owner) + "::ensure_initialized()", true));
                    expression = "::jnative::allocate<" + names.className(owner) + ">()";
                }
                else if(RuntimeLibrary.throwable(owner))
                    expression = "::jnative::allocate<::jnative::Throwable>(" + quote(owner) + ")";
                else if(owner.equals("java/lang/String"))
                    expression = "::jnative::allocate<::jnative::String>(std::u16string{})";
                else if(backend.nativeBase(owner) != null)
                    expression = "::jnative::allocate<" + backend.nativeBase(owner) + ">()";
                else throw new Unstructured("allocation has no structured lowering");
                push(OBJECT, "object", Expr.text(expression, true));
            }
            case ANEWARRAY -> {
                String descriptor = owner.startsWith("[") ? "[" + owner : "[L" + owner + ";";
                push(
                        OBJECT,
                        "array",
                        expr(
                                List.of(pop()),
                                v ->
                                        "::jnative::new_array("
                                                + quote(descriptor)
                                                + ", "
                                                + v.getFirst()
                                                + ")",
                                true));
            }
            case CHECKCAST -> push(
                    OBJECT,
                    "object",
                    expr(
                            List.of(pop()),
                            v ->
                                    "::jnative::check_cast("
                                            + v.getFirst()
                                            + ", "
                                            + backend.typeCheckArgument(owner)
                                            + ")",
                            true));
            case INSTANCEOF -> push(
                    Type.INT_TYPE,
                    "instance",
                    expr(
                            List.of(pop()),
                            v ->
                                    "::jnative::instance_of("
                                            + v.getFirst()
                                            + ", "
                                            + backend.typeCheckArgument(owner)
                                            + ")",
                            true));
            default -> fail("type instruction has no structured lowering");
        }
    }

    private void arrayInstruction(AbstractInsnNode instruction) {
        int opcode = instruction.getOpcode();
        boolean confined = confinedArrays.contains(instruction);
        boolean put = opcode >= IASTORE;
        int kind = opcode - (put ? IASTORE : IALOAD);
        Value value = put ? pop() : null, index = pop(), array = pop();
        ArrayRead reusable = new ArrayRead(kind, readIdentity(array), readIdentity(index));
        if(!put) {
            Value earlier = arrayReads.get(reusable);
            if(earlier != null) {
                stack.add(earlier);
                return;
            }
        }
        Type type =
                switch(kind) {
                    case 1 -> Type.LONG_TYPE;
                    case 2 -> Type.FLOAT_TYPE;
                    case 3 -> Type.DOUBLE_TYPE;
                    case 4 -> OBJECT;
                    default -> Type.INT_TYPE;
                };
        String element =
                switch(kind) {
                    case 5 -> "std::int8_t";
                    case 6 -> "std::uint16_t";
                    case 7 -> "std::int16_t";
                    default -> type(type);
                };
        String access =
                kind == 4
                        ? "reference_" + (put ? "set" : "get")
                        : kind == 5
                        ? "byte_" + (put ? "set" : "get")
                        : (confined ? "confined_array_" : "array_")
                        + (put ? "set" : "get")
                        + "<"
                        + element
                        + ">";
        Expr expression =
                expr(
                        put ? List.of(array, index, value) : List.of(array, index),
                        v -> {
                            String stored = put ? v.get(2) : "";
                            if(put && kind == 7)
                                stored = "std::int16_t(((" + stored + " & 65535) ^ 32768) - 32768)";
                            String view = arrayViews.get(arrayViewKey(array, element));
                            if(kind != 5 && (kind != 4 || !put) && view != null)
                                return view
                                        + (put ? ".set" : ".get")
                                        + (uncheckedArrayViews.contains(
                                        arrayViewKey(array, element))
                                        ? "_unchecked("
                                        : "(")
                                        + v.get(1)
                                        + (put ? ", " + stored : "")
                                        + ")";
                            return "::jnative::"
                                    + access
                                    + "("
                                    + v.get(0)
                                    + ", "
                                    + v.get(1)
                                    + (put ? ", " + stored : "")
                                    + ")";
                        },
                        true);
        if(kind != 5 && (kind != 4 || !put)) {
            expression.arrayElement = element;
            expression.arrayStore = put;
        }
        if(!put || kind != 4) expression.withoutCollection();
        if(put) {
            effect(expression);
            // The successful store has performed its null and bounds checks. Forward
            // only exact-width primitives: byte/boolean, char and short stores narrow
            // their input, and reference stores have a separate type/GC contract.
            // lower() clears prior reads before every possibly aliasing store and
            // at calls, synchronization, local changes and control-flow boundaries.
            if(kind >= 0 && kind <= 3) arrayReads.put(reusable, value);
        }
        else {
            push(type, "element", expression);
            arrayReads.put(reusable, stack.getLast());
        }
    }

    private static Object readIdentity(Value value) {
        Value canonical = value.canonical();
        if(canonical.expression != null) {
            if(canonical.expression.readIdentity != null) return canonical.expression.readIdentity;
            if(canonical.expression.integerConstant != null)
                return canonical.expression.integerConstant;
        }
        return canonical;
    }

    private void convert(int opcode) {
        Value value = pop();
        Type target =
                switch(opcode) {
                    case I2L, F2L, D2L -> Type.LONG_TYPE;
                    case I2F, L2F, D2F -> Type.FLOAT_TYPE;
                    case I2D, L2D, F2D -> Type.DOUBLE_TYPE;
                    default -> Type.INT_TYPE;
                };
        push(
                target,
                "value",
                expr(
                        List.of(value),
                        v -> {
                            String input = v.getFirst();
                            if(opcode == L2I)
                                return "::jnative::signed32(static_cast<std::uint32_t>("
                                        + input
                                        + "))";
                            if(opcode == I2B || opcode == I2S) {
                                int mask = opcode == I2B ? 255 : 65535,
                                        sign = opcode == I2B ? 128 : 32768;
                                return "(((" + input + " & " + mask + ") ^ " + sign + ") - " + sign
                                        + ")";
                            }
                            if(opcode == I2C) return "(" + input + " & 65535)";
                            if((value.type.equals(Type.FLOAT_TYPE)
                                    || value.type.equals(Type.DOUBLE_TYPE))
                                    && (target.equals(Type.INT_TYPE)
                                    || target.equals(Type.LONG_TYPE)))
                                return "::jnative::float_to_integer<"
                                        + type(target)
                                        + ">("
                                        + input
                                        + ")";
                            return "static_cast<" + type(target) + ">(" + input + ")";
                        },
                        false));
    }

    private void permute(int opcode) {
        Value a = pop();
        switch(opcode) {
            case DUP -> stack.addAll(List.of(a, a));
            case SWAP -> {
                Value b = pop();
                stack.addAll(List.of(a, b));
            }
            case DUP_X1 -> {
                Value b = pop();
                stack.addAll(List.of(a, b, a));
            }
            case DUP_X2 -> {
                Value b = pop();
                if(b.type.getSize() == 2) stack.addAll(List.of(a, b, a));
                else {
                    Value c = pop();
                    stack.addAll(List.of(a, c, b, a));
                }
            }
            case DUP2 -> {
                if(a.type.getSize() == 2) stack.addAll(List.of(a, a));
                else {
                    Value b = pop();
                    stack.addAll(List.of(b, a, b, a));
                }
            }
            case DUP2_X1, DUP2_X2 -> {
                var top = new ArrayList<Value>();
                if(a.type.getSize() == 1) top.add(pop());
                top.add(a);
                Value b = pop();
                var below = new ArrayList<Value>();
                if(opcode == DUP2_X2 && b.type.getSize() == 1) below.add(pop());
                below.add(b);
                stack.addAll(top);
                stack.addAll(below);
                stack.addAll(top);
            }
            default -> fail("unsupported stack permutation");
        }
    }

    private void branch(JumpInsnNode jump, int position) {
        block.yes = blockAt.get(positions.get(jump.label));
        if(block.yes == null) fail("branch has no reachable target");
        int opcode = jump.getOpcode();
        if(opcode == GOTO) {
            block.endKind = End.JUMP;
            return;
        }
        Value right, left;
        String operator;
        if(opcode >= IFEQ && opcode <= IFLE) {
            right = literal(Type.INT_TYPE, "0");
            left = pop();
            operator = List.of("==", "!=", "<", ">=", ">", "<=").get(opcode - IFEQ);
        }
        else if(opcode >= IF_ICMPEQ && opcode <= IF_ICMPLE) {
            right = pop();
            left = pop();
            operator = List.of("==", "!=", "<", ">=", ">", "<=").get(opcode - IF_ICMPEQ);
        }
        else if(opcode == IFNULL || opcode == IFNONNULL) {
            right = literal(OBJECT, "nullptr");
            left = pop();
            operator = opcode == IFNULL ? "==" : "!=";
        }
        else if(opcode == IF_ACMPEQ || opcode == IF_ACMPNE) {
            right = pop();
            left = pop();
            operator = opcode == IF_ACMPEQ ? "==" : "!=";
        }
        else throw new Unstructured("conditional branch has no structured lowering");
        // A comparison consumed immediately by a branch needs no intermediate -1/0/1.
        // Keep unordered floating-point outcomes in the predicate, including its inverse.
        if(opcode >= IFEQ
                && opcode <= IFLE
                && left.expression.comparisonOpcode >= LCMP
                && !block.steps.isEmpty()
                && block.steps.getLast().value == left) {
            int comparison = left.expression.comparisonOpcode;
            boolean floating = comparison != LCMP;
            boolean unorderedHigh = comparison == FCMPG || comparison == DCMPG;
            boolean negated =
                    floating
                            && switch(opcode) {
                        case IFGE, IFGT -> unorderedHigh;
                        case IFLT, IFLE -> !unorderedHigh;
                        default -> false;
                    };
            String relation = negated ? inverseComparison(operator) : operator;
            Function<List<String>, String> predicate =
                    v -> {
                        String direct = v.get(0) + " " + relation + " " + v.get(1);
                        return negated ? "!(" + direct + ")" : direct;
                    };
            block.condition =
                    new Step(
                            Kind.CONDITION,
                            expr(left.expression.values, predicate, false),
                            null,
                            null,
                            sourceLine);
            block.inverse = v -> "!(" + predicate.apply(v) + ")";
            block.steps.add(block.condition);
            block.no = blockAt.get(position + 1);
            block.endKind = End.IF;
            return;
        }
        block.condition =
                new Step(
                        Kind.CONDITION,
                        expr(
                                List.of(left, right),
                                v -> v.get(0) + " " + operator + " " + v.get(1),
                                false),
                        null,
                        null,
                        sourceLine);
        block.condition.expression.comparisonOperator = operator;
        block.inverse =
                v ->
                        v.get(0)
                                + " "
                                + switch(operator) {
                            case "==" -> "!=";
                            case "!=" -> "==";
                            case "<" -> ">=";
                            case ">=" -> "<";
                            case ">" -> "<=";
                            default -> ">";
                        }
                                + " "
                                + v.get(1);
        block.steps.add(block.condition);
        block.no = blockAt.get(position + 1);
        block.endKind = End.IF;
    }

    private static String inverseComparison(String operator) {
        return switch(operator) {
            case "==" -> "!=";
            case "!=" -> "==";
            case "<" -> ">=";
            case ">=" -> "<";
            case ">" -> "<=";
            default -> ">";
        };
    }

    private void constant(Type type, String expression) {
        stack.add(literal(type, expression));
    }

    private String classReference(String owner) {
        return owner.equals(method.id().owner()) ? names.simpleName(owner) : names.className(owner);
    }

    private Value literal(Type type, String expression) {
        Value value = new Value(type, "", Expr.text(expression, false), rootedReferences);
        if(type.equals(Type.INT_TYPE)) {
            try {
                value.expression.integerConstant = Integer.valueOf(expression);
            } catch(NumberFormatException ignored) {
                // Nonliteral spellings remain on the checked path.
            }
        }
        value.inline = true;
        return value;
    }

    private void push(Type type, String hint, Expr expression) {
        stack.add(value(type, hint, expression));
    }

    private Value value(Type type, String hint, Expr expression) {
        Value value =
                new Value(type, CppNames.unique(hint, usedNames), expression, rootedReferences);
        block.steps.add(new Step(Kind.LET, expression, value, null, sourceLine));
        return value;
    }

    private Value pop() {
        if(stack.isEmpty()) throw new Unstructured("operand-stack join needs materialization");
        return stack.removeLast();
    }

    private void effect(Expr expression) {
        block.steps.add(new Step(Kind.EFFECT, expression, null, null, sourceLine));
    }

    private static Expr expr(
            List<Value> values, Function<List<String>, String> render, boolean effect) {
        return new Expr(values, render, effect, Set.of());
    }

    private static void fail(String reason) {
        throw new Unstructured(reason);
    }

    private static final class Unstructured extends RuntimeException {
        Unstructured(String reason) {
            super(reason);
        }
    }

    private final class Local {
        final String name;
        final Type type;
        final boolean receiver, parameter;
        boolean rootedReferences;

        Local(
                String name,
                Type type,
                boolean receiver,
                boolean parameter,
                boolean rootedReferences) {
            this.name = name;
            this.type = type;
            this.receiver = receiver;
            this.parameter = parameter;
            this.rootedReferences = rootedReferences;
        }

        boolean rooted() {
            return rootedReferences && reference(type);
        }

        String read() {
            return receiver
                    ? rootedReferences
                    ? "keep_alive.get()"
                    : backend.isInterface(method.id().owner()) ? "self" : "this"
                    : name + (rooted() ? ".get()" : "");
        }
    }

    private static final class Value {
        final Type type;
        String name;
        final Expr expression;
        boolean rootedReferences;
        boolean inline, receiver, shared;
        Value alias;

        Value(Type type, String name, Expr expression, boolean rootedReferences) {
            this.type =
                    type.getSort() >= Type.BOOLEAN && type.getSort() <= Type.INT
                            ? Type.INT_TYPE
                            : type;
            this.name = name;
            this.expression = expression;
            this.rootedReferences = rootedReferences;
        }

        boolean rooted() {
            return rootedReferences && reference(type);
        }

        String code() {
            return inline ? expression.code() : name + (rooted() ? ".get()" : "");
        }

        Value canonical() {
            return alias == null ? this : (alias = alias.canonical());
        }
    }

    private static final class Expr {
        List<Value> values;
        final Function<List<String>, String> render;
        final boolean effect;
        boolean collection;
        final Set<String> locals;
        boolean repeated;
        boolean afterArguments;
        boolean confinedArray;
        String invariantArray;
        String invariantArrayName;
        Local directLocal;
        Object readIdentity;
        String arrayElement;
        boolean arrayStore;
        Integer integerConstant;
        int integerOperation = -1;
        Local incrementLocal;
        int incrementAmount;
        String comparisonOperator;
        int comparisonOpcode = -1;
        boolean passThrough;

        Expr(
                List<Value> values,
                Function<List<String>, String> render,
                boolean effect,
                Set<String> locals) {
            this.values = List.copyOf(values);
            this.render = render;
            this.effect = effect;
            this.collection = effect;
            this.locals = locals;
        }

        static Expr text(String text, boolean effect) {
            return new Expr(List.of(), ignored -> text, effect, Set.of());
        }

        static Expr use(Value value) {
            Expr expression = expr(List.of(value), List::getFirst, false);
            expression.passThrough = true;
            return expression;
        }

        Expr repeats() {
            repeated = true;
            return this;
        }

        Expr withoutCollection() {
            collection = false;
            return this;
        }

        boolean collects() {
            return collection || values.stream().anyMatch(v -> v.inline && v.expression.collects());
        }

        String code() {
            return render.apply(values.stream().map(Value::code).toList());
        }

        int effects() {
            return (effect ? 1 : 0)
                    + values.stream()
                    .filter(v -> v.inline)
                    .mapToInt(v -> v.expression.effects())
                    .sum();
        }

        Set<String> reads() {
            Set<String> result = new HashSet<>(locals);
            for(Value value : values) if(value.inline) result.addAll(value.expression.reads());
            return result;
        }

        void uses(Map<Value, Integer> result) {
            for(Value value : values) {
                if(value.inline) value.expression.uses(result);
                else result.merge(value, repeated ? 2 : 1, Integer::sum);
            }
        }
    }

    private enum Kind {
        LET,
        STORE,
        EFFECT,
        RETURN,
        CONDITION,
        EDGE,
        MONITOR_ENTER
    }

    private record Step(Kind kind, Expr expression, Value value, Local local, int line) {
    }

    private enum End {
        JUMP,
        IF,
        SWITCH,
        RETURN
    }

    private static final class Block {
        final int start, end;
        final List<Step> steps = new ArrayList<>();
        final Set<String> initialized = new HashSet<>();
        final List<Value> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        final Map<Integer, Block> cases = new LinkedHashMap<>();
        final Map<Block, List<Step>> transfers = new HashMap<>();
        End endKind;
        Block yes, no;
        Step condition;
        Step caughtStore;
        Function<List<String>, String> inverse;

        Block(int start, int end) {
            this.start = start;
            this.end = end;
        }

        List<Block> successors() {
            if(endKind == End.SWITCH) {
                var targets = new LinkedHashSet<>(cases.values());
                targets.add(yes);
                return new ArrayList<>(targets);
            }
            return endKind == End.RETURN
                    ? List.of()
                    : endKind == End.IF ? List.of(yes, no) : yes == null ? List.of() : List.of(yes);
        }

        String condition(boolean invert) {
            return invert
                    ? inverse.apply(condition.expression.values.stream().map(Value::code).toList())
                    : condition.expression.code();
        }
    }

    /**
     * Resolve block parameters, keeping only actual merges as named C++ values.
     */
    private void connectValues() {
        Map<Value, List<Value>> incoming = new IdentityHashMap<>();
        for(Block current : blocks)
            for(Block next : current.successors()) {
                if(handlerEntries.contains(next))
                    fail("exception handler also has a normal entry");
                if(current.outputs.size() != next.inputs.size())
                    fail("control-flow edge has inconsistent values");
                for(int i = 0; i < next.inputs.size(); ++i)
                    incoming.computeIfAbsent(next.inputs.get(i), ignored -> new ArrayList<>())
                            .add(current.outputs.get(i));
            }
        boolean changed;
        do {
            changed = false;
            for(var entry : incoming.entrySet()) {
                Value input = entry.getKey();
                if(input.alias != null) continue;
                Set<Value> values = Collections.newSetFromMap(new IdentityHashMap<>());
                for(Value value : entry.getValue())
                    if(value.canonical() != input) values.add(value.canonical());
                if(values.size() == 1) {
                    input.alias = values.iterator().next();
                    changed = true;
                }
            }
        } while(changed);
        for(Block current : blocks) {
            for(Value input : current.inputs)
                if(input.alias == null) {
                    input.name =
                            CppNames.unique(
                                    handlerEntries.contains(current)
                                            ? "caught_value"
                                            : "selected_value",
                                    usedNames);
                    input.shared = true;
                    sharedValues.add(input);
                }
            for(Step step : current.steps)
                step.expression.values =
                        step.expression.values.stream().map(Value::canonical).toList();
            for(Block next : current.successors()) {
                var copies = new ArrayList<Step>();
                for(int i = 0; i < next.inputs.size(); ++i) {
                    Value target = next.inputs.get(i), value = current.outputs.get(i).canonical();
                    if(target.alias != null || target == value) continue;
                    Step copy = new Step(Kind.EDGE, Expr.use(value), target, null, -1);
                    copies.add(copy);
                    current.steps.add(
                            copy); // Include edge uses in liveness and expression sequencing.
                }
                if(!copies.isEmpty()) current.transfers.put(next, copies);
            }
        }
        Map<Value, Block> definitions = new IdentityHashMap<>();
        for(Block current : blocks)
            for(Step step : current.steps)
                if(step.kind == Kind.LET) definitions.put(step.value, current);
        for(Block current : blocks)
            for(Step step : current.steps) {
                Map<Value, Integer> uses = new IdentityHashMap<>();
                step.expression.uses(uses);
                for(Value value : uses.keySet())
                    if(definitions.containsKey(value)
                            && definitions.get(value) != current
                            && !value.shared) {
                        value.shared = true;
                        sharedValues.add(value);
                    }
            }
        Map<Value, Integer> uses = new IdentityHashMap<>();
        for(Block current : blocks) for(Step step : current.steps) step.expression.uses(uses);
        for(Block handler : handlerEntries) {
            Value input = handler.inputs.getFirst();
            if(handler.steps.isEmpty() || uses.getOrDefault(input, 0) != 1) continue;
            Step first = handler.steps.getFirst();
            if(first.kind == Kind.STORE && first.expression.values.equals(List.of(input))) {
                handler.caughtStore = first;
                handler.steps.removeFirst();
                sharedValues.remove(input);
            }
        }
        sharedValues.sort(Comparator.comparing(value -> value.name));
    }

    private void simplify() {
        boolean changed;
        do {
            changed = false;
            Map<Value, Integer> uses = new IdentityHashMap<>();
            for(Block current : blocks) for(Step step : current.steps) step.expression.uses(uses);
            for(Block current : blocks) {
                List<Step> steps = current.steps;
                for(int i = steps.size() - 1; i >= 0; --i) {
                    Step step = steps.get(i);
                    if(step.kind != Kind.LET || step.value.shared) continue;
                    Value value = step.value;
                    int count = uses.getOrDefault(value, 0);
                    boolean pure = step.expression.effects() == 0;
                    if(count == 0) {
                        if(pure) steps.remove(i);
                        else
                            steps.set(
                                    i,
                                    new Step(Kind.EFFECT, step.expression, null, null, step.line));
                        changed = true;
                        break;
                    }
                    int lastUse = -1;
                    boolean localsUnchanged = true;
                    Set<String> reads = step.expression.reads();
                    for(int j = i + 1; j < steps.size(); ++j) {
                        Map<Value, Integer> at = new IdentityHashMap<>();
                        steps.get(j).expression.uses(at);
                        if(at.containsKey(value)) lastUse = j;
                    }
                    for(int j = i + 1; j < lastUse; ++j)
                        if(steps.get(j).kind == Kind.STORE
                                && reads.contains(steps.get(j).local.name)) localsUnchanged = false;
                    boolean simple = step.expression.values.isEmpty();
                    boolean inline = pure && localsUnchanged && (count == 1 || simple);
                    if(!pure && count == 1 && i + 1 < steps.size() && lastUse == i + 1) {
                        Step consumer = steps.get(i + 1);
                        // Only fuse an effect into a pure consumer. In particular, a null
                        // receiver check must not overtake evaluation of a field-store RHS.
                        inline =
                                consumer.kind != Kind.EDGE
                                        && !(splitExceptionRegions
                                        && consumer.kind == Kind.CONDITION)
                                        && (consumer.expression.effects() == 0
                                        || !reference(value.type)
                                        && consumer.expression.afterArguments
                                        && consumer.expression.effects() == 1)
                                        && !consumer.expression.repeated
                                        && step.line == consumer.line
                                        && (!reference(value.type)
                                        || consumer.kind == Kind.STORE
                                        || consumer.kind == Kind.RETURN);
                    }
                    if(inline) {
                        value.inline = true;
                        steps.remove(i);
                        changed = true;
                        break;
                    }
                }
            }
        } while(changed);
    }

    private void removeUnusedLocals() {
        Set<String> reads = new HashSet<>();
        for(Block current : blocks) {
            for(Step step : current.steps) reads.addAll(step.expression.reads());
            if(current.caughtStore != null) reads.add(current.caughtStore.local.name);
        }
        Set<Local> unused = new HashSet<>();
        for(Local local : locals.values())
            if(!local.parameter && !local.receiver && !reads.contains(local.name))
                unused.add(local);
        for(Block current : blocks) {
            for(int i = current.steps.size() - 1; i >= 0; --i) {
                Step step = current.steps.get(i);
                if(step.kind != Kind.STORE || !unused.contains(step.local)) continue;
                if(step.expression.effects() == 0) current.steps.remove(i);
                else
                    current.steps.set(
                            i, new Step(Kind.EFFECT, step.expression, null, null, step.line));
            }
        }
        locals.values().removeAll(unused);
    }

    private record Loop(Block header, Set<Block> members, Block exit) {
    }

    /**
     * Bound the work between polls, including every branch, without changing memory accesses.
     */
    private boolean boundedLoopBody(Loop loop) {
        if(!method.bytecode().tryCatchBlocks.isEmpty()
                || !monitorEntries.isEmpty()
                || !exitLabels.isEmpty()) return false;
        for(Block header : loops.keySet())
            if(header != loop.header && loop.members.contains(header)) return false;
        int count = 0;
        for(Block member : loop.members) {
            for(int i = member.start; i < member.end; ++i) {
                if(++count > 256) return false;
                AbstractInsnNode instruction = instructions.get(i);
                int opcode = instruction.getOpcode();
                if(boundedIntrinsic(instruction)) continue;
                if(instruction instanceof FieldInsnNode field && opcode == GETSTATIC && field.desc.startsWith("[")
                        && invariantStaticArray(backend.fieldOwner(field.owner, field.name, field.desc),
                        field.name, field.desc)) continue;
                if(opcode == JSR || opcode == RET || opcode == AASTORE) return false;
                if(opcode > RETURN
                        && opcode != GETFIELD
                        && opcode != PUTFIELD
                        && opcode != ARRAYLENGTH) return false;
                if(instruction instanceof LdcInsnNode constant
                        && !(constant.cst instanceof Number)) return false;
                if(instruction instanceof JumpInsnNode jump) {
                    int target = positions.get(jump.label);
                    if(target <= i && target != loop.header.start) return false;
                }
                if(instruction instanceof TableSwitchInsnNode selection) {
                    if(positions.get(selection.dflt) <= i) return false;
                    for(LabelNode label : selection.labels)
                        if(positions.get(label) <= i) return false;
                }
                if(instruction instanceof LookupSwitchInsnNode selection) {
                    if(positions.get(selection.dflt) <= i) return false;
                    for(LabelNode label : selection.labels)
                        if(positions.get(label) <= i) return false;
                }
            }
        }
        return true;
    }

    private record Handler(Block entry, List<String> types) {
    }

    private record Protection(Block entry, int end, List<Handler> handlers, int order) {
        boolean contains(Block block) {
            return block != null && block.start >= entry.start && block.start < end;
        }
    }

    private record Selection(Block exit, Set<Block> labels, Block entry, Loop enclosingLoop) {
    }

    private record Context(
            Loop loop,
            List<Protection> protections,
            Selection selection,
            List<MonitorRegion> monitors,
            Context parent) {
        static final Context EMPTY = new Context(null, List.of(), null, List.of(), null);

        Context inside(Protection protection) {
            var nested = new ArrayList<>(protections);
            nested.add(protection);
            return new Context(loop, nested, selection, monitors, this);
        }

        Context inside(Loop nested) {
            return new Context(nested, protections, selection, monitors, this);
        }

        Context inside(Selection nested) {
            return new Context(loop, protections, nested, monitors, this);
        }

        Context inside(MonitorRegion monitor) {
            var nested = new ArrayList<>(monitors);
            nested.add(monitor);
            return new Context(loop, protections, selection, nested, this);
        }
    }

    private void prepareProtections() {
        var groups = new LinkedHashMap<String, List<TryCatchBlockNode>>();
        for(TryCatchBlockNode handler : exceptionTable) {
            int start = positions.get(handler.start), end = positions.get(handler.end);
            if(blockAt.get(start) == null) continue;
            if(splitExceptionRegions && harmlessInterval(start, end)) continue;
            groups.computeIfAbsent(start + ":" + end, ignored -> new ArrayList<>()).add(handler);
        }
        for(var entries : groups.values()) {
            var handlers = new ArrayList<Handler>();
            for(TryCatchBlockNode entry : entries) {
                Block target = blockAt.get(positions.get(entry.handler));
                if(target == null || target.inputs.size() != 1)
                    fail("exception handler has an invalid entry frame");
                handlerEntries.add(target);
                // Adjacent multi-catch entries share one body. Keep exception-table order.
                if(!handlers.isEmpty() && handlers.getLast().entry == target)
                    handlers.getLast().types.add(entry.type);
                else
                    handlers.add(
                            new Handler(
                                    target, new ArrayList<>(Collections.singleton(entry.type))));
            }
            protections.add(
                    new Protection(
                            blockAt.get(positions.get(entries.getFirst().start)),
                            positions.get(entries.getFirst().end),
                            handlers,
                            method.bytecode().tryCatchBlocks.indexOf(entries.getFirst())));
        }
        protections.sort(
                Comparator.comparingInt((Protection p) -> p.entry.start)
                        .thenComparing(Comparator.comparingInt(Protection::end).reversed()));
        if(splitExceptionRegions)
            return; // Preserve the original table order at each throwing block.
        for(int i = 0; i < protections.size(); ++i) {
            Protection first = protections.get(i);
            for(int j = i + 1; j < protections.size(); ++j) {
                Protection second = protections.get(j);
                if(second.entry.start < first.end && second.end > first.end)
                    fail("overlapping exception intervals cannot be nested safely");
                // An outer interval must appear after its inner interval in the JVM
                // table; otherwise it would intercept exceptions before the inner catch.
                if(second.entry.start < first.end && second.end <= first.end) {
                    int outer = first.order, inner = second.order;
                    if(outer < inner)
                        fail("exception-table precedence differs from lexical nesting");
                }
            }
        }
        // javac excludes return instructions from protected ranges and emits
        // another range around the exceptional copy of a finally block. Rejoin
        // identical handlers only across instructions that cannot throw.
        boolean changed;
        do {
            changed = false;
            outer:
            for(int i = 0; i < protections.size(); ++i)
                for(int j = i + 1; j < protections.size(); ++j) {
                    Protection first = protections.get(i), second = protections.get(j);
                    if(first.end > second.entry.start || !first.handlers.equals(second.handlers))
                        continue;
                    boolean safeGap = true;
                    for(int n = first.end; n < second.entry.start; ++n)
                        if(!nonThrowingGap(instructions.get(n).getOpcode())) {
                            safeGap = false;
                            break;
                        }
                    if(!safeGap) continue;
                    protections.set(
                            i,
                            new Protection(first.entry, second.end, first.handlers, first.order));
                    protections.remove(j);
                    changed = true;
                    break outer;
                }
        } while(changed);
        protections.sort(
                Comparator.comparingInt((Protection p) -> p.entry.start)
                        .thenComparing(Comparator.comparingInt(Protection::end).reversed()));
        for(int i = 0; i < protections.size(); ++i)
            for(int j = i + 1; j < protections.size(); ++j) {
                Protection first = protections.get(i), second = protections.get(j);
                if(second.entry.start < first.end && second.end > first.end)
                    fail("disjoint exception intervals cannot be combined into lexical regions");
            }
    }

    private boolean harmlessInterval(int start, int end) {
        for(int i = start; i < end; ++i) {
            int opcode = instructions.get(i).getOpcode();
            if(!nonThrowingGap(opcode)
                    && !(opcode >= IFEQ && opcode <= LOOKUPSWITCH)
                    && opcode != IFNULL
                    && opcode != IFNONNULL) return false;
        }
        return true;
    }

    private static boolean nonThrowingGap(int opcode) {
        return opcode == NOP
                || opcode >= ACONST_NULL && opcode <= SIPUSH
                || opcode >= ILOAD && opcode <= ALOAD
                || opcode >= ISTORE && opcode <= ASTORE
                || opcode >= POP && opcode <= SWAP
                || opcode == IINC
                || opcode == GOTO
                || opcode >= IRETURN && opcode <= RETURN;
    }

    private void bypassEmptyJumps() {
        Block entry = skipEmptyJumps(blocks.getFirst());
        for(Block current : blocks) {
            var transfers = new HashMap<Block, List<Step>>();
            current.transfers.forEach(
                    (target, copies) -> transfers.put(skipEmptyJumps(target), copies));
            current.transfers.clear();
            current.transfers.putAll(transfers);
            current.yes = skipEmptyJumps(current.yes);
            current.no = skipEmptyJumps(current.no);
            current.cases.replaceAll((key, target) -> skipEmptyJumps(target));
        }
        Set<Block> reachable = new HashSet<>();
        var pending = new ArrayDeque<Block>();
        pending.add(entry);
        pending.addAll(handlerEntries);
        while(!pending.isEmpty()) {
            Block current = pending.removeFirst();
            if(reachable.add(current)) pending.addAll(current.successors());
        }
        blocks.removeIf(b -> !reachable.contains(b));
        blocks.remove(entry);
        blocks.addFirst(entry);
    }

    private Block skipEmptyJumps(Block current) {
        Set<Block> seen = new HashSet<>();
        while(current != null
                && current.endKind == End.JUMP
                && current.steps.isEmpty()
                && !anchor(current)
                && current.yes != null
                && current.yes != current
                && seen.add(current)) current = current.yes;
        return current;
    }

    private boolean anchor(Block block) {
        return monitorEntries.containsKey(block.start)
                || monitorEntries.values().stream().anyMatch(m -> m.end == block.start)
                || handlerEntries.contains(block)
                || protections.stream().anyMatch(p -> p.entry == block || p.end == block.start);
    }

    private Set<Block> controlSuccessors(Block block) {
        Set<Block> result = new HashSet<>(block.successors());
        for(Protection protection : protections)
            if(protection.contains(block))
                for(Handler handler : protection.handlers) result.add(handler.entry);
        return result;
    }

    private void analyzeRegions() {
        Set<Block> all = new HashSet<>(blocks);
        Map<Block, Set<Block>> predecessors = new HashMap<>(), dominators = new HashMap<>();
        for(Block current : blocks) {
            predecessors.put(current, new HashSet<>());
            dominators.put(
                    current,
                    current == blocks.getFirst()
                            ? new HashSet<>(Set.of(current))
                            : new HashSet<>(all));
        }
        // Handler paths participate in loop dominance: a catch that continues
        // the loop is reached through its protected body, not a second entry.
        for(Block current : blocks)
            for(Block next : controlSuccessors(current)) predecessors.get(next).add(current);
        boolean changed;
        do {
            changed = false;
            for(Block current : blocks) {
                if(current == blocks.getFirst()) continue;
                Set<Block> next = new HashSet<>(all);
                for(Block previous : predecessors.get(current))
                    next.retainAll(dominators.get(previous));
                next.add(current);
                if(!next.equals(dominators.get(current))) {
                    dominators.put(current, next);
                    changed = true;
                }
            }
        } while(changed);
        Map<Block, Set<Block>> naturalLoops = new HashMap<>();
        for(Block tail : blocks)
            for(Block head : tail.successors())
                if(dominators.get(tail).contains(head)) {
                    Set<Block> members =
                            naturalLoops.computeIfAbsent(
                                    head, ignored -> new HashSet<>(Set.of(head)));
                    var pending = new ArrayDeque<Block>();
                    pending.add(tail);
                    while(!pending.isEmpty()) {
                        Block member = pending.removeFirst();
                        if(members.add(member)) pending.addAll(predecessors.get(member));
                    }
                }
        for(var entry : naturalLoops.entrySet()) {
            Set<Block> exits = new HashSet<>();
            for(Block member : entry.getValue())
                for(Block next : member.successors())
                    if(!entry.getValue().contains(next)) exits.add(next);
            // A return can first release a monitor acquired inside the loop.
            // Keep that tail inside the monitor's C++ scope instead of turning
            // it into a break followed by an out-of-scope unlock.
            exits.removeIf(
                    exit ->
                            monitorEntries.values().stream()
                                    .anyMatch(
                                            monitor ->
                                                    monitor.contains(exit)
                                                            && !entry.getValue().stream()
                                                            .allMatch(monitor::contains)));
            Block head = entry.getKey();
            // Prefer the lexical loop condition's exit. Other exits can be early
            // returns, throws, or labeled exits to an enclosing loop/switch.
            Block exit =
                    head.endKind == End.IF && exits.contains(head.yes)
                            ? head.yes
                            : head.endKind == End.IF && exits.contains(head.no)
                            ? head.no
                            : exits.stream()
                            .filter(b -> b.start > head.start)
                            .max(Comparator.comparingInt(b -> b.start))
                            .orElse(null);
            loops.put(head, new Loop(head, entry.getValue(), exit));
        }
        Set<Block> universe = new HashSet<>(all);
        universe.add(null); // Synthetic method exit.
        for(Block current : blocks) postdominators.put(current, new HashSet<>(universe));
        postdominators.put(null, new HashSet<>(Collections.singleton(null)));
        do {
            changed = false;
            for(int i = blocks.size() - 1; i >= 0; --i) {
                Block current = blocks.get(i);
                Set<Block> next = new HashSet<>(universe);
                if(current.successors().isEmpty()) next.retainAll(postdominators.get(null));
                else
                    for(Block successor : current.successors())
                        next.retainAll(postdominators.get(successor));
                next.add(current);
                if(!next.equals(postdominators.get(current))) {
                    postdominators.put(current, next);
                    changed = true;
                }
            }
        } while(changed);
    }

    private Node region(Block start, Block stop, Context context, Set<Block> visited) {
        var nodes = new ArrayList<Node>();
        Block current = start;
        Loop active = context.loop;
        while(current != null && current != stop) {
            Node escape = controlExit(current, context, visited);
            if(escape != null) {
                nodes.add(escape);
                break;
            }
            if(!context.protections.isEmpty() && !context.protections.getLast().contains(current))
                fail("control flow leaves a protected interval through multiple regions");
            Loop loop = loops.get(current);
            MonitorRegion monitor = monitorEntries.get(current.start);
            if(monitor != null && context.monitors.contains(monitor)) monitor = null;
            Block entry = current;
            Protection protection =
                    splitExceptionRegions
                            ? null
                            : protections.stream()
                            .filter(
                                    p ->
                                            p.entry == entry
                                                    && !context.protections.contains(p))
                            .findFirst()
                            .orElse(null);
            if(protection != null
                    && (loop == null
                    || loop == active
                    || loop.members.stream().allMatch(protection::contains))) {
                ProtectedNode structured = protectedRegion(protection, context, visited);
                nodes.add(new ExitLabelNode(current));
                nodes.add(structured.node);
                current = structured.next;
                continue;
            }
            if(monitor != null
                    && (loop == null
                    || loop == active
                    || loop.members.stream().allMatch(monitor::contains))) {
                visited.add(current);
                Block exit = blockAt.get(monitor.end);
                Step enter =
                        current.steps.stream()
                                .filter(s -> s.kind == Kind.MONITOR_ENTER)
                                .findFirst()
                                .orElseThrow();
                Node acquisition =
                        splitExceptionRegions
                                ? protectedStatements(
                                current,
                                new TextNode(
                                        monitor.name
                                                + ".lock("
                                                + enter.expression.code()
                                                + ");"),
                                context,
                                visited)
                                : null;
                Node body =
                        edgeRegion(current, current.yes, exit, context.inside(monitor), visited);
                nodes.add(new ExitLabelNode(current));
                nodes.add(new MonitorNode(monitor, current, acquisition, body));
                current = exit;
                continue;
            }
            if(loop != null && loop != active) {
                if(visited.contains(current)) fail("control flow re-enters a structured region");
                boolean testAtHead =
                        protection == null
                                && monitor == null
                                && current.endKind == End.IF
                                && (!splitExceptionRegions
                                || protections.stream().noneMatch(p -> p.contains(entry)))
                                && (current.yes == loop.exit || current.no == loop.exit);
                Node body;
                if(testAtHead) {
                    visited.add(current);
                    Block inside = current.yes == loop.exit ? current.no : current.yes;
                    body = edgeRegion(current, inside, current, context.inside(loop), visited);
                }
                else body = region(current, null, context.inside(loop), visited);
                nodes.add(new LoopNode(loop, body, testAtHead));
                current = loop.exit;
                continue;
            }
            if(!visited.add(current))
                fail(
                        "control flow shares a region that cannot be structured safely at"
                                + " instruction "
                                + current.start);
            if(++emittedBlocks > blocks.size() * 8 + 64)
                fail("shared control-flow tails require excessive duplication");
            if(loop == null && !context.protections.stream().anyMatch(p -> p.entry == entry))
                nodes.add(new ExitLabelNode(current));
            Node statements = new StatementsNode(current.steps);
            nodes.add(
                    splitExceptionRegions
                            ? protectedStatements(current, statements, context, visited)
                            : statements);
            if(current.endKind == End.RETURN) break;
            if(current.endKind == End.JUMP) {
                nodes.add(transfer(current, current.yes));
                current = current.yes;
                continue;
            }
            if(current.endKind == End.SWITCH) {
                ProtectedNode structured = switchRegion(current, context, visited);
                nodes.add(structured.node);
                current = structured.next;
                continue;
            }
            Block join = join(current, context);
            if(stop != null
                    && stop.start > current.start
                    && (join == null || join.start >= stop.start)) join = stop;
            // Short-circuit boolean diamonds can share a small tail before the
            // final merge. Emit that tail in each mutually exclusive arm instead
            // of inventing a jump or evaluating the right operand eagerly.
            Set<Block> thenVisited = new HashSet<>(visited), elseVisited = new HashSet<>(visited);
            if(join == current.yes) {
                Node yes = edgeRegion(current, current.no, join, context, thenVisited);
                Node no = transfer(current, current.yes);
                nodes.add(new IfNode(current, true, yes, empty(no) ? null : no));
            }
            else if(join == current.no) {
                Node yes = edgeRegion(current, current.yes, join, context, thenVisited);
                Node no = transfer(current, current.no);
                nodes.add(new IfNode(current, false, yes, empty(no) ? null : no));
            }
            else {
                // javac usually jumps over the source-level then branch.
                Node yes = edgeRegion(current, current.no, join, context, thenVisited);
                Node no = edgeRegion(current, current.yes, join, context, elseVisited);
                nodes.add(new IfNode(current, true, yes, no));
            }
            visited.addAll(thenVisited);
            visited.addAll(elseVisited);
            current = join;
        }
        return new Sequence(nodes);
    }

    private Node edgeRegion(Block from, Block to, Block stop, Context context, Set<Block> visited) {
        return new Sequence(List.of(transfer(from, to), region(to, stop, context, visited)));
    }

    private Node namedExit(Block target, String hint) {
        exitLabels.computeIfAbsent(target, ignored -> CppNames.unique(hint, usedNames));
        return new NamedExitNode(target);
    }

    private Node controlExit(Block target, Context context, Set<Block> visited) {
        return controlExit(target, context, visited, true);
    }

    private Node controlExit(Block target, Context context, Set<Block> visited, boolean register) {
        for(Context scope = context; scope != null; scope = scope.parent) {
            Selection selection = scope.selection;
            if(selection != null && target == selection.exit)
                return selection == context.selection && context.loop == selection.enclosingLoop
                        ? new TextNode("break;")
                        : register ? namedExit(target, "switch_exit") : new TextNode("");
            Loop loop = scope.loop;
            if(loop == null) continue;
            if(target == loop.header && visited.contains(target))
                return loop == context.loop
                        ? new TextNode("continue;")
                        : register ? namedExit(target, "loop_continue") : new TextNode("");
            if(target == loop.exit)
                return loop == context.loop
                        && (context.selection == null
                        || context.selection.enclosingLoop != loop)
                        ? new TextNode("break;")
                        : register ? namedExit(target, "loop_exit") : new TextNode("");
            if(loop != context.loop
                    && context.loop.exit != null
                    && target.start >= context.loop.exit.start
                    && loop.members.contains(target)
                    && !context.loop.members.contains(target)
                    && target.successors().contains(loop.header))
                return register ? namedExit(target, "loop_continue") : new TextNode("");
        }
        if(context.selection != null
                && context.selection.labels.contains(target)
                && target != context.selection.entry)
            return register ? namedExit(target, "next_case") : new TextNode("");
        return null;
    }

    /**
     * Fragmented javac finally tables cannot always surround a whole C++ region. Protect just the
     * throwing statements in that case; the ordinary CFG still supplies loops and switches. Each
     * catch leaves through the same continuation as the Java handler, outside the protected
     * statements.
     */
    private Node protectedStatements(
            Block current, Node statements, Context context, Set<Block> visited) {
        if(current.steps.stream()
                .noneMatch(
                        s ->
                                s.kind == Kind.MONITOR_ENTER
                                        || s.kind != Kind.EDGE
                                        && s.kind != Kind.CONDITION
                                        && s.expression.effects() != 0)) return statements;
        var handlers = new ArrayList<Handler>();
        for(TryCatchBlockNode entry : exceptionTable) {
            if(current.start < positions.get(entry.start)
                    || current.start >= positions.get(entry.end)) continue;
            Block target = blockAt.get(positions.get(entry.handler));
            if(target == current) fail("a throwing handler protects itself");
            if(!handlers.isEmpty() && handlers.getLast().entry == target)
                handlers.getLast().types.add(entry.type);
            else
                handlers.add(
                        new Handler(target, new ArrayList<>(Collections.singleton(entry.type))));
            if(entry.type == null) break;
        }
        if(handlers.isEmpty()) return statements;
        // The branch is emitted after this try block. Values it consumes must
        // outlive the statement scope even when the JVM kept them on one stack.
        var escaping = new HashMap<Value, Integer>();
        if(current.condition != null) current.condition.expression.uses(escaping);
        for(var copies : current.transfers.values())
            for(Step copy : copies) copy.expression.uses(escaping);
        for(Step step : current.steps) {
            if(step.kind == Kind.LET && escaping.containsKey(step.value) && !step.value.shared) {
                step.value.shared = true;
                sharedValues.add(step.value);
            }
        }
        var catches = new ArrayList<CatchNode>();
        for(Handler handler : handlers) {
            var entries = new ArrayList<>(current.successors());
            entries.add(handler.entry);
            Block join = current.successors().isEmpty() ? null : commonSuccessor(entries);
            if(join == handler.entry) join = null;
            var handlerVisited = new HashSet<>(visited);
            handlerVisited.remove(handler.entry);
            Node body = region(handler.entry, join, context, handlerVisited);
            if(join != null) {
                Node exit = controlExit(join, context, visited);
                body =
                        new Sequence(
                                List.of(
                                        body,
                                        exit == null ? namedExit(join, "catch_completed") : exit));
            }
            catches.add(new CatchNode(handler, body));
        }
        return new TryNode(
                statements,
                catches,
                new Sequence(List.of()),
                "",
                CppNames.unique("caught", usedNames));
    }

    private static Node transfer(Block from, Block to) {
        return new TransferNode(from.transfers.getOrDefault(to, List.of()));
    }

    private static boolean empty(Node node) {
        return node instanceof Sequence sequence
                && sequence.nodes.stream().allMatch(ReadableMethodEmitter::empty)
                || node instanceof StatementsNode statements
                && statements.steps.stream()
                .allMatch(s -> s.kind == Kind.EDGE || s.kind == Kind.CONDITION)
                || node instanceof TransferNode transfer && transfer.copies.isEmpty();
    }

    private Block join(Block branch, Context context) {
        Loop active = context.loop;
        if(context.selection != null
                && (branch.yes == context.selection.exit || branch.no == context.selection.exit))
            return branch.yes == context.selection.exit ? branch.no : branch.yes;
        if(active != null && (branch.yes == active.exit || branch.no == active.exit))
            return branch.yes == active.exit ? branch.no : branch.yes;
        if(active != null && (branch.yes == active.header || branch.no == active.header))
            return branch.yes == active.header ? branch.no : branch.yes;
        Set<Block> entered = new HashSet<>();
        for(Context scope = context; scope != null; scope = scope.parent)
            if(scope.loop != null) entered.add(scope.loop.header);
        if(controlExit(branch.yes, context, entered, false) != null) return branch.no;
        if(controlExit(branch.no, context, entered, false) != null) return branch.yes;
        if(branch.yes.start > branch.start && endsBefore(branch.no, branch.yes, new HashMap<>()))
            return branch.yes;
        if(branch.no.start > branch.start && endsBefore(branch.yes, branch.no, new HashMap<>()))
            return branch.no;
        Set<Block> common = new HashSet<>(postdominators.get(branch.yes));
        common.retainAll(postdominators.get(branch.no));
        common.remove(branch);
        common.remove(null);
        return common.stream()
                .max(Comparator.comparingInt(b -> postdominators.get(b).size()))
                .orElse(null);
    }

    private Block commonSuccessor(Collection<Block> entries) {
        if(entries.isEmpty()) return null;
        Set<Block> common = new HashSet<>(postdominators.get(entries.iterator().next()));
        for(Block entry : entries) common.retainAll(postdominators.get(entry));
        common.remove(null);
        return common.stream()
                .max(Comparator.comparingInt(b -> postdominators.get(b).size()))
                .orElse(null);
    }

    private record ProtectedNode(Node node, Block next) {
    }

    private record CatchNode(Handler handler, Node body) {
    }

    private ProtectedNode protectedRegion(
            Protection protection, Context context, Set<Block> visited) {
        for(Handler handler : protection.handlers)
            if(protection.contains(handler.entry))
                fail("exception handler is inside its own protected interval");
        Set<Block> exits = new HashSet<>();
        for(Block current : blocks)
            if(protection.contains(current))
                for(Block next : current.successors())
                    if(!protection.contains(next)) exits.add(next);
        if(exits.size() > 1) fail("exception region has multiple normal exit regions");
        Block normalExit = exits.stream().findFirst().orElse(null);
        var entries = new ArrayList<Block>();
        if(normalExit != null) entries.add(normalExit);
        for(Handler handler : protection.handlers) entries.add(handler.entry);
        Block join = normalExit == null ? null : commonSuccessor(entries);
        if(join == null) join = normalExit;
        if(protection.contains(join)) fail("exception region re-enters its protected body");
        Node body = region(protection.entry, normalExit, context.inside(protection), visited);
        Node normalTail = region(normalExit, join, context, visited);
        var catches = new ArrayList<CatchNode>();
        // A shared handler can belong to several disjoint intervals (javac finally).
        // Printing its body in each catch is safe; each execution takes only one edge.
        for(Handler handler : protection.handlers) {
            var handlerVisited = new HashSet<>(visited);
            handlerVisited.remove(handler.entry);
            Node handlerBody = region(handler.entry, join, context, handlerVisited);
            catches.add(new CatchNode(handler, handlerBody));
        }
        return new ProtectedNode(
                new TryNode(
                        body,
                        catches,
                        normalTail,
                        CppNames.unique("try_completed", usedNames),
                        CppNames.unique("caught", usedNames)),
                join);
    }

    private ProtectedNode switchRegion(Block branch, Context context, Set<Block> visited) {
        Set<Block> labels = new HashSet<>(branch.successors());
        Block join = commonSuccessor(labels);
        int lastCase = labels.stream().mapToInt(b -> b.start).max().orElse(branch.start);
        for(Block candidate :
                blocks.stream().sorted(Comparator.comparingInt(b -> b.start)).toList()) {
            if(candidate.start <= lastCase
                    || controlExit(candidate, context, visited, false) != null) continue;
            if(labels.stream()
                    .allMatch(b -> endsAt(b, candidate, context, visited, new HashMap<>()))) {
                join = candidate;
                break;
            }
        }
        // A default label can itself be the continuation for an empty default arm.
        Block continuation = join;
        var ordered =
                labels.stream()
                        .filter(b -> b != continuation)
                        .sorted(Comparator.comparingInt(b -> b.start))
                        .toList();
        var cases = new ArrayList<CaseNode>();
        Set<Block> caseVisited = new HashSet<>(visited);
        for(int i = 0; i < ordered.size(); ++i) {
            Block label = ordered.get(i), next = i + 1 < ordered.size() ? ordered.get(i + 1) : null;
            Context inside = context.inside(new Selection(join, labels, label, context.loop));
            Set<Block> armVisited = new HashSet<>(caseVisited);
            Node body = region(label, next, inside, armVisited);
            if(next != null && !branch.transfers.getOrDefault(next, List.of()).isEmpty())
                body = new Sequence(List.of(body, namedExit(next, "next_case")));
            visited.addAll(armVisited);
            cases.add(new CaseNode(label, body));
        }
        if(labels.contains(join)) cases.add(new CaseNode(join, new TextNode("break;")));
        return new ProtectedNode(new SwitchNode(branch, cases), join);
    }

    private boolean endsAt(
            Block current,
            Block end,
            Context context,
            Set<Block> entered,
            Map<Block, Boolean> results) {
        if(current == end || controlExit(current, context, entered, false) != null) return true;
        if(current == null) return false;
        Boolean result = results.get(current);
        if(result != null) return result;
        // An unfinished visit is a cycle, which cannot guarantee reaching the end.
        results.put(current, false);
        for(Block next : current.successors())
            if(!endsAt(next, end, context, entered, results)) return false;
        results.put(current, true);
        return true;
    }

    private boolean endsBefore(Block current, Block end, Map<Block, Boolean> results) {
        if(current == end) return true;
        if(current == null || current.start >= end.start) return false;
        Boolean result = results.get(current);
        if(result != null) return result;
        results.put(current, false);
        for(Block next : current.successors()) if(!endsBefore(next, end, results)) return false;
        results.put(current, true);
        return true;
    }

    private interface Node {
        void write(Writer out, int indent);
    }

    private record Sequence(List<Node> nodes) implements Node {
        public void write(Writer out, int indent) {
            for(Node node : nodes) node.write(out, indent);
        }
    }

    private record TextNode(String text) implements Node {
        public void write(Writer out, int indent) {
            out.line(indent, text);
        }
    }

    private record ExitLabelNode(Block block) implements Node {
        public void write(Writer out, int indent) {
            out.label(block, indent);
        }
    }

    private record NamedExitNode(Block block) implements Node {
        public void write(Writer out, int indent) {
            out.jump(block, indent);
        }
    }

    private record StatementsNode(List<Step> steps) implements Node {
        public void write(Writer out, int indent) {
            out.activateExceptionalRoots(steps, indent);
            out.writeStoreRegions(steps, indent);
        }

        private static void writeChecked(List<Step> steps, Writer out, int indent) {
            for(Step step : steps) {
                if(step.kind == Kind.CONDITION
                        || step.kind == Kind.EDGE
                        || step.kind == Kind.MONITOR_ENTER) continue;
                out.location(indent, step.line);
                String expression = step.expression.code();
                switch(step.kind) {
                    case LET -> {
                        if(step.value.shared) out.line(indent, assignment(step.value, expression));
                        else if(step.value.rooted()) out.root(indent, step.value.name, expression);
                        else
                            out.line(
                                    indent,
                                    (type(step.value.type) + " ")
                                            + step.value.name
                                            + " = "
                                            + expression
                                            + ";");
                        out.snapshotArrayViews(step.value, indent);
                    }
                    case STORE -> out.line(
                            indent,
                            step.local.name
                                    + (step.local.rooted()
                                    ? ".set(" + expression + ");"
                                    : " = " + expression + ";"));
                    case RETURN -> out.returnValue(indent, expression);
                    case EFFECT -> out.line(indent, expression + ";");
                    default -> throw new AssertionError();
                }
            }
        }
    }

    private record MonitorNode(MonitorRegion monitor, Block entry, Node acquisition, Node body)
            implements Node {
        public void write(Writer out, int indent) {
            Step enter =
                    entry.steps.stream()
                            .filter(s -> s.kind == Kind.MONITOR_ENTER)
                            .findFirst()
                            .orElseThrow();
            out.line(indent, "{");
            new StatementsNode(entry.steps).write(out, indent + 1);
            out.location(indent + 1, enter.line);
            out.line(
                    indent + 1,
                    "::jnative::MonitorGuard "
                            + monitor.name
                            + (acquisition == null ? "(" + enter.expression.code() + ");" : ";"));
            out.enterScope();
            if(acquisition != null) acquisition.write(out, indent + 1);
            body.write(out, indent + 1);
            out.leaveScope();
            out.line(indent, "}");
            out.reset();
        }
    }

    private static String assignment(Value value, String expression) {
        return value.name
                + (value.rooted() ? ".set(" + expression + ");" : " = " + expression + ";");
    }

    private record TransferNode(List<Step> copies) implements Node {
        public void write(Writer out, int indent) {
            if(copies.isEmpty()) return;
            if(copies.size() == 1) {
                Step copy = copies.getFirst();
                out.line(indent, assignment(copy.value, copy.expression.code()));
                return;
            }
            // Edge copies are simultaneous. Snapshot all RHS values before a loop
            // merge can overwrite one of the values used by another assignment.
            int rootCheckpoint = out.nextRootSlot;
            out.line(indent, "{");
            var incoming = new ArrayList<String>();
            for(int i = 0; i < copies.size(); ++i) {
                Step copy = copies.get(i);
                incoming.add(out.name("incoming"));
                if(copy.value.rooted())
                    out.root(indent + 1, incoming.get(i), copy.expression.code());
                else
                    out.line(
                            indent + 1,
                            type(copy.value.type)
                                    + " "
                                    + incoming.get(i)
                                    + " = "
                                    + copy.expression.code()
                                    + ";");
            }
            for(int i = 0; i < copies.size(); ++i) {
                Step copy = copies.get(i);
                out.line(
                        indent + 1,
                        assignment(
                                copy.value,
                                incoming.get(i) + (copy.value.rooted() ? ".get()" : "")));
            }
            out.line(indent, "}");
            out.nextRootSlot = rootCheckpoint;
        }
    }

    private record CaseNode(Block entry, Node body) {
    }

    private record SwitchNode(Block block, List<CaseNode> cases) implements Node {
        public void write(Writer out, int indent) {
            out.location(indent, block.condition.line);
            out.line(indent, "switch (" + block.condition.expression.code() + ") {");
            for(CaseNode arm : cases) {
                for(var entry : block.cases.entrySet())
                    if(entry.getValue() == arm.entry)
                        out.line(indent + 1, "case " + integer(entry.getKey()) + ":");
                if(block.yes == arm.entry) out.line(indent + 1, "default:");
                out.line(indent + 1, "{");
                out.reset();
                transfer(block, arm.entry).write(out, indent + 2);
                arm.body.write(out, indent + 2);
                out.line(indent + 1, "}");
            }
            out.line(indent, "}");
            out.reset();
        }
    }

    private record TryNode(
            Node body, List<CatchNode> catches, Node normalTail, String completed, String caught)
            implements Node {
        public void write(Writer out, int indent) {
            boolean tail = !empty(normalTail);
            boolean scoped = tail && out.hasLabels();
            if(scoped) {
                out.line(indent++, "{");
                out.enterScope();
            }
            if(tail) out.line(indent, "bool " + completed + " = false;");
            out.line(indent, "try {");
            out.enterScope();
            out.reset();
            body.write(out, indent + 1);
            if(tail) out.line(indent + 1, completed + " = true;");
            out.leaveScope();
            out.line(indent, "} catch (const ::jnative::Thrown& " + caught + ") {");
            out.enterScope();
            boolean catchAll = false;
            boolean direct = catches.getFirst().handler.types.contains(null);
            for(int i = 0; i < catches.size(); ++i) {
                CatchNode arm = catches.get(i);
                catchAll = arm.handler.types.contains(null);
                if(!direct) {
                    String condition =
                            String.join(
                                    " || ",
                                    arm.handler.types.stream()
                                            .filter(Objects::nonNull)
                                            .map(
                                                    t ->
                                                            "::jnative::instance_of("
                                                                    + caught
                                                                    + ".object(), "
                                                                    + out.typeCheckArgument(t)
                                                                    + ")")
                                            .toList());
                    out.line(
                            indent + 1,
                            catchAll
                                    ? "} else {"
                                    : (i == 0 ? "if (" : "} else if (") + condition + ") {");
                }
                int depth = direct ? indent + 1 : indent + 2;
                out.reset();
                Step store = arm.handler.entry.caughtStore;
                if(store == null)
                    out.line(
                            depth,
                            assignment(arm.handler.entry.inputs.getFirst(), caught + ".object()"));
                else {
                    out.location(depth, store.line);
                    out.line(
                            depth,
                            store.local.name
                                    + (store.local.rooted()
                                    ? ".set(" + caught + ".object());"
                                    : " = " + caught + ".object();"));
                }
                arm.body.write(out, depth);
                if(catchAll) break;
            }
            if(!catchAll) {
                out.line(indent + 1, "} else {");
                out.line(indent + 2, "throw;");
            }
            if(!direct) out.line(indent + 1, "}");
            out.leaveScope();
            out.line(indent, "}");
            out.reset();
            if(tail) {
                out.line(indent, "if (" + completed + ") {");
                normalTail.write(out, indent + 1);
                out.line(indent, "}");
                out.reset();
            }
            if(scoped) {
                out.leaveScope();
                out.line(--indent, "}");
            }
        }
    }

    private record IfNode(Block block, boolean invert, Node yes, Node no) implements Node {
        public void write(Writer out, int indent) {
            out.location(indent, block.condition.line);
            Step thenValue = singleTransfer(yes), elseValue = singleTransfer(no);
            if(thenValue != null && elseValue != null && thenValue.value == elseValue.value) {
                String first = thenValue.expression.code(), second = elseValue.expression.code();
                String expression =
                        first.equals("1") && second.equals("0")
                                ? "(" + block.condition(invert) + ")"
                                : first.equals("0") && second.equals("1")
                                ? "(" + block.condition(!invert) + ")"
                                : "("
                                + block.condition(invert)
                                + " ? "
                                + first
                                + " : "
                                + second
                                + ")";
                out.line(indent, assignment(thenValue.value, expression));
                return;
            }
            out.line(indent, "if (" + block.condition(invert) + ") {");
            out.reset();
            // Branch-local FrameRoot destructors run before the sibling branch
            // or continuation. Hoisted locals and outer roots keep their slots.
            int rootCheckpoint = out.nextRootSlot;
            boolean coldCheckpoint = out.coldRootsActive;
            out.enterCollectingBranch(yes, indent + 1);
            yes.write(out, indent + 1);
            out.nextRootSlot = rootCheckpoint;
            out.coldRootsActive = coldCheckpoint;
            if(no != null) {
                out.line(indent, "} else {");
                out.reset();
                out.enterCollectingBranch(no, indent + 1);
                no.write(out, indent + 1);
                out.nextRootSlot = rootCheckpoint;
                out.coldRootsActive = coldCheckpoint;
            }
            out.line(indent, "}");
            out.reset();
        }
    }

    private static Step singleTransfer(Node node) {
        if(node instanceof TransferNode transfer && transfer.copies.size() == 1)
            return transfer.copies.getFirst();
        if(node instanceof Sequence sequence) {
            List<Node> nonempty = sequence.nodes.stream().filter(n -> !empty(n)).toList();
            if(nonempty.size() == 1) return singleTransfer(nonempty.getFirst());
        }
        return null;
    }

    private record LoopNode(Loop loop, Node body, boolean testAtHead) implements Node {
        public void write(Writer out, int indent) {
            Map<ArrayViewKey, String> previousViews = out.beginArrayViews(loop, indent);
            boolean scopedViews = previousViews != null;
            if(scopedViews) ++indent;
            Block header = loop.header;
            boolean simple =
                    testAtHead
                            && header.steps.stream().allMatch(s -> s.kind == Kind.CONDITION)
                            && header.transfers.isEmpty();
            boolean invert = testAtHead && header.yes == loop.exit;
            LoopGuard guard = simple ? out.rangeGuard(loop, indent, invert) : null;
            if(guard != null) {
                out.line(indent, "if (" + guard.condition + ") {");
                out.useUnchecked(guard.arrays, true);
                writeGuardedLoop(out, indent + 1, invert, guard);
                out.useUnchecked(guard.arrays, false);
                out.line(indent, "} else {");
                writeLoop(out, indent + 1, simple, invert, true);
                out.line(indent, "}");
            }
            else {
                writeLoop(out, indent, simple, invert, true);
            }
            if(scopedViews) {
                out.endArrayViews(previousViews);
                out.line(--indent, "}");
            }
            out.reset();
        }

        private void writeLoop(
                Writer out, int indent, boolean simple, boolean invert, boolean budgeted) {
            Block header = loop.header;
            String pollBudget = budgeted ? out.loopPollBudget(loop, indent) : null;
            if(simple) out.location(indent, header.condition.line);
            out.line(indent, "while (" + (simple ? header.condition(invert) : "true") + ") {");
            out.reset();
            out.line(
                    indent + 1,
                    pollBudget == null
                            ? "loop_safepoints.poll();"
                            : "if ((" + pollBudget + "++ & 63u) == 0) loop_safepoints.poll();");
            if(testAtHead && !simple) {
                new StatementsNode(header.steps).write(out, indent + 1);
                out.location(indent + 1, header.condition.line);
                out.line(indent + 1, "if (" + header.condition(!invert) + ") {");
                transfer(header, loop.exit).write(out, indent + 2);
                out.line(indent + 2, "break;");
                out.line(indent + 1, "}");
            }
            body.write(out, indent + 1);
            out.label(header, indent + 1);
            out.line(indent, "}");
            out.reset();
        }

        private void writeGuardedLoop(Writer out, int indent, boolean invert, LoopGuard guard) {
            // The range proof establishes a unit induction step, stable limit,
            // representable final counter and a bounded body on every path.
            // Rows loaded inside the body never survive the poll at a chunk boundary.
            String end = out.name("chunk_end");
            out.line(indent, "while (" + loop.header.condition(invert) + ") {");
            out.line(indent + 1, "loop_safepoints.poll();");
            out.line(indent + 1, "const std::int32_t " + end
                    + " = static_cast<std::int32_t>(std::min<std::int64_t>(static_cast<std::int64_t>("
                    + guard.counter.read() + ") + 64LL, " + guard.end + "));");
            out.line(indent + 1, "while (" + guard.counter.read() + " < " + end + ") {");
            out.reset();
            body.write(out, indent + 2);
            out.line(indent + 1, "}");
            out.line(indent, "}");
            out.reset();
        }
    }

    private record ArrayViewKey(
            Local local,
            String field,
            String hint,
            String element,
            boolean confined,
            Value snapshot) {
        String source() {
            return local != null
                    ? local.read()
                    : snapshot != null
                    ? snapshot.name + (snapshot.rooted() ? ".get()" : "")
                    : field;
        }

        String declaration() {
            return element.equals("::jnative::Object*") ? "const ::jnative::ReferenceArrayView "
                    : (confined ? "const ::jnative::ConfinedArrayView<" : "const ::jnative::PrimitiveArrayView<")
                    + element + "> ";
        }
    }

    private static ArrayViewKey arrayViewKey(Value value, String element) {
        Value canonical = value.canonical();
        if(canonical.expression != null && canonical.expression.invariantArray != null)
            return new ArrayViewKey(
                    null,
                    canonical.expression.invariantArray,
                    canonical.expression.invariantArrayName,
                    element,
                    canonical.expression.confinedArray,
                    null);
        if(!canonical.inline && !canonical.shared && canonical.expression != null)
            return new ArrayViewKey(
                    null,
                    null,
                    canonical.name,
                    element,
                    canonical.expression.confinedArray,
                    canonical);
        return canonical.inline
                && canonical.expression != null
                && canonical.expression.directLocal != null
                ? new ArrayViewKey(
                canonical.expression.directLocal,
                null,
                canonical.expression.directLocal.name,
                element,
                canonical.expression.confinedArray,
                null)
                : null;
    }

    private static void collectArrayViews(Expr expression, Set<ArrayViewKey> views) {
        if(expression.arrayElement != null) {
            ArrayViewKey key = arrayViewKey(expression.values.getFirst(), expression.arrayElement);
            if(key != null) views.add(key);
        }
        for(Value value : expression.values)
            if(value.inline && value.expression != null)
                collectArrayViews(value.expression, views);
    }

    private static void countArrayViews(Expr expression, Map<ArrayViewKey, Integer> views) {
        if(expression.arrayElement != null) {
            ArrayViewKey key = arrayViewKey(expression.values.getFirst(), expression.arrayElement);
            if(key != null) views.merge(key, 1, Integer::sum);
        }
        for(Value value : expression.values)
            if(value.inline && value.expression != null) countArrayViews(value.expression, views);
    }

    // Affine expressions describe one execution of a loop body, including forward branches.
    // Their deliberately small coefficients keep all generated guard arithmetic in int64 range.
    private record Affine(Map<Local, Long> terms, long constant) {
        static Affine local(Local local) {
            return new Affine(Map.of(local, 1L), 0);
        }

        static Affine constant(long value) {
            return new Affine(Map.of(), value);
        }

        Affine add(Affine other, long scale) {
            if(other == null) return null;
            try {
                Map<Local, Long> next = new LinkedHashMap<>(terms);
                for(var term : other.terms.entrySet()) {
                    long value =
                            Math.addExact(
                                    next.getOrDefault(term.getKey(), 0L),
                                    Math.multiplyExact(term.getValue(), scale));
                    if(value == 0) next.remove(term.getKey());
                    else next.put(term.getKey(), value);
                }
                long value = Math.addExact(constant, Math.multiplyExact(other.constant, scale));
                long weight = 0;
                for(long coefficient : next.values()) {
                    if(coefficient < -(1L << 20) || coefficient > (1L << 20)) return null;
                    weight = Math.addExact(weight, Math.abs(coefficient));
                }
                return value >= -(1L << 40) && value <= (1L << 40) && weight <= (1L << 20)
                        ? new Affine(next, value)
                        : null;
            } catch(ArithmeticException overflow) {
                return null;
            }
        }

        String code() {
            var pieces = new ArrayList<String>();
            for(var term : terms.entrySet()) {
                pieces.add(
                        "static_cast<std::int64_t>("
                                + term.getKey().read()
                                + ")"
                                + (term.getValue() == 1 ? "" : " * " + term.getValue() + "LL"));
            }
            if(constant != 0 || pieces.isEmpty()) pieces.add(constant + "LL");
            return "(" + String.join(" + ", pieces) + ")";
        }
    }

    private record AccessRange(ArrayViewKey array, Affine index, Long mask) {
    }

    private record LoopGuard(String condition, Set<ArrayViewKey> arrays, Local counter, String end) {
    }

    private record StoreRegion(int end, ArrayViewKey array, int minimum, int maximum,
                               int stores, boolean bounded) {
    }

    private static final class RangeState {
        final Map<Local, Affine> locals = new HashMap<>();
        final Map<Value, Affine> values = new IdentityHashMap<>();
        final Map<Local, Long> localMasks = new HashMap<>();
        final Map<Value, Long> valueMasks = new IdentityHashMap<>();
        final List<AccessRange> ranges = new ArrayList<>();

        RangeState copy() {
            RangeState result = new RangeState();
            result.locals.putAll(locals);
            result.values.putAll(values);
            result.localMasks.putAll(localMasks);
            result.valueMasks.putAll(valueMasks);
            return result;
        }

        void merge(RangeState other) {
            Set<Local> assigned = new HashSet<>(locals.keySet());
            assigned.addAll(other.locals.keySet());
            for(Local local : assigned)
                if(!Objects.equals(local(local), other.local(local))) locals.put(local, null);
            Set<Value> defined = new HashSet<>(values.keySet());
            defined.addAll(other.values.keySet());
            for(Value value : defined)
                if(!Objects.equals(values.get(value), other.values.get(value)))
                    values.put(value, null);
            localMasks.keySet().removeIf(local -> !Objects.equals(localMasks.get(local), other.localMasks.get(local)));
            valueMasks.keySet().removeIf(value -> !Objects.equals(valueMasks.get(value), other.valueMasks.get(value)));
        }

        Affine local(Local local) {
            return local.type.equals(Type.INT_TYPE)
                    ? locals.getOrDefault(local, Affine.local(local))
                    : null;
        }

        Affine value(Value value) {
            value = value.canonical();
            return value.inline ? expression(value.expression) : values.get(value);
        }

        Affine expression(Expr expression) {
            if(expression == null) return null;
            if(expression.integerConstant != null)
                return Affine.constant(expression.integerConstant);
            if(expression.directLocal != null) return local(expression.directLocal);
            if(expression.incrementLocal != null) {
                Affine previous = local(expression.incrementLocal);
                return previous == null
                        ? null
                        : previous.add(Affine.constant(expression.incrementAmount), 1);
            }
            if(expression.passThrough) return value(expression.values.getFirst());
            if(expression.integerOperation == IADD
                    || expression.integerOperation == ISUB
                    || expression.integerOperation == IMUL) {
                Affine left = value(expression.values.get(0)),
                        right = value(expression.values.get(1));
                if(left == null || right == null) return null;
                if(expression.integerOperation != IMUL)
                    return left.add(right, expression.integerOperation == IADD ? 1 : -1);
                if(left.terms.isEmpty()) return Affine.constant(0).add(right, left.constant);
                if(right.terms.isEmpty()) return Affine.constant(0).add(left, right.constant);
            }
            return null;
        }

        Long mask(Value value) {
            value = value.canonical();
            return value.inline ? mask(value.expression) : valueMasks.get(value);
        }

        Long mask(Expr expression) {
            if(expression == null) return null;
            if(expression.directLocal != null) return localMasks.get(expression.directLocal);
            if(expression.passThrough) return mask(expression.values.getFirst());
            if(expression.integerOperation == IAND) {
                for(Value operand : expression.values) {
                    Affine constant = value(operand);
                    if(constant != null && constant.terms.isEmpty()
                            && constant.constant >= 0 && constant.constant <= Integer.MAX_VALUE)
                        return constant.constant;
                }
            }
            return null;
        }

        void accesses(Expr expression) {
            if(expression.arrayElement != null) {
                ArrayViewKey key =
                        arrayViewKey(expression.values.getFirst(), expression.arrayElement);
                if(key != null) ranges.add(new AccessRange(key, value(expression.values.get(1)),
                        mask(expression.values.get(1))));
            }
            for(Value value : expression.values)
                if(value.inline && value.expression != null) accesses(value.expression);
        }

        void step(Step step) {
            accesses(step.expression);
            Affine result = expression(step.expression);
            if(step.kind == Kind.LET) {
                values.put(step.value.canonical(), result);
                valueMasks.put(step.value.canonical(), mask(step.expression));
            }
            if(step.kind == Kind.STORE) {
                locals.put(step.local, result);
                localMasks.put(step.local, mask(step.expression));
            }
        }

        Long stride(Affine index) {
            if(index == null) return null;
            long stride = 0;
            for(var term : index.terms.entrySet()) {
                Affine end = local(term.getKey());
                Affine delta = end == null ? null : end.add(Affine.local(term.getKey()), -1);
                if(delta == null
                        || !delta.terms.isEmpty()
                        || Math.abs(delta.constant) > (1L << 20)) return null;
                stride += term.getValue() * delta.constant;
            }
            return Math.abs(stride) <= (1L << 20) ? stride : null;
        }
    }

    /**
     * Joins forward paths once, keeping only affine values agreed on by every predecessor.
     */
    private static RangeState loopRanges(Loop loop) {
        Block entry = loop.header.yes == loop.exit ? loop.header.no : loop.header.yes;
        Map<Block, Integer> pending = new LinkedHashMap<>();
        for(Block member : loop.members) {
            if(member == loop.header) continue;
            if((member.endKind != End.JUMP && member.endKind != End.IF)
                    || !member.transfers.isEmpty()) return null;
            pending.put(member, 0);
        }
        for(Block member : pending.keySet()) {
            for(Block next : new LinkedHashSet<>(member.successors())) {
                if(next == loop.header) continue;
                if(!pending.containsKey(next)) return null;
                pending.put(next, pending.get(next) + 1);
            }
        }
        if(!Integer.valueOf(0).equals(pending.get(entry))) return null;
        Map<Block, RangeState> incoming = new HashMap<>();
        incoming.put(entry, new RangeState());
        ArrayDeque<Block> ready = new ArrayDeque<>();
        ready.add(entry);
        List<AccessRange> accesses = new ArrayList<>();
        RangeState backedge = null;
        int processed = 0;
        while(!ready.isEmpty()) {
            Block member = ready.removeFirst();
            RangeState state = incoming.get(member);
            for(Step step : member.steps) state.step(step);
            if(member.condition != null) state.accesses(member.condition.expression);
            accesses.addAll(state.ranges);
            ++processed;
            for(Block next : new LinkedHashSet<>(member.successors())) {
                if(next == loop.header) {
                    if(backedge == null) backedge = state.copy();
                    else backedge.merge(state);
                }
                else {
                    if(!incoming.containsKey(next)) incoming.put(next, state.copy());
                    else incoming.get(next).merge(state);
                    int remaining = pending.get(next) - 1;
                    pending.put(next, remaining);
                    if(remaining == 0) ready.add(next);
                }
            }
        }
        // Reject inner cycles, early exits and paths without a proven unit induction step.
        if(processed != pending.size() || backedge == null) return null;
        backedge.ranges.addAll(accesses);
        return backedge;
    }

    private final class Writer {
        String typeCheckArgument(String type) {
            return backend.typeCheckArgument(type);
        }

        final StringBuilder out;
        final Set<Block> labels = new HashSet<>();
        final List<Object> scopes = new ArrayList<>();
        final Map<Block, Set<Object>> destinations = new HashMap<>();
        final Map<Block, List<Set<Object>>> jumps = new HashMap<>();
        int line = -1;
        int nextRootSlot;
        boolean coldRootsActive;
        final List<String> rootNames = new ArrayList<>();
        final Map<Value, List<ArrayViewKey>> snapshotViews = new HashMap<>();

        Writer(StringBuilder out) {
            this.out = out;
        }

        void enterCollectingBranch(Node node, int indent) {
            if(!scopedRejoiningRoots || !collectingBranches.contains(node)) return;
            activateRoots(indent);
            // Even a scalar-only arm must cooperate before a recursive call. Its
            // callee may use this same scoped entry policy instead of an entry poll.
            line(indent, "::jnative::safepoint();");
        }

        void activateExceptionalRoots(List<Step> steps, int indent) {
            if(deferredRoots && steps.stream().anyMatch(exceptionalSteps::contains))
                activateRoots(indent);
        }

        void activateRoots(int indent) {
            if(scopedColdRoots) {
                if(coldRootsActive) return;
                if(nextRootSlot > 0) {
                    String addresses =
                            String.join(
                                    ", ",
                                    rootNames.subList(0, nextRootSlot).stream()
                                            .map(name -> name + ".address()")
                                            .toList());
                    line(
                            indent,
                            "::jnative::RootAddressFrame<"
                                    + nextRootSlot
                                    + "> "
                                    + name("branch_roots")
                                    + "({"
                                    + addresses
                                    + "});");
                }
                coldRootsActive = true;
                return;
            }
            line(indent, "gc_roots.activate();");
        }

        void useUnchecked(Set<ArrayViewKey> arrays, boolean enabled) {
            if(enabled) uncheckedArrayViews.addAll(arrays);
            else uncheckedArrayViews.removeAll(arrays);
        }

        void writeStoreRegions(List<Step> steps, int indent) {
            // Keep handler/root publication, monitor scopes and named exits under
            // their existing emission rules. Regions never cross a basic block.
            if(hasLabels() || !method.bytecode().tryCatchBlocks.isEmpty()
                    || !monitorEntries.isEmpty()) {
                StatementsNode.writeChecked(steps, this, indent);
                return;
            }
            for(int start = 0; start < steps.size();) {
                StoreRegion region = storeRegion(steps, start);
                if(region == null) {
                    StatementsNode.writeChecked(steps.subList(start, start + 1), this, indent);
                    ++start;
                    continue;
                }
                List<Step> body = steps.subList(start, region.end);
                if(!region.bounded || region.stores < 2
                        || uncheckedArrayViews.contains(region.array)) {
                    StatementsNode.writeChecked(body, this, indent);
                } else {
                    // The array is an already evaluated local or immutable named
                    // value, not a repeated field read. Construct at the first
                    // store without throwing or moving any Java computation.
                    String view = name(region.array.hint + "_store_elements");
                    Map<ArrayViewKey, String> previous = new LinkedHashMap<>(arrayViews);
                    line(indent, "{");
                    enterScope();
                    line(indent + 1,
                            (region.array.confined
                                    ? "const ::jnative::ConfinedArrayView<"
                                    : "const ::jnative::PrimitiveArrayView<")
                                    + region.array.element + "> " + view
                                    + "(" + region.array.source() + ");");
                    line(indent + 1, "if (" + view + ".covers("
                            + region.minimum + ", " + region.maximum + ")) {");
                    reset();
                    arrayViews.put(region.array, view);
                    useUnchecked(Set.of(region.array), true);
                    StatementsNode.writeChecked(body, this, indent + 2);
                    useUnchecked(Set.of(region.array), false);
                    endArrayViews(previous);
                    line(indent + 1, "} else {");
                    reset();
                    // Preserve the original checked sequence and partial writes:
                    // a short/null array still fails at its first invalid access.
                    StatementsNode.writeChecked(body, this, indent + 2);
                    line(indent + 1, "}");
                    leaveScope();
                    line(indent, "}");
                    reset();
                }
                start = region.end;
            }
        }

        private StoreRegion storeRegion(List<Step> steps, int start) {
            ArrayViewKey array = constantStoreArray(steps.get(start));
            if(array == null) return null;
            int minimum = Integer.MAX_VALUE, maximum = Integer.MIN_VALUE;
            int stores = 0, end = start, lastStore = start;
            for(; end < steps.size(); ++end) {
                Step step = steps.get(end);
                ArrayViewKey stored = constantStoreArray(step);
                if(stored != null && stored.equals(array)) {
                    int index = step.expression.values.get(1).canonical().expression.integerConstant;
                    minimum = Math.min(minimum, index);
                    maximum = Math.max(maximum, index);
                    ++stores;
                    lastStore = end;
                } else if(step.kind != Kind.STORE || reference(step.local.type)
                        || step.expression.effects() != 0) {
                    break;
                }
            }
            // Duplicate at most 32 lowered steps / 16 stores. Scan an oversized
            // run to its boundary and reject it as a whole instead of optimizing
            // a smaller tail. LET declarations are boundaries: their C++ scope
            // or cached snapshot may be needed after the conditional region.
            boolean bounded = end - start <= 32 && stores <= 16;
            return new StoreRegion(lastStore + 1, array, minimum, maximum, stores, bounded);
        }

        private ArrayViewKey constantStoreArray(Step step) {
            Expr expression = step.expression;
            if(step.kind != Kind.EFFECT || !expression.arrayStore
                    || expression.arrayElement == null || expression.values.size() != 3)
                return null;
            Value index = expression.values.get(1).canonical();
            Value stored = expression.values.get(2).canonical();
            if(!index.inline || index.expression.integerConstant == null
                    || reference(stored.type)
                    || stored.inline && stored.expression.effects() != 0)
                return null;
            ArrayViewKey array = arrayViewKey(expression.values.getFirst(), expression.arrayElement);
            // Keys carry Local/Value identity, never just their printed names.
            return array != null && (array.local != null || array.snapshot != null)
                    ? array : null;
        }

        LoopGuard rangeGuard(Loop loop, int indent, boolean invert) {
            if(!boundedLoopBody(loop) || arrayViews.isEmpty()) return null;
            Expr condition = loop.header.condition.expression;
            String comparison = condition.comparisonOperator;
            if(comparison == null) return null;
            if(invert)
                comparison =
                        switch(comparison) {
                            case ">=" -> "<";
                            case ">" -> "<=";
                            default -> "";
                        };
            if(!comparison.equals("<") && !comparison.equals("<=")) return null;
            RangeState state = new RangeState();
            Affine counter = state.value(condition.values.get(0));
            Affine limit = state.value(condition.values.get(1));
            if(counter == null
                    || counter.constant != 0
                    || counter.terms.size() != 1
                    || counter.terms.values().iterator().next() != 1L
                    || limit == null) return null;
            // The loop limit must be an int literal or one unchanged int local.
            if(!limit.terms.isEmpty()
                    && (limit.constant != 0
                    || limit.terms.size() != 1
                    || limit.terms.values().iterator().next() != 1L)) return null;
            state = loopRanges(loop);
            if(state == null || !Long.valueOf(1).equals(state.stride(counter))) return null;
            for(Local local : limit.terms.keySet())
                if(state.locals.containsKey(local)) return null;
            Map<ArrayViewKey, List<AccessRange>> ranges = new LinkedHashMap<>();
            Set<ArrayViewKey> rejected = new HashSet<>();
            for(AccessRange range : state.ranges) {
                if(!arrayViews.containsKey(range.array)
                        || uncheckedArrayViews.contains(range.array)) continue;
                if(range.mask == null && state.stride(range.index) == null) rejected.add(range.array);
                else ranges.computeIfAbsent(range.array, ignored -> new ArrayList<>()).add(range);
            }
            ranges.keySet().removeAll(rejected);
            if(ranges.isEmpty()) return null;
            String count = name("range_iterations");
            line(
                    indent,
                    "const std::int64_t "
                            + count
                            + " = "
                            + limit.code()
                            + " - "
                            + counter.code()
                            + (comparison.equals("<=") ? " + 1LL" : "")
                            + ";");
            var guards = new ArrayList<String>();
            guards.add(count + " > 0 && " + count + " <= INT32_MAX");
            guards.add(counter.code() + " + " + count + " <= INT32_MAX");
            for(var entry : ranges.entrySet()) {
                // Adjacent channels of the same strided access share one guard.
                Map<Map<Local, Long>, long[]> spans = new LinkedHashMap<>();
                long mask = -1;
                for(AccessRange range : entry.getValue()) {
                    if(range.mask != null) {
                        mask = Math.max(mask, range.mask);
                        continue;
                    }
                    long[] span =
                            spans.computeIfAbsent(
                                    range.index.terms,
                                    ignored ->
                                            new long[]{
                                                    range.index.constant, range.index.constant
                                            });
                    span[0] = Math.min(span[0], range.index.constant);
                    span[1] = Math.max(span[1], range.index.constant);
                }
                if(mask >= 0) guards.add(arrayViews.get(entry.getKey()) + ".covers(0LL, " + mask + "LL)");
                for(var span : spans.entrySet()) {
                    Affine low = new Affine(span.getKey(), span.getValue()[0]);
                    Affine high = new Affine(span.getKey(), span.getValue()[1]);
                    long stride = state.stride(low);
                    String first = (stride < 0 ? high : low).code();
                    String last =
                            "("
                                    + (stride < 0 ? low : high).code()
                                    + " + ("
                                    + count
                                    + " - 1LL) * "
                                    + stride
                                    + "LL)";
                    guards.add(
                            arrayViews.get(entry.getKey())
                                    + ".covers("
                                    + first
                                    + ", "
                                    + last
                                    + ")");
                }
            }
            String valid = name("valid_array_ranges");
            line(indent, "const bool " + valid + " =");
            for(int i = 0; i < guards.size(); i++)
                line(indent + 1, guards.get(i) + (i + 1 == guards.size() ? ";" : " &&"));
            return new LoopGuard(valid, ranges.keySet(), counter.terms.keySet().iterator().next(),
                    limit.code() + (comparison.equals("<=") ? " + 1LL" : ""));
        }

        void beginParameterArrayViews(int indent) {
            // Parameters dominate every use. Cache only immutable array metadata;
            // a live parameter root retains storage across calls and polls. A proven
            // unrooted prefix finishes its accesses before handing control to a callee.
            // Keep reassigned slots and exceptional control flow conservative.
            if(hasLabels() || !method.bytecode().tryCatchBlocks.isEmpty()) return;
            Set<Local> written = new HashSet<>();
            Map<ArrayViewKey, Integer> uses = new LinkedHashMap<>();
            for(Block member : blocks) {
                for(Step step : member.steps) {
                    if(step.kind == Kind.STORE) written.add(step.local);
                    countArrayViews(step.expression, uses);
                }
            }
            for(var entry : uses.entrySet()) {
                ArrayViewKey key = entry.getKey();
                if(entry.getValue() >= 2 && key.snapshot != null) {
                    snapshotViews
                            .computeIfAbsent(key.snapshot, ignored -> new ArrayList<>())
                            .add(key);
                    continue;
                }
                if(entry.getValue() < 2
                        || key.local == null
                        || !key.local.parameter
                        || written.contains(key.local)
                        || arrayViews.containsKey(key)) continue;
                String view = name(key.hint + "_elements");
                line(
                        indent,
                        key.declaration() + view
                                + "("
                                + key.source()
                                + ");");
                arrayViews.put(key, view);
            }
        }

        void snapshotArrayViews(Value value, int indent) {
            // Capture metadata after this exact bytecode value is evaluated. In
            // particular, never hoist an instance-field read to method entry:
            // construction, publication or a call may change its reference.
            for(ArrayViewKey key : snapshotViews.getOrDefault(value.canonical(), List.of())) {
                String view =
                        arrayViews.computeIfAbsent(key, ignored -> name(key.hint + "_elements"));
                line(
                        indent,
                        key.declaration() + view
                                + "("
                                + key.source()
                                + ");");
            }
        }

        Map<ArrayViewKey, String> beginArrayViews(Loop loop, int indent) {
            // Handler control flow and named exits need a separate dominance analysis.
            if(hasLabels() || !method.bytecode().tryCatchBlocks.isEmpty()) return null;
            Set<Local> written = new HashSet<>();
            Set<ArrayViewKey> candidates = new LinkedHashSet<>();
            for(Block member : blocks) {
                if(!loop.members.contains(member)) continue;
                for(Step step : member.steps) {
                    if(step.kind == Kind.STORE) written.add(step.local);
                    collectArrayViews(step.expression, candidates);
                }
            }
            candidates.removeIf(
                    key ->
                            key.snapshot != null
                                    || written.contains(key.local)
                                    || arrayViews.containsKey(key));
            if(candidates.isEmpty()) return null;
            Map<ArrayViewKey, String> previous = new LinkedHashMap<>(arrayViews);
            line(indent, "{");
            for(ArrayViewKey key : candidates) {
                String view = name(key.hint + "_elements");
                line(
                        indent + 1,
                        key.declaration() + view
                                + "("
                                + key.source()
                                + ");");
                arrayViews.put(key, view);
            }
            return previous;
        }

        void endArrayViews(Map<ArrayViewKey, String> previous) {
            arrayViews.clear();
            arrayViews.putAll(previous);
        }

        void returnValue(int indent, String expression) {
            if(synchronizedReferenceReturn()) {
                // This root outlives the method's monitor guard and its collection safepoint.
                line(indent, "synchronized_return.set(" + expression + ");");
                line(indent, "return synchronized_return.get();");
            }
            else {
                line(indent, "return " + expression + ";");
            }
        }

        String name(String hint) {
            return CppNames.unique(hint, usedNames);
        }

        boolean hasLabels() {
            return !exitLabels.isEmpty();
        }

        void enterScope() {
            scopes.add(new Object());
        }

        void leaveScope() {
            scopes.removeLast();
        }

        void jump(Block block, int indent) {
            jumps.computeIfAbsent(block, ignored -> new ArrayList<>()).add(new HashSet<>(scopes));
            line(indent, "goto " + exitLabels.get(block) + ";");
        }

        void verifyExits() {
            for(var entry : jumps.entrySet())
                for(Set<Object> source : entry.getValue())
                    if(!source.containsAll(destinations.get(entry.getKey())))
                        fail("a named exit would enter an exception or monitor scope");
        }

        void label(Block block, int indent) {
            String name = exitLabels.get(block);
            if(name == null) return;
            if(!labels.add(block))
                fail(
                        "a named exit would enter a duplicated control-flow tail at instruction "
                                + block.start);
            destinations.put(block, new HashSet<>(scopes));
            line(indent, name + ":;");
            reset();
        }

        void line(int indent, String text) {
            out.append("    ".repeat(indent)).append(text).append('\n');
        }

        void root(int indent, String name, String initial) {
            int slot = nextRootSlot++;
            rootSlots = Math.max(rootSlots, nextRootSlot);
            if(scopedColdRoots) {
                if(rootNames.size() == slot) rootNames.add(name);
                else rootNames.set(slot, name);
                line(
                        indent,
                        "::jnative::"
                                + (coldRootsActive ? "LocalRoot" : "RootValue")
                                + "<> "
                                + name
                                + "("
                                + initial
                                + ");");
                return;
            }
            line(
                    indent,
                    "::jnative::FrameRoot<> "
                            + name
                            + "(gc_roots.slot("
                            + slot
                            + "), "
                            + initial
                            + ");");
        }

        String loopPollBudget(Loop loop, int indent) {
            if(!boundedLoopBody(loop)) return null;
            String budget = name("poll_budget");
            line(indent, "// At most 64 bounded iterations between collection checks.");
            line(indent, "std::uint32_t " + budget + " = 0;");
            return budget;
        }

        void location(int indent, int next) {
            if(request.javaSourceLocations() && next >= 0 && line != next) {
                line(indent, "java_frame.line = " + next + ";");
                line = next;
            }
        }

        void reset() {
            line = -1;
        }
    }
}
