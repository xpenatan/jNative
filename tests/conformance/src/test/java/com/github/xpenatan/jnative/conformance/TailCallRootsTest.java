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
import java.util.regex.Pattern;

class TailCallRootsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void finalCalleesRetainArgumentsWithoutRemovingRecursivePolls(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("TailRoots.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class TailRoots {
                            static class Payload {
                                int value;
                                Payload(int value) { this.value = value; }
                                Payload direct(Payload other, int[] values) {
                                    return collect(other, values[0] + values[1]);
                                }
                                final Payload collect(Payload other, int delta) {
                                    System.gc();
                                    value += delta;
                                    other.value += value;
                                    return other;
                                }
                            }
                            interface Action { Payload apply(Payload value); }
                            static class First implements Action {
                                public Payload apply(Payload value) { System.gc(); value.value += 7; return value; }
                            }
                            static class Second implements Action {
                                public Payload apply(Payload value) { System.gc(); value.value += 13; return value; }
                            }
                            static class Fast implements Action {
                                public Payload apply(Payload value) { return value; }
                            }
                            interface CollectingAction { Payload apply(Payload value); }
                            static class CollectingFirst implements CollectingAction {
                                public Payload apply(Payload value) { System.gc(); value.value += 19; return value; }
                            }
                            static class CollectingSecond implements CollectingAction {
                                public Payload apply(Payload value) { System.gc(); value.value += 23; return value; }
                            }
                            static class InitializedCaller {
                                static int value = initialize();
                                static int initialize() { System.gc(); System.out.println("caller initialized"); return 3; }
                                static Payload pass(Payload value) { return InitializedTarget.apply(value); }
                            }
                            static class InitializedTarget {
                                static int value = initialize();
                                static int initialize() { System.gc(); System.out.println("target initialized"); return 5; }
                                static Payload apply(Payload payload) { System.gc(); payload.value += value; return payload; }
                            }
                            static class PrefixInitialization {
                                static int value = initialize();
                                static int initialize() { System.gc(); System.out.println("prefix initialized"); return 31; }
                            }
                            static Payload target(Payload value, int delta) {
                                System.gc(); value.value += delta; return value;
                            }
                            static Payload direct(Payload value, int delta) { return target(value, delta + 1); }
                            static Payload first(Payload[] values) { return target(values[0], 2); }
                            static Payload dispatch(CollectingAction action, Payload value) { return action.apply(value); }
                            static Payload mixed(Action action, Payload value) { return action.apply(value); }
                            static Payload chainOuter(Payload value) { return chainInner(value); }
                            static Payload chainInner(Payload value) { return target(value, 3); }
                            static Payload afterward(Payload value) { target(value, 4); System.gc(); return value; }
                            static Payload catches(Payload value) {
                                try { return target(value, 5); }
                                catch (NullPointerException expected) { System.gc(); return value; }
                            }
                            static Payload allocating(Payload value) { return target(new Payload(value.value), 6); }
                            static Payload initialization(Payload value) { return target(value, PrefixInitialization.value); }
                            static void put(Payload value, int delta) { consume(value, delta); }
                            static void consume(Payload value, int delta) { System.gc(); value.value += delta; }
                            static void runtime(Payload unused) { System.gc(); }
                            static int scalar(Payload value) { return scalarTarget(value); }
                            static int scalarTarget(Payload value) { System.gc(); return value.value; }
                            static long wide(Payload value) { return wideTarget(value); }
                            static long wideTarget(Payload value) { System.gc(); return value.value * 10000000000L; }
                            static double floating(Payload value) { return floatingTarget(value); }
                            static double floatingTarget(Payload value) { System.gc(); return value.value * .5; }
                            static Payload recursive(Payload value, int count) { return recursiveBody(value, count); }
                            static Payload recursiveBody(Payload value, int count) {
                                if (count == 0) return target(value, 1);
                                return recursive(value, count - 1);
                            }
                            // Keep the cycle reachable without executing an unbounded recursion.
                            static Payload cycleA(Payload value) { return cycleB(value); }
                            static Payload cycleB(Payload value) { return cycleA(value); }
                            static synchronized Payload locked(Payload value) { return target(value, 1); }
                            public static void main(String[] args) throws Exception {
                                if (args.length > 100) cycleA(new Payload(1));
                                System.out.println(InitializedCaller.pass(new Payload(10)).value);
                                System.out.println(direct(new Payload(20), 2).value);
                                System.out.println(first(new Payload[]{new Payload(30)}).value);
                                System.out.println(new Payload(40).direct(new Payload(50), new int[]{2, 3}).value);
                                System.out.println(dispatch(new CollectingFirst(), new Payload(60)).value);
                                System.out.println(dispatch(new CollectingSecond(), new Payload(70)).value);
                                System.out.println(mixed(new First(), new Payload(80)).value);
                                System.out.println(mixed(new Second(), new Payload(90)).value);
                                System.out.println(mixed(new Fast(), new Payload(100)).value);
                                System.out.println(chainOuter(new Payload(110)).value);
                                System.out.println(afterward(new Payload(120)).value);
                                System.out.println(catches(new Payload(130)).value);
                                System.out.println(catches(null) == null);
                                System.out.println(allocating(new Payload(140)).value);
                                System.out.println(initialization(new Payload(150)).value);
                                System.out.println(locked(new Payload(160)).value);
                                Payload value = new Payload(170);
                                runtime(value);
                                put(value, 4); System.out.println(value.value);
                                System.out.println(scalar(value));
                                System.out.println(wide(value));
                                System.out.println(floating(value));
                                System.out.println(recursive(new Payload(180), 250).value);
                                try { new Payload(1).direct(new Payload(2), null); }
                                catch (NullPointerException expected) { System.gc(); System.out.println("null array"); }
                                try { new Payload(1).direct(new Payload(2), new int[1]); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.gc(); System.out.println("short array"); }
                                try { dispatch(null, new Payload(2)); }
                                catch (NullPointerException expected) { System.gc(); System.out.println("null target"); }
                                int[] results = new int[2];
                                Thread one = new Thread(() -> results[0] = direct(new Payload(190), 1).value);
                                Thread two = new Thread(() -> results[1] = dispatch(new CollectingSecond(), new Payload(200)).value);
                                one.start(); two.start(); one.join(); two.join();
                                System.out.println(results[0] + ":" + results[1]);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "TailRoots"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("TailRoots")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        Path output = generated.request().generatedSourcesDirectory();
        String cpp = Files.readString(output.resolve("classes/TailRoots.cpp"));
        for(String method :
                List.of(
                        "direct",
                        "first",
                        "dispatch",
                        "chainInner",
                        "put",
                        "scalar",
                        "wide",
                        "floating",
                        "recursive")) {
            String body = body(cpp, "TailRoots", method);
            assertFalse(body.contains("gc_roots"), body);
            assertFalse(body.contains("safepoint"), body);
        }
        for(String method :
                List.of(
                        "mixed",
                        "chainOuter",
                        "afterward",
                        "catches",
                        "allocating",
                        "initialization",
                        "cycleA",
                        "cycleB",
                        "locked")) {
            assertTrue(body(cpp, "TailRoots", method).contains("gc_roots"), method);
        }
        String runtime = body(cpp, "TailRoots", "runtime");
        assertTrue(runtime.contains("poll_if_requested") || runtime.contains("safepoint"), runtime);
        String instance = Files.readString(output.resolve("classes/TailRoots_Payload.cpp"));
        assertFalse(body(instance, "TailRoots_Payload", "direct").contains("gc_roots"), instance);
        assertTrue(body(instance, "TailRoots_Payload", "collect").contains("gc_roots"), instance);
        String initialized =
                Files.readString(output.resolve("classes/TailRoots_InitializedCaller.cpp"));
        assertTrue(
                body(initialized, "TailRoots_InitializedCaller", "pass")
                        .contains("initialization_roots"),
                initialized);
        assertFalse(
                body(initialized, "TailRoots_InitializedCaller", "pass").contains("gc_roots"),
                initialized);
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String owner, String method) {
        var definition =
                Pattern.compile("(?m)^[\\w:*]+ " + Pattern.quote(owner + "::" + method + "("))
                        .matcher(cpp);
        assertTrue(definition.find(), method);
        int start = definition.start();
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, method);
        return cpp.substring(start, end);
    }
}
