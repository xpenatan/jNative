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

class FloatingTextTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void portableFormattingMatchesJavaForDecimalsEdgesAndConcurrentCalls(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("FloatingText.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class FloatingText {
                            static String text(float f, double d) {
                                return Float.toString(f) + ":" + Double.toString(d) + ":"
                                    + new StringBuilder().append(f).append('/').append(d).toString();
                            }
                            public static void main(String[] args) throws Exception {
                                double[] special = {0, -0.0, .001, -.001, .1, -.1, .5, 1.25, -1.25,
                                    8388607, 8388608, 9999999, 10000000, 1e-4, 1e-10, 1e23,
                                    Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE,
                                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
                                for (double value : special) System.out.println(text((float)value, value));
                                float[] single = {Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE,
                                    Float.intBitsToFloat(0x3dcccccd), Float.intBitsToFloat(0x3dcccccc),
                                    Float.intBitsToFloat(0x3dccccce), Float.intBitsToFloat(0x3a83126e),
                                    Float.intBitsToFloat(0x3a83126f), Float.intBitsToFloat(0x3a831270)};
                                for (float value : single) System.out.println(text(value, value));
                                int seed = 873491;
                                int scale = 1;
                                for (int decimal = 0; decimal <= 6; ++decimal, scale *= 10) {
                                    for (int i = 0; i < 48; ++i) {
                                        seed = seed * 1664525 + 1013904223;
                                        int numerator = (seed & 0xffffff) - 8388608;
                                        float f = numerator / (float)scale;
                                        double d = numerator / (double)scale;
                                        System.out.println(text(f, d));
                                        System.out.println(text(Float.intBitsToFloat(Float.floatToRawIntBits(f) + 1),
                                            Double.longBitsToDouble(Double.doubleToRawLongBits(d) + 1)));
                                    }
                                }
                                String[] results = new String[2];
                                Thread one = new Thread(() -> {
                                    StringBuilder result = new StringBuilder();
                                    for (int i = 1; i <= 24; ++i) result.append(text(i / 10f, i / 7.0)).append(';');
                                    results[0] = result.toString();
                                });
                                Thread two = new Thread(() -> {
                                    StringBuilder result = new StringBuilder();
                                    for (int i = 1; i <= 24; ++i) result.append(text(-i / 3f, -i / 100.0)).append(';');
                                    results[1] = result.toString();
                                });
                                one.start(); two.start(); one.join(); two.join();
                                System.gc(); System.out.println(results[0]); System.out.println(results[1]);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "FloatingText"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var compiled =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("FloatingText")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType)
                        .cmakeDefine("JNATIVE_FEATURES", "PORTABLE")
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(90),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
