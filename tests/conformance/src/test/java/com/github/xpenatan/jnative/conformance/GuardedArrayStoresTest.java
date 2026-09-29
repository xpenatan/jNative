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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GuardedArrayStoresTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void constantRegionsPreservePartialWritesArithmeticAndRootedFallbacks(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ConstantStores.java"), classes = temporary.resolve("classes");
        String oversized = IntStream.range(0, 17)
                .mapToObj(index -> "target[" + index + "] = value + " + index + ";")
                .collect(Collectors.joining("\n"));
        Files.writeString(source, """
                import java.util.Arrays;
                public class ConstantStores {
                    static int effects;
                    static volatile int fieldValue = 19;
                    static volatile boolean done;
                    static class Holder {
                        float[] values;
                        Holder(float[] values) { this.values = values; }
                    }
                    static void scatter(int[] target, int value) {
                        target[2] = value;
                        value += 3;
                        target[7] = value;
                        target[2] = value ^ 17;
                    }
                    static void negative(int[] target) {
                        target[0] = 11;
                        target[-1] = 13;
                        target[2] = 17;
                    }
                    static int arithmeticState(int[] target, int value) {
                        target[0] = value;
                        value += 3;
                        target[3] = value;
                        value ^= 17;
                        target[1] = value;
                        return value;
                    }
                    static void vertices(float[] target, float x, float y, boolean rotated) {
                        if (rotated) {
                            float x1 = x + 2, y1 = y - 3;
                            target[0] = x1; target[1] = y1;
                            float x2 = x * 3, y2 = y * 5;
                            target[5] = x2; target[6] = y2;
                            float x3 = x - 7, y3 = y + 11;
                            target[10] = x3; target[11] = y3;
                            target[15] = x1 + (x3 - x2);
                            target[16] = y3 - (y2 - y1);
                        } else {
                            target[0] = x; target[1] = y;
                            target[5] = x; target[6] = y + 1;
                            target[10] = x + 1; target[11] = y + 1;
                            target[15] = x + 1; target[16] = y;
                        }
                    }
                    static void captured(Holder holder) {
                        float[] target = holder.values;
                        target[0] = 5;
                        target[3] = 7;
                    }
                    static void longs(long[] target, long value) { target[0] = value; target[3] = value + 1; }
                    static void doubles(double[] target, double value) { target[0] = value; target[3] = value; }
                    static void floats(float[] target, float value) { target[0] = value; target[3] = value; }
                    static void shorts(short[] target, int value) { target[0] = (short)value; target[3] = (short)(value + 1); }
                    static void chars(char[] target, int value) { target[0] = (char)value; target[3] = (char)(value + 1); }
                    static void bytes(byte[] target, int value) { target[0] = (byte)value; target[3] = (byte)(value + 1); }
                    static void booleans(boolean[] target, boolean value) { target[0] = value; target[3] = !value; }
                    static void variable(int[] target, int index) { target[0] = 11; target[index] = 13; }
                    static int effect() { effects++; System.gc(); return effects + 20; }
                    static void calls(int[] target) { target[0] = 11; target[3] = effect(); }
                    static void fieldRead(int[] target) { target[0] = 11; target[3] = fieldValue; }
                    static void fieldWrite(int[] target) { target[0] = 11; fieldValue = 23; target[3] = 13; }
                    static void divide(int[] target, int divisor) { target[0] = 11; target[3] = 17 / divisor; }
                    static void branch(int[] target, boolean take) { target[0] = 11; if (take) target[3] = 13; }
                    static void reassign(int[] target, int[] other) {
                        target[0] = 11; target[2] = 13;
                        target = other;
                        target[0] = 17; target[2] = 19;
                    }
                    static void escapedTemporary(int[] target, int value) {
                        target[0] = 11;
                        int product = value * 17;
                        target[2] = product;
                        target[3] = product;
                        effects = product;
                    }
                    static void oversized(int[] target, int value) {
                        OVERSIZED
                    }
                    static void handler(int[] target, String retained) {
                        try { target[0] = 11; target[5] = 13; }
                        catch (ArrayIndexOutOfBoundsException expected) {
                            System.gc();
                            System.out.println(retained + ":handler:" + Arrays.toString(target));
                        }
                        System.gc();
                        System.out.println(retained + ":after:" + Arrays.toString(target));
                    }
                    static void observe(int[] target, int operation) {
                        try {
                            switch (operation) {
                                case 0: scatter(target, 7); break;
                                case 1: negative(target); break;
                                case 2: System.out.println("state:" + arithmeticState(target, 7)); break;
                                case 3: calls(target); break;
                                case 4: fieldRead(target); break;
                                case 5: fieldWrite(target); break;
                                case 6: divide(target, 0); break;
                                case 7: branch(target, true); break;
                                case 8: variable(target, 3); break;
                                case 9: escapedTemporary(target, 7); break;
                                default: oversized(target, 7);
                            }
                            System.out.println("ok");
                        } catch (NullPointerException expected) { System.out.println("null"); }
                        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
                        catch (ArithmeticException expected) { System.out.println("arithmetic"); }
                        System.out.println(operation + ":" + effects + ":" + fieldValue + ":" + Arrays.toString(target));
                    }
                    static void observeVertices(float[] target, boolean rotated) {
                        try { vertices(target, -0.0f, 3.25f, rotated); System.out.println("vertices ok"); }
                        catch (NullPointerException expected) { System.out.println("vertices null"); }
                        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("vertices bounds"); }
                        System.out.println(Arrays.toString(target));
                    }
                    static void kinds(int length) {
                        long[] l = length < 0 ? null : new long[length];
                        double[] d = length < 0 ? null : new double[length];
                        float[] f = length < 0 ? null : new float[length];
                        short[] s = length < 0 ? null : new short[length];
                        char[] c = length < 0 ? null : new char[length];
                        byte[] b = length < 0 ? null : new byte[length];
                        boolean[] z = length < 0 ? null : new boolean[length];
                        try { longs(l, Long.MIN_VALUE); } catch (RuntimeException expected) { System.out.println("long failure"); }
                        try { doubles(d, Double.longBitsToDouble(0x7ff8123456789abcL)); } catch (RuntimeException expected) { System.out.println("double failure"); }
                        try { floats(f, Float.intBitsToFloat(0x7fc12345)); } catch (RuntimeException expected) { System.out.println("float failure"); }
                        try { shorts(s, 65535); } catch (RuntimeException expected) { System.out.println("short failure"); }
                        try { chars(c, -1); } catch (RuntimeException expected) { System.out.println("char failure"); }
                        try { bytes(b, 255); } catch (RuntimeException expected) { System.out.println("byte failure"); }
                        try { booleans(z, true); } catch (RuntimeException expected) { System.out.println("boolean failure"); }
                        System.out.println(Arrays.toString(l)); System.out.println(Arrays.toString(s));
                        System.out.println(Arrays.toString(c)); System.out.println(Arrays.toString(b));
                        System.out.println(Arrays.toString(z));
                        if (length > 0) {
                            System.out.println(Double.doubleToRawLongBits(d[0]));
                            System.out.println(Float.floatToRawIntBits(f[0]));
                            try { doubles(d, -0.0); } catch (RuntimeException expected) { System.out.println("zero double failure"); }
                            try { floats(f, -0.0f); } catch (RuntimeException expected) { System.out.println("zero float failure"); }
                            System.out.println(Double.doubleToRawLongBits(d[0]));
                            System.out.println(Float.floatToRawIntBits(f[0]));
                        }
                    }
                    public static void main(String[] args) throws Exception {
                        for (int operation = 0; operation < 11; operation++) {
                            observe(new int[20], operation); observe(new int[3], operation);
                            observe(new int[0], operation); observe(null, operation);
                        }
                        for (int length : new int[] {0, 1, 6, 16, 17, 20}) {
                            observeVertices(new float[length], true);
                            observeVertices(new float[length], false);
                        }
                        observeVertices(null, true); observeVertices(null, false);
                        for (int length : new int[] {-1, 0, 1, 4}) kinds(length);
                        int[] first = new int[3], second = new int[3];
                        reassign(first, second);
                        System.out.println(Arrays.toString(first) + ":" + Arrays.toString(second));
                        reassign(first, first); System.out.println(Arrays.toString(first));
                        Holder holder = new Holder(new float[4]); captured(holder);
                        System.out.println(Arrays.toString(holder.values));
                        try { captured(new Holder(null)); } catch (NullPointerException expected) { System.out.println("captured null"); }
                        handler(new int[1], new String("retained"));
                        handler(new int[6], new String("full"));
                        int[] shared = new int[8];
                        Thread collector = new Thread(() -> { while (!done) System.gc(); });
                        Thread firstWriter = new Thread(() -> { for (int i = 0; i < 100; i++) scatter(shared, i); });
                        Thread secondWriter = new Thread(() -> { for (int i = 0; i < 100; i++) scatter(shared, i + 100); });
                        collector.start(); firstWriter.start(); secondWriter.start();
                        firstWriter.join(); secondWriter.join(); done = true; collector.join();
                        scatter(shared, 7);
                        System.out.println("shared:" + Arrays.toString(shared));
                    }
                }
                """.replace("OVERSIZED", oversized));
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ConstantStores"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("ConstantStores").buildRoot(temporary.resolve("out"))
                .buildType(buildType);
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(
                generated.request().generatedSourcesDirectory(), "ConstantStores");
        for(String name : List.of("scatter", "arithmeticState", "vertices", "captured",
                "longs", "doubles", "floats", "shorts", "chars")) {
            String body = body(cpp, name);
            assertTrue(body.contains(".covers("), body);
            assertTrue(body.contains(".set_unchecked("), body);
            assertTrue(body.contains("} else {"), body);
            assertTrue(body.contains("_elements.set(") || body.contains("::jnative::array_set<"), body);
        }
        String vertices = body(cpp, "vertices");
        assertEquals(2, occurrences(vertices, ".covers(0, 16)"), vertices);
        assertEquals(16, occurrences(vertices, ".set_unchecked("), vertices);
        String reassigned = body(cpp, "reassign");
        assertEquals(2, occurrences(reassigned, ".covers(0, 2)"), reassigned);
        for(String name : List.of("calls", "fieldRead", "fieldWrite", "divide", "branch",
                "variable", "oversized", "handler", "bytes", "booleans")) {
            String body = body(cpp, name);
            assertFalse(body.contains(".set_unchecked("), name + ":" + body);
        }
        String negative = body(cpp, "negative");
        assertTrue(negative.contains(".covers(-1, 2)"), negative);
        assertTrue(body(cpp, "scatter").contains("::jnative::PrimitiveArrayView<std::int32_t>"), cpp);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(compiled.executable().toString()), Duration.ofSeconds(90),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(buildType == BuildType.RELEASE && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, compiled, expected, 1);
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n;]*\\bConstantStores::"
                + Pattern.quote(name) + "\\([^\\n;]*\\) \\{").matcher(cpp);
        assertTrue(declaration.find(), name);
        int end = cpp.indexOf("\n}", declaration.start());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }

    private static int occurrences(String source, String text) {
        return source.split(Pattern.quote(text), -1).length - 1;
    }
}
