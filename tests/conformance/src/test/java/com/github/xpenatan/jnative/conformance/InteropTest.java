package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.*;

import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;

class InteropTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void cAbiCallbacksOwnReferencesAndDrainAtShutdown(BuildType configuration) throws Exception {
        Path source = temporary.resolve("Bridge.java");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.*;
                        import java.util.concurrent.atomic.AtomicInteger;
                        @com.github.xpenatan.jnative.interop.NativeInclude("jnative_imports.h")
                        public class Bridge {
                            static Object lock = new Object();
                            static int count;
                            static AtomicInteger atomic = new AtomicInteger();
                            static class Node { Node next; }
                            static class Worker implements Runnable {
                                public void run() {
                                    for (int i = 0; i < 40; ++i) {
                                        Node cycle = new Node(); cycle.next = cycle;
                                        synchronized (lock) { ++count; }
                                        atomic.incrementAndGet();
                                        System.gc();
                                        if (cycle.next != cycle) throw new IllegalStateException("cycle");
                                    }
                                }
                            }
                            static String stored;
                            static ThreadLocal<Thread> local = new ThreadLocal<>();
                            static ThreadLocal<String> history = new ThreadLocal<>();
                            @NativeExport("bridge_echo")
                            static String echo(String value, int index) {
                                if (local.get() == null) local.set(Thread.currentThread());
                                if (local.get() != Thread.currentThread()) throw new IllegalStateException("thread identity");
                                if (index > 0 && history.get() == null) throw new IllegalStateException("lost native thread locals");
                                history.set(value);
                                System.gc();
                                return value + ":" + index;
                            }
                            @NativeExport("bridge_fail")
                            static void fail() { throw new IllegalArgumentException("from Java callback"); }
                            @NativeImport("mark_started")
                            static void markStarted() {}
                            @NativeExport("bridge_slow")
                            static int slow() throws InterruptedException {
                                markStarted();
                                Thread.sleep(30);
                                System.gc();
                                return 123;
                            }
                            @NativeImport("c_roundtrip")
                            static String roundtrip(String value) { return value; }
                            @NativeImport("store_text")
                            static void store(String text) { stored = text; }
                            @NativeImport("read_text")
                            static String read() { String result = stored; stored = null; return result; }
                            @NativeImport("native_workers")
                            static int workers() {
                                int result = 0;
                                for (int worker = 0; worker < 3; ++worker)
                                    for (int i = 0; i < 40; ++i) result += echo(stored, i).length();
                                return result + Separate.plusFive(7);
                            }
                            @NativeImport("native_rethrow")
                            static void rethrow() { fail(); }
                            @NativeImport("native_cpp_failure")
                            static void cppFailure() { throw new RuntimeException("C++ failure"); }
                            @NativeImport("c_bytes")
                            static byte[] bytes(byte[] value) { value[0] = 7; value[1] = 8; return value; }
                            @NativeImport("drain_callbacks")
                            static boolean shutdown() { return true; }
                            public static void main(String[] args) throws InterruptedException {
                                String text = new String("A\\0Ω🌎");
                                System.out.println(roundtrip(text).equals(text));
                                store(text);
                                text = null;
                                System.gc();
                                Thread first = new Thread(new Worker()), second = new Thread(new Worker());
                                first.start(); second.start();
                                System.out.println(workers());
                                first.join(); second.join();
                                System.out.println(count == 80 && atomic.get() == 80);
                                try { rethrow(); }
                                catch (IllegalArgumentException error) { System.gc(); System.out.println(error.getMessage()); }
                                try { cppFailure(); }
                                catch (RuntimeException error) { System.out.println(error.getMessage()); }
                                System.out.println(read().length());
                                byte[] input = {0, 1, -1};
                                byte[] output = bytes(input);
                                System.out.println(input[0] + output[1] + output[2]);
                                System.out.println(shutdown());
                                System.gc();
                            }
                        }
                        class Separate {
                            @NativeExport("separate_plus_five")
                            static int plusFive(int value) { return value + 5; }
                        }
                        """);
        Path c = temporary.resolve("bridge.c");
        Files.writeString(
                c,
                """
                        #include "jnative_exports.h"
                        #include <stdlib.h>
                        static void require(int ok) { if (!ok) abort(); }
                        jn_handle c_roundtrip(jn_handle value) {
                            jn_owned_buffer buffer = {0};
                            jn_handle result = 0, expired = 0;
                            jn_handle null_copy = 123;
                            require(jn_retain(0, &null_copy) == JN_OK && null_copy == 0);
                            require(jn_release(0) == JN_OK);
                            jn_clear_error();
                            jn_clear_error();
                            require(jn_error_status() == JN_OK && jn_take_exception() == 0);
                            require(jn_string_copy_utf8(value, &buffer) == JN_OK);
                            require(buffer.size == 8 && buffer.data[1] == 0);
                            require(jn_string_from_utf8(buffer.data, buffer.size, &result) == JN_OK);
                            jn_buffer_free(&buffer);
                            require(jn_retain(result, &expired) == JN_OK);
                            require(jn_release(expired) == JN_OK);
                            jn_handle invalid = 0;
                            require(jn_retain(expired, &invalid) == JN_INVALID_ARGUMENT);
                            require(jn_release(expired) == JN_INVALID_ARGUMENT);
                            jn_clear_error();
                            require(jn_error_status() == JN_OK && jn_last_error()[0] == 0);
                            const uint8_t malformed[] = {0xc0, 0x80};
                            require(jn_string_from_utf8(malformed, 2, &invalid) == JN_INVALID_ARGUMENT);
                            const uint16_t utf16[] = {65, 0, 0x03a9, 0xd83c, 0xdf0e};
                            jn_handle second = 0;
                            require(jn_string_from_utf16(utf16, 5, &second) == JN_OK);
                            require(jn_string_copy_utf16(second, &buffer) == JN_OK && buffer.size == 10);
                            jn_buffer_free(&buffer);
                            require(jn_release(second) == JN_OK);
                            return result;
                        }
                        jn_handle c_bytes(jn_handle value) {
                            const uint8_t changed[] = {7, 8};
                            require(jn_bytes_write(value, 0, changed, 2) == JN_OK);
                            jn_owned_buffer buffer = {0};
                            require(jn_bytes_copy(value, &buffer) == JN_OK);
                            require(buffer.size == 3 && buffer.data[2] == 255);
                            jn_handle result = 0;
                            require(jn_bytes_from_copy(buffer.data, buffer.size, &result) == JN_OK);
                            jn_buffer_free(&buffer);
                            return result;
                        }
                        """);
        Path cpp = temporary.resolve("workers.cpp");
        Files.writeString(
                cpp,
                """
                        #include "jnative_exports.h"
                        #include <atomic>
                        #include <thread>
                        #include <vector>
                        #include <stdexcept>
                        #include <cstdlib>
                        static jn_handle stored = 0, failure = 0;
                        static std::atomic<int> started{0};
                        static void require(bool ok) { if (!ok) std::abort(); }
                        extern "C" void mark_started() { ++started; }
                        extern "C" void store_text(jn_handle text) { require(jn_retain(text, &stored) == JN_OK); }
                        extern "C" jn_handle read_text() { auto result = stored; stored = 0; return result; }
                        extern "C" void native_cpp_failure() { throw std::runtime_error("C++ failure"); }
                        extern "C" int32_t native_workers() {
                            std::atomic<int> total{0};
                            std::vector<std::thread> workers;
                            for (int worker = 0; worker < 3; ++worker) workers.emplace_back([&, worker] {
                                require(jn_attach_thread() == JN_OK);
                                jn_handle text = 0;
                                require(jn_retain(stored, &text) == JN_OK);
                                for (int i = 0; i < 40; ++i) {
                                    jn_handle result = 0;
                                    require(bridge_echo(text, i, &result) == JN_OK);
                                    jn_owned_buffer buffer{};
                                    require(jn_string_copy_utf16(result, &buffer) == JN_OK);
                                    total += int(buffer.size / 2);
                                    jn_buffer_free(&buffer);
                                    require(jn_release(result) == JN_OK);
                                }
                                if (worker == 0) {
                                    require(bridge_fail() == JN_JAVA_EXCEPTION);
                                    failure = jn_take_exception();
                                    require(failure != 0);
                                    jn_clear_error();
                                }
                                require(jn_release(text) == JN_OK);
                                require(jn_detach_thread() == JN_OK);
                            });
                            for (auto& worker : workers) worker.join();
                            // A foreign thread can call an export without explicit attachment.
                            std::thread automatic([] {
                                jn_handle result = 0;
                                require(bridge_echo(stored, 0, &result) == JN_OK);
                                require(jn_release(result) == JN_OK);
                            });
                            automatic.join();
                            int32_t extra = 0;
                            require(separate_plus_five(7, &extra) == JN_OK);
                            return total + extra;
                        }
                        extern "C" void native_rethrow() {
                            require(jn_set_exception(failure) == JN_OK);
                            require(jn_release(failure) == JN_OK);
                            failure = 0;
                        }
                        extern "C" int32_t drain_callbacks() {
                            auto work = [] {
                                require(jn_attach_thread() == JN_OK);
                                int32_t result = 0;
                                require(bridge_slow(&result) == JN_OK && result == 123);
                                while (true) {
                                    auto status = separate_plus_five(1, &result);
                                    if (status == JN_SHUTTING_DOWN) break;
                                    require(status == JN_OK && result == 6);
                                }
                                require(jn_detach_thread() == JN_OK);
                            };
                            std::thread a(work), b(work);
                            while (started.load() != 2) std::this_thread::yield();
                            require(jn_shutdown() == JN_OK);
                            a.join(); b.join();
                            int32_t result = 0;
                            require(separate_plus_five(1, &result) == JN_SHUTTING_DOWN);
                            jn_clear_error();
                            return 1;
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Bridge"),
                        Duration.ofSeconds(40));
        assertEquals(0, expected.exitCode(), expected.text());
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Bridge")
                        .exportClass("Separate")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(configuration)
                        .nativeFile(c)
                        .nativeFile(cpp)
                        .build();
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(45),
                        Map.of("JNATIVE_GC_INTERVAL", "1"));
        assertEquals(expected, actual);
        if(configuration == BuildType.RELEASE) {
            var exported =
                    NativeProjects.export(
                            result.generation(), temporary.resolve("edited integration export"));
            Path application = exported.directory().resolve("src/launcher.cpp");
            Files.writeString(
                    application,
                    Files.readString(application)
                            .replace(
                                    "int status = 0;",
                                    "int status = 0; std::cout << \"edited export\\n\";"));
            Files.move(classes, temporary.resolve("java inputs removed"));
            var edited =
                    NativeBuilder.create()
                            .buildType(BuildType.RELEASE)
                            .compileProject(exported.directory());
            var editedExpected = new ProcessHarness.Output(0, "edited export\n" + expected.text());
            assertEquals(
                    editedExpected,
                    ProcessHarness.run(
                            temporary,
                            List.of(edited.executable().toString()),
                            Duration.ofSeconds(45),
                            Map.of("JNATIVE_GC_INTERVAL", "1")));
            if(Boolean.getBoolean("jnative.linux"))
                ProcessHarness.compareLinuxProject(
                        temporary,
                        exported.directory(),
                        exported.targetFileName(),
                        editedExpected,
                        1);
        }
    }
}
