package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ReadableSynchronizationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void lexicalMonitorsUnlockOnEveryExit(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Locks.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Locks {
                            static final Object gate = new Object();
                            static int count;
                            static int guarded(Object lock, int value) {
                                synchronized (lock) {
                                    System.gc();
                                    if (!Thread.holdsLock(lock)) throw new IllegalStateException("unlocked");
                                    synchronized (lock) {
                                        if (value < 0) throw new IllegalArgumentException("negative");
                                        if (value == 0) return 7;
                                        count += value;
                                    }
                                    return count;
                                }
                            }
                            static int caughtInside() {
                                synchronized (gate) {
                                    try { guarded(gate, -1); }
                                    catch (IllegalArgumentException error) {
                                        System.gc();
                                        return Thread.holdsLock(gate) ? error.getMessage().length() : -1;
                                    }
                                }
                                return -2;
                            }
                            static class Worker extends Thread {
                                public void run() {
                                    for (int i = 0; i < 40; ++i) synchronized (gate) {
                                        int value = count;
                                        Thread.yield();
                                        count = value + 1;
                                    }
                                }
                            }
                            public static void main(String[] args) throws Exception {
                                System.out.println(guarded(gate, 0));
                                System.out.println(guarded(gate, 2));
                                try { guarded(gate, -1); }
                                catch (IllegalArgumentException error) { System.out.println(Thread.holdsLock(gate)); }
                                try { guarded(null, 1); }
                                catch (NullPointerException error) { System.out.println("null lock"); }
                                System.out.println(caughtInside());
                                System.out.println(Thread.holdsLock(gate));
                                Worker first = new Worker(), second = new Worker();
                                first.start(); second.start(); first.join(); second.join();
                                System.out.println(count);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, buildType == BuildType.DEBUG ? 17 : 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Locks"),
                        Duration.ofSeconds(20));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Locks")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType)
                        .debugInformation(true);
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String report = Files.readString(generated.resolve("source-readability.tsv"));
        assertFalse(report.contains("low-level"), report);
        String cpp = Files.readString(generated.resolve("classes/Locks.cpp"));
        assertTrue(cpp.contains("::jnative::MonitorGuard synchronized_block"), cpp);
        assertFalse(
                cpp.contains("monitor_enter(")
                        || cpp.contains("monitor_exit(")
                        || cpp.contains("goto "),
                cpp);
        var result = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(buildType == BuildType.RELEASE && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, result, expected, 1);
    }
}
