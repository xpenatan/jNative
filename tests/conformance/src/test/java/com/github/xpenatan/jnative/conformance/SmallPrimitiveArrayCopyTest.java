package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SmallPrimitiveArrayCopyTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void smallCopiesPreserveBitsOverlapAndFailureBoundaries(BuildType buildType) throws Exception {
        List<String> types = List.of("boolean", "byte", "char", "short", "int", "long", "float", "double");
        List<String> seeds = List.of("i % 3 == 0", "(byte) (i * 13 - 128)", "(char) (i * 991)",
                "(short) (i * 1777 - 32768)", "i * 1234567", "i * 1234567890123L",
                "Float.intBitsToFloat(i % 4 == 0 ? 0x80000000 : i % 4 == 1 ? 0x7fc00007 : 0x3f800000 + i * 4096)",
                "Double.longBitsToDouble(i % 4 == 0 ? 0x8000000000000000L : i % 4 == 1 ? 0x7ff8000000000007L : 0x3ff0000000000000L + i * 4096L)");
        StringBuilder methods = new StringBuilder();
        StringBuilder calls = new StringBuilder();
        for (int kind = 0; kind < types.size(); kind++) {
            String type = types.get(kind);
            methods.append("""
                    static String copy%1$s(%1$s[] source, int from, %1$s[] target, int to, int count) {
                        try {
                            switch (count) {
                                case 0: System.arraycopy(source, from, target, to, 0); break;
                                case 1: System.arraycopy(source, from, target, to, 1); break;
                                case 3: System.arraycopy(source, from, target, to, 3); break;
                                case 4: System.arraycopy(source, from, target, to, 4); break;
                                case 5: System.arraycopy(source, from, target, to, 5); break;
                                case 20: System.arraycopy(source, from, target, to, 20); break;
                                case 63: System.arraycopy(source, from, target, to, 63); break;
                                case 64: System.arraycopy(source, from, target, to, 64); break;
                                default: System.arraycopy(source, from, target, to, count);
                            }
                            return "copied";
                        } catch (RuntimeException error) { return error.getClass().getName(); }
                    }
                    static void run%1$s() {
                        %1$s[] source = new %1$s[72];
                        for (int i = 0; i < source.length; i++) source[i] = %2$s;
                        for (int count = 0; count <= 64; count++) {
                            %1$s[] target = new %1$s[72];
                            System.out.println("%1$s:" + count + ":" + copy%1$s(source, 3, target, 4, count));
                            System.out.println(snapshot(target));
                            for (int distance : new int[] {-3, -1, 0, 1, 3}) {
                                %1$s[] shared = source.clone();
                                System.out.println(copy%1$s(shared, 4, shared, 4 + distance, count));
                                System.out.println(snapshot(shared));
                            }
                        }
                        for (int count : new int[] {0, 1, 3, 4, 5, 20, 63, 64}) {
                            %1$s[] shared = source.clone();
                            System.out.println(copy%1$s(null, -1, null, -1, count));
                            System.out.println(copy%1$s(null, -1, shared, -1, count));
                            System.out.println(copy%1$s(shared, -1, null, -1, count));
                            System.out.println(copy%1$s(shared, -1, shared, 0, count));
                            System.out.println(copy%1$s(shared, 0, shared, -1, count));
                            System.out.println(copy%1$s(shared, 73 - count, shared, 0, count));
                            System.out.println(copy%1$s(shared, 0, shared, 73 - count, count));
                            System.out.println(copy%1$s(shared, Integer.MAX_VALUE, shared, 0, count));
                            System.out.println(copy%1$s(shared, 0, shared, Integer.MIN_VALUE, count));
                            System.gc(); System.out.println(snapshot(shared));
                            System.out.println(copy%1$s(shared, 72 - count, shared, 72 - count, count));
                        }
                    }
                    """.formatted(type, seeds.get(kind)));
            calls.append("run").append(type).append("();\n");
        }
        Path source = temporary.resolve("SmallCopies.java");
        Files.writeString(source, """
                import java.util.Arrays;
                public class SmallCopies {
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
                    public static void main(String[] args) {
                        %s
                    }
                }
                """.formatted(methods, calls));
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "SmallCopies"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("SmallCopies")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generation = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generation.request().generatedSourcesDirectory(), "SmallCopies");
        for (String nativeType : List.of("std::uint8_t", "std::int8_t", "std::uint16_t", "std::int16_t",
                "std::int32_t", "std::int64_t", "float", "double")) {
            assertTrue(cpp.contains("::jnative::bounded_primitive_array_copy<" + nativeType + ">("), cpp);
        }
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(result.executable().toString()), Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
