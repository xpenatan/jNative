package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TypedPrimitiveArrayCopyTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void typedCopiesPreserveRangesOverlapBitsAndArgumentEvaluation(BuildType buildType)
            throws Exception {
        List<String> types = List.of("boolean", "byte", "char", "short", "int", "long", "float", "double");
        List<String> nativeTypes = List.of("std::uint8_t", "std::int8_t", "std::uint16_t", "std::int16_t",
                "std::int32_t", "std::int64_t", "float", "double");
        List<String> seeds = List.of(
                "i % 3 == 0",
                "(byte) (i * 13 - 128)",
                "(char) (i * 991)",
                "(short) (i * 1777 - 32768)",
                "i == 0 ? Integer.MIN_VALUE : i == 1 ? Integer.MAX_VALUE : i * 1234567",
                "i == 0 ? Long.MIN_VALUE : i == 1 ? Long.MAX_VALUE : i * 1234567890123L",
                "Float.intBitsToFloat(i == 0 ? 0x80000000 : i == 1 ? 0x7fc00007 : i == 2 ? 0x7fc12345 : i == 3 ? 0 : 0x3f800000 + i * 4096)",
                "Double.longBitsToDouble(i == 0 ? 0x8000000000000000L : i == 1 ? 0x7ff8000000000007L : i == 2 ? 0x7ff8123456789abcL : i == 3 ? 0 : 0x3ff0000000000000L + i * 4096L)");
        StringBuilder methods = new StringBuilder();
        StringBuilder calls = new StringBuilder();
        for (int kind = 0; kind < types.size(); kind++) {
            String type = types.get(kind);
            methods.append("""
                    static String copy%1$s(%1$s[] source, int from, %1$s[] target, int to, int count) {
                        try {
                            System.arraycopy(source, from, target, to, count);
                            return "copied";
                        } catch (RuntimeException error) { return error.getClass().getName(); }
                    }
                    static void run%1$s() {
                        %1$s[] source = new %1$s[70];
                        for (int i = 0; i < source.length; i++) source[i] = %2$s;
                        for (int count : COUNTS) {
                            %1$s[] target = new %1$s[70];
                            System.out.println("%1$s:" + count + ":" + copy%1$s(source, 0, target, 0, count));
                            System.gc();
                            System.out.println(snapshot(target));
                        }
                        %1$s[] shared = source.clone();
                        System.out.println(copy%1$s(shared, 0, shared, 1, 20));
                        System.gc(); System.out.println(snapshot(shared));
                        System.out.println(copy%1$s(shared, 1, shared, 0, 20));
                        System.gc(); System.out.println(snapshot(shared));
                        System.out.println(copy%1$s(shared, 70, shared, 70, 0));
                        System.out.println(copy%1$s(null, -1, null, -1, -1));
                        System.out.println(copy%1$s(null, -1, shared, -1, 0));
                        System.out.println(copy%1$s(shared, -1, null, -1, 1));
                        System.out.println(copy%1$s(shared, -1, shared, 0, 1));
                        System.out.println(copy%1$s(shared, 0, shared, -1, 1));
                        System.out.println(copy%1$s(shared, 69, shared, 0, 2));
                        System.out.println(copy%1$s(shared, 0, shared, 69, 2));
                        System.out.println(copy%1$s(shared, Integer.MAX_VALUE, shared, 0, 0));
                        System.out.println(copy%1$s(shared, 0, shared, Integer.MIN_VALUE, 0));
                        System.gc(); System.out.println(snapshot(shared));
                    }
                    """.formatted(type, seeds.get(kind)));
            calls.append("run").append(type).append("();\n");
        }
        Path source = temporary.resolve("TypedCopies.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                import java.util.Arrays;
                public class TypedCopies {
                    static final int[] COUNTS = {0, 1, 20, 64, 65, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};
                    static int events;
                    static String snapshot(Object array) {
                        if (array instanceof boolean[] values) return Arrays.toString(values);
                        if (array instanceof byte[] values) return Arrays.toString(values);
                        if (array instanceof short[] values) return Arrays.toString(values);
                        if (array instanceof int[] values) return Arrays.toString(values);
                        if (array instanceof long[] values) return Arrays.toString(values);
                        if (array instanceof char[] values) {
                            int[] bits = new int[values.length];
                            for (int i = 0; i < values.length; i++) bits[i] = values[i];
                            return Arrays.toString(bits);
                        }
                        if (array instanceof float[] values) {
                            int[] bits = new int[values.length];
                            for (int i = 0; i < values.length; i++) bits[i] = Float.floatToRawIntBits(values[i]);
                            return Arrays.toString(bits);
                        }
                        double[] values = (double[]) array;
                        long[] bits = new long[values.length];
                        for (int i = 0; i < values.length; i++) bits[i] = Double.doubleToRawLongBits(values[i]);
                        return Arrays.toString(bits);
                    }
                    %s
                    static String objectCopy(Object source, Object target, int count) {
                        try { System.arraycopy(source, -1, target, 0, count); return "copied"; }
                        catch (RuntimeException error) { return error.getClass().getName(); }
                    }
                    static String referenceCopy(Object[] source, String[] target, int count) {
                        try { System.arraycopy(source, 0, target, 0, count); return "copied"; }
                        catch (RuntimeException error) { return error.getClass().getName(); }
                    }
                    static void nestedCopy(int[][] source, int[][] target, int count) {
                        System.arraycopy(source, 0, target, 0, count);
                    }
                    static String mismatched(int[] source, float[] target, int count) {
                        try { System.arraycopy(source, -1, target, -1, count); return "copied"; }
                        catch (RuntimeException error) { return error.getClass().getName(); }
                    }
                    static void merged(int[] integers, float[] floats, boolean choose, int count) {
                        System.arraycopy(choose ? integers : floats, 0, choose ? integers : floats, 1, count);
                    }
                    static void literal(int[] source, int[] target) {
                        System.arraycopy(source, 0, target, 0, 64);
                    }
                    static int[] source(int[] value) { events = events * 10 + 1; System.gc(); return value; }
                    static int index(int value, int tag) { events = events * 10 + tag; System.gc(); return value; }
                    static int[] target(int[] value) { events = events * 10 + 3; System.gc(); return value; }
                    static int count(int value) { events = events * 10 + 5; System.gc(); return value; }
                    static String effectful(int[] from, int[] to, int length) {
                        events = 0;
                        try {
                            System.arraycopy(source(from), index(1, 2), target(to), index(2, 4), count(length));
                            return "copied";
                        } catch (RuntimeException error) { return error.getClass().getName(); }
                    }
                    public static void main(String[] args) {
                        %s
                        int[] integers = {1, 2, 3, 4};
                        float[] floats = {5, 6, 7, 8};
                        merged(integers, floats, true, 3);
                        merged(integers, floats, false, 3);
                        System.out.println(snapshot(integers)); System.out.println(snapshot(floats));
                        for (int length : COUNTS) {
                            System.out.println(objectCopy(new int[4], new float[4], length));
                            System.out.println(objectCopy(new Object(), new int[4], length));
                            System.out.println(objectCopy(null, new Object(), length));
                            System.out.println(objectCopy(new Object(), null, length));
                            System.out.println(mismatched(new int[4], new float[4], length));
                            System.out.println(mismatched(null, new float[4], length));
                            System.out.println(mismatched(new int[4], null, length));
                            int[] from = new int[70];
                            for (int i = 0; i < from.length; i++) from[i] = i + 11;
                            int[] to = new int[70];
                            System.out.println(effectful(from, to, length) + ":" + events);
                            System.gc(); System.out.println(snapshot(to));
                        }
                        System.out.println(effectful(null, new int[70], -1) + ":" + events);
                        System.out.println(effectful(new int[70], null, 0) + ":" + events);
                        int[] literalSource = new int[70];
                        for (int i = 0; i < literalSource.length; i++) literalSource[i] = i + 1;
                        int[] copied = new int[70];
                        literal(literalSource, copied);
                        System.gc(); System.out.println(snapshot(copied));
                        String[] narrow = {"first", "second", "third"};
                        System.out.println(referenceCopy(new Object[]{"written", new Object(), "later"}, narrow, 3));
                        System.gc(); System.out.println(Arrays.toString(narrow));
                        int[][] nested = {{1}, {2, 3}};
                        int[][] other = new int[2][];
                        nestedCopy(nested, other, 2);
                        System.gc(); System.out.println(other[0] == nested[0] && other[1] == nested[1]);
                    }
                }
                """.formatted(methods, calls));
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "TypedCopies"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("TypedCopies")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generation = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generation.request().generatedSourcesDirectory(), "TypedCopies");
        for (int kind = 0; kind < types.size(); kind++) {
            String body = body(cpp, "copy" + types.get(kind));
            assertTrue(body.contains("::jnative::bounded_primitive_array_copy<" + nativeTypes.get(kind) + ">("), body);
            assertTrue(body.contains("<= 64"), body);
            assertTrue(body.contains("gc_roots"), body);
        }
        for (String method : List.of("objectCopy", "referenceCopy", "nestedCopy", "mismatched", "merged")) {
            String body = body(cpp, method);
            assertFalse(body.contains("bounded_primitive_array_copy"), body);
            assertTrue(body.contains("platform_System_arraycopy_7b15f0890b"), body);
        }
        assertTrue(body(cpp, "literal").contains("::jnative::bounded_primitive_array_copy<std::int32_t>("), cpp);
        assertTrue(body(cpp, "effectful").contains("::jnative::bounded_primitive_array_copy<std::int32_t>("), cpp);
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String method) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n;]*\\bTypedCopies::"
                + Pattern.quote(method) + "\\([^\\n;]*\\) \\{").matcher(cpp);
        assertTrue(declaration.find(), method);
        int start = declaration.start();
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, method);
        return cpp.substring(start, end);
    }
}
