package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class CollectionStressTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void generatedRootsSurviveCollectionsFromMultipleNativeThreads(BuildType configuration)
            throws Exception {
        Path source = temporary.resolve("GcStress.java");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        @com.github.xpenatan.jnative.interop.NativeInclude("jnative_imports.h")
                        public class GcStress {
                            static class Node {
                                Node next;
                                String value;
                                Node(String text) { value = text; }
                            }
                            static Node retained = new Node("static");
                            static String combine(Node first, Node second) {
                                System.gc();
                                return first.value + second.value;
                            }
                            static void fail(Node value) {
                                System.gc();
                                throw new IllegalArgumentException(value.value);
                            }
                            static int worker(int seed) {
                                int total = seed;
                                for (int i = 0; i < 120; ++i) {
                                    Node first = new Node("first" + i);
                                    Node second = new Node("second" + i);
                                    first.next = second; second.next = first;
                                    Node[] array = {first, second};
                                    String temporary = combine(new Node("a"), new Node("b"));
                                    try { fail(new Node("failure" + i)); }
                                    catch (IllegalArgumentException error) {
                                        System.gc();
                                        total += error.getMessage().length();
                                    }
                                    System.gc();
                                    total += array[1].next.value.length() + temporary.length() + retained.value.length();
                                }
                                return total;
                            }
                            @NativeImport("run_workers")
                            static int nativeWorkers() { return worker(10) + worker(20) + worker(30); }
                            public static void main(String[] args) {
                                System.out.println(worker(0));
                                System.out.println(nativeWorkers());
                                System.out.println(retained.value);
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "GcStress"),
                        Duration.ofSeconds(40));
        Path adapter = temporary.resolve("workers.cpp");
        Files.writeString(
                adapter,
                "/* Native worker harness is filled after reading the generated public declarations. */\n");
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("GcStress")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(configuration)
                        .nativeFile(adapter);
        var initial = builder.generate();
        String header =
                Files.readString(
                        initial.request().generatedSourcesDirectory().resolve("application.hpp"));
        var declaration =
                Pattern.compile(
                                "std::int32_t (j_GcStress_worker_[a-f0-9]+)\\(std::int32_t arg0\\);")
                        .matcher(header);
        assertTrue(declaration.find(), "Generated worker must have a callable C++ declaration");
        String worker = declaration.group(1);
        Files.writeString(
                adapter,
                """
                        #include "application.hpp"
                        #include <atomic>
                        #include <thread>
                        #include <set>
                        #include <mutex>
                        extern "C" std::int32_t run_workers() {
                            std::atomic<std::int32_t> total{0};
                            std::atomic<bool> failed{false};
                            std::mutex mutex;
                            std::set<std::thread::id> ids;
                            auto work = [&](int seed) {
                                try {
                                    jnative::ThreadAttachment attached;
                                    { std::lock_guard<std::mutex> lock(mutex); ids.insert(std::this_thread::get_id()); }
                                    auto value = generated::%s(seed);
                                    total.fetch_add(value);
                                } catch (...) { failed = true; }
                            };
                            std::thread a(work, 10), b(work, 20), c(work, 30);
                            a.join(); b.join(); c.join();
                            return failed || ids.size() != 3 ? -1 : total.load();
                        }
                        """
                        .formatted(worker));
        var result = builder.build();
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(45),
                        Map.of("JNATIVE_GC_INTERVAL", "1"));
        assertEquals(0, expected.exitCode(), expected.text());
        assertEquals(expected, actual);
        if(Boolean.getBoolean("jnative.linux") && configuration == BuildType.RELEASE)
            ProcessHarness.compareLinux(temporary, result, expected, 1);
    }
}
