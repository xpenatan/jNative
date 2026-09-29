package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.tree.MethodInsnNode;

class InitializedStaticEntryTest {
    @TempDir Path directory;

    @Test void sameDeclaringOwnerVariantsKeepOrdinaryEntryFramesAndSourceIdentities() throws Exception {
        Path source = directory.resolve("InitializedEntries.java");
        Files.writeString(source, """
                public class InitializedEntries extends Parent {
                    static int initialized_step;
                    static int initialized_step_2(int x) { return x-1; }
                    static class initialized_step_3 {}
                    static class Foreign {
                        static int read(int[] a) { return a[0]; }
                        static int inside(int[] a) { return read(a); }
                    }
                    static int step(int x) { return x+1; }
                    static int chain(int x) { return step(x)+1; }
                    static int read(int[] a) { return a[0]; }
                    static int[] identity(int[] a) { return a; }
                    static int safeCaller(int[] a) { return read(identity(a)); }
                    static int foreign(int[] a) { return Foreign.read(a)+Foreign.inside(a); }
                    static int subclass(int x) { return InitializedEntries.parentStep(x); }
                    int instanceCaller(int x) { return step(x); }
                    static synchronized int monitored(int x) { return x; }
                    static int handled(int[] a) { try { return a[0]; } catch(RuntimeException failure) { return -1; } }
                    static int expensive(int x) { %s return x; }
                    public static void main(String[] args) {
                        int[] a={3};
                        System.out.println(chain(2)+safeCaller(a)+foreign(a)+subclass(3)+Parent.inside(3)
                            +new InitializedEntries().instanceCaller(4)+monitored(2)+handled(a)+expensive(3)
                            +initialized_step_2(3)+new initialized_step_3().hashCode());
                    }
                }
                class Parent {
                    static int parentStep(int x) { return x+2; }
                    static int inside(int x) { return InitializedEntries.parentStep(x); }
                }
                """.formatted("x+=7;".repeat(129)));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "InitializedEntries", directory.resolve("out"),
                null, null, "initialized-entries", BuildType.DEBUG, true);
        Program program = new BytecodeCompiler().compile(request);
        // Preserve a legal subclass qualifier to independently verify the actual owner.
        for(var instruction : program.methods().get(new MethodId("Parent", "inside", "(I)I")).bytecode().instructions)
            if(instruction instanceof MethodInsnNode call && call.name.equals("parentStep"))
                call.owner = "InitializedEntries";
        var sources = new CppEmitter().emit(program, request);
        String cpp = sources.get("classes/InitializedEntries.cpp");
        String header = sources.get("classes/InitializedEntries.hpp");
        String internalRead = variantName(sources.get("source-map.json"), "InitializedEntries", "read");
        assertTrue(header.contains("initialized_step_4("), header);
        assertTrue(body(cpp, "InitializedEntries", "chain").contains("initialized_step_4("), cpp);
        assertTrue(body(cpp, "InitializedEntries", "safeCaller").contains("initialized_identity("), cpp);
        assertTrue(body(cpp, "InitializedEntries", "safeCaller").contains(internalRead + "("), cpp);
        for(String name : List.of("step", "chain", "read", "identity")) {
            String ordinary = body(cpp, "InitializedEntries", name);
            String internal = body(cpp, "InitializedEntries", variantName(sources.get("source-map.json"), "InitializedEntries", name));
            assertTrue(ordinary.contains("ensure_initialized()"), ordinary);
            assertFalse(internal.contains("ensure_initialized()") || internal.contains("initialization_complete"), internal);
            assertEquals(1, ordinary.lines().filter(line -> line.contains("JavaFrame java_frame(")).count(), ordinary);
            assertEquals(1, internal.lines().filter(line -> line.contains("JavaFrame java_frame(")).count(), internal);
            assertTrue(internal.contains("\"InitializedEntries." + name + "\""), internal);
        }
        assertTrue(body(cpp, "InitializedEntries", "read").contains("RootFrame<1> initialization_roots"), cpp);
        assertFalse(body(cpp, "InitializedEntries", internalRead).contains("RootFrame<"), cpp);
        assertFalse(body(cpp, "InitializedEntries", "foreign").contains("::initialized_"), cpp);
        assertTrue(body(cpp, "InitializedEntries", "instanceCaller").contains("::step("), cpp);
        assertFalse(body(cpp, "InitializedEntries", "subclass").contains("initialized_parentStep("), cpp);
        assertTrue(body(sources.get("classes/Parent.cpp"), "Parent", "inside").contains("Parent::initialized_parentStep("));
        for(String name : List.of("expensive", "handled", "monitored", "instanceCaller"))
            assertFalse(header.contains(" initialized_" + name + "("), header);
        Map<String, Object> map = Json.object(Json.read(sources.get("source-map.json")));
        for(Object value : Json.array(map.get("classes"))) {
            Map<String, Object> owner = Json.object(value);
            String path = (String)owner.get("cppFile");
            List<String> lines = sources.get(path).lines().toList();
            for(Object entry : Json.array(owner.get("methods"))) {
                Map<String, Object> method = Json.object(entry);
                if(!"initialized".equals(method.get("entry"))) continue;
                int start = ((Number)method.get("cppLineStart")).intValue();
                int end = ((Number)method.get("cppLineEnd")).intValue();
                String mapped = String.join("\n", lines.subList(start - 1, end));
                assertTrue(mapped.contains("::" + method.get("cppMethod") + "("), mapped);
                String javaMethod = (String)method.get("javaMethod");
                assertTrue(Json.array(owner.get("methods")).stream().map(Json::object)
                        .anyMatch(original -> javaMethod.equals(original.get("javaMethod")) && !original.containsKey("entry")), javaMethod);
            }
        }
    }

    private static String body(String cpp, String owner, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* " + Pattern.quote(owner) + "::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), owner + "." + name);
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }

    private static String variantName(String sourceMap, String owner, String name) {
        for(Object value : Json.array(Json.object(Json.read(sourceMap)).get("classes"))) {
            Map<String, Object> type = Json.object(value);
            if(!owner.equals(type.get("javaInternalName"))) continue;
            for(Object entry : Json.array(type.get("methods"))) {
                Map<String, Object> method = Json.object(entry);
                if("initialized".equals(method.get("entry"))
                        && ((String)method.get("javaMethod")).startsWith(owner + "." + name + "("))
                    return (String)method.get("cppMethod");
            }
        }
        throw new AssertionError(owner + "." + name);
    }
}
