package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SynchronizedReturnsTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void newlyAllocatedResultsSurviveMonitorReleaseAndConcurrentCollection(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("SynchronizedReturns.java");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        import java.io.ByteArrayOutputStream;
                        import com.github.xpenatan.jnative.interop.NativeInclude;
                        @NativeInclude("jnative_imports.h")
                        public class SynchronizedReturns {
                            static volatile boolean running = true;
                            static volatile Throwable failure;
                            static final class Value {
                                final int number;
                                Value(int number) { this.number = number; }
                            }
                            static synchronized Object staticValue(int number) { return new Value(number); }
                            synchronized Object instanceValue(int number) { return new Value(number); }
                            static synchronized byte[] bytes(int number) { return new byte[] {(byte) number, 7}; }
                            @NativeImport("synchronized_native_bytes")
                            static synchronized byte[] nativeBytes(int number) { return new byte[] {(byte) number, 7}; }
                            static void check(byte[] value, int number) {
                                if (value.length != 2 || value[0] != (byte) number || value[1] != 7)
                                    throw new IllegalStateException("Returned bytes were corrupted");
                            }
                            static void work() {
                                try {
                                    SynchronizedReturns factory = new SynchronizedReturns();
                                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                    for (int i = 0; i < 600; ++i) {
                                        if (((Value) staticValue(i)).number != i
                                                || ((Value) factory.instanceValue(i)).number != i)
                                            throw new IllegalStateException("Returned object was corrupted");
                                        check(bytes(i), i);
                                        check(nativeBytes(i), i);
                                        stream.reset(); stream.write(i); stream.write(7);
                                        check(stream.toByteArray(), i);
                                    }
                                } catch (Throwable error) { failure = error; }
                            }
                            public static void main(String[] args) throws Exception {
                                Thread collector = new Thread(() -> {
                                    while (running) { System.gc(); Thread.yield(); }
                                });
                                Thread first = new Thread(SynchronizedReturns::work);
                                Thread second = new Thread(SynchronizedReturns::work);
                                collector.start(); first.start(); second.start();
                                first.join(); second.join(); running = false; collector.join();
                                if (failure != null) throw new RuntimeException("Concurrent return failed", failure);
                                System.out.println("synchronized returns intact");
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(
                                ProcessHarness.java(),
                                "-cp",
                                classes.toString(),
                                "SynchronizedReturns"),
                        Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());

        Path adapter = temporary.resolve("synchronized_returns.cpp");
        Files.writeString(
                adapter,
                """
                        #include "jn_abi.h"
                        extern "C" jn_handle synchronized_native_bytes(int32_t number) {
                            const uint8_t bytes[] = {static_cast<uint8_t>(number), 7};
                            jn_handle result = 0;
                            jn_bytes_from_copy(bytes, 2, &result);
                            return result;
                        }
                        """);
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("SynchronizedReturns")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType)
                        .nativeFile(adapter)
                        .build();
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "2"));
        assertEquals(expected, actual);
    }
}
