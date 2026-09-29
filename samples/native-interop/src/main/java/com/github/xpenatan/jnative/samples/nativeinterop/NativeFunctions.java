package com.github.xpenatan.jnative.samples.nativeinterop;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/**
 * Java bodies run on the JVM; the generated program calls the named C-linkage functions.
 */
@NativeInclude("sample_native.h")
public final class NativeFunctions {
    private NativeFunctions() {
    }

    @NativeImport("sample_native_marker")
    public static boolean isNative() {
        return false;
    }

    @NativeImport("sample_c_add")
    public static int add(int first, int second) {
        return first + second;
    }

    @NativeImport("sample_c_checksum")
    public static long checksum(byte[] bytes) {
        long sum = 0;
        for(byte value : bytes) sum += value & 255;
        return sum;
    }

    @NativeImport("sample_cpp_weighted_average")
    public static double weightedAverage(double first, double second, double weight) {
        if(weight != weight || weight < 0 || weight > 1)
            throw new RuntimeException("weight must be between 0 and 1");
        return first * (1 - weight) + second * weight;
    }

    @NativeImport("sample_cpp_greeting")
    public static String greeting(String name) {
        return "Hello, " + name + " — from C++";
    }

    @NativeImport("sample_cpp_reverse")
    public static byte[] reversedCopy(byte[] input) {
        byte[] result = new byte[input.length];
        for(int index = 0; index < input.length; ++index)
            result[index] = input[input.length - 1 - index];
        return result;
    }
}
