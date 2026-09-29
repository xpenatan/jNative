package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ComplexReadableControlFlowTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void nestedExitsAndCleanupMatchJava(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Nested.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Nested {
                            static final Object gate = new Object();
                            static int cleanups;
                            static int labeled(int limit) {
                                int result = 0;
                                outer: for (int i = 0; i < limit; ++i) {
                                    for (int j = 0; j < 5; ++j) {
                                        switch ((i + j) % 5) {
                                            case 0: result += i; continue outer;
                                            case 1: if (i > 3) break outer; break;
                                            case 2: result += j; break;
                                            default: result += 10;
                                        }
                                        if (j == 3) break;
                                    }
                                    result += 100;
                                }
                                return result;
                            }
                            static int nestedSwitch(int key) {
                                int result = 0;
                                done: switch (key) {
                                    case 0:
                                        for (int i = 0; i < 4; ++i) {
                                            if (i == 2) break done;
                                            result += i + 1;
                                        }
                                    case 1:
                                        switch (key + 1) {
                                            case 2: result += 20; break;
                                            default: result += 30;
                                        }
                                        result += 40;
                                        break;
                                    default: result = 5;
                                }
                                return result;
                            }
                            static int tailLoop(int count) {
                                int result = 0;
                                outer: do {
                                    int inner = 3;
                                    while (inner-- > 0) {
                                        if (count == 2) { --count; continue outer; }
                                        if (count == 1) break outer;
                                        result += count + inner;
                                    }
                                } while (--count > 0);
                                return result;
                            }
                            static int guardedExits(int limit) {
                                int result = 0;
                                outer: for (int i = 0; i < limit; ++i) {
                                    synchronized (gate) {
                                        try {
                                            for (int j = 0; j < 4; ++j) {
                                                System.gc();
                                                if (i == 1) continue outer;
                                                if (i == 3) break outer;
                                                if (j == 2) throw new IllegalArgumentException("two");
                                                result += i + j;
                                            }
                                        } catch (IllegalArgumentException error) {
                                            result += error.getMessage().length();
                                        } finally {
                                            cleanups += Thread.holdsLock(gate) ? 1 : 1000;
                                        }
                                    }
                                }
                                return result + (Thread.holdsLock(gate) ? 10000 : 0);
                            }
                            static int finallyExits(int count) {
                                int result = 0;
                                while (count-- > 0) {
                                    try {
                                        switch (count) {
                                            case 1: continue;
                                            case 2: return result;
                                            case 3: throw new IllegalStateException("three");
                                            default: result += count;
                                        }
                                    } catch (IllegalStateException error) {
                                        result += error.getMessage().length();
                                    } finally {
                                        ++cleanups;
                                        System.gc();
                                    }
                                }
                                return result;
                            }
                            static int cleanupThrows(int count) {
                                try {
                                    while (count-- > 0) {
                                        try {
                                            if (count == 3) continue;
                                            if (count == 1) break;
                                        } finally {
                                            ++cleanups;
                                            if (count == 2) throw new IllegalArgumentException("cleanup");
                                        }
                                    }
                                    return count;
                                } catch (IllegalArgumentException error) {
                                    return error.getMessage().length();
                                }
                            }
                            static int monitorFailure(Object lock, int count) {
                                int result = 0;
                                while (count-- > 0) {
                                    try {
                                        synchronized (lock) {
                                            System.gc();
                                            if (count == 2) continue;
                                            if (count == 1) break;
                                            ++result;
                                        }
                                    } catch (NullPointerException error) {
                                        result += 7;
                                        break;
                                    } finally {
                                        ++cleanups;
                                    }
                                }
                                return result;
                            }
                            public static void main(String[] args) {
                                for (int i = 0; i < 8; ++i) {
                                    System.out.println(labeled(i));
                                    System.out.println(nestedSwitch(i));
                                    System.out.println(tailLoop(i));
                                    System.out.println(guardedExits(i));
                                    System.out.println(finallyExits(i));
                                    System.out.println(cleanupThrows(i));
                                    System.out.println(monitorFailure(gate, i));
                                    System.out.println(monitorFailure(null, i));
                                    System.out.println(cleanups);
                                }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, buildType == BuildType.DEBUG ? 17 : 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Nested"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Nested")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType)
                        .debugInformation(true);
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String report = Files.readString(generated.resolve("source-readability.tsv"));
        assertFalse(report.contains("low-level"), report);
        String cpp = Files.readString(generated.resolve("classes/Nested.cpp"));
        assertFalse(
                cpp.contains("goto block_")
                        || cpp.contains("OperandStack")
                        || cpp.contains("resume_handler"),
                cpp);
        assertTrue(
                cpp.contains("switch (")
                        && cpp.contains("try {")
                        && cpp.contains("::jnative::MonitorGuard"),
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
