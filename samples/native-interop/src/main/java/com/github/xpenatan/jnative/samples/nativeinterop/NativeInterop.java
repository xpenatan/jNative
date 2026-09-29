package com.github.xpenatan.jnative.samples.nativeinterop;

/**
 * Numbers, copied buffers, managed reference results and a caught C++ exception.
 */
public final class NativeInterop {
    private NativeInterop() {
    }

    public static void main(String[] args) {
        System.out.println(
                NativeFunctions.isNative()
                        ? "Execution: native C/C++"
                        : "Execution: JVM reference");
        System.out.println("C addition: " + NativeFunctions.add(20, 22));
        System.out.println("C integer wrap: " + NativeFunctions.add(Integer.MAX_VALUE, 1));
        System.out.println(
                "C++ weighted average: " + NativeFunctions.weightedAverage(10, 20, 0.25));
        System.out.println(NativeFunctions.greeting("Java 🌎"));

        byte[] input = {0, 1, 2, -1};
        byte[] reversed = NativeFunctions.reversedCopy(input);
        System.out.println("C unsigned-byte checksum: " + NativeFunctions.checksum(input));
        System.out.println("Original bytes: " + describe(input));
        System.out.println("C++ reversed copy: " + describe(reversed));
        System.gc();
        System.out.println("Copy survives GC: " + (reversed[0] == -1 && reversed[3] == 0));

        // The native implementation throws std::invalid_argument; the import wrapper
        // translates it to a Java RuntimeException, caught here.
        try {
            NativeFunctions.weightedAverage(10, 20, -0.25);
        } catch(RuntimeException failure) {
            System.out.println("Caught C++ error: " + failure.getMessage());
        }
    }

    private static String describe(byte[] values) {
        StringBuilder text = new StringBuilder();
        for(int index = 0; index < values.length; ++index) {
            if(index != 0) text.append(',');
            text.append(values[index]);
        }
        return text.toString();
    }
}
