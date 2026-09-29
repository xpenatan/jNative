package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeBindingTest {
    @TempDir Path directory;

    @Test void nativeRequiresExactAnnotationIncludingFormerIntrinsics() {
        ClassNode owner = owner("java/lang/Math", false);
        MethodNode nativeMethod = method("sqrt", "(D)D");
        nativeMethod.invisibleAnnotations = List.of(new AnnotationNode("Lother/NativeImport;"));
        var failure = assertThrows(CompilerException.class, () -> NativeBinding.read(owner, nativeMethod));
        assertTrue(failure.getMessage().contains("NativeImport is required"));
        assertTrue(failure.getMessage().contains("java.lang.Math.sqrt(D)D"));
    }

    @Test void includeRequiredAndExternalContractPreserved() {
        MethodNode method = imported("call", "(Ljava/lang/Object;)Ljava/lang/Object;", "external_call", false);
        assertTrue(assertThrows(CompilerException.class, () -> NativeBinding.read(owner("Demo", false), method))
                .getMessage().contains("NativeInclude is required"));
        NativeBinding binding = NativeBinding.read(owner("Demo", true), method);
        assertFalse(binding.managed());
        assertFalse(binding.leaf());
        assertFalse(binding.bounded());
        method.invisibleAnnotations.getFirst().values.addAll(List.of("leaf", true));
        assertThrows(CompilerException.class, () -> NativeBinding.read(owner("Demo", true), method));
    }

    @Test void qualifiedSymbolsAndEffectsAreValidated() {
        ClassNode owner = owner("Demo", true);
        MethodNode method = imported("call", "(I)I", "jnative::helper", true);
        method.invisibleAnnotations.getFirst().values.addAll(List.of("bounded", true));
        assertTrue(NativeBinding.read(owner, method).bounded());
        method.invisibleAnnotations.getFirst().values.addAll(List.of("callbacks", List.of("Demo.next(I)I")));
        assertThrows(CompilerException.class, () -> NativeBinding.read(owner, method));
        assertThrows(CompilerException.class, () -> NativeBinding.read(owner, imported("call", "(I)I", "jnative::helper<int>", true)));
        assertThrows(CompilerException.class, () -> NativeBinding.read(owner, imported("call", "(I)I", "jnative::helper", false)));
    }

    @Test void malformedCallbackMetadataIsRejectedWithTheBindingSignature() {
        for(String callback : List.of("Demo.next(V)I", "Demo.next()Ijunk", "Demo.next([V)I", "Demo.next(LBad)I")) {
            MethodNode method = imported("driver", "(LDemo;)I", "jnative::driver", true);
            method.invisibleAnnotations.getFirst().values.addAll(List.of("callbacks", List.of(callback)));
            var failure = assertThrows(CompilerException.class, () -> NativeBinding.read(owner("Demo", true), method));
            assertTrue(failure.getMessage().contains("Demo.driver(LDemo;)I"));
        }
    }

    @Test void metadataDescriptorsRespectJvmDimensionAndParameterUnitLimits() {
        assertTrue(NativeBinding.validMethodDescriptor("(" + "I".repeat(255) + ")V"));
        assertFalse(NativeBinding.validMethodDescriptor("(" + "I".repeat(256) + ")V"));
        assertFalse(NativeBinding.validMethodDescriptor("(" + "J".repeat(128) + ")V"));
        assertTrue(NativeBinding.validValueDescriptor("[".repeat(255) + "I"));
        assertFalse(NativeBinding.validValueDescriptor("[".repeat(256) + "I"));
        assertFalse(NativeBinding.validMethodDescriptor("()[" + "[".repeat(255) + "I"));
        MethodNode method = imported("driver", "()V", "jnative::driver", true);
        method.invisibleAnnotations.getFirst().values.addAll(List.of("fields", List.of("Demo.array:" + "[".repeat(256) + "I")));
        assertThrows(CompilerException.class, () -> NativeBinding.read(owner("Demo", true), method));
    }

    @Test void compilerRejectsUnannotatedNativeAtReachableCall() throws Exception {
        ClassNode owner = owner("Demo", false);
        owner.methods.add(method("call", "()V"));
        addMain(owner, "call", "()V", false);
        write(owner);
        var failure = assertThrows(CompilerException.class, () -> new BytecodeCompiler().compile(request()));
        assertTrue(failure.getMessage().contains("NativeImport is required"));
        assertTrue(failure.getMessage().contains("Demo.call()V"));
    }

    @Test void hiddenCallbacksSurviveLinkingAndInvalidReceiverIsRejected() throws Exception {
        ClassNode owner = owner("Demo", true);
        MethodNode driver = imported("driver", "(LDemo;)I", "jnative::driver", true);
        driver.invisibleAnnotations.getFirst().values.addAll(List.of("callbacks", List.of("Demo.next()I")));
        owner.methods.add(driver);
        MethodNode next = new MethodNode(Opcodes.ACC_PUBLIC, "next", "()I", null, null);
        next.instructions.add(new InsnNode(Opcodes.ICONST_1));
        next.instructions.add(new InsnNode(Opcodes.IRETURN));
        next.maxStack = 1;
        next.maxLocals = 1;
        owner.methods.add(next);
        addMain(owner, "driver", "(LDemo;)I", true);
        write(owner);
        Program program = new BytecodeCompiler().compile(request());
        assertTrue(program.methods().containsKey(new Program.MethodId("Demo", "next", "()I")));
        var binding = program.methods().get(new Program.MethodId("Demo", "driver", "(LDemo;)I")).nativeBinding();
        assertEquals("jnative::driver", binding.symbol());
        assertEquals(new Program.MethodId("Demo", "next", "()I"), binding.callbacks().getFirst().method());
        driver.invisibleAnnotations.getFirst().values.addAll(List.of("callbackReceivers", List.of(1)));
        write(owner);
        assertThrows(CompilerException.class, () -> new BytecodeCompiler().compile(request()));
    }

    @Test void duplicateExternalSymbolsAreRejectedButSharedManagedSymbolsAreSupported() throws Exception {
        ClassNode owner = owner("Demo", true);
        owner.methods.add(imported("first", "()I", "shared", false));
        owner.methods.add(imported("second", "()I", "shared", false));
        addMain(owner, "first", "()I", false);
        MethodNode main = owner.methods.getLast();
        InsnList second = new InsnList();
        second.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "Demo", "second", "()I", false));
        second.add(new InsnNode(Opcodes.POP));
        main.instructions.insertBefore(main.instructions.getLast(), second);
        write(owner);
        assertTrue(assertThrows(CompilerException.class, () -> new BytecodeCompiler().compile(request()))
                .getMessage().contains("Duplicate native symbol shared"));
        owner.methods.set(0, imported("first", "()I", "jnative::shared", true));
        owner.methods.set(1, imported("second", "()I", "jnative::shared", true));
        write(owner);
        assertEquals(3, new BytecodeCompiler().compile(request()).methods().size());
    }

    @Test void nativeTypeDependenciesRetainHierarchyWithoutInitialization() throws Exception {
        ClassNode owner = owner("Demo", true);
        MethodNode driver = imported("driver", "()V", "jnative::driver", true);
        driver.invisibleAnnotations.getFirst().values.addAll(List.of("types", List.of("java/lang/NumberFormatException")));
        owner.methods.add(driver);
        addMain(owner, "driver", "()V", false);
        write(owner);
        Program program = new BytecodeCompiler().compile(request());
        assertTrue(program.classes().containsKey("java/lang/NumberFormatException"));
        assertEquals("java/lang/IllegalArgumentException", program.classes().get("java/lang/NumberFormatException").superName);
        assertFalse(program.methods().keySet().stream().anyMatch(id -> id.owner().equals("java/lang/NumberFormatException")));
        for(String type : List.of("java.lang.NumberFormatException", "Ljava/lang/NumberFormatException;", "[Ljava/lang/Object;", "java/lang/../Error")) {
            MethodNode invalid = imported("driver", "()V", "jnative::driver", true);
            invalid.invisibleAnnotations.getFirst().values.addAll(List.of("types", List.of(type)));
            assertTrue(assertThrows(CompilerException.class, () -> NativeBinding.read(owner, invalid))
                    .getMessage().contains("Demo.driver()V"));
        }
        driver.invisibleAnnotations.getFirst().values = new ArrayList<>(List.of("value", "jnative::driver", "managed", true,
                "types", List.of("no/such/Exception")));
        write(owner);
        assertThrows(CompilerException.class, () -> new BytecodeCompiler().compile(request()));
    }

    @Test void scalarInliningRequiresTrivialSuperclassAndInterfaceInitialization() {
        ClassNode helper = owner("Helper", true);
        assertTrue(NativeBinding.trivialInitialization(helper));
        helper.superName = "BaseWithInitialization";
        assertFalse(NativeBinding.trivialInitialization(helper));
        helper.superName = "java/lang/Object";
        helper.interfaces.add("DefaultInterfaceWithInitialization");
        assertFalse(NativeBinding.trivialInitialization(helper));
        helper.interfaces.clear();
        helper.methods.add(new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null));
        assertFalse(NativeBinding.trivialInitialization(helper));
    }

    @Test void synchronousCallbackPromiseDefaultsConservativeAndRequiresManagedMode() {
        ClassNode owner = owner("Demo", true);
        MethodNode method = imported("driver", "(Ljava/lang/Object;)V", "jnative::driver", true);
        assertFalse(NativeBinding.read(owner, method).callbacksSynchronous());
        method.invisibleAnnotations.getFirst().values.addAll(List.of("callbacksSynchronous", true));
        assertTrue(NativeBinding.read(owner, method).callbacksSynchronous());
        MethodNode external = imported("driver", "()V", "external_driver", false);
        external.invisibleAnnotations.getFirst().values.addAll(List.of("callbacksSynchronous", true));
        assertThrows(CompilerException.class, () -> NativeBinding.read(owner, external));
    }

    @Test void nativeRootingPromiseDefaultsConservativeAndRequiresManagedMode() {
        ClassNode owner = owner("Demo", true);
        MethodNode method = imported("driver", "(Ljava/lang/Object;)I", "jnative::driver", true);
        assertFalse(NativeBinding.read(owner, method).managesRoots());
        method.invisibleAnnotations.getFirst().values.addAll(List.of("managesRoots", true));
        NativeBinding binding = NativeBinding.read(owner, method);
        assertTrue(binding.managesRoots());
        assertFalse(binding.bounded());
        assertFalse(binding.boundedAccess());
        MethodNode external = imported("driver", "(Ljava/lang/Object;)I", "external_driver", false);
        external.invisibleAnnotations.getFirst().values.addAll(List.of("managesRoots", true));
        var failure = assertThrows(CompilerException.class, () -> NativeBinding.read(owner, external));
        assertTrue(failure.getMessage().contains("managesRoots requires managed=true"));
        assertTrue(failure.getMessage().contains("Demo.driver(Ljava/lang/Object;)I"));
    }

    @Test void registeredReflectionRequiresManagedModeAndConservativeEffects() {
        ClassNode owner = owner("Demo", true);
        MethodNode method = imported("driver", "()J", "jnative::driver", true);
        assertFalse(NativeBinding.read(owner, method).registeredReflection());
        method.invisibleAnnotations.getFirst().values.addAll(List.of("registeredReflection", true));
        assertTrue(NativeBinding.read(owner, method).registeredReflection());
        MethodNode external = imported("driver", "()J", "external_driver", false);
        external.invisibleAnnotations.getFirst().values.addAll(List.of("registeredReflection", true));
        var failure = assertThrows(CompilerException.class, () -> NativeBinding.read(owner, external));
        assertTrue(failure.getMessage().contains("registeredReflection requires managed=true"));
        assertTrue(failure.getMessage().contains("Demo.driver()J"));
        for(String promise : List.of("bounded", "boundedAccess")) {
            MethodNode bounded = imported("driver", "()J", "jnative::driver", true);
            bounded.invisibleAnnotations.getFirst().values.addAll(List.of("registeredReflection", true, promise, true,
                    "targets", List.of("java/lang/System.nanoTime()J")));
            assertTrue(assertThrows(CompilerException.class, () -> NativeBinding.read(owner, bounded))
                    .getMessage().contains("registeredReflection cannot use bounded"));
        }
    }

    @Test void packagedNativeDeclarationsAllHaveValidatedBindings() {
        int count = 0;
        for(String name : ClassLibrary.classes()) {
            var node = new ClassNode();
            new ClassReader(ClassLibrary.read(name)).accept(node, ClassReader.SKIP_CODE);
            for(MethodNode method : node.methods)
                if((method.access & Opcodes.ACC_NATIVE) != 0) {
                    assertNotNull(NativeBinding.read(node, method), name + "." + method.name + method.desc);
                    ++count;
                }
        }
        assertTrue(count > 50);
    }

    private NativeBuildRequest request() {
        return new NativeBuildRequest(List.of(directory), "Demo", directory.resolve("out"), null, null, "demo", BuildType.DEBUG, false);
    }
    private void write(ClassNode owner) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        owner.accept(writer);
        Files.write(directory.resolve(owner.name + ".class"), writer.toByteArray());
    }
    private static void addMain(ClassNode owner, String target, String desc, boolean receiver) {
        MethodNode main = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        if(receiver) main.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        main.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner.name, target, desc, false));
        if(Type.getReturnType(desc).getSort() != Type.VOID) main.instructions.add(new InsnNode(Opcodes.POP));
        main.instructions.add(new InsnNode(Opcodes.RETURN));
        main.maxStack = 1;
        main.maxLocals = 1;
        owner.methods.add(main);
    }
    private static ClassNode owner(String name, boolean include) {
        ClassNode owner = new ClassNode();
        owner.version = Opcodes.V17;
        owner.access = Opcodes.ACC_PUBLIC;
        owner.name = name;
        owner.superName = "java/lang/Object";
        if(include) {
            var annotation = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeInclude;");
            annotation.values = new ArrayList<>(List.of("value", "jn_classlib.hpp"));
            owner.invisibleAnnotations = List.of(annotation);
        }
        return owner;
    }
    private static MethodNode method(String name, String desc) {
        return new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, name, desc, null, null);
    }
    private static MethodNode imported(String name, String desc, String symbol, boolean managed) {
        MethodNode method = method(name, desc);
        var annotation = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeImport;");
        annotation.values = new ArrayList<>(List.of("value", symbol, "managed", managed));
        method.invisibleAnnotations = List.of(annotation);
        return method;
    }
}
