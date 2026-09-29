package com.github.xpenatan.jnative.samples.nativecallbacks;

/**
 * Three native workers reenter Java, retain results and preserve per-thread state.
 */
public final class NativeCallbacks {
    private NativeCallbacks() {
    }

    public static void main(String[] args) {
        System.out.println(
                CallbackBridge.isNative() ? "Execution: native C/C++" : "Execution: JVM reference");
        long textUnits = CallbackBridge.runWorkers("Task 🌎", 3, 4);
        System.out.println("Workers: 3");
        System.out.println("Callbacks completed: " + JavaCallbacks.completedCount());
        System.out.println("Returned UTF-16 units: " + textUnits);
        System.out.println("ThreadLocal order and GC: passed");
        try {
            CallbackBridge.roundtripException();
        } catch(IllegalArgumentException error) {
            System.out.println("Caught callback error: " + error.getMessage());
        }
        System.out.println("Native workers joined and handles released");
    }
}
