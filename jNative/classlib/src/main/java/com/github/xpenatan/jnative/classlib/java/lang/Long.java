package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@NativeInclude("jn_classlib_numbers.hpp")
@SubstituteClass("java.lang.Long")
public final class Long extends Number implements Comparable<Long> {
    public static final long MIN_VALUE = 0x8000000000000000L, MAX_VALUE = 0x7fffffffffffffffL;
    public static final int SIZE = 64, BYTES = 8;
    private final long value;

    @NativeImport(value = "jnative::long_leading_zeros", managed = true, runtimeOnly = true, bounded = true)
    public static native int numberOfLeadingZeros(long value);

    private static final Long[] CACHE = new Long[256];

    static {
        for(int i = 0; i < 256; ++i) CACHE[i] = new Long(i - 128);
    }

    public static long parseLong(String text) {
        return parseLong(text, 10);
    }

    @NativeImport(value = "jnative::long_parse", managed = true, runtimeOnly = true,
            types = {"java/lang/NumberFormatException"})
    public static native long parseLong(String text, int radix);

    @NativeImport(value = "jnative::long_unsigned_string", managed = true, runtimeOnly = true)
    public static native String toUnsignedString(long value);

    public Long(long value) {
        this.value = value;
    }

    public static Long valueOf(long value) {
        return value >= -128 && value <= 127 ? CACHE[(int)value + 128] : new Long(value);
    }

    public int intValue() {
        return (int)value;
    }

    public long longValue() {
        return (long)value;
    }

    public float floatValue() {
        return (float)value;
    }

    public double doubleValue() {
        return (double)value;
    }

    public byte byteValue() {
        return (byte)value;
    }

    public short shortValue() {
        return (short)value;
    }

    public boolean equals(Object other) {
        return other instanceof Long && ((Long)other).value == value;
    }

    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(long value) {
        return (int)(value ^ (value >>> 32));
    }

    public int compareTo(Long other) {
        return compare(value, other.value);
    }

    public static int compare(long first, long second) {
        return first < second ? -1 : first == second ? 0 : 1;
    }

    public String toString() {
        return toString(value);
    }

    @NativeImport(value = "jnative::long_string", managed = true, runtimeOnly = true)
    public static native String toString(long value);
}
