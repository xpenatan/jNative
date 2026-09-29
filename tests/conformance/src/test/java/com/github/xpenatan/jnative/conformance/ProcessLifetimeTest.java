package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ProcessLifetimeTest {
    @TempDir
    Path temporary;

    @Test
    void timeoutAndInterruptionTerminateTheToolchainAndChild() throws Exception {
        Path fakeSource = temporary.resolve("slow-tool.cpp");
        Path fake =
                temporary.resolve(
                        System.getProperty("os.name").startsWith("Windows")
                                ? "slow-tool.exe"
                                : "slow-tool");
        Files.writeString(
                fakeSource,
                """
                        #include <fstream>
                        #include <thread>
                        #include <chrono>
                        #include <string>
                        #ifdef _WIN32
                        #include <process.h>
                        #else
                        #include <unistd.h>
                        #define _getpid getpid
                        #endif
                        int main(int argc, char** argv) {
                            bool child = argc == 2 && std::string(argv[1]) == "child";
                            { std::ofstream out(child ? "child.pid" : "parent.pid"); out << _getpid(); }
                            if (!child) {
                        #ifdef _WIN32
                                _spawnl(_P_NOWAIT, argv[0], argv[0], "child", nullptr);
                        #else
                                if (fork() == 0) { execl(argv[0], argv[0], "child", nullptr); return 3; }
                        #endif
                            }
                            std::this_thread::sleep_for(std::chrono::seconds(30));
                        }
                        """);
        var built =
                ProcessHarness.run(
                        temporary,
                        List.of("g++", "-pthread", fakeSource.toString(), "-o", fake.toString()),
                        Duration.ofSeconds(30));
        assertEquals(0, built.exitCode(), built.text());
        Path source = temporary.resolve("Empty.java"), classes = temporary.resolve("classes");
        Files.writeString(source, "public class Empty { public static void main(String[] a) {} }");
        ProcessHarness.javac(source, classes, 17);
        for(boolean cancel : List.of(false, true)) {
            var builder =
                    NativeBuilder.create()
                            .classpath(classes)
                            .mainClass("Empty")
                            .buildRoot(temporary.resolve(cancel ? "cancel" : "timeout"))
                            .cmake(fake.toString())
                            .timeout(Duration.ofSeconds(cancel ? 30 : 2));
            var generated = builder.generate();
            Path project = generated.request().buildRoot().resolve("native");
            var failure = new AtomicReference<Throwable>();
            var interruptPreserved = new AtomicBoolean();
            Thread worker =
                    new Thread(
                            () -> {
                                try {
                                    builder.compile(generated);
                                } catch(Throwable error) {
                                    failure.set(error);
                                    interruptPreserved.set(Thread.currentThread().isInterrupted());
                                }
                            });
            worker.start();
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                while((!Files.exists(project.resolve("parent.pid"))
                        || !Files.exists(project.resolve("child.pid")))
                        && System.nanoTime() < deadline) Thread.sleep(10);
                assertTrue(
                        Files.exists(project.resolve("child.pid")),
                        "Toolchain child must start before cancellation");
                if(cancel) worker.interrupt();
                worker.join(8000);
                assertFalse(worker.isAlive(), "Toolchain invocation must finish promptly");
                assertInstanceOf(CompilerException.class, failure.get());
                assertTrue(
                        failure.get().getMessage().contains("JN3003"), failure.get().getMessage());
                if(cancel) assertTrue(interruptPreserved.get());
                for(String name : List.of("parent.pid", "child.pid")) {
                    long pid = Long.parseLong(Files.readString(project.resolve(name)).strip());
                    deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
                    while(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)
                            && System.nanoTime() < deadline) Thread.sleep(10);
                    assertFalse(
                            ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),
                            "Leaked process: " + pid);
                }
            } finally {
                worker.interrupt();
                worker.join(1000);
            }
        }
    }
}
