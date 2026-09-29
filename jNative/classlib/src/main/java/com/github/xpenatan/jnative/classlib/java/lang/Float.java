package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

/**
 * Primitive value used by boxing and reflection.
 */
@NativeInclude("jn_classlib.hpp")
@SubstituteClass("java.lang.Float")
public final class Float extends Number implements Comparable<Float> {
    private final float value;

    public Float(float value) {
        this.value = value;
    }

    public static Float valueOf(float value) {
        return new Float(value);
    }

    public byte byteValue() {
        return (byte)value;
    }

    public short shortValue() {
        return (short)value;
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

    public boolean equals(Object other) {
        return other instanceof Float
                && floatToIntBits(value) == floatToIntBits(((Float)other).value);
    }

    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(float value) {
        return floatToIntBits(value);
    }

    public int compareTo(Float other) {
        return compare(value, other.value);
    }

    public static int compare(float first, float second) {
        if(first < second) return -1;
        if(first > second) return 1;
        int a = floatToIntBits(first), b = floatToIntBits(second);
        return a == b ? 0 : a < b ? -1 : 1;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(float value) {
        return String.valueOf(value);
    }

    @NativeImport(value = "jnative::floating_is_nan", managed = true, runtimeOnly = true, bounded = true)
    public static boolean isNaN(float value) {
        return value != value;
    }

    @NativeImport(value = "jnative::floating_is_infinite", managed = true, runtimeOnly = true, bounded = true)
    public static boolean isInfinite(float value) {
        return value == 1.0f / 0.0f || value == -1.0f / 0.0f;
    }

    @NativeImport(value = "jnative::floating_is_finite", managed = true, runtimeOnly = true, bounded = true)
    public static boolean isFinite(float value) {
        return !isNaN(value) && !isInfinite(value);
    }

    public boolean isNaN() {
        return isNaN(value);
    }

    public boolean isInfinite() {
        return isInfinite(value);
    }

    @NativeImport(value = "jnative::float_canonical_bits", managed = true, runtimeOnly = true, bounded = true)
    public static native int floatToIntBits(float value);

    @NativeImport(value = "jnative::float_raw_bits", managed = true, runtimeOnly = true, bounded = true)
    public static native int floatToRawIntBits(float value);

    @NativeImport(value = "jnative::bits_float", managed = true, runtimeOnly = true, bounded = true)
    public static native float intBitsToFloat(int bits);

    @NativeImport(value = "jnative::parse_float", managed = true, runtimeOnly = true,
            types = {"java/lang/NumberFormatException"})
    public static native float parseFloat(String value);
}
