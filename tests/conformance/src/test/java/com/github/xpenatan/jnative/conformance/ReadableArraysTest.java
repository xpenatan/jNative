package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.*;

import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class ReadableArraysTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void dimensionsAndPartialArraysPreserveJavaBehavior(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Dimensions.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Dimensions {
                            static int order;
                            static int dimension(int value) { order = order * 10 + value; System.gc(); return value; }
                            static int[][] matrix(int rows, int columns) { return new int[rows][columns]; }
                            public static void main(String[] args) {
                                int[][] values = matrix(dimension(2), dimension(3));
                                values[1][2] = 19;
                                System.gc();
                                System.out.println(order + ":" + values.length + ":" + values[0].length + ":" + values[1][2] + ":" + values[0][2]);
                                Object[][][] objects = new String[2][3][];
                                objects[1][2] = new String[]{new String("retained")};
                                System.gc();
                                System.out.println(objects[0][0] == null);
                                System.out.println(objects[1][2][0]);
                                System.out.println(objects.getClass().getName());
                                long[][] wide = new long[1][2];
                                double[][][] decimals = new double[1][1][2];
                                boolean[][] flags = new boolean[1][2];
                                byte[][] bytes = new byte[1][2];
                                short[][] shorts = new short[1][2];
                                char[][] chars = new char[1][2];
                                float[][] floats = new float[1][2];
                                wide[0][1] = Long.MIN_VALUE; decimals[0][0][1] = -0.0;
                                flags[0][1] = true; bytes[0][1] = -128; shorts[0][1] = -32768;
                                chars[0][1] = 'Z'; floats[0][1] = 1.5f;
                                System.gc();
                                System.out.println(wide[0][1] + ":" + decimals[0][0][1] + ":" + flags[0][1]);
                                System.out.println(bytes[0][1] + ":" + shorts[0][1] + ":" + chars[0][1] + ":" + floats[0][1]);
                                order = 0;
                                try { int[][] invalid = new int[dimension(-1)][dimension(2)]; }
                                catch (NegativeArraySizeException error) { System.out.println("negative:" + order); }
                                order = 0;
                                try { int[][] invalid = new int[dimension(0)][dimension(-1)]; }
                                catch (NegativeArraySizeException error) { System.out.println("zero:" + order); }
                                order = 0;
                                try { int[][] invalid = new int[dimension(0)][10 / order]; }
                                catch (ArithmeticException error) { System.out.println("arithmetic"); }
                                try { objects[0][0] = new Object[1]; }
                                catch (ArrayStoreException error) { System.out.println("store"); }
                                try { int missing = values[2][0]; }
                                catch (ArrayIndexOutOfBoundsException error) { System.out.println("bounds"); }
                                try { float[] absent = null; System.out.println(absent[0]); }
                                catch (NullPointerException error) { System.out.println("null primitive"); }
                                try { Object[] absent = null; System.out.println(absent[0]); }
                                catch (NullPointerException error) { System.out.println("null reference"); }
                                try { boolean missing = (new boolean[0])[0]; }
                                catch (ArrayIndexOutOfBoundsException error) { System.out.println("empty boolean"); }
                                try { bytes[0][-1] = 1; }
                                catch (ArrayIndexOutOfBoundsException error) { System.out.println("negative byte"); }
                                try { floats[0][2] = 1; }
                                catch (ArrayIndexOutOfBoundsException error) { System.out.println("float bounds"); }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, buildType == BuildType.DEBUG ? 17 : 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Dimensions"),
                        Duration.ofSeconds(20));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Dimensions")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType)
                        .debugInformation(true);
        var generation = builder.generate();
        String report =
                Files.readString(
                        generation
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("source-readability.tsv"));
        assertFalse(report.contains("low-level"), report);
        String cpp =
                Files.readString(
                        generation
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/Dimensions.cpp"));
        assertTrue(cpp.contains("::jnative::multi_array(\"[[I\", {rows, columns})"), cpp);
        var result = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(buildType == BuildType.RELEASE && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, result, expected, 1);
    }
}
