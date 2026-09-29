package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class GuardedArrayLoopsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void guardedRangesPreserveAliasingPartialWritesAndOverflow(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("Ranges.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.util.Arrays;
                        public class Ranges {
                            static volatile boolean done;
                            static void interleave(float[] x, float[] y, float[] target, int offset, int count) {
                                int position = offset;
                                for (int i = 0; i < count; i++) {
                                    target[position++] = x[i];
                                    target[position++] = y[i];
                                }
                            }
                            static void copy(int[] source, int[] target, int start, int end, int offset) {
                                for (int i = start; i < end; i++) target[offset++] = source[i - start];
                            }
                            static void inclusive(int[] source, int[] target, int start, int end) {
                                for (int i = start; i <= end; i++) target[i - start] = source[i - start];
                            }
                            static void wrappedConstants(int[] target, int count) {
                                for (int i = 0; i < count; i++) {
                                    int low = -1073741824;
                                    low *= 2;
                                    int scale = 65536;
                                    scale *= scale;
                                    target[low * scale] = i;
                                }
                            }
                            static void branching(float[] source, float[] target, int[] occupied, int count, int offset) {
                                int used = 0;
                                for (int row = 0; row < 2; row++) {
                                    int base = row * count + offset;
                                    for (int i = 0; i < count; i++) {
                                        float mass = Math.max(0, source[i]);
                                        if (mass <= 0) continue;
                                        int index = (base + i) * 4;
                                        if (target[index] == 0) occupied[used++] = index;
                                        target[index] += mass;
                                        if ((i & 1) == 0) target[index + 1] += mass * 0.5f;
                                        else target[index + 2] -= mass;
                                    }
                                }
                            }
                            static void divergentIndex(int[] source, int[] target, int count) {
                                int index = 0;
                                for (int i = 0; i < count; i++) {
                                    if (source[i] > 0) index += 2;
                                    else index++;
                                    target[index] = i + 1;
                                }
                            }
                            static void reverseOffsets(int[] target, int offset, int count) {
                                for (int i = 0; i < count; i++) {
                                    target[offset - i] = i;
                                    if ((i & 1) == 0) target[offset - i + 3] = i + 10;
                                }
                            }
                            static void conditionIndex(int[] source, int[] target, int offset, int count) {
                                for (int i = 0; i < count; i++) {
                                    target[i] = source[i];
                                    if (source[offset + i] != 0) target[i]++;
                                }
                            }
                            static void observeBranches(float[] source, float[] target, int[] occupied, int count, int offset) {
                                try { branching(source, target, occupied, count, offset); System.out.println("branches ok"); }
                                catch (NullPointerException expected) { System.out.println("branches null"); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println("branches bounds"); }
                                System.out.println(Arrays.toString(target));
                                System.out.println(Arrays.toString(occupied));
                            }
                            static void observe(float[] x, float[] y, float[] target, int offset, int count) {
                                try { interleave(x, y, target, offset, count); System.out.println("ok"); }
                                catch (NullPointerException expected) { System.out.println("null"); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
                                System.out.println(Arrays.toString(target));
                            }
                            public static void main(String[] args) throws Exception {
                                float[] values = {1, 2, 3, 4};
                                observe(null, null, null, -1, 0);
                                observe(null, null, null, 0, -1);
                                observe(null, values, new float[8], -1, 1);
                                observe(values, null, new float[8], 1, 2);
                                observe(values, new float[] {9}, new float[8], 0, 3);
                                observe(values, values, new float[5], 0, 4);
                                observe(values, values, new float[8], Integer.MAX_VALUE, 2);
                                float[] aliased = {1, 2, 3, 4, 5, 6, 7, 8};
                                observe(aliased, aliased, aliased, 0, 4);
                                aliased = new float[] {1, 2, 3, 4, 5, 6, 7, 8};
                                observe(aliased, values, aliased, 1, 3);
                                for (int size : new int[] {1, 3, 4, 5, 63, 64, 65, 257}) {
                                    float[] input = new float[size];
                                    for (int i = 0; i < size; i++) input[i] = i + 0.5f;
                                    float[] target = new float[size * 2];
                                    interleave(input, input, target, 0, size);
                                    System.out.println(target[0] + ":" + target[target.length - 1]);
                                }
                                int[] result = new int[2];
                                wrappedConstants(result, 5);
                                System.out.println(Arrays.toString(result));
                                copy(new int[] {3}, result, Integer.MAX_VALUE - 1, Integer.MAX_VALUE, 1);
                                System.out.println(Arrays.toString(result));
                                result = new int[2];
                                try { inclusive(new int[] {7, 9}, result, Integer.MAX_VALUE - 1, Integer.MAX_VALUE); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println(Arrays.toString(result)); }
                                observeBranches(null, null, null, 0, -1);
                                observeBranches(null, new float[32], new int[8], 4, 0);
                                observeBranches(new float[] {0, -1, 0, 0}, null, null, 4, 0);
                                observeBranches(values, new float[32], new int[8], 4, 0);
                                observeBranches(values, new float[32], new int[1], 4, 0);
                                observeBranches(values, new float[15], new int[8], 4, 0);
                                observeBranches(new float[] {1, 2}, new float[32], new int[8], 4, 0);
                                observeBranches(values, new float[32], new int[8], 4, Integer.MAX_VALUE);
                                observeBranches(new float[] {Float.NaN, -0.0f, 1, Float.POSITIVE_INFINITY},
                                    new float[32], new int[8], 4, 0);
                                float[] shared = new float[32];
                                shared[0] = 1; shared[1] = 2; shared[2] = 3; shared[3] = 4;
                                observeBranches(shared, shared, new int[8], 4, 0);
                                int[] divergent = new int[5];
                                try { divergentIndex(new int[] {1, -1, 1, -1}, divergent, 4); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println(Arrays.toString(divergent)); }
                                for (int offset : new int[] {-1, 1, 3, 4, 5, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                                    int[] reverse = new int[8];
                                    try { reverseOffsets(reverse, offset, 4); System.out.println("reverse ok"); }
                                    catch (ArrayIndexOutOfBoundsException expected) { System.out.println("reverse bounds"); }
                                    System.out.println(Arrays.toString(reverse));
                                    int[] conditional = new int[4];
                                    try { conditionIndex(new int[] {1, 2, 3, 4}, conditional, offset, 4); }
                                    catch (ArrayIndexOutOfBoundsException expected) { System.out.println("condition bounds"); }
                                    System.out.println(Arrays.toString(conditional));
                                }
                                Thread collector = new Thread(() -> { while (!done) System.gc(); });
                                collector.start();
                                float[] input = new float[2048], output = new float[4096];
                                for (int i = 0; i < input.length; i++) input[i] = i;
                                for (int iteration = 0; iteration < 100; iteration++) interleave(input, input, output, 0, input.length);
                                float[] deposits = new float[2048 * 8];
                                int[] occupied = new int[4096];
                                for (int iteration = 0; iteration < 10; iteration++) branching(input, deposits, occupied, 2048, 0);
                                done = true;
                                collector.join();
                                System.out.println(output[4095]);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Ranges"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Ranges")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String code =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/Ranges.cpp"));
        String interleave = code.substring(code.indexOf("void Ranges::interleave("));
        interleave = interleave.substring(0, interleave.indexOf("\n}\n") + 3);
        assertTrue(interleave.contains(".covers("), interleave);
        assertTrue(interleave.contains(".get_unchecked("), interleave);
        assertTrue(interleave.contains(".set_unchecked("), interleave);
        assertTrue(interleave.contains("} else {"), interleave);
        String branching = code.substring(code.indexOf("void Ranges::branching("));
        branching = branching.substring(0, branching.indexOf("\n}\n") + 3);
        assertTrue(branching.contains("source_elements.get_unchecked("), branching);
        assertTrue(branching.contains("target_elements.set_unchecked("), branching);
        assertTrue(branching.contains("occupied_elements.set("), branching);
        String divergent = code.substring(code.indexOf("void Ranges::divergentIndex("));
        divergent = divergent.substring(0, divergent.indexOf("\n}\n") + 3);
        assertTrue(divergent.contains("target_elements.set("), divergent);
        assertTrue(!divergent.contains("target_elements.set_unchecked("), divergent);
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(90),
                        Map.of("JNATIVE_GC_INTERVAL", "2")));
    }
}
