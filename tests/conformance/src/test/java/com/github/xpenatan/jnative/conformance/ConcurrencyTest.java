package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @Test
    void mainTerminationWaitsForNonDaemonThreadsAndStopsDaemons() throws Exception {
        Path source = temporary.resolve("Lifecycle.java");
        Files.writeString(
                source,
                """
                        public class Lifecycle {
                            static volatile boolean started;
                            static class Daemon extends Thread {
                                public void run() { started = true; while (true) Thread.yield(); }
                            }
                            static class Background extends Thread {
                                Thread main;
                                Background(Thread main) { this.main = main; }
                                public void run() {
                                    try { main.join(); }
                                    catch (InterruptedException error) { throw new RuntimeException(error); }
                                    System.out.println("background finished");
                                }
                            }
                            public static void main(String[] args) {
                                Daemon daemon = new Daemon();
                                daemon.setDaemon(true); daemon.start();
                                while (!started) Thread.yield();
                                new Background(Thread.currentThread()).start();
                                System.out.println("main finished");
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Lifecycle"),
                        Duration.ofSeconds(20));
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Lifecycle")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(BuildType.DEBUG)
                        .build();
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(20),
                        Map.of("JNATIVE_GC_INTERVAL", "2"));
        assertEquals(0, expected.exitCode(), expected.text());
        assertEquals("main finished\nbackground finished\n", expected.text());
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 25})
    void javaThreadsAndMonitorsMatchJvm(int release) throws Exception {
        Path source = temporary.resolve("Concurrent.java");
        Files.writeString(
                source,
                """
                        import java.util.concurrent.atomic.AtomicInteger;
                        import java.util.concurrent.atomic.AtomicLong;
                        public class Concurrent {
                            static final Object gate = new Object();
                            static final AtomicInteger atomic = new AtomicInteger();
                            static final AtomicLong wide = new AtomicLong(Long.MAX_VALUE);
                            static final AtomicInteger initialized = new AtomicInteger();
                            static final AtomicInteger failures = new AtomicInteger();
                            static final Local local = new Local();
                            static volatile boolean go;
                            static int count;
                            static class Local extends ThreadLocal<String> {
                                protected String initialValue() { return new String("initial"); }
                            }
                            static class Late {
                                static String value = initialize();
                                static String initialize() {
                                    initialized.incrementAndGet();
                                    System.gc();
                                    return new String("initialized");
                                }
                            }
                            static synchronized void increment() {
                                int before = count;
                                Thread.yield();
                                count = before + 1;
                            }
                            static class Work implements Runnable {
                                String name;
                                Work(String name) { this.name = name; }
                                public void run() {
                                    while (!go) Thread.yield();
                                    if (!local.get().equals("initial")) failures.incrementAndGet();
                                    local.set(new String(name));
                                    if (!Late.value.equals("initialized")) failures.incrementAndGet();
                                    for (int i = 0; i < 80; ++i) {
                                        increment();
                                        atomic.incrementAndGet();
                                        wide.incrementAndGet();
                                        if (i % 10 == 0) System.gc();
                                        if (!local.get().equals(name)) failures.incrementAndGet();
                                    }
                                    local.remove();
                                    if (!local.get().equals("initial")) failures.incrementAndGet();
                                }
                            }
                            static class GateWorker extends Thread {
                                volatile boolean ready, released, owns;
                                public void run() {
                                    synchronized (gate) {
                                        synchronized (gate) {
                                            ready = true;
                                            gate.notifyAll();
                                            while (!released) {
                                                try { gate.wait(); } catch (InterruptedException error) { failures.incrementAndGet(); }
                                            }
                                            owns = Thread.holdsLock(gate);
                                        }
                                    }
                                }
                            }
                            static class Sleeper extends Thread {
                                volatile boolean ready, caught, cleared;
                                public void run() {
                                    ready = true;
                                    try { Thread.sleep(60000); }
                                    catch (InterruptedException error) {
                                        caught = true;
                                        cleared = !Thread.currentThread().isInterrupted();
                                    }
                                }
                            }
                            static class Joiner extends Thread {
                                Thread target;
                                volatile boolean ready, caught;
                                Joiner(Thread target) { this.target = target; }
                                public void run() {
                                    ready = true;
                                    try { target.join(); } catch (InterruptedException error) { caught = true; }
                                }
                            }
                            static class Waiter extends Thread {
                                volatile boolean ready, caught, owns;
                                public void run() {
                                    synchronized (gate) {
                                        ready = true; gate.notifyAll();
                                        try { gate.wait(); }
                                        catch (InterruptedException error) { caught = true; owns = Thread.holdsLock(gate); }
                                    }
                                }
                            }
                            static void monitorInsideEndlessLoop() {
                                int completed = 0;
                                while (true) {
                                    synchronized (gate) {
                                        while (!go) {
                                            try { gate.wait(); }
                                            catch (InterruptedException ignored) { }
                                        }
                                        if (completed == 2) return;
                                    }
                                    try { completed++; }
                                    catch (Throwable failure) { throw new RuntimeException(failure); }
                                }
                            }
                            public static void main(String[] args) throws Exception {
                                Thread first = new Thread(new Work("first"), "first");
                                Thread second = new Thread(new Work("second"), "second");
                                first.start(); second.start(); go = true;
                                first.join(); second.join();
                                System.out.println(count + ":" + atomic.get() + ":" + failures.get() + ":" + initialized.get());
                                System.out.println(wide.get());
                                System.out.println(first.getId() != second.getId());
                                System.out.println(first.getName() + ":" + first.isAlive());
                                System.out.println(local.get());
                                System.out.println(atomic.compareAndSet(160, 12));
                                System.out.println(atomic.getAndAdd(3) + ":" + atomic.get());
                                try { first.start(); } catch (IllegalThreadStateException error) { System.out.println("restart rejected"); }
                                GateWorker worker = new GateWorker();
                                synchronized (gate) {
                                    worker.start();
                                    while (!worker.ready) gate.wait();
                                    worker.released = true;
                                    gate.notifyAll();
                                }
                                worker.join();
                                System.out.println("reentrant " + worker.owns);
                                Sleeper sleeper = new Sleeper();
                                sleeper.start();
                                while (!sleeper.ready) Thread.yield();
                                Joiner joiner = new Joiner(sleeper);
                                joiner.start();
                                while (!joiner.ready) Thread.yield();
                                joiner.interrupt(); joiner.join();
                                sleeper.interrupt(); sleeper.join();
                                System.out.println("interrupt " + sleeper.caught + ":" + sleeper.cleared + ":" + joiner.caught);
                                Waiter waiter = new Waiter();
                                synchronized (gate) {
                                    waiter.start();
                                    while (!waiter.ready) gate.wait();
                                }
                                waiter.interrupt(); waiter.join();
                                System.out.println("wait interrupt " + waiter.caught + ":" + waiter.owns);
                                Thread.currentThread().interrupt();
                                try { Thread.sleep(0); } catch (InterruptedException error) { System.out.println("pre-interrupt cleared " + !Thread.interrupted()); }
                                try { gate.notify(); } catch (IllegalMonitorStateException error) { System.out.println("monitor ownership"); }
                                synchronized (gate) { gate.wait(1, 1); System.out.println(Thread.holdsLock(gate)); }
                                monitorInsideEndlessLoop();
                                System.out.println("loop monitor released " + !Thread.holdsLock(gate));
                                System.out.println(failures.get());
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, release);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Concurrent"),
                        Duration.ofSeconds(40));
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Concurrent")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(release == 17 ? BuildType.DEBUG : BuildType.RELEASE)
                        .build();
        assertEquals(0, expected.exitCode(), expected.text());
        for(int repeat = 0; repeat < 3; ++repeat) {
            var actual =
                    ProcessHarness.run(
                            temporary,
                            List.of(result.executable().toString()),
                            Duration.ofSeconds(45),
                            Map.of("JNATIVE_GC_INTERVAL", "3"));
            assertEquals(expected, actual);
        }
        if(Boolean.getBoolean("jnative.linux") && release == 25)
            ProcessHarness.compareLinux(temporary, result, expected, 3);
    }
}
