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

class NullCheckRootsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void successfulReceiverChecksDoNotCollectButHandlersKeepTheirReferences(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ReceiverChecks.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class ReceiverChecks {
                            static class Box {
                                Box next;
                                int value;
                                Box(Box next, int value) { this.next = next; this.value = value; }
                                final Box child() { return next; }
                                final int value() { return value; }
                                final int accept(Box other) { return value + other.value; }
                                final int collecting(Box other) { System.gc(); return value + other.value; }
                            }
                            static int success(Box root) {
                                System.gc();
                                return root.child().child().value();
                            }
                            static int catches(Box root) {
                                Box retained = new Box(null, 31);
                                try { return root.child().child().value(); }
                                catch (NullPointerException failure) {
                                    System.gc(); return retained.value + (root == null ? 0 : root.value);
                                }
                            }
                            static Box argument() {
                                System.gc(); System.out.println("argument evaluated"); return new Box(null, 19);
                            }
                            static int evaluation(Box root) { return root.child().accept(argument()); }
                            static int collecting(Box root) {
                                return root.child().collecting(new Box(null, 23));
                            }
                            static int inlineValue(Box root) {
                                return root.child().accept(new Box(null, 29));
                            }
                            static int finallyPath(Box root) {
                                Box retained = new Box(null, 37);
                                try { return root.child().value(); }
                                finally { System.gc(); System.out.println(retained.value); }
                            }
                            static class Initialized {
                                static Box kept = initialize();
                                static Box initialize() { System.gc(); return new Box(null, 41); }
                            }
                            static int initialization(Box root) {
                                Box child = root.child();
                                return child.accept(Initialized.kept);
                            }
                            public static void main(String[] args) throws Exception {
                                Box root = new Box(new Box(new Box(null, 7), 5), 3);
                                System.out.println(success(root));
                                System.out.println(catches(root));
                                System.out.println(catches(null));
                                System.out.println(catches(new Box(null, 11)));
                                System.out.println(catches(new Box(new Box(null, 17), 13)));
                                System.out.println(evaluation(root));
                                try { evaluation(new Box(null, 1)); }
                                catch (NullPointerException failure) { System.gc(); System.out.println("null receiver after argument"); }
                                System.out.println(collecting(new Box(new Box(null, 43), 0)));
                                System.out.println(inlineValue(new Box(new Box(null, 47), 0)));
                                System.out.println(finallyPath(root));
                                try { finallyPath(new Box(null, 1)); }
                                catch (NullPointerException failure) { System.gc(); System.out.println("finally failure"); }
                                System.out.println(initialization(new Box(new Box(null, 53), 0)));
                                int[] results = new int[2];
                                Thread one = new Thread(() -> results[0] = catches(new Box(null, 59)));
                                Thread two = new Thread(() -> results[1] = collecting(new Box(new Box(null, 61), 0)));
                                one.start(); two.start(); one.join(); two.join();
                                System.out.println(results[0] + ":" + results[1]);
                                System.gc(); System.out.println(success(root));
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "ReceiverChecks"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("ReceiverChecks")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/ReceiverChecks.cpp"));
        String success = body(cpp, "success");
        assertTrue(success.contains("RootFrame<1>"), success);
        assertFalse(success.contains("FrameRoot<> child_result"), success);
        for(String method :
                List.of(
                        "catches",
                        "collecting",
                        "inlineValue",
                        "evaluation",
                        "finallyPath",
                        "initialization")) {
            assertTrue(body(cpp, method).contains("gc_roots"), method);
        }
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String method) {
        int start = cpp.indexOf("\nstd::int32_t ReceiverChecks::" + method + "(");
        assertTrue(start >= 0, method);
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, method);
        return cpp.substring(start, end);
    }
}
