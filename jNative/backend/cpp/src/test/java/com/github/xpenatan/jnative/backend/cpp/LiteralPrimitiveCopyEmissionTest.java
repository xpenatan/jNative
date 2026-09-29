package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LiteralPrimitiveCopyEmissionTest {
    @TempDir
    Path directory;

    @Test
    void readablePlatformResolutionRetainsOnlyProvedLiteralCounts() throws Exception {
        Path source = directory.resolve("LiteralCopies.java");
        Files.writeString(source, """
                public class LiteralCopies {
                    static void literal(float[] source, int from, float[] target, int to) {
                        System.arraycopy(source, from, target, to, 20);
                    }
                    static void empty(float[] source, float[] target) {
                        System.arraycopy(source, 0, target, 0, 0);
                    }
                    static void limit(float[] source, float[] target) {
                        System.arraycopy(source, 0, target, 0, 64);
                    }
                    static void one(float[] source, float[] target) {
                        System.arraycopy(source, 0, target, 0, 1);
                    }
                    static void fixedLimit(float[] source, float[] target) {
                        System.arraycopy(source, 0, target, 0, 32);
                    }
                    static void aboveFixed(float[] source, float[] target) {
                        System.arraycopy(source, 0, target, 0, 33);
                    }
                    static void references(Object[] source, Object[] target) {
                        System.arraycopy(source, 0, target, 0, 20);
                    }
                    static void variable(float[] source, float[] target, int count) {
                        System.arraycopy(source, 0, target, 0, count);
                    }
                    static void merged(float[] source, float[] target, boolean choose, int count) {
                        System.arraycopy(source, 0, target, 0, choose ? count : 20);
                    }
                    static void large(float[] source, float[] target) {
                        System.arraycopy(source, 0, target, 0, 65);
                    }
                    public static void main(String[] args) {
                        float[] source = new float[72], target = new float[72];
                        literal(source, 1, target, 2);
                        empty(source, target); limit(source, target);
                        one(source, target); fixedLimit(source, target); aboveFixed(source, target);
                        references(new Object[20], new Object[20]);
                        variable(source, target, 5); merged(source, target, true, 7);
                        large(source, target);
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "LiteralCopies", directory.resolve("out"),
                null, null, "literal-copies", BuildType.DEBUG, false);
        var sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
        String cpp = sources.get("classes/LiteralCopies.cpp");
        String report = sources.get("source-readability.tsv");
        for (String method : List.of("literal", "empty", "one", "fixedLimit", "aboveFixed", "references", "limit", "variable", "merged", "large")) {
            assertTrue(report.lines().anyMatch(line -> line.startsWith("LiteralCopies." + method + "(")
                    && line.contains("\tstructured")), report);
        }
        for (String method : List.of("limit", "aboveFixed")) {
            assertTrue(body(cpp, method).contains("::jnative::bounded_primitive_array_copy<float>("), body(cpp, method));
            assertFalse(body(cpp, method).contains("<= 64"), body(cpp, method));
        }
        for(var entry : Map.of("literal", 20, "empty", 0, "one", 1, "fixedLimit", 32).entrySet()) {
            String body = body(cpp, entry.getKey());
            assertTrue(body.contains("::jnative::bounded_primitive_array_copy<float, " + entry.getValue() + ">("), body);
            assertFalse(body.contains("<= 64"), body);
        }
        assertFalse(body(cpp, "references").contains("bounded_primitive_array_copy"), cpp);
        String variable = body(cpp, "variable");
        int guard = variable.indexOf("<= 64");
        int slow = variable.indexOf("} else {", guard);
        assertTrue(guard >= 0 && slow > guard, variable);
        assertTrue(variable.substring(guard, slow).contains("::jnative::bounded_primitive_array_copy<float>("), variable);
        assertFalse(variable.substring(guard, slow).contains("RootAddressFrame<"), variable);
        int activation = variable.indexOf("RootAddressFrame<", slow);
        int poll = variable.indexOf("::jnative::safepoint();", slow);
        assertTrue(activation > slow && poll > activation, variable);
        assertTrue(variable.indexOf("::platform_System_arraycopy_", slow) > poll, variable);
        for (String method : List.of("variable", "merged")) {
            assertTrue(body(cpp, method).contains("<= 64"), body(cpp, method));
        }
        assertTrue(body(cpp, "large").contains("<= 64"), cpp);
    }

    private static String body(String cpp, String method) {
        int start = cpp.indexOf("void LiteralCopies::" + method + "(");
        assertTrue(start >= 0, method);
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, method);
        return cpp.substring(start, end);
    }
}
