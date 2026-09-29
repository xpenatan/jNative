package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

/**
 * Primitive value used by boxing and reflection.
 */
@SubstituteClass("java.lang.Byte")
public final class Byte extends Number implements Comparable<Byte> {
    private final byte value;
    private static final Byte[] CACHE = new Byte[256];

    static {
        for(int i = 0; i < CACHE.length; ++i) CACHE[i] = new Byte((byte)(i - 128));
    }

    public Byte(byte value) {
        this.value = value;
    }

    public static Byte valueOf(byte value) {
        return value >= -128 && value <= 127 ? CACHE[value + 128] : new Byte(value);
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
        return other instanceof Byte && ((Byte)other).value == value;
    }

    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(byte value) {
        return (int)value;
    }

    public int compareTo(Byte other) {
        return compare(value, other.value);
    }

    public static int compare(byte first, byte second) {
        return first < second ? -1 : first == second ? 0 : 1;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(byte value) {
        return String.valueOf(value);
    }
}
