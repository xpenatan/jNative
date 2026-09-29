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

class ConditionalInitializationTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void lazyInitializationRootsArgumentsAndPreservesFailureAndWorkerOrdering(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ConditionalInitialization.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class ConditionalInitialization {
                            static final class Payload {
                                final String text;
                                int number;
                                Payload(String text) { this.text = text; }
                                Payload select(Payload other, int choice) {
                                    return Helpers.select(this, other, choice);
                                }
                            }
                            static final class Helpers {
                                static Payload select(Payload first, Payload second, int choice) {
                                    int value = choice == 0 ? Left.value : choice == 1 ? Right.value : Broken.value;
                                    first.number += value;
                                    return second;
                                }
                            }
                            static class Left {
                                static int value = initialize();
                                static int initialize() {
                                    System.gc(); System.out.println("left initialized"); return 11;
                                }
                            }
                            static class Right {
                                static int value = initialize();
                                static int initialize() {
                                    System.gc(); System.out.println("right initialized"); return 19;
                                }
                            }
                            static class Broken {
                                static int value = initialize();
                                static int initialize() {
                                    System.gc(); System.out.println("broken initialized");
                                    throw new IllegalStateException("expected failure");
                                }
                            }
                            static class Slow {
                                static int value = initialize();
                                static int initialize() {
                                    try { Thread.sleep(40); }
                                    catch (InterruptedException failure) { throw new RuntimeException(failure); }
                                    System.gc(); return 31;
                                }
                            }
                            static int readSlow(Payload value) { return Slow.value + value.text.length(); }
                            static class Failure extends RuntimeException {
                                final Payload kept;
                                Failure(Payload kept, Throwable cause) {
                                    super(cause); System.gc(); this.kept = kept;
                                }
                            }
                            static int narrow(long value, Payload kept) {
                                try { return Math.toIntExact(value); }
                                catch (ArithmeticException failure) {
                                    System.gc(); throw new Failure(kept, failure);
                                }
                            }
                            static int narrowChain(long value, Payload kept) { return narrow(value, kept) + 1; }
                            static Payload recover(Payload kept) {
                                try { narrow(Long.MAX_VALUE, kept); return null; }
                                catch (Failure expected) { System.gc(); return expected.kept; }
                            }
                            static Payload passWithoutUse(Payload value, boolean access) {
                                if (access) value.number += Right.value;
                                return value;
                            }
                            public static void main(String[] args) throws Exception {
                                Payload first = new Payload(new String("first"));
                                Payload second = new Payload(new String("second"));
                                System.out.println(passWithoutUse(first, false).text);
                                System.out.println(first.select(second, 0).text + ":" + first.number);
                                System.gc();
                                System.out.println(first.select(second, 0).text + ":" + first.number);
                                System.out.println(first.select(second, 1).text + ":" + first.number);
                                try { first.select(second, 2); }
                                catch (ExceptionInInitializerError expected) {
                                    System.gc(); System.out.println(first.text + ":" + second.text);
                                }
                                try { first.select(second, 2); }
                                catch (NoClassDefFoundError expected) {
                                    System.gc(); System.out.println(first.text + ":" + second.text);
                                }
                                int[] results = new int[2];
                                Thread a = new Thread(() -> results[0] = readSlow(new Payload(new String("worker one"))));
                                Thread b = new Thread(() -> results[1] = readSlow(new Payload(new String("worker two"))));
                                a.start(); b.start(); a.join(); b.join();
                                System.out.println(results[0] + ":" + results[1]);
                                System.out.println(passWithoutUse(second, true).number);
                                System.out.println(narrowChain(42, new Payload(new String("success"))));
                                try { narrowChain(Long.MAX_VALUE, new Payload(new String("failed narrowing"))); }
                                catch (Failure expected) { System.gc(); System.out.println(expected.kept.text); }
                                System.out.println(recover(new Payload(new String("recovered narrowing"))).text);
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
                                "ConditionalInitialization"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("ConditionalInitialization")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        Path output = generated.request().generatedSourcesDirectory();
        String cpp =
                Files.readString(output.resolve("classes/ConditionalInitialization_Helpers.cpp"));
        assertTrue(cpp.contains("DeferredRootFrame"), cpp);
        assertTrue(cpp.contains("ConditionalInitialization_Left::initialization_complete"), cpp);
        assertTrue(cpp.contains("ConditionalInitialization_Right::initialization_complete"), cpp);
        assertTrue(cpp.contains("ConditionalInitialization_Broken::initialization_complete"), cpp);
        String handlers = Files.readString(output.resolve("classes/ConditionalInitialization.cpp"));
        int start = handlers.indexOf("\nstd::int32_t ConditionalInitialization::narrow(");
        int end = handlers.indexOf("\n}", start);
        assertTrue(start >= 0 && end > start, handlers);
        String narrowed = handlers.substring(start, end);
        assertTrue(narrowed.contains("::jnative::RootFrame<"), narrowed);
        assertFalse(narrowed.contains("poll_if_requested"), narrowed);
        assertFalse(narrowed.contains("::jnative::safepoint"), narrowed);
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
