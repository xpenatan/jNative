package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class InitializationFastPathsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void recursiveAndFailedInitializationNeverPublishAnEarlyFastPath(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("Initialization.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Initialization {
                            static volatile boolean entered, release, attempted, returned;
                            static int seen;
                            static class Recursive {
                                static int value = initialize();
                                static int initialize() {
                                    // Reentry must not publish completion to the waiting thread.
                                    System.out.println("recursive:" + read());
                                    entered = true;
                                    while (!release) Thread.yield();
                                    System.gc();
                                    return 73;
                                }
                                static int read() { return value; }
                            }
                            static class Broken {
                                static int value = fail();
                                static int fail() {
                                    System.out.println("partial:" + read());
                                    System.gc();
                                    throw new IllegalStateException("failed");
                                }
                                static int read() { return value; }
                            }
                            static class Unused {
                                static { System.out.println("unexpected initialization"); }
                                static int read() { return 1; }
                            }
                            static class Names {
                                static int initialization_complete = 17;
                                static int initialize_slow() { return initialization_complete; }
                            }
                            static int sum(int count) {
                                int value = 0;
                                for (int i = 0; i < count; i++) value += Unused.read();
                                return value;
                            }
                            public static void main(String[] args) throws Exception {
                                Thread owner = new Thread(() -> { seen = Recursive.read(); });
                                Thread waiter = new Thread(() -> {
                                    attempted = true;
                                    int value = Recursive.read();
                                    if (value != 73) throw new IllegalStateException("partial publication");
                                    returned = true;
                                });
                                owner.start();
                                while (!entered) Thread.yield();
                                waiter.start();
                                while (!attempted) Thread.yield();
                                Thread.sleep(30);
                                if (returned) throw new IllegalStateException("early completion");
                                release = true;
                                owner.join(); waiter.join();
                                System.out.println(seen + ":" + returned + ":" + Recursive.read());
                                try { Broken.read(); }
                                catch (ExceptionInInitializerError expected) { System.out.println("first failure"); }
                                try { Broken.read(); }
                                catch (NoClassDefFoundError expected) { System.out.println("cached failure"); }
                                System.out.println(sum(0) + ":" + Names.initialize_slow());
                                int rotations = 0;
                                for (int i = 0; i < 1000; i++) rotations ^= Integer.rotateRight(i, i - 100);
                                System.out.println(rotations);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Initialization"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Initialization")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        Path directory = generated.request().generatedSourcesDirectory();
        String header = Files.readString(directory.resolve("classes/Initialization.hpp"));
        assertTrue(header.contains("static JNATIVE_NOINLINE void initialize_slow();"), header);
        StringBuilder support = new StringBuilder();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().startsWith("runtime_support")
                    && p.getFileName().toString().endsWith(".cpp")).toList())
                support.append(Files.readString(path));
        }
        assertTrue(support.toString().contains("JNATIVE_NOINLINE void Initialization::initialize_slow() {"),
                support.toString());
        var result = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "2")));
    }
}
