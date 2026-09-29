package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@NativeInclude("jn_classlib_numbers.hpp")
@SubstituteClass("java.lang.Integer")
public final class Integer extends Number implements Comparable<Integer> {
    public static final int MIN_VALUE = 0x80000000, MAX_VALUE = 0x7fffffff;
    public static final int SIZE = 32, BYTES = 4;
    private final int value;

    public static Integer getInteger(String name, int fallback) {
        try {
            String value = System.getProperty(name);
            if(value != null) return decode(value);
        } catch(IllegalArgumentException | NullPointerException ignored) {
        }
        return valueOf(fallback);
    }

    public static Integer decode(String text) {
        return valueOf(decodeValue(text));
    }

    @NativeImport(value = "jnative::integer_decode", managed = true, runtimeOnly = true,
            types = {"java/lang/NumberFormatException"})
    private static native int decodeValue(String text);

    public static long toUnsignedLong(int value) {
        return value & 0xffffffffL;
    }

    @NativeImport(value = "jnative::integer_leading_zeros", managed = true, runtimeOnly = true, bounded = true)
    public static native int numberOfLeadingZeros(int value);

    @NativeImport(value = "jnative::integer_trailing_zeros", managed = true, runtimeOnly = true, bounded = true)
    public static native int numberOfTrailingZeros(int value);

    public static int rotateRight(int value, int distance) {
        return (value >>> distance) | (value << -distance);
    }

    @NativeImport(value = "jnative::integer_hex", managed = true, runtimeOnly = true)
    public static native String toHexString(int value);

    private static final Integer[] CACHE = new Integer[256];

    static {
        for(int i = 0; i < 256; ++i) CACHE[i] = new Integer(i - 128);
    }

    public Integer(int value) {
        this.value = value;
    }

    public static Integer valueOf(int value) {
        return value >= -128 && value <= 127 ? CACHE[(int)value + 128] : new Integer(value);
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
        return other instanceof Integer && ((Integer)other).value == value;
    }

    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(int value) {
        return value;
    }

    public int compareTo(Integer other) {
        return compare(value, other.value);
    }

    public static int compare(int first, int second) {
        return first < second ? -1 : first == second ? 0 : 1;
    }

    public String toString() {
        return toString(value);
    }

    @NativeImport(value = "jnative::integer_string", managed = true, runtimeOnly = true)
    public static native String toString(int value);

    public static int parseInt(String text) {
        return parseInt(text, 10);
    }

    @NativeImport(value = "jnative::integer_parse", managed = true, runtimeOnly = true,
            types = {"java/lang/NumberFormatException"})
    public static native int parseInt(String text, int radix);
}
