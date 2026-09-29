package com.github.xpenatan.jnative.compiler;

import static com.github.xpenatan.jnative.compiler.Program.*;

import com.github.xpenatan.jnative.CompilerException;
import com.github.xpenatan.jnative.NativeBuildRequest;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

/**
 * Resolves reachable methods, validates operand types and builds a typed CFG.
 */
public final class BytecodeCompiler {
    private ClassPath classPath;
    private final Map<String, ClassNode> classes = new LinkedHashMap<>();
    private final Map<MethodId, Method> methods = new LinkedHashMap<>();
    private final Map<MethodId, List<MethodId>> pending = new LinkedHashMap<>();
    private final Set<String> instantiated = new LinkedHashSet<>();
    private final Set<MethodId> virtualCalls = new LinkedHashSet<>();
    private final Set<String> exportScanned = new HashSet<>();
    private final Set<String> loading = new HashSet<>();
    private final Set<String> loaded = new HashSet<>();
    private final Map<MethodId, NativeBinding> nativeBindings = new HashMap<>();
    private final Map<String, MethodId> nativeSymbols = new HashMap<>();
    private final Map<MethodId, String> unavailable = new LinkedHashMap<>();
    private final Map<String, String> unavailableFields = new LinkedHashMap<>();
    private final Set<MethodId> linkedCalls = new LinkedHashSet<>();
    private final Set<String> linkedTypes = new LinkedHashSet<>();

    public Program compile(NativeBuildRequest request) {
        classPath = new ClassPath(request.classpath());
        MethodId entry =
                new MethodId(
                        request.mainClass().replace('.', '/'), "main", "([Ljava/lang/String;)V");
        ClassNode entryClass = load(entry.owner());
        MethodNode main = find(entryClass, entry.name(), entry.descriptor());
        if(main == null
                || (main.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC))
                != (Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC))
            throw new CompilerException(
                    "JN1002 Entry point must be public static void main(String[]): " + entry);
        enqueue(entry, List.of());
        for(String name : request.exportClasses()) load(name.replace('.', '/'));
        ReflectionPlan reflection = ReflectionPlan.create(request.reflection(), this::load);
        for(String name : reflection.classes())
            if(!RuntimeLibrary.platform(name)) {
                ClassNode node = load(name);
                enqueueInitializer(node, List.of());
                for(FieldNode field : node.fields)
                    if((field.access & Opcodes.ACC_PUBLIC) != 0)
                        retainType(Type.getType(field.desc));
                for(MethodNode member : node.methods)
                    if((member.access & Opcodes.ACC_PUBLIC) != 0) {
                        for(Type argument : Type.getArgumentTypes(member.desc))
                            retainType(argument);
                        retainType(Type.getReturnType(member.desc));
                    }
            }
        for(MethodId member : reflection.methods()) {
            enqueue(member, List.of());
            if(RuntimeLibrary.platform(member.owner())) virtualCalls.add(member);
            else {
                MethodNode node = find(load(member.owner()), member.name(), member.descriptor());
                if(member.name().equals("<init>")) {
                    if((load(member.owner()).access
                            & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE))
                            == 0) instantiated.add(member.owner());
                }
                else if((node.access & Opcodes.ACC_STATIC) == 0) virtualCalls.add(member);
            }
        }
        if(!request.reflection().isEmpty())
            for(String primitive : List.of("Z", "B", "S", "C", "I", "J", "F", "D")) {
                String wrapper = RuntimeLibrary.wrapper(primitive);
                enqueue(
                        new MethodId(wrapper, "valueOf", "(" + primitive + ")L" + wrapper + ";"),
                        List.of());
            }
        while(!pending.isEmpty()) {
            var next = pending.entrySet().iterator().next();
            MethodId id = next.getKey();
            List<MethodId> path = next.getValue();
            pending.remove(id);
            if(methods.containsKey(id)) continue;
            analyze(id, path);
            var newCalls = new LinkedHashSet<>(virtualCalls);
            newCalls.removeAll(linkedCalls);
            var newTypes = new LinkedHashSet<>(instantiated);
            newTypes.removeAll(linkedTypes);
            for(MethodId call : newCalls) {
                enqueue(resolve(call.owner(), call.name(), call.descriptor()), path);
                for(MethodId bridge : PlatformBindings.interfaceImplementations(call)) enqueue(bridge, path);
            }
            for(MethodId call : newCalls)
                for(String implementation : List.copyOf(instantiated))
                    if(subtype(implementation, call.owner()))
                        enqueue(resolve(implementation, call.name(), call.descriptor()), path);
            for(MethodId call : linkedCalls)
                for(String implementation : newTypes)
                    if(subtype(implementation, call.owner()))
                        enqueue(resolve(implementation, call.name(), call.descriptor()), path);
            linkedCalls.addAll(newCalls);
            linkedTypes.addAll(newTypes);
        }
        if(!unavailable.isEmpty() || !unavailableFields.isEmpty())
            throw new CompilerException(
                    String.join("\n", unavailable.values())
                            + (unavailableFields.isEmpty()
                            ? ""
                            : "\n" + String.join("\n", unavailableFields.values())));
        return new Program(entry, classes, methods, instantiated, reflection, classPath.origins());
    }

    private void retainType(Type type) {
        if(type.getSort() == Type.ARRAY) retainType(type.getElementType());
        else if(type.getSort() == Type.OBJECT && !RuntimeLibrary.platform(type.getInternalName()))
            load(type.getInternalName());
    }

    private ClassNode load(String name) {
        if(loaded.contains(name)) return classes.get(name);
        if(!loading.add(name))
            throw new CompilerException("JN1004 Cyclic class hierarchy: " + name);
        ClassNode node = classes.computeIfAbsent(name, classPath::read);
        RecordLowering.lower(node);
        if(node.superName != null && !RuntimeLibrary.platform(node.superName))
            load(node.superName);
        for(String face : node.interfaces) if(!RuntimeLibrary.platform(face)) load(face);
        loading.remove(name);
        loaded.add(name);
        if(exportScanned.add(name)) {
            if((node.access & Opcodes.ACC_ENUM) != 0
                    && find(node, "values", "()[L" + node.name + ";") != null)
                enqueue(new MethodId(node.name, "values", "()[L" + node.name + ";"), List.of());
            for(MethodNode method : node.methods)
                if(Annotations.value(method.invisibleAnnotations, "NativeExport") != null)
                    enqueue(new MethodId(name, method.name, method.desc), List.of());
        }
        return node;
    }

    private static MethodNode find(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(m -> m.name.equals(name) && m.desc.equals(descriptor))
                .findFirst()
                .orElse(null);
    }

    private MethodId resolve(String owner, String name, String descriptor) {
        return MethodResolver.resolve(owner, name, descriptor, this::load);
    }

    private boolean subtype(String type, String base) {
        if(type.equals(base) || base.equals("java/lang/Object")) return true;
        if(RuntimeLibrary.platform(type)) {
            String parent = RuntimeLibrary.parent(type);
            return parent != null && subtype(parent, base);
        }
        ClassNode node = load(type);
        if(node.superName != null && subtype(node.superName, base)) return true;
        return node.interfaces.stream().anyMatch(i -> subtype(i, base));
    }

    private void enqueue(MethodId id, List<MethodId> path) {
        if(unavailable.containsKey(id)) return;
        NativeBinding binding = null;
        if(!RuntimeLibrary.platform(id.owner())) {
            ClassNode owner = load(id.owner());
            MethodNode node = find(owner, id.name(), id.descriptor());
            if(node != null) {
                if(!nativeBindings.containsKey(id)) {
                    nativeBindings.put(id, NativeBinding.read(owner, node));
                    PlatformBindings.read(owner, node);
                    PlatformBindings.readStaticFields(owner, node);
                }
                binding = nativeBindings.get(id);
            }
        }
        if(binding == null && RuntimeLibrary.intrinsic(id.owner(), id.name(), id.descriptor())) return;
        if(RuntimeLibrary.platform(id.owner())) {
            if(!RuntimeLibrary.method(id.owner(), id.name(), id.descriptor()))
                unavailable.putIfAbsent(
                        id,
                        "JN1003 Unsupported platform method " + id + "\nReachable through " + path);
            else {
                PlatformBindings.Target target = PlatformBindings.find(id);
                if(target == null)
                    throw new CompilerException("JN2001 Platform method requires an annotated NativeImport target: " + id);
                enqueue(target.helper(), path);
            }
            return;
        }
        if(!methods.containsKey(id)) {
            var nextPath = new ArrayList<>(path);
            nextPath.add(id);
            pending.putIfAbsent(id, List.copyOf(nextPath));
        }
        enqueueInitializer(load(id.owner()), path);
    }

    private void enqueueInitializer(ClassNode owner, List<MethodId> path) {
        MethodNode initializer = find(owner, "<clinit>", "()V");
        if(initializer != null) {
            MethodId clinit = new MethodId(owner.name, "<clinit>", "()V");
            if(!methods.containsKey(clinit)) {
                var next = new ArrayList<>(path);
                next.add(clinit);
                pending.putIfAbsent(clinit, List.copyOf(next));
            }
        }
        if(owner.superName != null && !RuntimeLibrary.platform(owner.superName))
            enqueueInitializer(load(owner.superName), path);
        for(ClassNode face : InitializationOrder.defaultInterfaces(owner, this::load))
            enqueueInitializer(face, path);
    }

    private void analyze(MethodId id, List<MethodId> path) {
        ClassNode owner = load(id.owner());
        MethodNode node = find(owner, id.name(), id.descriptor());
        if(node == null) {
            unavailable.putIfAbsent(
                    id,
                    (ClassLibrary.contains(id.owner())
                            ? "JN1003 Unsupported headless-v1 library method: "
                            : "JN1002 Method not found: ")
                            + id
                            + "\nReachable through "
                            + path);
            return;
        }
        NativeBinding binding = nativeBindings.get(id);
        if(!nativeBindings.containsKey(id)) binding = NativeBinding.read(owner, node);
        String exported = Annotations.value(node.invisibleAnnotations, "NativeExport");
        if(binding != null || exported != null) {
            String symbol = binding != null ? binding.symbol() : exported;
            if(binding == null && (!symbol.matches("[A-Za-z][A-Za-z_0-9]*")
                    || symbol.startsWith("jn_") || symbol.contains("__")
                    || (node.access & Opcodes.ACC_STATIC) == 0))
                throw new CompilerException("JN2001 Native export requires a static method and an unreserved C identifier: " + id);
            if(binding == null || !binding.managed()) {
                MethodId previous = nativeSymbols.putIfAbsent(symbol, id);
                if(previous != null && !previous.equals(id))
                    throw new CompilerException("JN2001 Duplicate native symbol " + symbol + ": " + previous + " and " + id);
            }
        }
        if(binding != null) {
            methods.put(id, new Method(id, node, null, List.of(), binding, path));
            for(String type : binding.types()) retainType(Type.getObjectType(type));
            for(Type argument : Type.getArgumentTypes(id.descriptor())) retainType(argument);
            retainType(Type.getReturnType(id.descriptor()));
            for(NativeBinding.Field field : binding.fields()) {
                ClassNode declaring = load(field.owner());
                FieldNode member = declaring.fields.stream().filter(candidate -> candidate.name.equals(field.name())
                        && candidate.desc.equals(field.descriptor())).findFirst().orElse(null);
                if(member == null || (member.access & Opcodes.ACC_STATIC) != 0)
                    throw new CompilerException("JN2001 Managed field must name an existing instance field: " + field + " on " + id);
                retainType(Type.getType(field.descriptor()));
            }
            for(NativeBinding.Callback callback : binding.callbacks()) linkCallback(id, callback, path);
            return;
        }
        if((node.access & Opcodes.ACC_ABSTRACT) != 0) {
            methods.put(id, new Method(id, node, null, List.of(), null, path));
            return;
        }
        Frame<BasicValue>[] frames;
        try {
            frames = new Analyzer<>(new BasicVerifier()).analyze(owner.name, node);
        } catch(AnalyzerException | RuntimeException error) {
            throw new CompilerException(
                    "JN1004 Invalid bytecode in " + id + ": " + error.getMessage(), error);
        }
        methods.put(id, new Method(id, node, frames, blocks(node), null, path));
        for(int index = 0; index < node.instructions.size(); ++index) {
            if(frames[index] == null) continue;
            AbstractInsnNode instruction = node.instructions.get(index);
            if(instruction instanceof MethodInsnNode call) {
                MethodId target = resolve(call.owner, call.name, call.desc);
                try {
                    enqueue(target, path);
                } catch(CompilerException error) {
                    throw new CompilerException(
                            error.getMessage() + "\nAt " + id + " instruction " + index, error);
                }
                if(RuntimeLibrary.platform(target.owner())) {
                    PlatformBindings.Target bindingTarget = PlatformBindings.find(target);
                    if(bindingTarget != null && bindingTarget.instance() == (call.getOpcode() == Opcodes.INVOKESTATIC))
                        throw new CompilerException("JN1004 Platform invocation kind mismatch: " + target + " at " + id);
                }
                MethodNode targetNode =
                        RuntimeLibrary.platform(target.owner())
                                ? null
                                : find(load(target.owner()), target.name(), target.descriptor());
                if(targetNode != null
                        && ((targetNode.access & Opcodes.ACC_STATIC) != 0)
                        != (call.getOpcode() == Opcodes.INVOKESTATIC))
                    throw new CompilerException(
                            "JN1004 Invocation kind mismatch: " + target + " at " + id);
                boolean privateTarget =
                        targetNode != null && (targetNode.access & Opcodes.ACC_PRIVATE) != 0;
                if(!privateTarget
                        && (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        || call.getOpcode() == Opcodes.INVOKEINTERFACE))
                    virtualCalls.add(new MethodId(call.owner, call.name, call.desc));

            }
            else if(instruction instanceof TypeInsnNode type
                    && !RuntimeLibrary.platform(type.desc)
                    && !type.desc.startsWith("[")) {
                ClassNode allocated = load(type.desc);
                if(type.getOpcode() == Opcodes.NEW
                        && (allocated.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0)
                    throw new CompilerException(
                            "JN1004 Cannot instantiate abstract class: " + type.desc + " at " + id);
                if(type.getOpcode() == Opcodes.NEW) instantiated.add(type.desc);
            }
            else if(instruction instanceof LdcInsnNode constant
                    && constant.cst instanceof Type type) {
                retainType(type);
            }
            else if(instruction instanceof FieldInsnNode field) {
                if(RuntimeLibrary.primitiveClassField(field.owner, field.name, field.desc))
                    continue;
                if(RuntimeLibrary.platform(field.owner)) {
                    PlatformBindings.FieldTarget route = PlatformBindings.field(field.owner, field.name, field.desc);
                    if(field.getOpcode() == Opcodes.GETSTATIC && route != null) enqueue(route.helper(), path);
                    else if(RuntimeLibrary.field(field.owner, field.name, field.desc))
                        throw new CompilerException("JN2001 Platform field requires an annotated staticFields getter: "
                                + field.owner + "." + field.name + ":" + field.desc);
                    if(!RuntimeLibrary.field(field.owner, field.name, field.desc))
                        unavailableFields.putIfAbsent(
                                field.owner + "." + field.name,
                                "JN1003 Unsupported platform field "
                                        + field.owner
                                        + "."
                                        + field.name
                                        + " at "
                                        + id
                                        + ":"
                                        + index);
                }
                else {
                    ClassNode declaring = load(field.owner);
                    if(field.getOpcode() == Opcodes.GETSTATIC
                            || field.getOpcode() == Opcodes.PUTSTATIC)
                        enqueueInitializer(declaring, path);
                }
            }
            else if(instruction instanceof InvokeDynamicInsnNode dynamic
                    && dynamic.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")) {
                virtualCalls.add(
                        new MethodId("java/lang/Object", "toString", "()Ljava/lang/String;"));
            }
        }
    }

    private void linkCallback(MethodId binding, NativeBinding.Callback callback, List<MethodId> path) {
        MethodId declared = callback.method();
        MethodId target = resolve(declared.owner(), declared.name(), declared.descriptor());
        if(RuntimeLibrary.platform(target.owner())) {
            if(!RuntimeLibrary.method(target.owner(), target.name(), target.descriptor()))
                throw new CompilerException("JN2001 Unsupported callback " + declared + " on " + binding);
            PlatformBindings.Target route = PlatformBindings.find(target);
            if(route == null || route.instance() == (callback.invocation() == NativeBinding.Invocation.STATIC))
                throw new CompilerException("JN2001 Callback invocation kind mismatch: " + declared + " on " + binding);
        }
        else {
            MethodNode node = find(load(target.owner()), target.name(), target.descriptor());
            if(node == null) throw new CompilerException("JN2001 Callback not found: " + declared + " on " + binding);
            boolean isStatic = (node.access & Opcodes.ACC_STATIC) != 0;
            if(isStatic != (callback.invocation() == NativeBinding.Invocation.STATIC)
                    || callback.invocation() == NativeBinding.Invocation.SPECIAL && (node.access & Opcodes.ACC_ABSTRACT) != 0
                    || (callback.invocation() == NativeBinding.Invocation.VIRTUAL || callback.invocation() == NativeBinding.Invocation.INTERFACE)
                    && (node.access & Opcodes.ACC_PRIVATE) != 0)
                throw new CompilerException("JN2001 Callback invocation kind mismatch: " + declared + " on " + binding);
            if(callback.invocation() == NativeBinding.Invocation.INTERFACE
                    && (load(declared.owner()).access & Opcodes.ACC_INTERFACE) == 0)
                throw new CompilerException("JN2001 Interface callback requires an interface owner: " + declared + " on " + binding);
        }
        if(callback.invocation() != NativeBinding.Invocation.STATIC && callback.receiver() >= 0) {
            Type receiver = Type.getArgumentTypes(binding.descriptor())[callback.receiver()];
            if(receiver.getSort() != Type.OBJECT || !subtype(receiver.getInternalName(), declared.owner()))
                throw new CompilerException("JN2001 Callback receiver type is incompatible with " + declared + " on " + binding);
        }
        for(Type argument : Type.getArgumentTypes(declared.descriptor())) retainType(argument);
        retainType(Type.getReturnType(declared.descriptor()));
        enqueue(target, path);
        if(callback.invocation() == NativeBinding.Invocation.VIRTUAL || callback.invocation() == NativeBinding.Invocation.INTERFACE)
            virtualCalls.add(declared);
    }

    private static List<Block> blocks(MethodNode method) {
        var starts = new TreeSet<Integer>();
        starts.add(0);
        for(int i = 0; i < method.instructions.size(); ++i) {
            AbstractInsnNode instruction = method.instructions.get(i);
            if(instruction instanceof LabelNode) starts.add(i);
            if(instruction instanceof JumpInsnNode
                    || instruction instanceof TableSwitchInsnNode
                    || instruction instanceof LookupSwitchInsnNode
                    || (instruction.getOpcode() >= Opcodes.IRETURN
                    && instruction.getOpcode() <= Opcodes.RETURN))
                if(i + 1 < method.instructions.size()) starts.add(i + 1);
        }
        var points = new ArrayList<>(starts);
        var result = new ArrayList<Block>();
        for(int b = 0; b < points.size(); ++b) {
            int start = points.get(b),
                    end = b + 1 == points.size() ? method.instructions.size() : points.get(b + 1);
            AbstractInsnNode last = method.instructions.get(end - 1);
            var successors = new LinkedHashSet<Integer>();
            if(last instanceof JumpInsnNode jump)
                successors.add(method.instructions.indexOf(jump.label));
            if(last instanceof TableSwitchInsnNode jump) {
                successors.add(method.instructions.indexOf(jump.dflt));
                jump.labels.forEach(l -> successors.add(method.instructions.indexOf(l)));
            }
            if(last instanceof LookupSwitchInsnNode jump) {
                successors.add(method.instructions.indexOf(jump.dflt));
                jump.labels.forEach(l -> successors.add(method.instructions.indexOf(l)));
            }
            int opcode = last.getOpcode();
            if(end < method.instructions.size()
                    && opcode != Opcodes.GOTO
                    && opcode != Opcodes.ATHROW
                    && !(opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN)
                    && !(last instanceof TableSwitchInsnNode)
                    && !(last instanceof LookupSwitchInsnNode)) successors.add(end);
            result.add(new Block(start, end, List.copyOf(successors)));
        }
        return List.copyOf(result);
    }
}
