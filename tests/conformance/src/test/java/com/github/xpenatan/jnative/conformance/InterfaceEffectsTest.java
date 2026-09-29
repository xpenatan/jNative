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

class InterfaceEffectsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void defaultMethodsShareTheEffectsOfEveryPossibleTarget(BuildType buildType) throws Exception {
        Path source = temporary.resolve("InterfaceLeaves.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class InterfaceLeaves {
                            interface Scalar {
                                int value();
                                default int total() { return value() + value(); }
                                default Object receiver() { return identity(this); }
                                private Object identity(Object value) { return value; }
                                default int divide(int divisor) { return value() / divisor; }
                            }
                            interface Left extends Scalar { }
                            interface Right extends Scalar { }
                            interface Diamond extends Left, Right {
                                default int total() { return Left.super.total() + 3; }
                            }
                            static class First implements Diamond {
                                final int stored;
                                First(int stored) { this.stored = stored; }
                                public int value() { return stored; }
                            }
                            static class Second implements Scalar {
                                public int value() { return 7; }
                            }
                            interface Choice {
                                Object value();
                                default Object choose(Object fallback) {
                                    Object selected = value();
                                    return selected == null ? fallback : selected;
                                }
                            }
                            static class Empty implements Choice {
                                public Object value() { return null; }
                            }
                            static class Collecting implements Choice {
                                final String stored = new String("stored");
                                public Object value() { System.gc(); return stored; }
                            }
                            interface Guard {
                                default Object select(Object value, int mode) {
                                    if (mode < 0) throw new IllegalArgumentException("guard");
                                    return value;
                                }
                                default Object handled(Object value) {
                                    try { return select(value, -1); }
                                    catch (IllegalArgumentException expected) {
                                        System.gc(); return value;
                                    }
                                }
                            }
                            static class Guarded implements Guard { }
                            interface Initialization {
                                Object MARKER = initialize();
                                static Object initialize() {
                                    System.gc(); System.out.println("initialized"); return new Object();
                                }
                                static Object identity(Object value) { return value; }
                            }
                            interface Broken {
                                Object MARKER = initialize();
                                static Object initialize() {
                                    System.gc(); throw new IllegalStateException("initialization");
                                }
                                static Object identity(Object value) { return value; }
                            }
                            static int twice(Scalar scalar) { return scalar.total() * 2; }
                            static String checked(Scalar scalar, int divisor) {
                                String retained = new String("survivor");
                                try { return Integer.toString(scalar.divide(divisor)); }
                                catch (NullPointerException expected) { System.gc(); return retained + ":null"; }
                                catch (ArithmeticException expected) { System.gc(); return retained + ":division"; }
                            }
                            public static void main(String[] args) throws Exception {
                                Scalar first = new First(11), second = new Second();
                                System.out.println(twice(first) + ":" + twice(second));
                                System.out.println(first.receiver() == first);
                                System.out.println(second.receiver() == second);
                                System.out.println(checked(first, 2));
                                System.out.println(checked(first, 0));
                                System.out.println(checked(null, 1));
                                Choice[] choices = { new Empty(), new Collecting() };
                                for (Choice choice : choices) {
                                    Object fallback = new String("fallback");
                                    System.out.println(choice.choose(fallback));
                                    System.gc();
                                    System.out.println(fallback);
                                }
                                Guard guard = new Guarded();
                                System.out.println(guard.select(new String("normal"), 1));
                                System.out.println(guard.handled(new String("caught")));
                                System.out.println(Initialization.identity(new String("argument")));
                                try { Broken.identity(new String("first")); }
                                catch (ExceptionInInitializerError expected) { System.out.println("first failure"); }
                                try { Broken.identity(new String("second")); }
                                catch (NoClassDefFoundError expected) { System.out.println("cached failure"); }
                                Thread collector = new Thread(() -> {
                                    for (int i = 0; i < 100; i++) System.gc();
                                });
                                collector.start();
                                int sum = 0;
                                for (int i = 0; i < 1000; i++) {
                                    sum += twice(first) + twice(second);
                                    if (first.receiver() != first || second.receiver() != second)
                                        throw new IllegalStateException("interface receiver changed");
                                }
                                collector.join();
                                System.out.println(sum);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(
                                ProcessHarness.java(),
                                "-cp",
                                classes.toString(),
                                "InterfaceLeaves"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("InterfaceLeaves")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        Path sources = generated.request().generatedSourcesDirectory();
        String scalar = Files.readString(sources.resolve("classes/InterfaceLeaves_Scalar.cpp"));
        for(String method : List.of("total", "receiver", "identity", "divide")) {
            String body = body(scalar, "InterfaceLeaves_Scalar::" + method + "(");
            assertFalse(body.contains("gc_roots"), body);
            assertFalse(body.contains("safepoint"), body);
            assertFalse(body.contains("this"), body);
        }
        String choice = Files.readString(sources.resolve("classes/InterfaceLeaves_Choice.cpp"));
        assertTrue(body(choice, "InterfaceLeaves_Choice::choose(").contains("gc_roots"), choice);
        String api = Files.readString(sources.resolve("runtime_support.cpp"));
        int namespace = api.indexOf("namespace InterfaceLeaves_Scalar {");
        assertTrue(namespace >= 0, api);
        String boundedAdapter = body(api.substring(namespace), "total(");
        assertFalse(boundedAdapter.contains("LocalRoot"), boundedAdapter);
        namespace = api.indexOf("namespace InterfaceLeaves_Choice {");
        assertTrue(namespace >= 0, api);
        String collectingAdapter = body(api.substring(namespace), "choose(");
        assertTrue(collectingAdapter.contains("LocalRoot"), collectingAdapter);
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String source, String declaration) {
        int start = source.indexOf("\nstd::int32_t " + declaration);
        if(start < 0) start = source.indexOf("\n::jnative::Object* " + declaration);
        int end = source.indexOf("\n}", start);
        assertTrue(start >= 0 && end > start, declaration + "\n" + source);
        return source.substring(start, end);
    }
}
