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

class PrimitiveArrayParametersTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void repeatedAccessesPreserveExceptionsAliasingAndCollection(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ArrayParameters.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.util.Arrays;
                        public class ArrayParameters {
                            static int events;
                            static volatile boolean written;
                            static int optional(int[] values, boolean take) {
                                if (!take) return 7;
                                return values[0] + values[1] + values[2];
                            }
                            static void mutate(int[] values) { values[0] += 5; }
                            static void write(int[] source, int[] target) {
                                events++;
                                target[0] = source[0];
                                events++;
                                target[1] = source[1];
                                mutate(source);
                                System.gc();
                                events++;
                                target[2] = source[0] + source[2];
                                events++;
                                target[3] = source[3];
                            }
                            static int reassigned(int[] values, int[] next) {
                                int first = values[0];
                                values = next;
                                System.gc();
                                return first + values[0] + values[1];
                            }
                            static long scalarKinds(float[] floats, double[] doubles, long[] longs,
                                    short[] shorts, char[] chars) {
                                floats[0] = floats[0] * -1.0f;
                                doubles[0] = doubles[0] * -1.0;
                                longs[0] += longs[1];
                                shorts[0] = (short) (shorts[0] + shorts[1]);
                                chars[0] = (char) (chars[0] + chars[1]);
                                return Float.floatToRawIntBits(floats[0])
                                    + Double.doubleToRawLongBits(doubles[0]) + longs[0] + shorts[0] + chars[0];
                            }
                            static int observePublication(int[] values) {
                                int first = values[0];
                                while (!written) { System.gc(); }
                                return first + values[0] + values[1];
                            }
                            static void observe(int[] source, int[] target) {
                                events = 0;
                                try { write(source, target); System.out.println("ok:" + events); }
                                catch (NullPointerException expected) { System.out.println("null:" + events); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds:" + events); }
                                System.out.println(Arrays.toString(source));
                                System.out.println(Arrays.toString(target));
                            }
                            public static void main(String[] args) throws Exception {
                                System.out.println(optional(null, false));
                                System.out.println(optional(new int[] {2, 3, 5}, true));
                                try { optional(null, true); }
                                catch (NullPointerException expected) { System.out.println("optional null"); }
                                try { optional(new int[] {1}, true); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println("optional bounds"); }
                                observe(null, null);
                                observe(new int[0], null);
                                observe(null, new int[0]);
                                for (int size = 0; size <= 4; size++) {
                                    observe(new int[] {1, 2, 3, 4}, new int[size]);
                                    observe(Arrays.copyOf(new int[] {1, 2, 3, 4}, size), new int[4]);
                                }
                                int[] alias = {1, 2, 3, 4};
                                observe(alias, alias);
                                System.out.println(reassigned(new int[] {3}, new int[] {5, 7}));
                                System.out.println(scalarKinds(new float[] {0.0f}, new double[] {-0.0},
                                    new long[] {Long.MAX_VALUE, 1}, new short[] {32767, 1}, new char[] {65535, 1}));
                                int[] shared = {2, 3};
                                // Publication is ordered; the view must cache metadata, never elements.
                                Thread worker = new Thread(() -> { shared[0] = 11; shared[1] = 13; written = true; });
                                worker.start();
                                while (!written) {}
                                System.out.println(observePublication(shared));
                                worker.join();
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(
                                ProcessHarness.java(),
                                "-cp",
                                classes.toString(),
                                "ArrayParameters"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("ArrayParameters")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/ArrayParameters.cpp"));
        String optional = cpp.substring(cpp.indexOf("\nstd::int32_t ArrayParameters::optional("));
        optional = optional.substring(0, optional.indexOf("\n}"));
        assertTrue(optional.contains("PrimitiveArrayView<std::int32_t>"), optional);
        String reassigned =
                cpp.substring(cpp.indexOf("\nstd::int32_t ArrayParameters::reassigned("));
        reassigned = reassigned.substring(0, reassigned.indexOf("\n}"));
        assertFalse(reassigned.contains("values_elements"), reassigned);
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
