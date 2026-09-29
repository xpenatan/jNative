package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
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

class InitializedEntryInliningTest {
    @TempDir Path directory;

    @Test void hintsOnlySelectedInternalEntriesAndKeepsFramesAndMappings() throws Exception {
        Path source = directory.resolve("InlineEntries.java");
        Files.writeString(source, """
                public class InlineEntries {
                    static int value;
                    static class Foreign {
                        static int call(int n) { return InlineEntries.arithmetic(n); }
                        static void direct(int[] a,int[] b) { System.arraycopy(a,0,b,0,7); }
                    }
                    static int arithmetic(int n) { return n*3+7; }
                    static void cooperative(int[] a,boolean collect) { if(collect) System.gc(); value+=a[0]; }
                    static void fixed(int[] a,int[] b) { System.arraycopy(a,0,b,0,7); }
                    static void empty(int[] a,int[] b) { System.arraycopy(a,0,b,0,0); }
                    static void fixedLimit(int[] a,int[] b) { System.arraycopy(a,0,b,0,32); }
                    static void dynamicPayload(int[] a,int[] b) { System.arraycopy(a,0,b,0,33); }
                    static void references(Object[] a,Object[] b) { System.arraycopy(a,0,b,0,7); }
                    static void large(int[] a,int[] b,int n) { %s System.arraycopy(a,0,b,0,7); value=n; }
                    public static void main(String[] args) {
                        int[] a=new int[64],b=new int[64];
                        value=arithmetic(3)+Foreign.call(1); cooperative(a,false); fixed(a,b); empty(a,b);
                        fixedLimit(a,b); dynamicPayload(a,b); references(new Object[7],new Object[7]);
                        large(a,b,1); Foreign.direct(a,b);
                    }
                }
                """.formatted("n+=7;".repeat(140)));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "InlineEntries", directory.resolve("out"),
                null, null, "initialized-inlining", BuildType.DEBUG, true);
        var program = new BytecodeCompiler().compile(request);
        int largeOperations = 0;
        for(var instruction : program.methods().get(new MethodId("InlineEntries", "large", "([I[II)V")).bytecode().instructions)
            if(instruction.getOpcode() >= 0) largeOperations++;
        assertTrue(largeOperations > 128, "Large direct-copy fixture must exceed initialized-entry budget");
        var sources = new CppEmitter().emit(program, request);
        String cpp = sources.get("classes/InlineEntries.cpp"), header = sources.get("classes/InlineEntries.hpp");
        List<String> hinted = List.of("arithmetic", "cooperative", "dynamicPayload");
        for(String name : List.of("arithmetic", "cooperative", "fixed", "empty", "fixedLimit", "dynamicPayload", "references")) {
            String initialized = "initialized_" + name;
            String ordinary = body(cpp, name), variant = body(cpp, initialized);
            assertFalse(ordinary.startsWith("JNATIVE_INLINE_ENTRY"), ordinary);
            assertTrue(ordinary.contains("ensure_initialized()"), ordinary);
            assertEquals(hinted.contains(name), variant.startsWith("JNATIVE_INLINE_ENTRY "), variant);
            String declaration = header.lines().filter(line -> line.contains(" " + initialized + "("))
                    .findFirst().orElseThrow();
            assertEquals(hinted.contains(name), declaration.contains("JNATIVE_INLINE_ENTRY "), declaration);
            assertFalse(variant.contains("ensure_initialized()"), variant);
            assertEquals(1, variant.lines().filter(line -> line.contains("JavaFrame java_frame(")).count(), variant);
            assertFalse(ordinary.contains("bounded_primitive_array_copy_inline"), ordinary);
            if(List.of("fixed", "empty", "fixedLimit").contains(name))
                assertTrue(variant.contains("::jnative::bounded_primitive_array_copy_inline<"), variant);
            else assertFalse(variant.contains("bounded_primitive_array_copy_inline"), variant);
        }
        String cooperative = body(cpp, "initialized_cooperative");
        assertTrue(cooperative.contains("RootAddressFrame<"), cooperative);
        assertTrue(cooperative.indexOf("a.address()") < cooperative.indexOf("::jnative::safepoint()"), cooperative);
        String large = body(cpp, "large");
        assertTrue(large.contains("::jnative::bounded_primitive_array_copy<std::int32_t, 7>"), large);
        assertFalse(large.contains("bounded_primitive_array_copy_inline"), large);
        assertFalse(header.contains(" initialized_large("), header);
        Map<String, Object> map = Json.object(Json.read(sources.get("source-map.json")));
        for(Object value : Json.array(map.get("classes"))) {
            Map<String, Object> owner = Json.object(value);
            if("InlineEntries$Foreign".equals(owner.get("javaInternalName"))) {
                String foreign = sources.get((String)owner.get("cppFile"));
                assertTrue(foreign.contains("InlineEntries::arithmetic("), foreign);
                assertFalse(foreign.contains("InlineEntries::initialized_arithmetic("), foreign);
                assertTrue(foreign.contains("::jnative::bounded_primitive_array_copy<std::int32_t, 7>"), foreign);
                assertFalse(foreign.contains("bounded_primitive_array_copy_inline"), foreign);
                assertFalse(owner.get("cppFile").equals("classes/InlineEntries.cpp"));
            }
            List<String> lines = sources.get((String)owner.get("cppFile")).lines().toList();
            for(Object entry : Json.array(owner.get("methods"))) {
                Map<String, Object> method = Json.object(entry);
                if(!"initialized".equals(method.get("entry"))) continue;
                int start = ((Number)method.get("cppLineStart")).intValue(), end = ((Number)method.get("cppLineEnd")).intValue();
                String mapped = String.join("\n", lines.subList(start - 1, end));
                assertTrue(mapped.contains("::" + method.get("cppMethod") + "("), mapped);
                assertEquals(1, mapped.lines().filter(line -> line.contains("JavaFrame java_frame(")).count(), mapped);
            }
        }
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* InlineEntries::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        return cpp.substring(declaration.start(), cpp.indexOf("\n}", declaration.end()));
    }
}
