package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class ConcurrencyOwnershipTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void synchronousRunnableCallableAndThreadRunKeepOwnedStorage(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("SynchronousOwnership.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                import java.util.concurrent.Callable;
                public class SynchronousOwnership {
                    static class Task implements Runnable {
                        private int count;
                        private final int[] values = new int[1];
                        public void run() { values[0] = ++count; }
                        int read() { return count + values[0]; }
                    }
                    static class Call implements Callable<Integer> {
                        private int count;
                        private final int[] values = new int[1];
                        public Integer call() { values[0] = ++count; return count + values[0]; }
                    }
                    static class DirectThread extends Thread {
                        private int count;
                        private final int[] values = new int[1];
                        public void run() { values[0] = ++count; }
                        int read() { return count + values[0]; }
                    }
                    static class LambdaPayload {
                        private int count;
                        private final int[] values = new int[1];
                        void update() { values[0] = ++count; }
                        int read() { return count + values[0]; }
                    }
                    public static void main(String[] args) throws Exception {
                        Task task = new Task(); Runnable runnable = task;
                        Call call = new Call();
                        DirectThread thread = new DirectThread();
                        LambdaPayload payload = new LambdaPayload();
                        Thread wrapper = new Thread(() -> payload.update());
                        for (int i = 0; i < 12; i++) {
                            runnable.run(); System.out.println(call.call());
                            thread.run(); wrapper.run(); System.gc();
                        }
                        System.out.println(task.read()); System.out.println(thread.read());
                        System.out.println(payload.read());
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "SynchronousOwnership"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("SynchronousOwnership").buildRoot(temporary.resolve("out"))
                .buildType(buildType);
        var generated = builder.generate();
        Path output = generated.request().generatedSourcesDirectory();
        String fields = Files.readString(output.resolve("field-storage.tsv"));
        String arrays = Files.readString(output.resolve("array-ownership.tsv"));
        for(String owner : List.of("Task", "Call", "DirectThread", "LambdaPayload")) {
            assertTrue(fields.contains("SynchronousOwnership$" + owner + ".countI\tplain"), fields);
            assertTrue(arrays.contains("SynchronousOwnership$" + owner + ".values[I\tplain"), arrays);
        }
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(compiled.executable().toString()), Duration.ofSeconds(60),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void startedThreadOverrideAndLambdaKeepWorkerStorageOrdinary(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ThreadOwnership.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                public class ThreadOwnership {
                    static class Worker extends Thread {
                        private int count;
                        private final int[] values = new int[1];
                        public void run() { values[0] = ++count; new Local().get(); System.gc(); }
                        int read() { return count + values[0]; }
                    }
                    static class Payload {
                        private int count;
                        private final int[] values = new int[1];
                        void update() { values[0] = ++count; }
                        int read() { return count + values[0]; }
                    }
                    static class CallbackPayload {
                        private int count;
                        private final int[] values = new int[1];
                        int update() { values[0] = ++count; return count + values[0]; }
                    }
                    static class Local extends ThreadLocal<Integer> {
                        protected Integer initialValue() { return new CallbackPayload().update(); }
                    }
                    public static void main(String[] args) throws Exception {
                        Worker worker = new Worker();
                        Payload payload = new Payload();
                        Thread lambda = new Thread(() -> { payload.update(); System.gc(); });
                        worker.start(); lambda.start(); worker.join(); lambda.join();
                        System.out.println(worker.read()); System.out.println(payload.read());
                        System.out.println(new Local().get());
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ThreadOwnership"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("ThreadOwnership").buildRoot(temporary.resolve("out"))
                .buildType(buildType);
        var generated = builder.generate();
        Path output = generated.request().generatedSourcesDirectory();
        String fields = Files.readString(output.resolve("field-storage.tsv"));
        String arrays = Files.readString(output.resolve("array-ownership.tsv"));
        for(String owner : List.of("Worker", "Payload", "CallbackPayload")) {
            assertFalse(fields.contains("ThreadOwnership$" + owner + "."), fields);
            assertFalse(arrays.contains("ThreadOwnership$" + owner + "."), arrays);
        }
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(compiled.executable().toString()), Duration.ofSeconds(60),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void declaredNativeCallbacksReflectionExportsAndOpaquePayloadsStayOrdinary(boolean directCallbacks)
            throws Exception {
        Path source = temporary.resolve("CallbackOwnership.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                import java.util.concurrent.Callable;
                import com.github.xpenatan.jnative.interop.NativeImport;
                import com.github.xpenatan.jnative.interop.NativeImport.Invocation;
                import com.github.xpenatan.jnative.interop.NativeExport;
                import com.github.xpenatan.jnative.interop.NativeInclude;
                @NativeInclude("jnative_imports.h")
                public class CallbackOwnership {
                    static class Task implements Runnable {
                        private int count;
                        private final int[] values = new int[1];
                        public void run() { values[0] = ++count; }
                    }
                    static class Call implements Callable<Integer> {
                        private int count;
                        private final int[] values = new int[1];
                        public Integer call() { values[0] = ++count; return values[0]; }
                    }
                    static class Helper {
                        private int count;
                        private final int[] values = new int[1];
                        final Task task = new Task();
                        final Call call = new Call();
                        void execute() { values[0] = ++count; synchronous(task, call); }
                    }
                    public static class Reflected {
                        private int count;
                        private final int[] values = new int[1];
                        public int read() { return count + values[0]; }
                    }
                    static class ExportPayload {
                        private int count;
                        private final int[] values = new int[1];
                        int read() { return count + values[0]; }
                    }
                    static class NativePayload {
                        private int count;
                        private final int[] values = new int[1];
                        int read() { return count + values[0]; }
                    }
                    static class Envelope { final NativePayload payload = new NativePayload(); }
                    @NativeImport(value = "jnative::async_runnable", managed = true, runtimeOnly = true,
                        callbacks = {"java/lang/Runnable.run()V"}, callbackKinds = {Invocation.INTERFACE})
                    static void asyncRunnable(Runnable task) { task.run(); }
                    @NativeImport(value = "jnative::async_callable", managed = true, runtimeOnly = true,
                        callbacks = {"CallbackOwnership$Call.call()Ljava/lang/Object;"})
                    static void asyncCallable(Call call) throws Exception { call.call(); }
                    @NativeImport(value = "jnative::async_helper", managed = true, runtimeOnly = true,
                        callbacks = {"CallbackOwnership$Helper.execute()V"})
                    static void asyncHelper(Helper helper) { helper.execute(); }
                    @NativeImport(value = "jnative::sync_tasks", managed = true, runtimeOnly = true,
                        callbacksSynchronous = true,
                        callbacks = {"java/lang/Runnable.run()V", "CallbackOwnership$Call.call()Ljava/lang/Object;"},
                        callbackKinds = {Invocation.INTERFACE, Invocation.VIRTUAL}, callbackReceivers = {0, 1})
                    static void synchronous(Runnable task, Call call) {}
                    @NativeImport("inspect_callback_envelope") static void inspect(Envelope envelope) {}
                    @NativeExport("callback_ownership_read")
                    static int exported() { return new ExportPayload().read(); }
                    public static void main(String[] args) throws Exception {
                        DIRECT_CALLBACKS
                        asyncHelper(new Helper());
                        System.out.println(new Reflected().read()); System.out.println(exported());
                        Envelope envelope = new Envelope(); inspect(envelope);
                        System.out.println(envelope.payload.read());
                    }
                }
                """.replace("DIRECT_CALLBACKS", directCallbacks
                        ? "asyncRunnable(new Task()); asyncCallable(new Call());" : ""));
        ProcessHarness.javac(source, classes, 25);
        var generated = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("CallbackOwnership").buildRoot(temporary.resolve("out"))
                .reflectClass("CallbackOwnership$Reflected").generate();
        Path output = generated.request().generatedSourcesDirectory();
        String fields = Files.readString(output.resolve("field-storage.tsv"));
        String arrays = Files.readString(output.resolve("array-ownership.tsv"));
        for(String owner : List.of("Task", "Call", "Helper", "Reflected", "ExportPayload", "NativePayload")) {
            assertFalse(fields.contains("CallbackOwnership$" + owner + "."), fields);
            assertFalse(arrays.contains("CallbackOwnership$" + owner + "."), arrays);
        }
    }
}
