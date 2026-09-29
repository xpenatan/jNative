package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.compiler.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class NativeBindingOwnershipTest {
    @Test void runtimeRepresentationProofPreservesFieldsWhileUnknownNativeAccessDoesNot() {
        assertTrue(owned(new Scenario()));
        assertFalse(owned(new Scenario().unknownNative()));
        assertTrue(owned(new Scenario().fieldAdapter()));
    }

    @Test void reflectionAndDeclaredAsynchronousCallbacksKeepFieldsOrdinary() {
        assertFalse(owned(new Scenario().reflection()));
        assertFalse(owned(new Scenario().callback(false)));
        var fieldAccess = PlatformBindings.find(new Program.MethodId("java/lang/reflect/Field", "getInt", "(Ljava/lang/Object;)I"));
        assertFalse(fieldAccess.binding().runtimeOnly());
        assertTrue(fieldAccess.binding().registeredReflection());
        var invocation = PlatformBindings.find(new Program.MethodId("java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"));
        assertFalse(invocation.binding().runtimeOnly());
        assertTrue(invocation.binding().registeredReflection());
    }

    @Test void registeredReflectionExposesRegisteredMembersAndDependenciesWithoutPoisoningUnrelatedClasses() {
        assertTrue(owned(new Scenario().registeredReflection()));
        assertFalse(owned(new Scenario().unknownNative()));
        assertFalse(owned(new Scenario().registeredReflection().reflection()));
        assertFalse(owned(new Scenario().registeredReflection().callback(true).reflectedCallback()));
        assertFalse(owned(new Scenario().registeredReflection().reflectedConstructor()));
        assertFalse(owned(new Scenario().registeredReflection().reflectedPayload()));
        assertFalse(owned(new Scenario().registeredReflection().concat(false).reflectedPlatformMethod()));
    }

    @Test void synchronousCallbacksAreConfinedUntilCalledFromWorkerExportOrReflection() {
        assertTrue(owned(new Scenario().callback(true)));
        assertFalse(owned(new Scenario().callback(true).worker()));
        assertFalse(owned(new Scenario().callback(true).exported()));
        assertFalse(owned(new Scenario().callback(true).reflectedHelper()));
    }

    @Test void workerToPlatformHelperToSynchronousCallbackAndFieldAdaptersRemainConcurrent() {
        assertFalse(owned(new Scenario().callback(true).platform().worker()));
        assertFalse(owned(new Scenario().fieldAdapter().worker()));
    }

    @Test void concatenationToStringEdgesAreConcurrentOnlyWhenObjectConcatenationRunsOnWorker() {
        assertTrue(owned(new Scenario().concat(false)));
        assertFalse(owned(new Scenario().concat(false).worker()));
        assertTrue(owned(new Scenario().concat(true).worker()));
    }

    @Test void nativeArrayFieldAdaptersRemainConservative() {
        ClassNode owner = type("Demo", "java/lang/Object");
        FieldNode array = new FieldNode(ACC_PRIVATE | ACC_FINAL, "values", "[I", null, null);
        owner.fields.add(array);
        MethodNode constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(ALOAD, 0));
        constructor.instructions.add(new InsnNode(ICONST_1));
        constructor.instructions.add(new IntInsnNode(NEWARRAY, T_INT));
        constructor.instructions.add(new FieldInsnNode(PUTFIELD, "Demo", "values", "[I"));
        constructor.instructions.add(new InsnNode(RETURN));
        constructor.maxStack = 2;
        constructor.maxLocals = 1;
        owner.methods.add(constructor);
        var id = new Program.MethodId("Demo", "<init>", "()V");
        var methods = new LinkedHashMap<Program.MethodId, Program.Method>();
        methods.put(id, method(id, constructor, null));
        var classes = new LinkedHashMap<String, ClassNode>();
        classes.put(owner.name, owner);
        assertTrue(new OwnedArrays(new Program(id, classes, methods, Set.of())).report().contains("Demo.values[I"));
        ClassNode helper = type("Helper", "java/lang/Object");
        MethodNode driver = nativeDriver(helper, "driver", "(Ljava/lang/Object;)V", true, false, false);
        driver.invisibleAnnotations.getFirst().values.addAll(List.of("fields", List.of("Demo.values:[I")));
        var driverId = new Program.MethodId("Helper", "driver", driver.desc);
        methods.put(driverId, method(driverId, driver, NativeBinding.read(helper, driver)));
        classes.put(helper.name, helper);
        assertFalse(new OwnedArrays(new Program(id, classes, methods, Set.of())).report().contains("Demo.values[I"));
    }

    private static final class Scenario {
        boolean runtimeOnly = true, fieldAdapter, reflection, callback, synchronous, worker, exported, reflectedHelper, platform, concat, primitiveConcat;
        boolean registeredReflection, reflectedCallback, reflectedConstructor, reflectedPayload, reflectedPlatformMethod;
        Scenario unknownNative() { runtimeOnly = false; return this; }
        Scenario fieldAdapter() { fieldAdapter = true; return this; }
        Scenario reflection() { reflection = true; return this; }
        Scenario callback(boolean sync) { callback = true; synchronous = sync; return this; }
        Scenario worker() { worker = true; return this; }
        Scenario exported() { exported = true; return this; }
        Scenario reflectedHelper() { reflectedHelper = true; return this; }
        Scenario platform() { platform = true; return this; }
        Scenario concat(boolean primitive) { concat = true; primitiveConcat = primitive; return this; }
        Scenario registeredReflection() { runtimeOnly = false; registeredReflection = true; return this; }
        Scenario reflectedCallback() { reflectedCallback = true; return this; }
        Scenario reflectedConstructor() { reflectedConstructor = true; return this; }
        Scenario reflectedPayload() { reflectedPayload = true; return this; }
        Scenario reflectedPlatformMethod() { reflectedPlatformMethod = true; return this; }
    }

    private static boolean owned(Scenario scenario) {
        ClassNode owner = type("Demo", "java/lang/Object");
        FieldNode field = new FieldNode(ACC_PRIVATE, "state", "I", null, null);
        owner.fields.add(field);
        var classes = new LinkedHashMap<String, ClassNode>();
        classes.put(owner.name, owner);
        Program.MethodId helperId = scenario.platform
                ? PlatformBindings.find(new Program.MethodId("java/lang/ThreadLocal", "get", "()Ljava/lang/Object;")).helper()
                : new Program.MethodId("Helper", "driver", "(Ljava/lang/Object;)V");
        ClassNode helper = type(helperId.owner(), "java/lang/Object");
        MethodNode driver = nativeDriver(helper, helperId.name(), helperId.descriptor(), scenario.runtimeOnly, scenario.callback, scenario.synchronous);
        if(scenario.registeredReflection) driver.invisibleAnnotations.getFirst().values.addAll(List.of("registeredReflection", true));
        if(scenario.fieldAdapter) driver.invisibleAnnotations.getFirst().values.addAll(List.of("fields", List.of("Demo.state:I")));
        var methods = new LinkedHashMap<Program.MethodId, Program.Method>();
        methods.put(helperId, method(helperId, driver, NativeBinding.read(helper, driver)));
        classes.put(helper.name, helper);
        if(scenario.callback) {
            MethodNode callback = fieldReader(ACC_STATIC, "callback", "(LDemo;)V", false);
            owner.methods.add(callback);
            var id = new Program.MethodId("Demo", "callback", callback.desc);
            methods.put(id, method(id, callback, null));
        }
        var instantiated = new HashSet<String>();
        MethodNode root = new MethodNode(ACC_PUBLIC | ACC_STATIC, "root", "()V", null, null);
        ClassNode rootOwner = type("Root", "java/lang/Object");
        if(scenario.worker) {
            rootOwner.interfaces.add("java/lang/Runnable");
            root.name = "run";
            root.access = ACC_PUBLIC;
            instantiated.add(rootOwner.name);
        }
        if(scenario.exported) {
            var export = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeExport;");
            export.values = List.of("value", "root_export");
            root.invisibleAnnotations = List.of(export);
        }
        if(scenario.concat) {
            MethodNode text = fieldReader(ACC_PUBLIC, "toString", "()Ljava/lang/String;", true);
            owner.methods.add(text);
            var textId = new Program.MethodId("Demo", "toString", text.desc);
            methods.put(textId, method(textId, text, null));
            instantiated.add(owner.name);
            String descriptor = scenario.primitiveConcat ? "(I)Ljava/lang/String;" : "(Ljava/lang/Object;)Ljava/lang/String;";
            root.instructions.add(new InvokeDynamicInsnNode("makeConcatWithConstants", descriptor,
                    new Handle(H_INVOKESTATIC, "java/lang/invoke/StringConcatFactory", "makeConcatWithConstants", "()V", false), "\u0001"));
        } else root.instructions.add(scenario.platform
                ? new MethodInsnNode(INVOKEVIRTUAL, "java/lang/ThreadLocal", "get", "()Ljava/lang/Object;", false)
                : new MethodInsnNode(INVOKESTATIC, helperId.owner(), helperId.name(), helperId.descriptor(), false));
        root.instructions.add(new InsnNode(RETURN));
        rootOwner.methods.add(root);
        classes.put(rootOwner.name, rootOwner);
        var rootId = new Program.MethodId(rootOwner.name, root.name, root.desc);
        methods.put(rootId, method(rootId, root, null));
        Set<ReflectionPlan.FieldId> fields = new HashSet<>();
        if(scenario.reflection) fields.add(new ReflectionPlan.FieldId("Demo", "state", "I"));
        Set<Program.MethodId> reflected = new HashSet<>();
        if(scenario.reflectedHelper) reflected.add(helperId);
        if(scenario.reflectedCallback) reflected.add(new Program.MethodId("Demo", "callback", "(LDemo;)V"));
        if(scenario.reflectedPlatformMethod)
            reflected.add(new Program.MethodId("java/lang/Object", "toString", "()Ljava/lang/String;"));
        if(scenario.reflectedConstructor) {
            var constructor = new MethodNode(ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.instructions.add(new InsnNode(RETURN));
            owner.methods.add(constructor);
            var constructorId = new Program.MethodId("Demo", "<init>", "()V");
            methods.put(constructorId, method(constructorId, constructor, null));
            reflected.add(constructorId);
        }
        if(scenario.reflectedPayload) {
            ClassNode envelope = type("Reflected", "java/lang/Object");
            envelope.fields.add(new FieldNode(ACC_PUBLIC, "payload", "LDemo;", null, null));
            classes.put(envelope.name, envelope);
            fields.add(new ReflectionPlan.FieldId("Reflected", "payload", "LDemo;"));
        }
        ReflectionPlan plan = new ReflectionPlan(Set.of(), reflected, fields);
        return new OwnedArrays(new Program(rootId, classes, methods, instantiated, plan)).plainField("Demo", field);
    }

    private static ClassNode type(String name, String parent) {
        var owner = new ClassNode();
        owner.name = name;
        owner.superName = parent;
        owner.access = ACC_PUBLIC;
        return owner;
    }
    private static MethodNode nativeDriver(ClassNode owner, String name, String descriptor, boolean runtimeOnly, boolean callback, boolean synchronous) {
        var include = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeInclude;");
        include.values = List.of("value", "driver.hpp");
        owner.invisibleAnnotations = List.of(include);
        var driver = new MethodNode(ACC_STATIC | ACC_NATIVE, name, descriptor, null, null);
        var annotation = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeImport;");
        annotation.values = new ArrayList<>(List.of("value", "jnative::driver", "managed", true,
                "runtimeOnly", runtimeOnly, "callbacksSynchronous", synchronous));
        if(callback) annotation.values.addAll(List.of("callbacks", List.of("Demo.callback(LDemo;)V"), "callbackKinds",
                List.<String[]>of(new String[]{"Lcom/github/xpenatan/jnative/interop/NativeImport$Invocation;", "STATIC"})));
        driver.invisibleAnnotations = List.of(annotation);
        owner.methods.add(driver);
        return driver;
    }
    private static MethodNode fieldReader(int access, String name, String descriptor, boolean text) {
        var method = new MethodNode(access, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new FieldInsnNode(GETFIELD, "Demo", "state", "I"));
        method.instructions.add(new InsnNode(POP));
        if(text) method.instructions.add(new InsnNode(ACONST_NULL));
        method.instructions.add(new InsnNode(text ? ARETURN : RETURN));
        return method;
    }
    private static Program.Method method(Program.MethodId id, MethodNode method, NativeBinding binding) {
        return new Program.Method(id, method, null, List.of(), binding, List.of());
    }
}
