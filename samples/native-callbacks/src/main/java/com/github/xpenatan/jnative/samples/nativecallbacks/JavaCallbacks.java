package com.github.xpenatan.jnative.samples.nativecallbacks;

import com.github.xpenatan.jnative.interop.NativeExport;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * These methods become checked C entry points in jnative_exports.h.
 */
public final class JavaCallbacks {
    private static final AtomicInteger completed = new AtomicInteger();
    private static final ThreadLocal<Integer> nextTask = new ThreadLocal<>();

    private JavaCallbacks() {
    }

    @NativeExport("sample_java_process")
    public static String process(String prefix, int worker, int task) {
        Integer next = nextTask.get();
        if(task != (next == null ? 0 : next.intValue()))
            throw new IllegalStateException("callback thread-local state was lost");
        nextTask.set(task + 1);
        String text = prefix + ":" + worker + ":" + task;
        System.gc(); // The borrowed argument and new result must both remain alive.
        completed.incrementAndGet();
        return text;
    }

    @NativeExport("sample_java_reject")
    public static void reject() {
        throw new IllegalArgumentException("rejected by Java callback");
    }

    public static int completedCount() {
        return completed.get();
    }
}
