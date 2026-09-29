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

class BoundedLibraryCallsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void boundedCopiesAndStringScalarsPreserveCollectionAndFailures(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("LibraryLeaves.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.util.Arrays;
                        public class LibraryLeaves {
                            static class Keep { int value = 43; }
                            static int text(String value, int index) {
                                return value.length() + (value.isEmpty() ? 0 : value.charAt(index));
                            }
                            static int identity(Object value) { return System.identityHashCode(value); }
                            static void small(float[] from, float[] to) { System.arraycopy(from, 0, to, 0, 16); }
                            static void small32(int[] from, int[] to) { System.arraycopy(from, 0, to, 0, 32); }
                            static void small33(int[] from, int[] to) { System.arraycopy(from, 0, to, 0, 33); }
                            static void small64(int[] from, int[] to) { System.arraycopy(from, 0, to, 0, 64); }
                            static void large65(int[] from, int[] to) { System.arraycopy(from, 0, to, 0, 65); }
                            static void references(Object[] from, String[] to) { System.arraycopy(from, 0, to, 0, 3); }
                            static void merged(int[] from, int[] to, boolean chooseVariable, int count) {
                                System.arraycopy(from, 0, to, 0, chooseVariable ? count : 16);
                            }
                            static void mergedOther(int[] from, int[] to, boolean chooseFixed, int count) {
                                System.arraycopy(from, 0, to, 0, chooseFixed ? 16 : count);
                            }
                            static void variable(int[] from, int[] to, int count) {
                                System.arraycopy(from, 0, to, 0, count);
                            }
                            static int effects;
                            static int[] source(int[] value) { effects = effects * 10 + 1; System.gc(); return value; }
                            static int index(int value, int tag) { effects = effects * 10 + tag; System.gc(); return value; }
                            static int[] destination(int[] value) { effects = effects * 10 + 3; System.gc(); return value; }
                            static int counted(int value) { effects = effects * 10 + 5; System.gc(); return value; }
                            static void evaluated(int[] from, int[] to, int count) {
                                effects = 0;
                                System.arraycopy(source(from), index(1, 2), destination(to), index(2, 4), counted(count));
                            }
                            static void overlap(int[] array, int count) {
                                System.arraycopy(array, 0, array, 1, count);
                                System.arraycopy(array, 1, array, 0, count);
                            }
                            static void variableReferences(Object[] from, String[] to, int count) {
                                System.arraycopy(from, 0, to, 0, count);
                            }
                            static int catchText(String value, int index) {
                                Keep keep = new Keep();
                                try { return text(value, index); }
                                catch (NullPointerException expected) { System.gc(); return keep.value; }
                                catch (StringIndexOutOfBoundsException expected) { System.gc(); return keep.value + value.length(); }
                            }
                            public static void main(String[] args) {
                                System.out.println(catchText(null, 0));
                                System.out.println(catchText("", 99));
                                System.out.println(catchText(new String(new char[]{'a', '\\uD834', '\\uDD1E'}), 1));
                                System.out.println(catchText(new String(new char[]{'b', 'c'}), 2));
                                Object object = new Object();
                                System.out.println(identity(object) == object.hashCode());
                                System.out.println(identity(null));
                                float[] from = new float[16];
                                for (int i = 0; i < from.length; i++) from[i] = i * .5f;
                                float[] to = new float[18];
                                small(from, to); System.gc();
                                System.out.println(Arrays.toString(to));
                                Keep keep = new Keep();
                                try { small(from, new float[15]); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.gc(); System.out.println(keep.value + from[15]); }
                                String[] narrow = {"first", "second", "third"};
                                try { references(new Object[]{"written", new Object(), "later"}, narrow); }
                                catch (ArrayStoreException expected) { System.gc(); System.out.println(Arrays.toString(narrow)); }
                                int[] large = new int[96];
                                for (int i = 0; i < large.length; i++) large[i] = i + 1;
                                int[] copied = new int[96];
                                merged(large, copied, true, 96);
                                System.out.println(copied[95]);
                                Arrays.fill(copied, 0);
                                merged(large, copied, false, 96);
                                System.out.println(copied[15] + ":" + copied[16]);
                                mergedOther(large, copied, false, 96);
                                System.out.println(copied[95]);
                                variable(large, copied, 0);
                                try { variable(large, copied, -1); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.gc(); System.out.println(copied[95]); }
                                Arrays.fill(copied, 0); small32(large, copied);
                                System.out.println("literal32:" + copied[31] + ":" + copied[32]);
                                Arrays.fill(copied, 0); small33(large, copied);
                                System.out.println("literal33:" + copied[32] + ":" + copied[33]);
                                Arrays.fill(copied, 0); small64(large, copied);
                                System.out.println("literal64:" + copied[63] + ":" + copied[64]);
                                Arrays.fill(copied, 0); large65(large, copied);
                                System.out.println("literal65:" + copied[64] + ":" + copied[65]);
                                for (int count : new int[] {0, 32, 33, 64, 65, -1}) {
                                    Arrays.fill(copied, 0);
                                    try {
                                        evaluated(large, copied, count);
                                        System.gc();
                                        System.out.println("evaluated:" + count + ":" + effects + ":" + copied[2] + ":" + copied[66]);
                                    } catch (ArrayIndexOutOfBoundsException expected) {
                                        System.gc(); System.out.println("evaluated-error:" + effects + ":" + keep.value);
                                    }
                                }
                                try { evaluated(null, copied, -1); }
                                catch (NullPointerException expected) {
                                    System.gc(); System.out.println("null-before-bounds:" + effects + ":" + keep.value);
                                }
                                try { evaluated(large, new int[65], 64); }
                                catch (ArrayIndexOutOfBoundsException expected) {
                                    System.gc(); System.out.println("destination-bounds:" + effects + ":" + keep.value);
                                }
                                for (int count : new int[] {32, 33, 64, 65}) {
                                    int[] shared = large.clone();
                                    overlap(shared, count); System.gc();
                                    System.out.println("overlap:" + count + ":" + Arrays.toString(shared));
                                }
                                String[] strings = {"first", "second", "third"};
                                try { variableReferences(new Object[]{"written", new Object(), "later"}, strings, 3); }
                                catch (ArrayStoreException expected) { System.gc(); System.out.println(Arrays.toString(strings)); }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "LibraryLeaves"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("LibraryLeaves")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generation = builder.generate();
        String cpp =
                Files.readString(
                        generation
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/LibraryLeaves.cpp"));
        for(String name : List.of("text", "identity", "small", "small32", "small33", "small64", "references")) {
            String body = body(cpp, name);
            assertFalse(body.contains("gc_roots"), body);
            assertFalse(body.contains("safepoint"), body);
        }
        for(String name : List.of("merged", "mergedOther", "variable", "large65", "evaluated", "overlap", "variableReferences", "catchText")) {
            assertTrue(body(cpp, name).contains("gc_roots"), body(cpp, name));
        }
        for(String name : List.of("variable", "evaluated", "overlap")) {
            assertTrue(body(cpp, name).contains("<= 64"), body(cpp, name));
            assertTrue(body(cpp, name).contains(" ? ::jnative::bounded_primitive_array_copy<std::int32_t>("), body(cpp, name));
        }
        for(String name : List.of("merged", "mergedOther", "variableReferences")) {
            assertTrue(body(cpp, name).contains("<= 64"), body(cpp, name));
            assertTrue(body(cpp, name).contains(" ? ::jnative::platform_System_arraycopy_7b15f0890b("), body(cpp, name));
            assertFalse(body(cpp, name).contains("bounded_primitive_array_copy"), body(cpp, name));
        }
        var result = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String method) {
        String type =
                List.of("text", "identity", "catchText").contains(method) ? "std::int32_t" : "void";
        int start = cpp.indexOf("\n" + type + " LibraryLeaves::" + method + "(");
        assertTrue(start >= 0, method);
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, method);
        return cpp.substring(start, end);
    }
}
