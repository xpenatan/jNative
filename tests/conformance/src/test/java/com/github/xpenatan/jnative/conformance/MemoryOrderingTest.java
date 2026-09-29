package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuilder;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class MemoryOrderingTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void volatileOrderAndOrdinaryPublicationSurviveConcurrentCollection(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("MemoryOrdering.java");
        Files.writeString(
                source,
                """
                        public class MemoryOrdering {
                            static final int ROUNDS = 3000;
                            static final State state = new State();
                            static volatile int phase, y, doneA, doneB;
                            static volatile boolean running = true;
                            static volatile Throwable failure;
                            static int readA, readB;
                            static class Payload {
                                int number;
                                int[] values;
                                Payload(int number) {
                                    this.number = number;
                                    values = new int[] {number, number + 1, number + 2};
                                }
                            }
                            static class State {
                                volatile int x;
                                volatile Payload published;
                            }
                            static void first() {
                                try {
                                    for (int round = 1; round <= ROUNDS; round++) {
                                        while (phase != round) {}
                                        state.x = 1;
                                        readA = y;
                                        state.published = new Payload(round);
                                        doneA = round;
                                    }
                                } catch (Throwable error) { failure = error; }
                            }
                            static void second() {
                                try {
                                    for (int round = 1; round <= ROUNDS; round++) {
                                        while (phase != round) {}
                                        y = 1;
                                        readB = state.x;
                                        Payload value;
                                        while ((value = state.published) == null) {}
                                        if (value.number != round || value.values[2] != round + 2)
                                            throw new IllegalStateException("Publication lost ordinary writes");
                                        doneB = round;
                                    }
                                } catch (Throwable error) { failure = error; }
                            }
                            public static void main(String[] args) throws Exception {
                                Thread collector = new Thread(() -> {
                                    while (running) { System.gc(); Thread.yield(); }
                                });
                                Thread a = new Thread(MemoryOrdering::first);
                                Thread b = new Thread(MemoryOrdering::second);
                                collector.start(); a.start(); b.start();
                                for (int round = 1; round <= ROUNDS; round++) {
                                    state.x = 0; y = 0; state.published = null;
                                    phase = round;
                                    while ((doneA != round || doneB != round) && failure == null) {}
                                    if (failure != null) throw new RuntimeException("Worker failed", failure);
                                    if (readA == 0 && readB == 0)
                                        throw new IllegalStateException("Volatile stores lost their total order");
                                }
                                a.join(); b.join(); running = false; collector.join();
                                System.out.println("memory ordering intact");
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "MemoryOrdering"),
                        Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var compiled =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("MemoryOrdering")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType)
                        .build();
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "2"));
        assertEquals(expected, actual);
    }
}
