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
        for(String name : List.of("text", "identity", "small", "references")) {
            String body = body(cpp, name);
            assertFalse(body.contains("gc_roots"), body);
            assertFalse(body.contains("safepoint"), body);
        }
        for(String name : List.of("merged", "mergedOther", "variable", "catchText")) {
            assertTrue(body(cpp, name).contains("gc_roots"), body(cpp, name));
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
