package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.compiler.MethodResolver;
import com.github.xpenatan.jnative.compiler.NativeBinding;
import com.github.xpenatan.jnative.compiler.PlatformBindings;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.ReflectionPlan.FieldId;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

/**
 * Closed-world entry-thread ownership for primitive fields and internal final arrays.
 */
final class OwnedArrays {
    private final Program program;
    private final Set<FieldId> candidates = new HashSet<>();
    private final Set<FieldId> primitiveFields = new HashSet<>();
    private final Set<Object> escaped = new HashSet<>();
    private final Map<Object, FieldId> owners = new HashMap<>();
    private final Map<MethodId, Map<AbstractInsnNode, Object>> uses = new LinkedHashMap<>();
    private final Map<MethodId, List<MethodId>> dispatch = new HashMap<>();
    private final Set<String> concurrentOwners = new HashSet<>();
    private final Map<MethodId, Set<AbstractInsnNode>> instructionCache = new HashMap<>();
    private final Map<FieldId, FieldId> declarations = new HashMap<>();

    OwnedArrays(Program program) {
        this.program = program;
        findConcurrentAccess();
        Set<String> exposed = new HashSet<>();
        Set<FieldId> nativeArrayFields = new HashSet<>();
        for(Program.Method method : program.methods().values()) {
            if(method.nativeSymbol() == null
                    && !exported(method.bytecode().visibleAnnotations)
                    && !exported(method.bytecode().invisibleAnnotations)) continue;
            if(method.nativeBinding() != null
                    && (method.nativeBinding().runtimeOnly() || method.nativeBinding().registeredReflection())) {
                for(var field : method.nativeBinding().fields())
                    if(Type.getType(field.descriptor()).getSort() == Type.ARRAY)
                        nativeArrayFields.add(new FieldId(field.owner(), field.name(), field.descriptor()));
                if(method.nativeBinding().registeredReflection()) {
                    for(var field : program.reflection().fields()) {
                        exposed.add(field.owner());
                        expose(exposed, Type.getType(field.descriptor()));
                    }
                    for(var member : program.reflection().methods())
                        if(program.classes().containsKey(member.owner())) exposed.add(member.owner());
                }
                // Callback bodies remain in concurrent access analysis below; a runtime-only
                // helper cannot inspect their receiver/argument fields outside declared adapters.
                continue;
            }
            if((method.bytecode().access & ACC_STATIC) == 0) exposed.add(method.id().owner());
            for(Type argument : Type.getArgumentTypes(method.id().descriptor()))
                expose(exposed, argument);
            expose(exposed, Type.getReturnType(method.id().descriptor()));
            if(method.nativeBinding() != null) {
                for(var callback : method.nativeBinding().callbacks()) {
                    exposed.add(callback.method().owner());
                    for(Type argument : Type.getArgumentTypes(callback.method().descriptor())) expose(exposed, argument);
                    expose(exposed, Type.getReturnType(callback.method().descriptor()));
                }
                for(var field : method.nativeBinding().fields()) {
                    exposed.add(field.owner());
                    expose(exposed, Type.getType(field.descriptor()));
                }
            }
        }
        // Native code can follow object edges through generated headers. Keep
        // the reachable payloads conservative too, including inherited fields.
        boolean changed;
        do {
            Set<String> payloads = new HashSet<>();
            for(ClassNode owner : program.classes().values()) {
                if(exposed.stream()
                        .noneMatch(type -> subtype(owner.name, type) || subtype(type, owner.name)))
                    continue;
                for(FieldNode field : owner.fields)
                    if((field.access & ACC_STATIC) == 0)
                        expose(payloads, Type.getType(field.desc));
            }
            changed = exposed.addAll(payloads);
        } while(changed);
        for(ClassNode owner : program.classes().values()) {
            if(concurrentOwners.contains(owner.name)
                    || exposed.stream()
                    .anyMatch(
                            type -> subtype(owner.name, type) || subtype(type, owner.name)))
                continue;
            for(FieldNode field : owner.fields) {
                FieldId id = new FieldId(owner.name, field.name, field.desc);
                if((field.access & (ACC_STATIC | ACC_VOLATILE)) == 0
                        && field.desc.length() == 1
                        && "ZBCSIFJD".contains(field.desc)
                        && !program.reflection().fields().contains(id)) primitiveFields.add(id);
                if((field.access & (ACC_PUBLIC | ACC_FINAL | ACC_STATIC | ACC_VOLATILE))
                        == ACC_FINAL
                        && field.desc.length() == 2
                        && "[CSIFJD".indexOf(field.desc.charAt(1)) > 0
                        && field.desc.charAt(0) == '['
                        && !program.reflection().fields().contains(id)
                        && !nativeArrayFields.contains(id)) candidates.add(id);
            }
        }
        for(Program.Method method : program.methods().values()) {
            if(method.nativeSymbol() != null || method.bytecode().instructions.size() == 0)
                continue;
            boolean relevant = false;
            for(AbstractInsnNode instruction : method.bytecode().instructions)
                if(instruction instanceof FieldInsnNode field && candidates.contains(id(field)))
                    relevant = true;
            if(!relevant) continue;
            Map<AbstractInsnNode, Object> methodUses = new IdentityHashMap<>();
            uses.put(method.id(), methodUses);
            try {
                new Analyzer<>(new Flow(methodUses))
                        .analyze(method.id().owner(), method.bytecode());
            } catch(AnalyzerException | RuntimeException failure) {
                // No field accessed by an unproved body can use a different layout.
                for(AbstractInsnNode instruction : method.bytecode().instructions)
                    if(instruction instanceof FieldInsnNode field) escaped.add(id(field));
            }
        }
        for(var entry : owners.entrySet())
            if(escaped.contains(entry.getKey())) escaped.add(entry.getValue());
        candidates.removeAll(escaped);
        // Final fields without a proved fresh assignment keep the ordinary representation.
        candidates.retainAll(new HashSet<>(owners.values()));
    }

    private static void expose(Set<String> exposed, Type type) {
        while(type.getSort() == Type.ARRAY) type = type.getElementType();
        if(type.getSort() == Type.OBJECT) exposed.add(type.getInternalName());
    }

    Set<AbstractInsnNode> instructions(MethodId method) {
        return instructionCache.computeIfAbsent(method, this::selectInstructions);
    }

    boolean plainField(String owner, FieldNode field) {
        return primitiveFields.contains(new FieldId(owner, field.name, field.desc));
    }

    String fieldReport() {
        StringBuilder result = new StringBuilder("java-field\tstorage\tproof\n");
        primitiveFields.stream()
                .sorted(Comparator.comparing(Object::toString))
                .forEach(
                        field ->
                                result.append(field.owner())
                                        .append('.')
                                        .append(field.name())
                                        .append(field.descriptor())
                                        .append(
                                                "\tplain\tinstance primitive; no worker access,"
                                                        + " reflection or native exposure\n"));
        return result.toString();
    }

    private Set<AbstractInsnNode> selectInstructions(MethodId method) {
        Set<AbstractInsnNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        uses.getOrDefault(method, Map.of())
                .forEach(
                        (instruction, origin) -> {
                            FieldId field = origin instanceof FieldId id ? id : owners.get(origin);
                            if(candidates.contains(field)) result.add(instruction);
                        });
        return result;
    }

    String report() {
        StringBuilder result = new StringBuilder("java-field\tstorage\tproof\n");
        candidates.stream()
                .sorted(Comparator.comparing(Object::toString))
                .forEach(
                        field ->
                                result.append(field.owner())
                                        .append('.')
                                        .append(field.name())
                                        .append(field.descriptor())
                                        .append(
                                                "\tplain\tinternal final fresh array; no escaping"
                                                        + " alias, worker access, reflection or native"
                                                        + " exposure\n"));
        return result.toString();
    }

    private void findConcurrentAccess() {
        Set<MethodId> reached = new HashSet<>();
        ArrayDeque<MethodId> pending = new ArrayDeque<>();
        enqueueReflection(pending);
        // Implementing Runnable/Callable or calling Thread.run does not start a worker.
        // Reachable asynchronous imports (including Thread.start) declare the actual
        // callback roots; traversal below also follows synchronous callbacks on workers.
        for(Program.Method method : program.methods().values()) {
            if(method.nativeBinding() != null && !method.nativeBinding().callbacksSynchronous())
                enqueueCallbacks(pending, method.nativeBinding());
            if(exported(method.bytecode().visibleAnnotations)
                    || exported(method.bytecode().invisibleAnnotations)) pending.add(method.id());
        }
        while(!pending.isEmpty()) {
            MethodId id = pending.removeFirst();
            if(!reached.add(id)) continue;
            Program.Method method = program.methods().get(id);
            if(method == null) continue;
            concurrentOwners.add(id.owner());
            enqueue(pending, new MethodId(id.owner(), "<clinit>", "()V"));
            if(method.nativeBinding() != null) {
                enqueueCallbacks(pending, method.nativeBinding());
                for(var field : method.nativeBinding().fields()) {
                    concurrentOwners.add(field.owner());
                    enqueue(pending, new MethodId(field.owner(), "<clinit>", "()V"));
                }
                if(method.nativeBinding().registeredReflection()) {
                    enqueueReflection(pending);
                    for(var field : program.reflection().fields()) {
                        concurrentOwners.add(field.owner());
                        enqueue(pending, new MethodId(field.owner(), "<clinit>", "()V"));
                    }
                }
            }
            for(AbstractInsnNode instruction : method.bytecode().instructions) {
                if(instruction instanceof FieldInsnNode field) {
                    var getter = field.getOpcode() == GETSTATIC ? program.bindings().field(field.owner, field.name, field.desc) : null;
                    if(getter != null) enqueue(pending, getter.helper());
                    String owner = id(field).owner();
                    concurrentOwners.add(owner);
                    enqueue(pending, new MethodId(owner, "<clinit>", "()V"));
                }
                else if(instruction instanceof TypeInsnNode type && type.getOpcode() == NEW) {
                    enqueue(pending, new MethodId(type.desc, "<clinit>", "()V"));
                }
                else if(instruction instanceof InvokeDynamicInsnNode dynamic
                        && dynamic.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")) {
                    boolean objectText = Arrays.stream(Type.getArgumentTypes(dynamic.desc)).anyMatch(type ->
                            (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)
                                    && !type.equals(Type.getType(String.class)));
                    if(objectText) enqueueVirtual(pending,
                            new MethodId("java/lang/Object", "toString", "()Ljava/lang/String;"));
                }
                else if(instruction instanceof MethodInsnNode call) {
                    if(call.getOpcode() == INVOKESTATIC || call.getOpcode() == INVOKESPECIAL)
                        enqueue(
                                pending,
                                MethodResolver.resolve(
                                        call.owner, call.name, call.desc, program.classes()::get, program::platform, program.bindings()));
                    else enqueueVirtual(pending, new MethodId(call.owner, call.name, call.desc));
                }
            }
        }
    }

    private void enqueueReflection(ArrayDeque<MethodId> pending) {
        for(MethodId reflected : program.reflection().methods()) {
            enqueue(pending, reflected);
            Program.Method method = program.methods().get(reflected);
            PlatformBindings.Target platform = method == null ? program.bindings().find(reflected) : null;
            boolean instance = method != null ? (method.bytecode().access & ACC_STATIC) == 0
                    : platform != null && platform.instance();
            if(instance && !reflected.name().equals("<init>")) enqueueVirtual(pending, reflected);
        }
    }

    private static boolean exported(List<AnnotationNode> annotations) {
        return annotations != null
                && annotations.stream()
                .anyMatch(
                        annotation ->
                                annotation.desc.equals(
                                        "Lcom/github/xpenatan/jnative/interop/NativeExport;"));
    }

    private void enqueueCallbacks(ArrayDeque<MethodId> pending, NativeBinding binding) {
        for(var callback : binding.callbacks()) {
            if(callback.invocation() == NativeBinding.Invocation.VIRTUAL
                    || callback.invocation() == NativeBinding.Invocation.INTERFACE)
                enqueueVirtual(pending, callback.method());
            else enqueue(pending, callback.method());
        }
    }

    private void enqueueVirtual(ArrayDeque<MethodId> pending, MethodId call) {
        enqueue(pending, MethodResolver.resolve(call.owner(), call.name(), call.descriptor(), program.classes()::get, program::platform, program.bindings()));
        for(MethodId target : dispatch.computeIfAbsent(call, this::targets)) enqueue(pending, target);
        for(MethodId bridge : program.bindings().interfaceImplementations(call)) enqueue(pending, bridge);
    }

    private void enqueue(ArrayDeque<MethodId> pending, MethodId method) {
        if(method == null) return;
        if(!program.methods().containsKey(method)) {
            PlatformBindings.Target target = program.bindings().find(method);
            if(target != null) method = target.helper();
        }
        if(program.methods().containsKey(method)) pending.add(method);
    }

    private List<MethodId> targets(MethodId call) {
        Set<MethodId> result = new HashSet<>();
        for(String type : program.instantiatedClasses())
            if(subtype(type, call.owner())) {
                MethodId target =
                        MethodResolver.resolve(
                                type, call.name(), call.descriptor(), program.classes()::get, program::platform, program.bindings());
                if(target != null) result.add(target);
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

    private FieldId id(FieldInsnNode field) {
        FieldId referenced = new FieldId(field.owner, field.name, field.desc);
        return declarations.computeIfAbsent(referenced, this::declaration);
    }

    private FieldId declaration(FieldId field) {
        ClassNode owner = program.classes().get(field.owner());
        if(owner == null) return field;
        for(FieldNode member : owner.fields)
            if(member.name.equals(field.name()) && member.desc.equals(field.descriptor()))
                return field;
        // Only instance fields are eligible; interface fields are always static.
        return owner.superName == null
                ? field
                : declaration(new FieldId(owner.superName, field.name(), field.descriptor()));
    }

    private final class Flow extends BasicInterpreter {
        private final Map<AbstractInsnNode, Object> methodUses;

        Flow(Map<AbstractInsnNode, Object> methodUses) {
            super(ASM9);
            this.methodUses = methodUses;
        }

        private void escape(BasicValue value) {
            if(value instanceof ArrayValue array) escaped.add(array.origin);
        }

        private void use(AbstractInsnNode instruction, BasicValue value) {
            if(value instanceof ArrayValue array) {
                Object previous = methodUses.put(instruction, array.origin);
                if(previous != null && !previous.equals(array.origin)) {
                    escaped.add(previous);
                    escaped.add(array.origin);
                }
            }
        }

        @Override
        public BasicValue copyOperation(AbstractInsnNode instruction, BasicValue value) {
            use(instruction, value);
            return value;
        }

        @Override
        public BasicValue unaryOperation(AbstractInsnNode instruction, BasicValue value)
                throws AnalyzerException {
            BasicValue result = super.unaryOperation(instruction, value);
            if(instruction instanceof IntInsnNode array
                    && array.getOpcode() == NEWARRAY
                    && array.operand != T_BYTE
                    && array.operand != T_BOOLEAN) {
                ArrayValue created = new ArrayValue(result.getType(), instruction);
                use(instruction, created);
                return created;
            }
            if(instruction instanceof FieldInsnNode field
                    && field.getOpcode() == GETFIELD
                    && candidates.contains(id(field))) {
                ArrayValue read = new ArrayValue(result.getType(), id(field));
                use(instruction, read);
                return read;
            }
            if(instruction.getOpcode() == ARRAYLENGTH
                    || instruction.getOpcode() == IFNULL
                    || instruction.getOpcode() == IFNONNULL) use(instruction, value);
            else escape(value);
            return result;
        }

        @Override
        public BasicValue binaryOperation(
                AbstractInsnNode instruction, BasicValue left, BasicValue right)
                throws AnalyzerException {
            int opcode = instruction.getOpcode();
            if(instruction instanceof FieldInsnNode field
                    && opcode == PUTFIELD
                    && candidates.contains(id(field))) {
                if(right instanceof ArrayValue array
                        && array.origin instanceof AbstractInsnNode allocation
                        && allocation.getOpcode() == NEWARRAY) {
                    FieldId previous = owners.put(array.origin, id(field));
                    if(previous != null && !previous.equals(id(field))) {
                        escaped.add(previous);
                        escaped.add(id(field));
                    }
                }
                else escaped.add(id(field));
            }
            else {
                if(opcode >= IALOAD && opcode <= SALOAD && opcode != AALOAD && opcode != BALOAD)
                    use(instruction, left);
                else escape(left);
                escape(right);
            }
            return super.binaryOperation(instruction, left, right);
        }

        @Override
        public BasicValue ternaryOperation(
                AbstractInsnNode instruction, BasicValue array, BasicValue index, BasicValue value)
                throws AnalyzerException {
            int opcode = instruction.getOpcode();
            if(opcode >= IASTORE && opcode <= SASTORE && opcode != AASTORE && opcode != BASTORE)
                use(instruction, array);
            else escape(array);
            escape(index);
            escape(value);
            return super.ternaryOperation(instruction, array, index, value);
        }

        @Override
        public BasicValue naryOperation(
                AbstractInsnNode instruction, List<? extends BasicValue> values)
                throws AnalyzerException {
            for(BasicValue value : values) escape(value);
            return super.naryOperation(instruction, values);
        }

        @Override
        public void returnOperation(
                AbstractInsnNode instruction, BasicValue value, BasicValue expected) {
            escape(value);
        }

        @Override
        public BasicValue merge(BasicValue left, BasicValue right) {
            if(left instanceof ArrayValue first
                    && right instanceof ArrayValue second
                    && first.origin.equals(second.origin)) return left;
            if(left instanceof ArrayValue || right instanceof ArrayValue) {
                escape(left);
                escape(right);
            }
            return super.merge(left, right);
        }
    }

    private static final class ArrayValue extends BasicValue {
        final Object origin;

        ArrayValue(Type type, Object origin) {
            super(type);
            this.origin = origin;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ArrayValue array && origin.equals(array.origin);
        }

        @Override
        public int hashCode() {
            return origin.hashCode();
        }
    }
}
