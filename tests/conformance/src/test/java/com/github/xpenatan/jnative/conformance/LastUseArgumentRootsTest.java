package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.ClassNode;

class LastUseArgumentRootsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void handoffProtectsDetachedArgumentsAndKeepsOtherCollectionRoots(BuildType buildType) throws Exception {
        Path source = temporary.resolve("LastUseArgumentRoots.java");
        try(var input = getClass().getResourceAsStream("/fixtures/LastUseArgumentRoots.java")) {
            assertNotNull(input);
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        forceIrreducibleFallback(classes);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "LastUseArgumentRoots"),
                Duration.ofSeconds(90));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("LastUseArgumentRoots")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5));
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                "LastUseArgumentRoots");
        String positive = body(cpp, "positive");
        assertTrue(positive.contains("::jnative::Object* array"), positive);
        assertFalse(positive.contains("FrameRoot<> array"), positive);
        assertTrue(positive.contains("::initialized_cold(array, collect)"), positive);
        String reassign = body(cpp, "reassignCaller");
        assertTrue(reassign.contains("::initialized_reassigned(array, collect)"), reassign);
        assertFalse(reassign.contains("FrameRoot<> array"), reassign);
        String reassigned = body(cpp, "initialized_reassigned");
        assertFalse(reassigned.contains("::ensure_initialized()") || reassigned.contains("initialization_complete"), reassigned);
        assertTrue(reassigned.contains("RootValue<> a"), reassigned);
        assertTrue(reassigned.indexOf("a.address()") < reassigned.indexOf("::jnative::safepoint()"), reassigned);
        assertTrue(body(cpp, "recurseCaller").contains("FrameRoot<> array("), body(cpp, "recurseCaller"));
        assertFalse(cpp.contains("::initialized_recursive("), cpp);
        String inherited = body(cpp, "inheritedCaller");
        String inheritedVariant = variantName(generated.request().generatedSourcesDirectory(),
                "LastUseArgumentRoots$Parent", "accept");
        assertFalse(inherited.contains("::" + inheritedVariant + "("), inherited);
        String parent = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                "LastUseArgumentRoots$Parent");
        assertTrue(body(parent, "LastUseArgumentRoots_Parent", "inside")
                .contains("::" + inheritedVariant + "("), parent);
        String child = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                "LastUseArgumentRoots$Child");
        assertFalse(child.contains("::" + inheritedVariant + "("), child);
        for(String name : List.of("firstUseCall", "failingInitializationCall")) {
            String entry = body(cpp, name);
            assertTrue(entry.contains("::jnative::Object* array"), entry);
            assertFalse(entry.contains("FrameRoot<> array"), entry);
        }
        for(String name : List.of("FirstUse", "FailingInitialization")) {
            String entry = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                    "LastUseArgumentRoots$" + name);
            int declaration = entry.indexOf("::accept(");
            String accept = entry.substring(declaration, entry.indexOf("\n}", declaration));
            assertTrue(accept.contains("RootValue<> a"), accept);
            assertTrue(accept.contains("RootAddressFrame<"), accept);
            assertTrue(accept.indexOf("a.address()") < accept.indexOf("::ensure_initialized()"), accept);
            if(name.equals("FirstUse")) {
                String variant = variantName(generated.request().generatedSourcesDirectory(),
                        "LastUseArgumentRoots$FirstUse", "accept");
                String initialized = body(entry, "LastUseArgumentRoots_FirstUse", variant);
                assertFalse(initialized.contains("::ensure_initialized()") || initialized.contains("initialization_complete"), initialized);
                assertTrue(initialized.contains("RootValue<> a"), initialized);
                assertTrue(initialized.indexOf("a.address()") < initialized.indexOf("::jnative::safepoint()"), initialized);
                String initializer = entryName(generated.request().generatedSourcesDirectory(),
                        "LastUseArgumentRoots$FirstUse", "initialize", false);
                assertTrue(body(entry, "LastUseArgumentRoots_FirstUse", initializer).contains("::" + variant + "("), entry);
            }
        }
        String rows = body(cpp, "rowCalls");
        assertTrue(rows.contains("::jnative::Object* element"), rows);
        assertFalse(rows.contains("FrameRoot<> element"), rows);
        assertTrue(rows.contains("RootValue<> rows"), rows);
        assertTrue(rows.contains("rows.address()"), rows);
        assertTrue(rows.indexOf("RootAddressFrame<", rows.indexOf("if (i"))
                < rows.indexOf("::jnative::LoopSafepoint"), rows);
        String holder = body(cpp, "holderCall");
        assertTrue(holder.contains("::jnative::Object* holder_value"), holder);
        assertFalse(holder.contains("FrameRoot<> holder_value"), holder);
        for(String name : List.of("laterArgument", "inlineScalar", "monitored", "wrapper", "mixedDispatch"))
            assertTrue(body(cpp, name).contains("FrameRoot<> array("), body(cpp, name));
        assertTrue(body(cpp, "caught").contains("FrameRoot<> array_2("), body(cpp, "caught"));
        assertTrue(body(cpp, "liveAfter").contains("FrameRoot<> a("), body(cpp, "liveAfter"));
        assertTrue(body(cpp, "liveAlias").contains("FrameRoot<> object("), body(cpp, "liveAlias"));
        assertTrue(body(cpp, "liveAlias").contains("FrameRoot<> alias("), body(cpp, "liveAlias"));
        assertTrue(body(cpp, "rejectedRow").contains("FrameRoot<> element("), body(cpp, "rejectedRow"));
        assertTrue(body(cpp, "nativeCall").contains("FrameRoot<> array"), body(cpp, "nativeCall"));
        assertTrue(body(cpp, "fallbackCall").contains("FrameRoot<> array("), body(cpp, "fallbackCall"));
        assertTrue(cpp.contains("// Low-level fallback:"), cpp);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(120)));
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(120), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String name) {
        return body(cpp, "LastUseArgumentRoots", name);
    }

    private static String body(String cpp, String owner, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* " + Pattern.quote(owner) + "::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        return cpp.substring(declaration.start(), cpp.indexOf("\n}", declaration.end()));
    }

    private static String variantName(Path generated, String owner, String name) throws Exception {
        return entryName(generated, owner, name, true);
    }

    private static String entryName(Path generated, String owner, String name, boolean initialized) throws Exception {
        for(Object value : Json.array(Json.object(Json.read(Files.readString(generated.resolve("source-map.json")))).get("classes"))) {
            Map<String, Object> type = Json.object(value);
            if(!owner.equals(type.get("javaInternalName"))) continue;
            for(Object entry : Json.array(type.get("methods"))) {
                Map<String, Object> method = Json.object(entry);
                if(initialized == "initialized".equals(method.get("entry"))
                        && ((String)method.get("javaMethod")).startsWith(owner + "." + name + "("))
                    return (String)method.get("cppMethod");
            }
        }
        throw new AssertionError(owner + "." + name);
    }

    private static void forceIrreducibleFallback(Path classes) throws Exception {
        Path file = classes.resolve("LastUseArgumentRoots.class");
        var owner = new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(owner, 0);
        var method = owner.methods.stream().filter(candidate -> candidate.name.equals("fallback"))
                .findFirst().orElseThrow();
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        Label first = new Label(), second = new Label(), end = new Label();
        method.visitVarInsn(ILOAD, 1);
        method.visitJumpInsn(IFLE, end);
        method.visitVarInsn(ILOAD, 1);
        method.visitInsn(ICONST_1);
        method.visitInsn(IAND);
        method.visitJumpInsn(IFNE, second);
        method.visitLabel(first);
        method.visitMethodInsn(INVOKESTATIC, "java/lang/System", "gc", "()V", false);
        method.visitFieldInsn(GETSTATIC, owner.name, "total", "I");
        method.visitVarInsn(ALOAD, 0);
        method.visitInsn(ICONST_0);
        method.visitInsn(IALOAD);
        method.visitInsn(IADD);
        method.visitFieldInsn(PUTSTATIC, owner.name, "total", "I");
        method.visitIincInsn(1, -1);
        method.visitVarInsn(ILOAD, 1);
        method.visitJumpInsn(IFLE, end);
        method.visitLabel(second);
        method.visitIincInsn(1, -1);
        method.visitVarInsn(ILOAD, 1);
        method.visitJumpInsn(IFGT, first);
        method.visitLabel(end);
        method.visitInsn(RETURN);
        method.visitMaxs(3, 2);
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        Files.write(file, writer.toByteArray());
    }
}
