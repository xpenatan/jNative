package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

/**
 * Primitive value used by boxing and reflection.
 */
@NativeInclude("jn_classlib.hpp")
@SubstituteClass("java.lang.Double")
public final class Double extends Number implements Comparable<Double> {
    private final double value;

    public Double(double value) {
        this.value = value;
    }

    public static Double valueOf(double value) {
        return new Double(value);
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
        return other instanceof Double
                && doubleToLongBits(value) == doubleToLongBits(((Double)other).value);
    }

    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(double value) {
        long bits = doubleToLongBits(value);
        return (int)(bits ^ (bits >>> 32));
    }

    public int compareTo(Double other) {
        return compare(value, other.value);
    }

    public static int compare(double first, double second) {
        if(first < second) return -1;
        if(first > second) return 1;
        long a = doubleToLongBits(first), b = doubleToLongBits(second);
        return a == b ? 0 : a < b ? -1 : 1;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(double value) {
        return String.valueOf(value);
    }

    @NativeImport(value = "jnative::floating_is_nan", managed = true, runtimeOnly = true, bounded = true)
    public static boolean isNaN(double value) {
        return value != value;
    }

    @NativeImport(value = "jnative::floating_is_infinite", managed = true, runtimeOnly = true, bounded = true)
    public static boolean isInfinite(double value) {
        return value == 1.0 / 0.0 || value == -1.0 / 0.0;
    }

    @NativeImport(value = "jnative::floating_is_finite", managed = true, runtimeOnly = true, bounded = true)
    public static boolean isFinite(double value) {
        return !isNaN(value) && !isInfinite(value);
    }

    public boolean isNaN() {
        return isNaN(value);
    }

    public boolean isInfinite() {
        return isInfinite(value);
    }

    @NativeImport(value = "jnative::double_canonical_bits", managed = true, runtimeOnly = true, bounded = true)
    public static native long doubleToLongBits(double value);

    @NativeImport(value = "jnative::double_raw_bits", managed = true, runtimeOnly = true, bounded = true)
    public static native long doubleToRawLongBits(double value);

    @NativeImport(value = "jnative::bits_double", managed = true, runtimeOnly = true, bounded = true)
    public static native double longBitsToDouble(long bits);

    @NativeImport(value = "jnative::parse_double", managed = true, runtimeOnly = true,
            types = {"java/lang/NumberFormatException"})
    public static native double parseDouble(String value);
}
