package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ArrayLengthViewsTest {
    @TempDir Path directory;

    @Test void lengthUsesOnlyAnAlreadyProvenStableView() throws Exception {
        Path source = directory.resolve("Lengths.java");
        Files.writeString(source, """
                public class Lengths {
                    static class State { int[] values; }
                    static int references(Object[] values, int rounds) {
                        int sum = 0;
                        for (int r = 0; r < rounds; r++)
                            for (int i = 0; i < values.length; i++) sum += values[i].hashCode();
                        return sum;
                    }
                    static int primitives(int[] values, int rounds) {
                        int sum = 0;
                        for (int r = 0; r < rounds; r++)
                            for (int i = 0; i < values.length; i++) sum += values[i];
                        return sum;
                    }
                    static int indexed(int[] values, int start) {
                        int sum = 0;
                        for (int i = start; i < values.length; i++) sum += values[i];
                        return sum;
                    }
                    static int shifted(int[] values) {
                        int sum = 0;
                        for (int i = 0; i < values.length; i++) sum += values[i + 1];
                        return sum;
                    }
                    static int parallel(int[] values, int[] other) {
                        int sum = 0;
                        for (int i = 0; i < values.length; i++) sum += values[i] + other[i];
                        return sum;
                    }
                    static int reassigned(int[] values, int[] other) {
                        int sum = 0;
                        for (int i = 0; i < values.length; i++) { sum += values[i]; values = other; }
                        return sum;
                    }
                    static int mutableRead(State state, int[] other) {
                        int sum = 0;
                        for (int i = 0; i < state.values.length; i++) {
                            sum += state.values[i]; state.values = other;
                        }
                        return sum;
                    }
                    public static void main(String[] args) {
                        int[] values = {1, 2}; State state = new State(); state.values = values;
                        System.out.println(references(new Object[]{state}, 2) + primitives(values, 2)
                                + indexed(values, 0) + shifted(new int[0]) + parallel(values, values)
                                + reassigned(values, new int[0]) + mutableRead(state, values));
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "Lengths", directory.resolve("out"),
                null, null, "lengths", BuildType.DEBUG, false);
        String cpp = new CppEmitter().emit(new BytecodeCompiler().compile(request), request).get("classes/Lengths.cpp");
        for (String name : List.of("references", "primitives")) {
            String body = method(cpp, name);
            assertTrue(body.contains("values_elements.length()"), body);
            assertFalse(body.contains("::jnative::array_length("), body);
            assertTrue(body.contains("const std::int32_t values_length ="), body);
        }
        assertTrue(method(cpp, "primitives").contains("values_elements.get_unchecked("), cpp);
        for (String name : List.of("primitives", "indexed")) {
            String body = method(cpp, name);
            assertTrue(body.contains("if (i >= 0)"), body);
            assertFalse(body.contains("range_iterations"), body);
            assertFalse(body.contains("valid_array_ranges"), body);
        }
        for (String name : List.of("shifted", "parallel"))
            assertTrue(method(cpp, name).contains("valid_array_ranges"), cpp);
        for (String name : List.of("reassigned", "mutableRead")) {
            String body = method(cpp, name);
            assertTrue(body.contains("::jnative::array_length("), body);
            assertFalse(body.contains("_elements.length()"), body);
        }
    }

    private static String method(String cpp, String name) {
        int start = cpp.indexOf("\nstd::int32_t Lengths::" + name + "(");
        assertTrue(start >= 0, name);
        return cpp.substring(start, cpp.indexOf("\n}", start));
    }
}
