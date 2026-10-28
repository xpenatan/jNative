package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrimitiveSortTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void primitiveSortsPreserveValuesRangesAndExceptions(BuildType buildType) throws Exception {
        Path source = temporary.resolve("PrimitiveSort.java");
        Files.writeString(source, """
                import java.util.Arrays;

                public class PrimitiveSort {
                    public static void main(String[] args) {
                        int random = 0x12345678;
                        long checksum = 0;
                        for (int size : new int[]{0, 1, 2, 3, 31, 32, 33, 255, 256, 257, 4097}) {
                            for (int shape = 0; shape < 5; shape++) {
                                int[] ints = new int[size];
                                long[] longs = new long[size + 2];
                                longs[0] = Long.MAX_VALUE;
                                longs[size + 1] = Long.MIN_VALUE;
                                for (int i = 0; i < size; i++) {
                                    random = random * 1664525 + 1013904223;
                                    ints[i] = shape == 0 ? i : shape == 1 ? size - i
                                            : shape == 2 ? random % 7 : shape == 3 ? random
                                            : i % 2 == 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
                                    longs[i + 1] = shape == 4 ? i % 2 == 0 ? Long.MIN_VALUE : Long.MAX_VALUE
                                            : (long) ints[i] * 4294967291L;
                                }
                                Arrays.sort(ints);
                                Arrays.sort(longs, 1, size + 1);
                                checksum = checksum * 31 + Arrays.hashCode(ints);
                                checksum = checksum * 31 + Arrays.hashCode(longs);
                            }
                        }
                        System.out.println(checksum);
                        for (int[] range : new int[][]{{2, 1}, {-1, 2}, {0, 4}, {4, 4}}) {
                            try { Arrays.sort(new long[3], range[0], range[1]); }
                            catch (RuntimeException expected) { System.out.println(expected.getClass().getName()); }
                        }
                        try { Arrays.sort((int[]) null); }
                        catch (NullPointerException expected) { System.out.println("null-int"); }
                        try { Arrays.sort((long[]) null, 0, 0); }
                        catch (NullPointerException expected) { System.out.println("null-long"); }
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "PrimitiveSort"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("PrimitiveSort")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5)).build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
