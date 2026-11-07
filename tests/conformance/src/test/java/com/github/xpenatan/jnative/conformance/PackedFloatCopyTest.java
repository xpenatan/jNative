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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PackedFloatCopyTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void preservesPackedLanesAndOrderedFallbacks(BuildType buildType) throws Exception {
        Path source = temporary.resolve("PackedFloats.java");
        Files.writeString(source, """
                public class PackedFloats {
                    static volatile float[] published;
                    static int evaluated;

                    static void store(float[] source, int from, float first, float second) {
                        source[from] = first;
                        source[from + 5] = second;
                    }

                    static void apply(float[] source, int from, float[] target, int to, float first, float second) {
                        store(source, from, first, second);
                        System.arraycopy(source, from, target, to, 8);
                    }

                    static final class Cold {
                        static { System.out.println("initialized"); }

                        static void store(float[] source, int from, float first) {
                            source[from] = first;
                        }

                        static void apply(float[] source, int from, float[] target, int to, float first) {
                            store(source, from, first);
                            System.arraycopy(source, from, target, to, 8);
                        }
                    }

                    static void print(float[] data) {
                        if (data == null) return;
                        long hash = 0;
                        for (float value : data) hash = 31 * hash + Float.floatToRawIntBits(value);
                        System.out.println(hash);
                    }

                    static void confined() {
                        float[] a = new float[16];
                        float[] b = new float[17];
                        a[3] = Float.intBitsToFloat(0x7fc01234);
                        b[1] = 4;
                        apply(a, 2, b, 4, value(0x80000000), value(0x7fc05678));
                        System.out.println(evaluated);
                        print(a);
                        print(b);
                    }

                    static float value(int bits) {
                        evaluated++;
                        return Float.intBitsToFloat(bits);
                    }

                    static void attempt(float[] source, int from, float[] target, int to) {
                        try {
                            apply(source, from, target, to, 3.25f, -0.0f);
                            System.out.println("copied");
                        } catch (RuntimeException error) {
                            System.out.println(error.getClass().getName());
                        }
                        print(source);
                        print(target);
                    }

                    public static void main(String[] args) {
                        confined();
                        float[] shared = new float[16];
                        published = shared;
                        attempt(shared, 2, new float[16], 3);
                        attempt(new float[16], 2, shared, 3);
                        float[] overlap = new float[16];
                        attempt(overlap, 2, overlap, 4);
                        attempt(overlap, 4, overlap, 2);
                        attempt(new float[7], 2, new float[16], 0);
                        attempt(new float[7], 0, new float[16], 0);
                        attempt(new float[16], 2, new float[7], 0);
                        attempt(new float[16], 2, null, 0);
                        attempt(null, 2, new float[16], 0);
                        attempt(new float[16], Integer.MAX_VALUE, new float[16], 0);
                        attempt(new float[16], 2, new float[16], Integer.MAX_VALUE);
                        float[] cold = new float[16];
                        Cold.apply(cold, 0, new float[16], 0, 7);
                        print(cold);
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "PackedFloats"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());

        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("PackedFloats")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generated = builder.generate();
        Path sources = generated.request().generatedSourcesDirectory();
        String cpp = ProcessHarness.generatedClassSource(sources, "PackedFloats");
        assertTrue(cpp.contains("try_packed_float_copy<8, 0, 5>"), cpp);
        assertTrue(cpp.contains("if (!::jnative::try_packed_float_copy<8, 0, 5>"), cpp);
        assertTrue(cpp.contains("new_confined_array<float>"), cpp);
        String cold = ProcessHarness.generatedClassSource(sources, "PackedFloats$Cold");
        assertFalse(cold.contains("try_packed_float_copy"), cold);

        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
