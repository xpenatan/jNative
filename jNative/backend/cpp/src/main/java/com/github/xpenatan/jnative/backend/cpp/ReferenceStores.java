package com.github.xpenatan.jnative.backend.cpp;

import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.ReflectionPlan.FieldId;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

/** Proves stores need only null/bounds checks, without assuming array covariance away. */
final class ReferenceStores {
    private static final BasicValue OBJECT_ARRAY = new BasicValue(Type.getType("[Ljava/lang/Object;"));
    private static final BasicValue NULL_VALUE = new BasicValue(BasicInterpreter.NULL_TYPE);
    private final Program program;
    private final Map<FieldId, FieldId> declarations = new HashMap<>();
    private final Map<MethodId, Set<AbstractInsnNode>> stores = new HashMap<>();

    ReferenceStores(Program program, OwnedArrays ownership) {
        this.program = program;
        Set<FieldId> fields = new HashSet<>();
        for(var owner : program.classes().values())
            for(var field : owner.fields)
                if(field.desc.equals("[Ljava/lang/Object;") && ownership.plainField(owner.name, field))
                    fields.add(new FieldId(owner.name, field.name, field.desc));
        // Even runtime-only field adapters can assign arbitrary callback results.
        for(var method : program.methods().values())
            if(method.nativeBinding() != null)
                for(var field : method.nativeBinding().fields())
                    fields.remove(declaration(new FieldId(field.owner(), field.name(), field.descriptor())));

        Set<FieldId> invalid = new HashSet<>();
        for(var method : program.methods().values()) {
            if(!writesCandidate(method.bytecode(), fields)) continue;
            Frame<BasicValue>[] frames = analyze(method, Set.of());
            for(int i = 0; i < method.bytecode().instructions.size(); i++) {
                AbstractInsnNode instruction = method.bytecode().instructions.get(i);
                if(!(instruction instanceof FieldInsnNode field) || field.getOpcode() != PUTFIELD) continue;
                FieldId id = id(field);
                if(!fields.contains(id)) continue;
                // Prove every reachable assignment independently, without relying on
                // the field being proved. Default null and unreachable writes are safe.
                if(frames == null) invalid.add(id);
                else if(frames[i] != null) {
                    BasicValue value = frames[i].getStack(frames[i].getStackSize() - 1);
                    if(!exact(value) && !NULL_VALUE.equals(value)) invalid.add(id);
                }
            }
        }
        fields.removeAll(invalid);
        for(var method : program.methods().values()) {
            boolean present = false;
            for(AbstractInsnNode instruction : method.bytecode().instructions)
                if(instruction.getOpcode() == AASTORE) present = true;
            if(!present) continue;
            Frame<BasicValue>[] frames = analyze(method, fields);
            if(frames == null) continue;
            Set<AbstractInsnNode> selected = Collections.newSetFromMap(new IdentityHashMap<>());
            for(int i = 0; i < frames.length; i++) {
                if(method.bytecode().instructions.get(i).getOpcode() != AASTORE || frames[i] == null) continue;
                Frame<BasicValue> frame = frames[i];
                if(exact(frame.getStack(frame.getStackSize() - 3))
                        || NULL_VALUE.equals(frame.getStack(frame.getStackSize() - 1)))
                    selected.add(method.bytecode().instructions.get(i));
            }
            selected.addAll(ArrayTypes.compatibleStores(method.id().owner(), method.bytecode(), name -> {
                var type = program.classes().get(name);
                return type != null && (type.access & ACC_FINAL) != 0;
            }));
            stores.put(method.id(), selected);
        }
    }

    boolean bounded(MethodId method, AbstractInsnNode instruction) {
        return stores.getOrDefault(method, Set.of()).contains(instruction);
    }

    private boolean writesCandidate(MethodNode method, Set<FieldId> fields) {
        for(AbstractInsnNode instruction : method.instructions)
            if(instruction instanceof FieldInsnNode field && field.getOpcode() == PUTFIELD
                    && fields.contains(id(field))) return true;
        return false;
    }

    private Frame<BasicValue>[] analyze(Program.Method method, Set<FieldId> fields) {
        if(method.bytecode().instructions.size() > 4096) return null;
        try {
            return new Analyzer<>(new Flow(fields)).analyze(method.id().owner(), method.bytecode());
        } catch(AnalyzerException | RuntimeException unsupported) {
            return null;
        }
    }

    private static boolean exact(BasicValue value) {
        return OBJECT_ARRAY.equals(value);
    }

    private FieldId id(FieldInsnNode field) {
        FieldId id = new FieldId(field.owner, field.name, field.desc);
        return declarations.computeIfAbsent(id, this::declaration);
    }

    private FieldId declaration(FieldId id) {
        var owner = program.classes().get(id.owner());
        if(owner == null) return id;
        for(var field : owner.fields)
            if(field.name.equals(id.name()) && field.desc.equals(id.descriptor())) return id;
        return owner.superName == null ? id
                : declaration(new FieldId(owner.superName, id.name(), id.descriptor()));
    }

    private final class Flow extends BasicInterpreter {
        private final Set<FieldId> fields;

        Flow(Set<FieldId> fields) {
            super(ASM9);
            this.fields = fields;
        }

        @Override
        public BasicValue newOperation(AbstractInsnNode instruction) throws AnalyzerException {
            return instruction.getOpcode() == ACONST_NULL ? NULL_VALUE : super.newOperation(instruction);
        }

        @Override
        public BasicValue unaryOperation(AbstractInsnNode instruction, BasicValue value) throws AnalyzerException {
            if(instruction instanceof TypeInsnNode array && array.getOpcode() == ANEWARRAY
                    && array.desc.equals("java/lang/Object")) return OBJECT_ARRAY;
            if(instruction instanceof FieldInsnNode field && field.getOpcode() == GETFIELD
                    && fields.contains(id(field))) return OBJECT_ARRAY;
            return super.unaryOperation(instruction, value);
        }

        @Override
        public BasicValue merge(BasicValue first, BasicValue second) {
            if(first.equals(second)) return first;
            // Null retains an exact class proof on the successful array-access path.
            if(exact(first) && NULL_VALUE.equals(second) || NULL_VALUE.equals(first) && exact(second)) return OBJECT_ARRAY;
            return super.merge(first, second);
        }
    }
}
