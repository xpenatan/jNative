package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

/**
 * Primitive value used by boxing and reflection.
 */
@SubstituteClass("java.lang.Short")
public final class Short extends Number implements Comparable<Short> {
    private final short value;
    private static final class Cache {
        static final Short[] VALUES = new Short[256];
        static {
            for(int i = 0; i < VALUES.length; ++i) VALUES[i] = new Short((short)(i - 128));
        }
    }

    public Short(short value) {
        this.value = value;
    }

    public static Short valueOf(short value) {
        if(value >= -128 && value <= 127) return Cache.VALUES[value + 128];
        return new Short(value);
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
        return other instanceof Short && ((Short)other).value == value;
    }

    public int hashCode() {
        return value;
    }

    public static int hashCode(short value) {
        return (int)value;
    }

    public int compareTo(Short other) {
        return compare(value, other.value);
    }

    public static int compare(short first, short second) {
        return first < second ? -1 : first == second ? 0 : 1;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(short value) {
        return String.valueOf(value);
    }
}
