package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class PrimitiveArrayLoopsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void cachedMetadataPreservesAccessesAndCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("ArrayLoops.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class ArrayLoops {
                            static volatile boolean running;
                            static class SpinState { volatile boolean started, stop; }
                            static void boundedSpin(SpinState state, int[] values) {
                                state.started = true;
                                while (!state.stop) values[0] = values[0] + 1;
                            }
                            static void numericSpin(SpinState state, float[] values) {
                                state.started = true;
                                while (!state.stop) {
                                    float value = Math.max(-2.0f, Math.min(2.0f, values[0]));
                                    if (value > 0) values[0] = value - 0.25f;
                                    else values[0] = value + 0.125f;
                                }
                            }
                            static void interleave(float[] x, float[] y, float[] target, int count) {
                                int index = 0;
                                for (int i = 0; i < count; i++) {
                                    target[index++] = x[i];
                                    target[index++] = y[i];
                                }
                            }
                            static void primitives(int[] ints, long[] longs, double[] doubles,
                                    char[] chars, short[] shorts, byte[] bytes, boolean[] flags) {
                                for (int i = 0; i < ints.length; i++) {
                                    ints[i] += 7; longs[i] = longs[i] * 3 + ints[i];
                                    doubles[i] = -doubles[i]; chars[i] = (char) (chars[i] + 65535);
                                    shorts[i] = (short) (shorts[i] + 32767); bytes[i]++;
                                    flags[i] = !flags[i];
                                }
                            }
                            static void nested(int[] values) {
                                for (int i = 0; i < 4; i++) {
                                    for (int j = 0; j < values.length; j++) {
                                        if (j == 1) continue;
                                        values[j] += i;
                                        if (j == 4) break;
                                    }
                                }
                            }
                            static int reassigned(int[] first, int[] second) {
                                int sum = 0;
                                for (int i = 0; i < 4; i++) {
                                    sum += first[0];
                                    first = second;
                                }
                                return sum;
                            }
                            static int caught(int[] values) {
                                int sum = 0;
                                for (int i = 0; i < 4; i++) {
                                    try { sum += values[i]; }
                                    catch (ArrayIndexOutOfBoundsException error) { values = new int[] {9, 8, 7, 6}; }
                                }
                                return sum;
                            }
                            static int range(int[] values, int start, int end) {
                                int sum = 0;
                                for (int i = start; i < end; i++) sum += values[i];
                                return sum;
                            }
                            static void racing(int[] values) {
                                for (int i = 0; i < 200000; i++) values[0] = i;
                            }
                            static int awaitStop(int[] values) {
                                int result = 0;
                                while (running) result = values[0];
                                return result;
                            }
                            public static void main(String[] args) throws Exception {
                                SpinState state = new SpinState();
                                int[] progress = new int[1];
                                Thread spinning = new Thread(() -> boundedSpin(state, progress));
                                spinning.start();
                                while (!state.started) {}
                                // Collection must finish while the other thread is inside its pure loop.
                                for (int i = 0; i < 30; i++) System.gc();
                                state.stop = true;
                                spinning.join();
                                System.out.println("bounded polling completed");
                                SpinState numeric = new SpinState();
                                float[] numericProgress = {1};
                                Thread calculating = new Thread(() -> numericSpin(numeric, numericProgress));
                                calculating.start();
                                while (!numeric.started) {}
                                for (int i = 0; i < 30; i++) System.gc();
                                numeric.stop = true;
                                calculating.join();
                                System.out.println("numeric polling completed");
                                float[] x = {1, -0.0f, 3}, y = {4, 5, 6}, target = new float[6];
                                interleave(x, y, target, 3);
                                for (float value : target) System.out.println(Float.floatToRawIntBits(value));
                                interleave(null, null, null, 0);
                                interleave(null, null, null, -1);
                                try { interleave(null, y, null, 1); }
                                catch (NullPointerException expected) { System.out.println("null source"); }
                                try { interleave(new float[0], y, null, 1); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println("source before null target"); }
                                try { interleave(x, null, target, 1); }
                                catch (NullPointerException expected) { System.out.println("first stored:" + target[0]); }
                                try { interleave(x, y, new float[1], 1); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println("short target"); }
                                float[] aliased = {1, 2, 3, 4, 5, 6};
                                interleave(aliased, aliased, aliased, 3);
                                for (float value : aliased) System.out.println(value);
                                int[] ints = {0, Integer.MAX_VALUE, -9};
                                long[] longs = {Long.MAX_VALUE, Long.MIN_VALUE, 3};
                                double[] doubles = {0.0, -0.0, 3.5};
                                char[] chars = {0, 1, 65535};
                                short[] shorts = {-32768, 1, 32767};
                                byte[] bytes = {127, -128, 0}; boolean[] flags = {false, true, false};
                                primitives(ints, longs, doubles, chars, shorts, bytes, flags);
                                for (int i = 0; i < ints.length; i++)
                                    System.out.println(ints[i] + ":" + longs[i] + ":" + Double.doubleToRawLongBits(doubles[i])
                                        + ":" + (int) chars[i] + ":" + shorts[i] + ":" + bytes[i] + ":" + flags[i]);
                                int[] values = new int[7]; nested(values);
                                for (int value : values) System.out.println(value);
                                System.out.println(reassigned(new int[] {2}, new int[] {7}));
                                System.out.println(caught(new int[] {3}));
                                try { range(new int[1], -1, 1); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println("negative index"); }
                                try { range(null, -1, 1); }
                                catch (NullPointerException expected) { System.out.println("null before negative index"); }
                                System.out.println(range(null, Integer.MAX_VALUE, Integer.MAX_VALUE));
                                Thread collector = new Thread(() -> { for (int i = 0; i < 100; i++) System.gc(); });
                                collector.start();
                                float[] large = new float[40000];
                                interleave(new float[20000], new float[20000], large, 20000);
                                collector.join(); System.out.println(large[39999]);
                                running = true;
                                int[] shared = new int[1];
                                Thread writer = new Thread(() -> { racing(shared); running = false; });
                                writer.start(); awaitStop(shared); writer.join();
                                System.out.println(shared[0]);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "ArrayLoops"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("ArrayLoops")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generation = builder.generate();
        String generated =
                Files.readString(
                        generation
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/ArrayLoops.cpp"));
        assertTrue(generated.contains("PrimitiveArrayView<float>"), generated);
        String interleave =
                generated.substring(generated.indexOf("\nvoid ArrayLoops::interleave("));
        interleave = interleave.substring(0, interleave.indexOf("\n}"));
        assertTrue(interleave.contains("poll_budget"), interleave);
        String spinning = generated.substring(generated.indexOf("\nvoid ArrayLoops::boundedSpin("));
        spinning = spinning.substring(0, spinning.indexOf("\n}"));
        assertTrue(spinning.contains("poll_budget"), spinning);
        String reassigned =
                generated.substring(generated.indexOf("\nstd::int32_t ArrayLoops::reassigned("));
        reassigned = reassigned.substring(0, reassigned.indexOf("\n}"));
        assertFalse(reassigned.contains("first_elements"), reassigned);
        var compiled = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
