package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuilder;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class ConfinedArraysTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void localStorageStaysPrivateAndAllEscapingAliasesKeepAtomicStorage(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("PrivateArrays.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class PrivateArrays {
                            static volatile int[] published;
                            int[] field;
                            static int privateSum(int length) {
                                int[] values = new int[length];
                                int[] alias = values;
                                for (int i = 0; i < values.length; i++) alias[i] = i * 7;
                                System.gc();
                                int sum = 0;
                                for (int i = 0; i < values.length; i++) sum += values[i];
                                return sum;
                            }
                            static long allTypes() {
                                char[] chars = new char[3]; short[] shorts = new short[3];
                                long[] longs = {Long.MIN_VALUE, Long.MAX_VALUE, 3};
                                float[] floats = {1.5f, -0.0f, -2.5f};
                                double[] doubles = {2.25, -0.0, 9.5};
                                for (int i = 0; i < 3; i++) { chars[i] = (char)(65535 + i); shorts[i] = (short)(32767 + i); }
                                System.gc();
                                return chars[0] + chars[1] + shorts[1] + longs[0] + longs[1]
                                    + Float.floatToRawIntBits(floats[1]) + Double.doubleToRawLongBits(doubles[1]);
                            }
                            static int exceptionAtAccess(int size, int index) {
                                int[] values = new int[size];
                                values[0] = 3;
                                System.gc();
                                return values[index];
                            }
                            static int[] returned() { int[] a = {11}; return a; }
                            static void publish() { int[] a = {17}; int[] alias = a; published = alias; }
                            static int called() { int[] a = {19}; return read(a); }
                            static int read(int[] a) { System.gc(); return a[0]; }
                            static Object[] nested() { int[] a = {23}; return new Object[] {a}; }
                            static int phi(boolean choose, int[] incoming) {
                                int[] a = new int[] {29};
                                int[] value = choose ? a : incoming;
                                System.gc();
                                return value[0];
                            }
                            static int reversePhi(boolean choose, int[] incoming) {
                                int[] a = new int[] {31};
                                int[] value = choose ? incoming : a;
                                System.gc();
                                return value[0];
                            }
                            static int allocationsPhi(boolean choose) {
                                int[] a = new int[] {37}, b = new int[] {41};
                                int[] value = choose ? a : b;
                                return value[0];
                            }
                            static int nullable(boolean choose) {
                                int[] a = new int[] {43};
                                if (choose) a = null;
                                return a[0];
                            }
                            static int captured() throws Exception {
                                int[] a = {47};
                                Thread t = new Thread(() -> { a[0] = 53; System.gc(); });
                                t.start(); t.join();
                                return a[0];
                            }
                            static int loopAllocation(int count) {
                                int sum = 0;
                                for (int i = 0; i < count; i++) {
                                    int[] a = new int[3]; a[0] = i;
                                    System.gc(); sum += a[0];
                                }
                                return sum;
                            }
                            public static void main(String[] args) throws Exception {
                                System.out.println(privateSum(20) + ":" + privateSum(0));
                                System.out.println(allTypes());
                                System.out.println(loopAllocation(20));
                                for (int index : new int[] {-1, 0, 2}) {
                                    try { System.out.println(exceptionAtAccess(1, index)); }
                                    catch (ArrayIndexOutOfBoundsException e) { System.out.println("bounds"); }
                                }
                                try { privateSum(-1); } catch (NegativeArraySizeException e) { System.out.println("negative"); }
                                System.out.println(returned()[0] + ":" + called());
                                publish(); System.gc(); System.out.println(published[0]);
                                PrivateArrays holder = new PrivateArrays();
                                int[] escaped = {59}; holder.field = escaped;
                                System.gc(); System.out.println(holder.field[0]);
                                System.out.println(((int[])nested()[0])[0]);
                                for (boolean choose : new boolean[] {true, false}) {
                                    System.out.println(phi(choose, new int[] {61}));
                                    System.out.println(reversePhi(choose, new int[] {67}));
                                    System.out.println(allocationsPhi(choose));
                                    try { System.out.println(nullable(choose)); }
                                    catch (NullPointerException e) { System.out.println("null"); }
                                }
                                System.out.println(captured());
                                Thread collector = new Thread(() -> { for (int i = 0; i < 100; i++) System.gc(); });
                                collector.start();
                                int result = 0;
                                for (int i = 0; i < 100; i++) result += privateSum(200);
                                collector.join(); System.out.println(result);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "PrivateArrays"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("PrivateArrays")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generation = builder.generate();
        String code =
                Files.readString(
                        generation
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/PrivateArrays.cpp"));
        String local = method(code, "std::int32_t PrivateArrays::privateSum(");
        assertTrue(local.contains("new_confined_array<std::int32_t>"), local);
        assertTrue(local.contains("ConfinedArrayView<std::int32_t>"), local);
        for(String name :
                List.of("phi", "reversePhi", "allocationsPhi", "nullable", "called", "captured"))
            assertFalse(
                    method(code, "std::int32_t PrivateArrays::" + name + "(")
                            .contains("new_confined_array"),
                    name);
        assertFalse(method(code, "void PrivateArrays::publish(").contains("new_confined_array"));
        var compiled = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String method(String code, String signature) {
        String body = code.substring(code.indexOf("\n" + signature));
        return body.substring(0, body.indexOf("\n}"));
    }
}
