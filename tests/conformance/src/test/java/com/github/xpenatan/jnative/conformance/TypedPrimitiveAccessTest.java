package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TypedPrimitiveAccessTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void typedAccessKeepsValuesExceptionsEvaluationOrderAndRoots(BuildType buildType) throws Exception {
        Path source = temporary.resolve("PrimitiveAccess.java"), classes = temporary.resolve("classes");
        Files.writeString(source, """
                public class PrimitiveAccess {
                    static volatile Object published;
                    static int sequence;
                    static int index(boolean fail) {
                        sequence = sequence * 10 + 1;
                        if(fail) throw new IllegalStateException();
                        return 0;
                    }
                    static float value() { sequence = sequence * 10 + 2; System.gc(); return -0.0f; }
                    static int ints(int[] a, int i, int v) { a[i] = v; return a[i]; }
                    static long longs(long[] a, int i, long v) { a[i] = v; return a[i]; }
                    static float floats(float[] a, int i, float v) { a[i] = v; return a[i]; }
                    static double doubles(double[] a, int i, double v) { a[i] = v; return a[i]; }
                    static char chars(char[] a, int i, char v) { a[i] = v; return a[i]; }
                    static short shorts(short[] a, int i, short v) { a[i] = v; return a[i]; }
                    static float erased(Object a) { return ((float[])a)[0]; }
                    public static void main(String[] args) {
                        int[] a = new int[3]; long[] b = new long[3]; float[] c = new float[3];
                        double[] d = new double[3]; char[] e = new char[3]; short[] f = new short[3];
                        Object[] roots = {a,b,c,d,e,f}; published = roots;
                        for(int i = 0; i < 3; i++) {
                            System.out.println(ints(a,i,Integer.MIN_VALUE+i));
                            System.out.println(longs(b,i,Long.MAX_VALUE-i));
                            System.out.println(Float.floatToRawIntBits(floats(c,i,Float.intBitsToFloat(0x80000000+i))));
                            System.out.println(Double.doubleToRawLongBits(doubles(d,i,Double.longBitsToDouble(0x8000000000000000L+i))));
                            System.out.println((int)chars(e,i,(char)(65535-i)));
                            System.out.println(shorts(f,i,(short)(32768+i)));
                        }
                        for(int i : new int[]{-1,3,Integer.MAX_VALUE}) {
                            try { floats(c,i,1); } catch(ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
                        }
                        float[] missing = null;
                        try { missing[index(false)] = value(); }
                        catch(NullPointerException expected) { System.out.println(sequence); }
                        sequence = 0;
                        try { floats(missing,index(false),value()); }
                        catch(NullPointerException expected) { System.out.println(sequence); }
                        sequence = 0;
                        try { missing[index(true)] = value(); }
                        catch(IllegalStateException expected) { System.out.println(sequence); }
                        try { erased(a); } catch(ClassCastException expected) { System.out.println("cast"); }
                        System.out.println(Float.floatToRawIntBits(erased(c)));
                        float[][] rows = {c};
                        float[] saved = rows[0]; rows[0] = new float[1]; System.gc();
                        System.out.println(Float.floatToRawIntBits(floats(saved,2,Float.MIN_VALUE)));
                        try { floats(rows[0],2,1); } catch(ArrayIndexOutOfBoundsException expected) { System.out.println("row"); }
                        byte[] bytes = {-1}; boolean[] flags = {true};
                        System.out.println(bytes[0]); System.out.println(flags[0]);
                        published = null;
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "PrimitiveAccess"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("PrimitiveAccess")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generated = builder.generate();
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
