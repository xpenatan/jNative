package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ArrayStoreForwardingTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void forwardsExactStoresAndStopsAtMutationAndSynchronization(BuildType buildType) throws Exception {
        Path source = temporary.resolve("ArrayForward.java"), classes = temporary.resolve("classes");
        Files.writeString(source, """
                public class ArrayForward {
                    static volatile int barrier;
                    static int integers(int[] a, int i, int v) { a[i] = v; return a[i]; }
                    static long longs(long[] a, int i, long v) { a[i] = v; return a[i]; }
                    static float floats(float[] a, int i, float v) { a[i] = v + 0.25f; return a[i]; }
                    static double doubles(double[] a, int i, double v) { a[i] = v + 0.25; return a[i]; }
                    static int narrow(byte[] a, char[] b, short[] c, int v) {
                        a[0] = (byte)v; int x = a[0]; b[0] = (char)v; int y = b[0];
                        c[0] = (short)v; return x + y + c[0];
                    }
                    static boolean bool(boolean[] a, boolean v) { a[0] = v; return a[0]; }
                    static int alias(int[] a, int[] b) { a[0] = 7; b[0] = 13; return a[0]; }
                    static int index(int[] a, int i, int j) { a[i] = 7; a[j] = 13; return a[i]; }
                    static int locals(int[] a, int i) { a[i] = 7; i++; return a[i]; }
                    static int reassign(int[] a, int[] b) { a[0] = 7; a = b; return a[0]; }
                    public static void mutate(int[] a) { System.gc(); a[0] = 19; }
                    static int call(int[] a) { a[0] = 7; mutate(a); return a[0]; }
                    static int nativeCopy(int[] a, int[] b) { a[0] = 7; System.arraycopy(b, 0, a, 0, 1); return a[0]; }
                    static int nested(int[][] a, int[] payload) { a[0][0] = 7; payload[0] = 13; return a[0][0]; }
                    static int replaced(int[][] a, int[][] b, int[] replacement) {
                        a[0][0] = 7; b[0] = replacement; return a[0][0];
                    }
                    static void mutateRows(int[][] a) { System.gc(); a[0] = new int[]{29}; }
                    static int nestedCall(int[][] a) { a[0][0] = 7; mutateRows(a); return a[0][0]; }
                    static int nestedVolatile(int[][] a) { a[0][0] = 7; int seen = barrier; return a[0][0] + seen; }
                    static int reflected(int[] a) throws Exception {
                        a[0] = 7;
                        ArrayForward.class.getMethod("mutate", int[].class).invoke(null, new Object[]{a});
                        return a[0];
                    }
                    static int joined(int[] a, boolean choose) { a[0] = 7; if (choose) a[0] = 29; return a[0]; }
                    static int acquire(int[] a) { a[0] = 7; int seen = barrier; return a[0] + seen; }
                    static int monitor(int[] a) { a[0] = 7; synchronized(a) { return a[0]; } }
                    static int localValue(int[] a, int v) { a[0] = v; v++; return a[0] + v; }
                    static int valueCall(int[] a) { a[0] = next(); return a[0]; }
                    static int calls;
                    static int next() { return ++calls; }
                    static int caught(int[] a, int i) {
                        try { return integers(a, i, 17); }
                        catch (NullPointerException e) { System.gc(); return -1; }
                        catch (ArrayIndexOutOfBoundsException e) { System.gc(); return -2; }
                    }
                    public static void main(String[] args) throws Exception {
                        int[] a = new int[]{1,2}, b = new int[]{31,37};
                        System.out.println(integers(a, 0, Integer.MIN_VALUE));
                        System.out.println(longs(new long[1], 0, Long.MAX_VALUE));
                        for (float v : new float[]{-0f, 0f, Float.MAX_VALUE, 16777216f, Float.POSITIVE_INFINITY, Float.NaN})
                            System.out.println(Float.floatToIntBits(floats(new float[1], 0, v)));
                        for (double v : new double[]{-0d, 0d, Double.MAX_VALUE, 0x1p53, Double.NEGATIVE_INFINITY, Double.NaN})
                            System.out.println(Double.doubleToLongBits(doubles(new double[1], 0, v)));
                        for (int v : new int[]{-1,128,65535,65536,Integer.MIN_VALUE})
                            System.out.println(narrow(new byte[1], new char[1], new short[1], v));
                        System.out.println(bool(new boolean[1], true));
                        System.out.println(alias(a,a)); System.out.println(alias(a,b));
                        System.out.println(index(a,0,0)); System.out.println(index(a,0,1));
                        System.out.println(locals(a,0)); System.out.println(reassign(a,b));
                        System.out.println(call(a)); System.out.println(reflected(a));
                        System.out.println(nativeCopy(a,b));
                        int[][] rows = new int[][]{a}, otherRows = new int[][]{b};
                        System.out.println(nested(rows,a)); System.out.println(nested(rows,b));
                        System.out.println(replaced(rows,otherRows,new int[]{31}));
                        System.out.println(replaced(rows,rows,new int[]{37}));
                        System.out.println(nestedCall(rows)); System.out.println(nestedVolatile(rows));
                        System.out.println(joined(a,true)); System.out.println(joined(a,false));
                        System.out.println(acquire(a)); System.out.println(monitor(a));
                        System.out.println(localValue(a,41)); System.out.println(valueCall(a) + ":" + calls);
                        System.out.println(caught(null,0)); System.out.println(caught(a,-1));
                        System.out.println(caught(a,Integer.MAX_VALUE));
                        Thread worker = new Thread(() -> { b[0] = 43; barrier = 1; });
                        worker.start(); while (barrier == 0) Thread.yield();
                        System.out.println(integers(b, 1, b[0])); worker.join();
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ArrayForward"), Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("ArrayForward")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .reflectMethod("ArrayForward", "mutate", "([I)V");
        var generated = builder.generate();
        String cpp = Files.readString(generated.request().generatedSourcesDirectory().resolve("classes/ArrayForward.cpp"));
        for (String name : List.of("integers", "longs", "floats", "doubles", "valueCall")) {
            String body = body(cpp, name);
            assertTrue(body.contains("array_set<"), body);
            assertFalse(body.contains("array_get<"), body);
            assertFalse(body.contains("_elements.get("), body);
        }
        for (String name : List.of("alias", "index", "locals", "reassign", "call", "nativeCopy", "reflected", "joined", "acquire"))
            assertTrue(body(cpp, name).contains("array_get<") || body(cpp, name).contains("_elements.get("), body(cpp, name));
        assertEquals(1, body(cpp, "nested").split("::jnative::reference_get\\(", -1).length - 1, body(cpp, "nested"));
        for (String name : List.of("replaced", "nestedCall", "nestedVolatile"))
            assertEquals(2, body(cpp, name).split("::jnative::reference_get\\(", -1).length - 1, body(cpp, name));
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String method) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n;]*\\bArrayForward::" + Pattern.quote(method) + "\\([^\\n;]*\\) \\{").matcher(cpp);
        assertTrue(declaration.find(), method);
        int end = cpp.indexOf("\n}", declaration.start());
        assertTrue(end > declaration.start(), method);
        return cpp.substring(declaration.start(), end);
    }
}
