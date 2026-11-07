package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.StackTraceMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TypedOptimizationTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void preservesJavaBehaviorAcrossTypedOptimizations(BuildType buildType) throws Exception {
        Path source = temporary.resolve("TypedOptimizations.java");
        Files.writeString(source, """
                public class TypedOptimizations {
                    static int trace;
                    static int tick(int n) { trace = trace * 10 + n; return n; }
                    static int twice(int a, int b) { return (a + a) ^ b; }
                    static int first(int a, int b) { return a; }
                    static int divide(int a, int b) { return a / b; }

                    static int arguments() {
                        return twice(tick(1), tick(2)) + first(tick(3), tick(4));
                    }

                    static float floating(float a, float b) { return a * b; }
                    static int zeroBits() {
                        return Float.floatToRawIntBits(floating(-0.0f, 2.0f));
                    }
                    static boolean nan() { return Float.isNaN(floating(Float.POSITIVE_INFINITY, 0.0f)); }
                    static long shift(long a, int b) { return (a << b) ^ Long.MIN_VALUE; }
                    static long shifted() { return shift(Long.MAX_VALUE, 65); }

                    static class Cold {
                        static { tick(9); }
                        static int fromCold(int a) { return a + 1; }
                    }
                    static int initializeAfterArguments() { return Cold.fromCold(tick(7)); }

                    static int scratch(int seed) {
                        int[] a = new int[3];
                        int[] alias = a;
                        int endpoint = Integer.MAX_VALUE;
                        int zero = (endpoint + 1) - Integer.MIN_VALUE;
                        alias[1] = seed;
                        a[2] = alias[1] + 2;
                        return a[zero] + a[1] + alias[2] + a.length;
                    }

                    static int effectfulIndex() {
                        int[] a = new int[1];
                        a[tick(8) & 0] = tick(5);
                        return a[0];
                    }

                    static int disjointSites(int seed) {
                        int[] a = new int[9], b = new int[9];
                        return a[0] + (b[0] = seed) + a[0];
                    }

                    static int disjointIndices(int seed) {
                        int[] a = new int[9];
                        return a[0] + (a[1] = seed) + a[0];
                    }

                    static int overlap(int seed) {
                        int[] a = new int[9], alias = a;
                        return a[0] + (alias[0] = seed) + a[0];
                    }

                    static int dynamic(int index) {
                        int[] a = new int[2];
                        a[index] = tick(6);
                        return a[0] + a[1];
                    }

                    static int[] escape(int n) { return new int[n]; }

                    public static void main(String[] args) {
                        System.out.println(arguments());
                        System.out.println(trace);
                        trace = 0;
                        System.out.println(initializeAfterArguments());
                        System.out.println(trace);
                        System.out.println(zeroBits());
                        System.out.println(nan());
                        System.out.println(shifted());
                        System.out.println(scratch(Integer.MAX_VALUE));
                        trace = 0;
                        System.out.println(effectfulIndex());
                        System.out.println(trace);
                        System.out.println(disjointSites(11));
                        System.out.println(disjointIndices(12));
                        System.out.println(overlap(13));
                        try { dynamic(-1); }
                        catch (ArrayIndexOutOfBoundsException expected) { System.out.println(trace); }
                        try { escape(-1); }
                        catch (NegativeArraySizeException expected) { System.out.println("negative"); }
                        System.out.println(escape(2).length);
                        try { divide(tick(2), 0); }
                        catch (ArithmeticException expected) { System.out.println(trace); }
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "TypedOptimizations"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());

        boolean sourceLocations = buildType == BuildType.DEBUG;
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("TypedOptimizations")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .stackTraces(StackTraceMode.JAVA).javaSourceLocations(sourceLocations);
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                "TypedOptimizations");
        String arguments = body(cpp, "arguments");
        assertEquals(sourceLocations, calls(arguments, "twice"), arguments);
        assertEquals(sourceLocations, calls(arguments, "first"), arguments);
        assertTrue(calls(body(cpp, "initializeAfterArguments"), "fromCold"), cpp);
        String scratch = body(cpp, "scratch");
        assertTrue(scratch.contains("array_slot"), scratch);
        assertFalse(scratch.contains("new_confined_array"), scratch);
        assertFalse(scratch.contains("array_get"), scratch);
        assertTrue(body(cpp, "dynamic").contains("new_confined_array"), cpp);
        assertEquals(1, loads(body(cpp, "disjointSites")), cpp);
        assertEquals(1, loads(body(cpp, "disjointIndices")), cpp);
        assertEquals(2, loads(body(cpp, "overlap")), cpp);

        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static long loads(String source) {
        return Pattern.compile("(?:::jnative::(?:confined_)?array_get<|\\.get(?:_unchecked)?\\((?!\\)))")
                .matcher(source).results().count();
    }

    private static boolean calls(String source, String name) {
        return Pattern.compile("::(?:initialized_)?" + name + "\\(").matcher(source).find();
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* TypedOptimizations::" + name + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }
}
