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

class RootFramesTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void framesPreserveLiveReferencesAndUnwindAcrossCollection(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("Roots.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Roots {
                            static class Box {
                                Box child;
                                int[] values = {37, 41};
                                Box() {}
                                Box(Box child) { this.child = child; }
                            }
                            static int shortLived(Box box) {
                                System.gc();
                                return box.child.values.length;
                            }
                            static int clearAndCollect(Box box) {
                                box.child = null;
                                System.gc();
                                return 5;
                            }
                            static int combine(Box saved, int number) {
                                System.gc();
                                return saved.values[0] + number;
                            }
                            static int argumentLifetime(Box box) {
                                return combine(box.child, clearAndCollect(box));
                            }
                            static int localLifetime(Box box) {
                                Box saved = box.child;
                                box.child = null;
                                System.gc();
                                return saved.values[1];
                            }
                            static Box recursive(Box value, int depth) {
                                if (depth == 0) { System.gc(); return value; }
                                Box other = new Box(value);
                                return recursive(other, depth - 1).child;
                            }
                            static Box unwind(Box box, boolean fail) {
                                Box saved = box.child;
                                try {
                                    box.child = null;
                                    System.gc();
                                    if (fail) throw new IllegalArgumentException("expected");
                                    return saved;
                                } catch (IllegalArgumentException e) {
                                    System.gc();
                                    return saved;
                                } finally {
                                    System.gc();
                                }
                            }
                            static int[] merged(boolean choose) {
                                int[] a = {3}, b = {7};
                                int[] selected = choose ? a : b;
                                for (int i = 0; i < 6; i++) {
                                    int[] old = a; a = b; b = old;
                                    System.gc();
                                    if (i == 2) selected = a;
                                }
                                return selected;
                            }
                            public static void main(String[] args) throws Exception {
                                Box box = new Box(new Box());
                                System.out.println(shortLived(box));
                                System.out.println(argumentLifetime(box));
                                System.out.println(localLifetime(new Box(new Box())));
                                Box original = new Box();
                                System.out.println(recursive(original, 12) == original);
                                for (boolean fail : new boolean[] {false, true}) {
                                    Box result = unwind(new Box(new Box()), fail);
                                    System.gc();
                                    System.out.println(result.values[0]);
                                }
                                System.out.println(merged(true)[0] + merged(false)[0]);
                                try { shortLived(new Box()); }
                                catch (NullPointerException expected) { System.gc(); System.out.println("null"); }
                                Thread collector = new Thread(() -> {
                                    for (int i = 0; i < 60; i++) System.gc();
                                });
                                collector.start();
                                int sum = 0;
                                for (int i = 0; i < 60; i++) sum += argumentLifetime(new Box(new Box()));
                                collector.join();
                                System.out.println(sum);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Roots"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Roots")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String code =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/Roots.cpp"));
        String shortLived = code.substring(code.indexOf("\nstd::int32_t Roots::shortLived("));
        shortLived = shortLived.substring(0, shortLived.indexOf("\n}"));
        assertTrue(shortLived.contains("RootFrame<1>"), shortLived);
        assertFalse(shortLived.contains("LocalRoot"), shortLived);
        Path launcher = generated.request().generatedSourcesDirectory().resolve("launcher.cpp");
        Files.writeString(
                launcher,
                """
                        #include "application.hpp"
                        struct RootProbe final : jnative::Object {
                            int* deaths;
                            explicit RootProbe(int* count) : deaths(count) {}
                            ~RootProbe() { ++*deaths; }
                        };
                        """
                        + Files.readString(launcher)
                        .replace(
                                "generated::initialize_program();",
                                """
                                        generated::initialize_program();
                                        {
                                            int deaths = 0;
                                            jnative::RootFrame<1> roots;
                                            jnative::FrameRoot<> local(roots.slot(0), jnative::allocate<RootProbe>(&deaths));
                                            jnative::Heap::instance().collect();
                                            if (deaths != 0 || local.get() != roots.slot(0))
                                                throw std::logic_error("Initial local was not retained");
                                            local.set(jnative::allocate<RootProbe>(&deaths));
                                            jnative::Heap::instance().collect();
                                            if (deaths != 1 || local.get() != roots.slot(0))
                                                throw std::logic_error("Local reassignment did not update its root");
                                            local.set(nullptr);
                                            jnative::Heap::instance().collect();
                                            if (deaths != 2 || roots.slot(0) != nullptr)
                                                throw std::logic_error("Cleared local remained live");
                                        }
                                        """));
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
