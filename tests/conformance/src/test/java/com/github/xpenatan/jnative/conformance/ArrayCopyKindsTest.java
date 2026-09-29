package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class ArrayCopyKindsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void copyKindsOverlapAndExceptionOrderMatchJvm(BuildType buildType) throws Exception {
        Path source = temporary.resolve("CopyKinds.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.nio.file.Path;
                        import java.util.Arrays;
                        public class CopyKinds {
                            static void attempt(Object source, Object target, int from, int to, int count) {
                                try {
                                    System.arraycopy(source, from, target, to, count);
                                    System.out.println("copied");
                                } catch (RuntimeException error) {
                                    System.out.println(error.getClass().getName());
                                }
                            }
                            public static void main(String[] args) throws Exception {
                                boolean[] z = {true, false, true, false};
                                byte[] b = {-128, 127, 1, -1};
                                char[] c = {0, 65535, 'x', 'y'};
                                short[] s = {-32768, 32767, 1, -1};
                                int[] i = {Integer.MIN_VALUE, Integer.MAX_VALUE, 1, -1};
                                long[] j = {Long.MIN_VALUE, Long.MAX_VALUE, 1, -1};
                                float[] f = {-0f, Float.intBitsToFloat(0x7fc00007), 1, -1};
                                double[] d = {-0d, Double.longBitsToDouble(0x7ff8000000000007L), 1, -1};
                                Object[] arrays = {z, b, c, s, i, j, f, d};
                                for (Object array : arrays) {
                                    System.arraycopy(array, 0, array, 1, 3);
                                    System.arraycopy(array, 1, array, 0, 2);
                                    System.arraycopy(array, 4, array, 4, 0);
                                }
                                System.gc();
                                System.out.println(Arrays.toString(z.clone()));
                                System.out.println(Arrays.toString(b.clone()));
                                System.out.println(Arrays.toString(c.clone()));
                                System.out.println(Arrays.toString(s.clone()));
                                System.out.println(Arrays.toString(i.clone()));
                                System.out.println(Arrays.toString(j.clone()));
                                for (float value : f.clone()) System.out.println(Float.floatToRawIntBits(value));
                                for (double value : d.clone()) System.out.println(Double.doubleToRawLongBits(value));
                                for (Object first : arrays) for (Object second : arrays) {
                                    attempt(first, second, 0, 0, 0);
                                    attempt(first, second, -1, 0, 1);
                                }
                                Object[] invalid = {null, new Object(), new int[2], new String[2]};
                                for (Object first : invalid) for (Object second : invalid) {
                                    attempt(first, second, -1, 0, 0);
                                    attempt(first, second, 0, 0, Integer.MAX_VALUE);
                                }
                                String[] narrow = {"a", "b", "c"};
                                Object[] wide = new Object[4];
                                System.arraycopy(narrow, 0, wide, 1, 3);
                                System.out.println(Arrays.toString(wide));
                                attempt(new Object[]{"written", new Object(), "later"}, narrow, 0, 0, 3);
                                System.out.println(Arrays.toString(narrow));
                                System.arraycopy(narrow, 0, narrow, 1, 2);
                                System.out.println(Arrays.toString(narrow));
                                int[][] nested = {{1}, {2, 3}};
                                System.arraycopy(nested, 0, wide, 0, 2);
                                System.out.println(wide[0] == nested[0] && wide[1] == nested[1]);
                                long[] shared = new long[4];
                                Thread worker = new Thread(() -> System.arraycopy(j, 0, shared, 0, 4));
                                worker.start(); worker.join(); System.gc();
                                System.out.println(Arrays.toString(shared));
                                Object path = Path.of("copy", "test");
                                Object same = Path.of("copy", "test");
                                Object plain = new Object();
                                System.out.println(path.equals(same) && path.hashCode() == same.hashCode());
                                System.out.println(!path.equals(plain) && !plain.equals(path));
                                System.out.println(!path.equals(null));
                                System.out.println(plain.hashCode() == System.identityHashCode(plain));
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "CopyKinds"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("CopyKinds")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType)
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
