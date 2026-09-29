package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformBindingsTest {
    @Test void supportedPlatformInventoryHasExactAnnotatedRoutes() throws Exception {
        var targets = PlatformBindings.targets();
        assertTrue(targets.size() >= 967);
        var signatures = new LinkedHashSet<String>();
        var owners = new LinkedHashSet<String>();
        for(var route : targets.values()) {
            owners.add(route.api().owner());
            signatures.add(route.api().name() + route.api().descriptor());
            assertTrue(route.binding().managed());
            assertEquals("jn_platform_bindings.hpp", route.binding().include());
            assertTrue(route.binding().symbol().startsWith("jnative::platform_"));
            try {
                Class<?> type = Class.forName(route.api().owner().replace('/', '.'));
                for(var method : type.getMethods()) signatures.add(method.getName() + Type.getMethodDescriptor(method));
                for(var constructor : type.getDeclaredConstructors()) signatures.add("<init>" + Type.getConstructorDescriptor(constructor));
            } catch(ClassNotFoundException absentOnHostJdk) { }
        }
        for(String owner : owners)
            for(String signature : signatures) {
                int descriptor = signature.indexOf('(');
                String name = signature.substring(0, descriptor), desc = signature.substring(descriptor);
                if(RuntimeLibrary.method(owner, name, desc))
                    assertNotNull(PlatformBindings.find(new Program.MethodId(owner, name, desc)), owner + "." + signature);
            }
        assertNotNull(PlatformBindings.find(new Program.MethodId("java/nio/ByteBuffer", "getInt", "(I)I")));
        assertNotNull(PlatformBindings.find(new Program.MethodId("java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")));
        assertNull(PlatformBindings.find(new Program.MethodId("java/lang/System", "getProperty", "(I)I")));
        assertNotNull(PlatformBindings.find(new Program.MethodId("[I", "clone", "()Ljava/lang/Object;")));
    }

    @Test void targetSignaturesRejectMalformedDescriptorsAndWrongReceiver() {
        for(String descriptor : List.of("(V)I", "([V)I", "()Ijunk", "(LBad)I", "(I)Vjunk"))
            assertThrows(CompilerException.class, () -> PlatformBindings.read(owner(), helper("(Ljava/lang/Object;I)I", true, "java/lang/String.test" + descriptor)));
        assertThrows(CompilerException.class, () -> PlatformBindings.read(owner(), helper("(I)I", true, "java/lang/String.test(I)I")));
        assertThrows(CompilerException.class, () -> PlatformBindings.read(owner(), helper("(Ljava/lang/Object;J)I", true, "java/lang/String.test(I)I")));
        assertThrows(CompilerException.class, () -> PlatformBindings.read(owner(), helper("(Ljava/lang/Object;)I", true, "java/lang/String.<init>()I")));
        assertThrows(CompilerException.class, () -> PlatformBindings.read(owner(), helper("()V", false, "java/lang/String.<init>()V")));
    }

    @Test void managedReferenceWideningAndConstructorReceiversAreExplicit() {
        var route = PlatformBindings.read(owner(), helper("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true,
                "java/lang/String.concat(Ljava/lang/String;)Ljava/lang/String;")).getFirst();
        assertTrue(route.instance());
        assertEquals("java/lang/String", route.api().owner());
        assertEquals(1, PlatformBindings.read(owner(), helper("(Ljava/lang/Object;)V", true, "java/lang/String.<init>()V")).size());
        assertThrows(CompilerException.class, () -> PlatformBindings.read(owner(), helper("(Ljava/lang/StringBuilder;)V", true, "java/lang/String.<init>()V")));
    }

    @Test void platformStaticFieldsUseValidatedZeroArgumentGetters() {
        assertEquals(4, PlatformBindings.fields().size());
        assertNotNull(PlatformBindings.field("java/lang/System", "out", "Ljava/io/PrintStream;"));
        assertNotNull(PlatformBindings.field("java/nio/ByteOrder", "BIG_ENDIAN", "Ljava/nio/ByteOrder;"));
        assertNull(PlatformBindings.field("java/lang/System", "out", "I"));
        for(String value : List.of("java/lang/System.out:V", "java/lang/System.out:Ibad", "java.lang.System.out:I")) {
            var method = helper("()Ljava/lang/Object;", false, "java/lang/System.test()Ljava/lang/Object;");
            method.invisibleAnnotations.getFirst().values = List.of("value", "jnative::getter", "managed", true, "staticFields", List.of(value));
            assertThrows(CompilerException.class, () -> PlatformBindings.readStaticFields(owner(), method));
        }
        var parameterized = helper("(I)Ljava/lang/Object;", false, "java/lang/System.test(I)Ljava/lang/Object;");
        parameterized.invisibleAnnotations.getFirst().values = List.of("value", "jnative::getter", "managed", true,
                "staticFields", List.of("java/lang/System.out:Ljava/io/PrintStream;"));
        assertThrows(CompilerException.class, () -> PlatformBindings.readStaticFields(owner(), parameterized));
    }

    private static ClassNode owner() {
        var owner = new ClassNode();
        owner.name = "Helper";
        var include = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeInclude;");
        include.values = List.of("value", "helper.hpp");
        owner.invisibleAnnotations = List.of(include);
        return owner;
    }
    private static MethodNode helper(String descriptor, boolean instance, String target) {
        var method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, "call", descriptor, null, null);
        var annotation = new AnnotationNode("Lcom/github/xpenatan/jnative/interop/NativeImport;");
        annotation.values = List.of("value", "jnative::call", "managed", true, "instance", instance, "targets", List.of(target));
        method.invisibleAnnotations = List.of(annotation);
        return method;
    }
}
