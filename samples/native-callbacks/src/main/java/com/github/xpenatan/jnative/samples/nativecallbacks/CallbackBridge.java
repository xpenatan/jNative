package com.github.xpenatan.jnative.samples.nativecallbacks;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reference bodies run only on the JVM; generated calls enter callbacks.cpp.
 */
@NativeInclude("callbacks.h")
public final class CallbackBridge {
    private CallbackBridge() {
    }

    @NativeImport("sample_callbacks_marker")
    public static boolean isNative() {
        return false;
    }

    @NativeImport("sample_run_workers")
    public static long runWorkers(String prefix, int workerCount, int tasksPerWorker) {
        if(workerCount < 1 || workerCount > 32 || tasksPerWorker < 1 || tasksPerWorker > 1000)
            throw new RuntimeException("workers must be 1..32 and tasks must be 1..1000");
        AtomicLong total = new AtomicLong();
        Thread[] workers = new Thread[workerCount];
        for(int worker = 0; worker < workerCount; ++worker) {
            final int workerId = worker;
            workers[worker] =
                    new Thread(
                            () -> {
                                for(int task = 0; task < tasksPerWorker; ++task)
                                    total.addAndGet(
                                            JavaCallbacks.process(prefix, workerId, task).length());
                            });
            workers[worker].start();
        }
        for(Thread worker : workers) {
            try {
                worker.join();
            } catch(InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("reference worker interrupted", error);
            }
        }
        return total.get();
    }

    @NativeImport("sample_roundtrip_exception")
    public static void roundtripException() {
        JavaCallbacks.reject();
    }
}
