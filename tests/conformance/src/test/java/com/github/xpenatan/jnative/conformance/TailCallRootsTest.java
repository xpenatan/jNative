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
                                Payload prefix(Payload[] others) {
                                    return collect(read(others), ExtractionInitialization.value);
                                }
                                Payload read(Payload[] others) { return others[0]; }
                                Payload boundedRead(Payload[] others, boolean fail) {
                                    Payload other = others[0];
                                    value += BoundedFirst.value + BoundedSecond.value;
                                    if(fail) {
                                        System.gc();
                                        System.out.println("bounded failure:" + (other.value + value));
                                        throw new IllegalStateException("bounded failure");
                                    }
                                    other.value += value;
                                    return other;
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
                            static class InitializedPrefixCaller {
                                static int value = initialize();
                                static int initialize() { System.gc(); System.out.println("prefix caller initialized"); return 41; }
                                static Payload pass(Payload value) { return target(value, PrefixInitialization.value); }
                            }
                            static class TransitiveFirst {
                                static int value = initialize();
                                static int initialize() { System.gc(); System.out.println("transitive first"); return 7; }
                            }
                            static class TransitiveSecond {
                                static int value = initialize();
                                static int initialize() { System.gc(); System.out.println("transitive second"); return 11; }
                            }
                            static class TransitiveReader {
                                static int read() { return TransitiveFirst.value + TransitiveSecond.value; }
                            }
                            static Payload[] extractionSource;
                            static Payload[] boundedSource;
                            static class BoundedFirst {
                                static int value = initialize();
                                static int initialize() {
                                    boundedSource[0] = null; boundedSource = null;
                                    System.gc(); System.out.println("bounded first"); return 3;
                                }
                            }
                            static class BoundedSecond {
                                static int value = initialize();
                                static int initialize() { System.gc(); System.out.println("bounded second"); return 5; }
                            }
                            static class ExtractionInitialization {
                                static int value = initialize();
                                static int initialize() {
                                    extractionSource[0] = null;
                                    extractionSource = null;
                                    System.gc();
                                    return 13;
                                }
                            }
                            static class ReentrantInitialization {
                                static int value = initialize();
                                static int initialize() {
                                    System.gc();
                                    System.out.println("reentrant:" + reentrant(fresh(270)).value);
                                    return 17;
                                }
                            }
                            static class FailedInitialization {
                                static int value = initialize();
                                static int initialize() { System.gc(); throw new IllegalStateException("failed prefix"); }
                            }
                            static volatile int prefixAttempts;
                            static volatile boolean initializerStarted, releaseInitializer;
                            static class ConcurrentInitialization {
                                static int value = initialize();
                                static int initialize() {
                                    initializerStarted = true;
                                    while (!releaseInitializer) Thread.yield();
                                    System.gc();
                                    return 37;
                                }
                            }
                            static Payload fresh(int value) { return new Payload(value); }
                            static Payload[] freshArray(int value) { return new Payload[]{fresh(value)}; }
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
                            static Payload transitive(Payload value) { return target(value, TransitiveReader.read()); }
                            static Payload extraction(Payload[] values) {
                                return target(values[0], ExtractionInitialization.value);
                            }
                            static Payload reentrant(Payload value) { return target(value, ReentrantInitialization.value); }
                            static Payload failed(Payload value) { return target(value, FailedInitialization.value); }
                            static Payload concurrent(Payload value) {
                                ++prefixAttempts;
                                return target(value, ConcurrentInitialization.value);
                            }
                            static int upstream(Payload value) {
                                Payload spare = fresh(value.value + 1000);
                                initialization(value);
                                return spare.value + value.value;
                            }
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
                                System.out.println(initialization(fresh(151)).value);
                                extractionSource = freshArray(50);
                                System.out.println(new Payload(40).prefix(extractionSource).value);
                                System.out.println(new Payload(41).prefix(freshArray(51)).value);
                                boundedSource = freshArray(310);
                                System.out.println(new Payload(42).boundedRead(boundedSource, false).value);
                                System.out.println(new Payload(43).boundedRead(freshArray(311), false).value);
                                try { new Payload(44).boundedRead(freshArray(312), true); }
                                catch(IllegalStateException expected) { System.gc(); System.out.println("bounded caught"); }
                                System.out.println(InitializedPrefixCaller.pass(fresh(152)).value);
                                System.out.println(transitive(fresh(240)).value);
                                System.out.println(transitive(fresh(241)).value);
                                extractionSource = freshArray(250);
                                System.out.println(extraction(extractionSource).value);
                                System.out.println(reentrant(fresh(280)).value);
                                System.out.println(reentrant(fresh(281)).value);
                                try { failed(fresh(290)); }
                                catch (ExceptionInInitializerError expected) { System.gc(); System.out.println("failed first"); }
                                try { failed(fresh(291)); }
                                catch (NoClassDefFoundError expected) { System.gc(); System.out.println("failed again"); }
                                System.out.println(upstream(fresh(300)));
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
                                Thread initializing = new Thread(() -> results[0] = concurrent(fresh(301)).value);
                                Thread waiting = new Thread(() -> results[1] = concurrent(fresh(302)).value);
                                initializing.start();
                                while (!initializerStarted) Thread.yield();
                                waiting.start();
                                while (prefixAttempts < 2) Thread.yield();
                                // Both prefix guards have executed before their volatile counters.
                                // Collect while initialization is still incomplete on both threads.
                                System.gc();
                                releaseInitializer = true;
                                initializing.join(); waiting.join();
                                System.out.println(results[0] + ":" + results[1]);
                                System.out.println(concurrent(fresh(303)).value);
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
        String effect =
                Files.readString(output.resolve("method-effects.tsv")).lines()
                        .filter(line -> line.startsWith("TailRoots.initialization("))
                        .findFirst()
                        .orElseThrow();
        String[] effects = effect.split("\t", -1);
        assertEquals("false", effects[1], effect); // Whole body remains collecting.
        assertEquals("false", effects[5], effect); // Initialization does not bound its final call.
        assertEquals("true", effects[7], effect); // Only roots/poll delegation is certified.
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
                        "cycleA",
                        "cycleB",
                        "locked")) {
            assertTrue(body(cpp, "TailRoots", method).contains("gc_roots"), method);
        }
        String runtime = body(cpp, "TailRoots", "runtime");
        assertTrue(runtime.contains("poll_if_requested") || runtime.contains("safepoint"), runtime);
        for(String method : List.of("initialization", "transitive", "extraction", "reentrant", "failed", "concurrent")) {
            String prefix = body(cpp, "TailRoots", method);
            assertTrue(prefix.contains("::jnative::RootFrame<"), prefix);
            assertTrue(prefix.contains("::jnative::FrameRoot<>"), prefix);
            assertTrue(prefix.contains("poll_if_requested") || prefix.contains("safepoint"), prefix);
            assertFalse(prefix.contains("::jnative::RootAddressFrame<"), prefix);
            String warm = prefix.substring(prefix.indexOf("    if ("), prefix.indexOf("    ::jnative::RootFrame<"));
            assertFalse(warm.contains("gc_roots") || warm.contains("safepoint"), warm);
        }
        String initialization = body(cpp, "TailRoots", "initialization");
        assertGuardBeforeInitialization(initialization, "TailRoots_PrefixInitialization");
        String transitive = body(cpp, "TailRoots", "transitive");
        for(String owner : List.of("TailRoots_TransitiveReader", "TailRoots_TransitiveFirst", "TailRoots_TransitiveSecond"))
            assertGuardBeforeOperation(transitive, owner, "TailRoots_TransitiveReader::read(");
        String extraction = body(cpp, "TailRoots", "extraction");
        assertGuardBeforeInitialization(extraction, "TailRoots_ExtractionInitialization");
        assertTrue(extraction.contains("::jnative::FrameRoot<> element"), extraction);
        String concurrent = body(cpp, "TailRoots", "concurrent");
        assertTrue(
                concurrent.indexOf("TailRoots_ConcurrentInitialization::initialization_complete.load")
                        < concurrent.indexOf("prefixAttempts.get()"),
                concurrent);
        String upstream = body(cpp, "TailRoots", "upstream");
        assertTrue(upstream.contains("::jnative::FrameRoot<> spare"), upstream);
        assertTrue(upstream.contains("poll_if_requested") || upstream.contains("safepoint"), upstream);
        String instance = Files.readString(output.resolve("classes/TailRoots_Payload.cpp"));
        assertFalse(body(instance, "TailRoots_Payload", "direct").contains("gc_roots"), instance);
        assertTrue(body(instance, "TailRoots_Payload", "collect").contains("gc_roots"), instance);
        String instancePrefix = body(instance, "TailRoots_Payload", "prefix");
        assertTrue(instancePrefix.contains("::initialization_complete.load"), instancePrefix);
        assertTrue(instancePrefix.contains("FrameRoot<> keep_alive"), instancePrefix);
        String boundedRead = body(instance, "TailRoots_Payload", "boundedRead");
        int boundedCold = boundedRead.indexOf("    ::jnative::DeferredRootFrame<");
        assertTrue(boundedCold > 0, boundedRead);
        String boundedWarm = boundedRead.substring(0, boundedCold);
        assertFalse(boundedWarm.contains("gc_roots"), boundedWarm);
        assertTrue(boundedWarm.contains("RootValue<> keep_alive"), boundedWarm);
        int failure = boundedWarm.indexOf("if (fail");
        int publication = boundedWarm.indexOf("RootAddressFrame<", failure);
        assertTrue(failure >= 0 && publication > failure, boundedWarm);
        assertTrue(boundedWarm.substring(publication).contains("other.address()"), boundedWarm);
        assertTrue(publication < boundedWarm.indexOf("System_gc", publication), boundedWarm);
        String initialized =
                Files.readString(output.resolve("classes/TailRoots_InitializedCaller.cpp"));
        assertTrue(
                body(initialized, "TailRoots_InitializedCaller", "pass")
                        .contains("initialization_roots"),
                initialized);
        assertFalse(
                body(initialized, "TailRoots_InitializedCaller", "pass").contains("gc_roots"),
                initialized);
        String prefixCaller = body(
                Files.readString(output.resolve("classes/TailRoots_InitializedPrefixCaller.cpp")),
                "TailRoots_InitializedPrefixCaller",
                "pass");
        assertTrue(prefixCaller.contains("::jnative::RootFrame<"), prefixCaller);
        assertGuardBeforeInitialization(prefixCaller, "TailRoots_InitializedPrefixCaller");
        assertTrue(prefixCaller.contains("poll_if_requested") || prefixCaller.contains("safepoint"), prefixCaller);
        // Force collection exactly at generated terminal callee entry, after its root publication.
        // Test instrumentation changes generated sources only; production runtime is unchanged.
        forceEntryCollection(output.resolve("classes/TailRoots.cpp"), "TailRoots", "target");
        forceEntryCollection(output.resolve("classes/TailRoots_Payload.cpp"), "TailRoots_Payload", "collect");
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static void forceEntryCollection(Path source, String owner, String method) throws Exception {
        String cpp = Files.readString(source);
        String original = body(cpp, owner, method);
        assertTrue(original.contains("gc_roots.heap().poll_if_requested();"), original);
        Files.writeString(source, cpp.replace(original,
                original.replace("gc_roots.heap().poll_if_requested();", "gc_roots.heap().collect();")));
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

    private static void assertGuardBeforeInitialization(String cpp, String owner) {
        assertGuardBeforeOperation(cpp, owner, owner + "::ensure_initialized()");
    }

    private static void assertGuardBeforeOperation(String cpp, String dependency, String operation) {
        int guard = cpp.indexOf(dependency + "::initialization_complete.load(std::memory_order_acquire)");
        int use = cpp.indexOf(operation);
        assertTrue(guard >= 0 && use > guard, cpp);
    }
}
