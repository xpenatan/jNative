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

class DeferredRootsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void exceptionalBranchesPublishTheirLiveReferencesBeforeCollection(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("DeferredRoots.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class DeferredRoots {
                            static class Payload {
                                final String value;
                                Payload(String value) { this.value = value; }
                            }
                            static class Failure extends RuntimeException {
                                final Payload first, second;
                                static { System.gc(); }
                                Failure(Payload first, Payload second) {
                                    System.gc(); this.first = first;
                                    System.gc(); this.second = second;
                                }
                                String text() { System.gc(); return first.value + ":" + second.value; }
                            }
                            final Payload stored;
                            DeferredRoots(Payload stored) { this.stored = stored; }
                            int guard(Payload value, int mode) {
                                if (mode < 0) throw new Failure(stored, value);
                                if (mode == 0) throw new Failure(value, new Payload(new String("inner")));
                                return mode + 7;
                            }
                            int chain(Payload value, int mode) { return guard(value, mode) + 3; }
                            static int staticGuard(Payload a, Payload b, int mode) {
                                switch (mode) {
                                    case 0: throw new Failure(a, b);
                                    case 1: return 17;
                                    default: throw new Failure(b, a);
                                }
                            }
                            int caught(Payload a) {
                                Payload survivor = new Payload(new String("survivor"));
                                try { return chain(a, -1); }
                                catch (Failure expected) {
                                    System.gc(); System.out.println(expected.text() + ":" + survivor.value);
                                    return 3;
                                }
                            }
                            static class Initialized {
                                static { System.gc(); }
                                static int guard(Payload value, int mode) {
                                    if (mode == 0) throw new Failure(value, value);
                                    return mode;
                                }
                            }
                            int changingColdLocals(Payload a, Payload b, int mode) {
                                Payload selected = a;
                                if (mode < 0) {
                                    System.gc();
                                    selected = new Payload(new String("replacement"));
                                    if (mode == -1) {
                                        Payload nested = new Payload(new String("nested"));
                                        System.gc();
                                        throw new Failure(selected, nested);
                                    }
                                    System.gc();
                                    selected = b;
                                    System.gc();
                                    throw new Failure(a, selected);
                                }
                                if (mode == 0) {
                                    Payload sibling = new Payload(new String("sibling"));
                                    System.gc();
                                    throw new Failure(selected, sibling);
                                }
                                return mode;
                            }
                            public static void main(String[] args) {
                                DeferredRoots test = new DeferredRoots(new Payload(new String("stored")));
                                for (int round = 0; round < 4; round++) {
                                    Payload value = new Payload(new String("argument"));
                                    System.out.println(test.chain(value, 4));
                                    System.out.println(staticGuard(value, test.stored, 1));
                                    for (int mode = -1; mode <= 0; mode++) {
                                        try { test.chain(value, mode); }
                                        catch (Failure expected) { System.out.println(expected.text()); }
                                    }
                                    try { staticGuard(value, test.stored, 2); }
                                    catch (Failure expected) { System.out.println(expected.text()); }
                                    try { Initialized.guard(new Payload(new String("initialized")), 0); }
                                    catch (Failure expected) { System.out.println(expected.text()); }
                                    System.out.println(test.caught(value));
                                    for (int mode = -2; mode <= 1; mode++) {
                                        try { System.out.println(test.changingColdLocals(value, test.stored, mode)); }
                                        catch (Failure expected) { System.out.println(expected.text()); }
                                    }
                                }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "DeferredRoots"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("DeferredRoots")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/DeferredRoots.cpp"));
        int start = cpp.indexOf("\nstd::int32_t DeferredRoots::guard(");
        int end = cpp.indexOf("\n}\n", start);
        assertTrue(start >= 0 && end > start, cpp);
        String guard = cpp.substring(start, end);
        assertTrue(guard.contains("RootAddressFrame<"), guard);
        assertTrue(guard.contains("RootValue<>"), guard);
        assertFalse(guard.contains("DeferredRootFrame<"), guard);
        assertTrue(guard.indexOf("RootAddressFrame<") > guard.indexOf("if ("), guard);
        assertFalse(
                guard.contains("poll_if_requested") || guard.contains("::jnative::safepoint()"),
                guard);
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
