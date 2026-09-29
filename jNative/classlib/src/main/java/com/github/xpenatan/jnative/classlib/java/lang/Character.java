package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

/**
 * Primitive value used by boxing and reflection.
 */
@NativeInclude("jn_classlib_numbers.hpp")
@SubstituteClass("java.lang.Character")
public final class Character implements Comparable<Character> {
    private final char value;

    @NativeImport(value = "jnative::unicode_info", managed = true, runtimeOnly = true, bounded = true)
    private static native int info(int codePoint);

    @NativeImport(value = "jnative::character_lower", managed = true, runtimeOnly = true, bounded = true)
    public static native int toLowerCase(int codePoint);

    @NativeImport(value = "jnative::character_upper", managed = true, runtimeOnly = true, bounded = true)
    public static native int toUpperCase(int codePoint);

    public static char toLowerCase(char value) {
        return (char)toLowerCase((int)value);
    }

    public static char toUpperCase(char value) {
        return (char)toUpperCase((int)value);
    }

    public static int getType(int codePoint) {
        return info(codePoint) & 255;
    }

    public static int getType(char value) {
        return getType((int)value);
    }

    public static boolean isWhitespace(int codePoint) {
        return (info(codePoint) & 256) != 0;
    }

    public static boolean isWhitespace(char value) {
        return isWhitespace((int)value);
    }

    public static boolean isJavaIdentifierStart(int codePoint) {
        return (info(codePoint) & 512) != 0;
    }

    public static boolean isJavaIdentifierStart(char value) {
        return isJavaIdentifierStart((int)value);
    }

    public static boolean isJavaIdentifierPart(int codePoint) {
        return (info(codePoint) & 1024) != 0;
    }

    public static boolean isJavaIdentifierPart(char value) {
        return isJavaIdentifierPart((int)value);
    }

    public static boolean isLetter(int codePoint) {
        int type = getType(codePoint);
        return type >= 1 && type <= 5;
    }

    public static boolean isLetter(char value) {
        return isLetter((int)value);
    }

    public static boolean isDigit(int codePoint) {
        return getType(codePoint) == 9;
    }

    public static boolean isDigit(char value) {
        return isDigit((int)value);
    }

    public static boolean isLetterOrDigit(int codePoint) {
        return isLetter(codePoint) || isDigit(codePoint);
    }

    public static boolean isLetterOrDigit(char value) {
        return isLetterOrDigit((int)value);
    }

    public static boolean isISOControl(int value) {
        return value >= 0 && value <= 31 || value >= 127 && value <= 159;
    }

    public static boolean isISOControl(char value) {
        return isISOControl((int)value);
    }

    public static boolean isHighSurrogate(char value) {
        return value >= 0xd800 && value <= 0xdbff;
    }

    public static boolean isLowSurrogate(char value) {
        return value >= 0xdc00 && value <= 0xdfff;
    }

    public static boolean isSurrogate(char value) {
        return value >= 0xd800 && value <= 0xdfff;
    }

    public static boolean isBmpCodePoint(int value) {
        return value >= 0 && value <= 0xffff;
    }

    public static int charCount(int value) {
        return value >= 0x10000 ? 2 : 1;
    }

    public static int toCodePoint(char high, char low) {
        return ((high - 0xd800) << 10) + low - 0xdc00 + 0x10000;
    }

    public static char[] toChars(int value) {
        if(value < 0 || value > 0x10ffff) throw new IllegalArgumentException("Invalid code point");
        if(value <= 0xffff) return new char[]{(char)value};
        return new char[]{
                (char)(0xd800 + ((value - 0x10000) >>> 10)), (char)(0xdc00 + (value & 1023))
        };
    }

    private static final Character[] CACHE = new Character[128];

    static {
        for(int i = 0; i < CACHE.length; ++i) CACHE[i] = new Character((char)(i));
    }

    public Character(char value) {
        this.value = value;
    }

    public static Character valueOf(char value) {
        return value < 128 ? CACHE[value] : new Character(value);
    }

    public char charValue() {
        return value;
    }

    public boolean equals(Object other) {
        return other instanceof Character && ((Character)other).value == value;
    }

    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(char value) {
        return (int)value;
    }

    public int compareTo(Character other) {
        return compare(value, other.value);
    }

    public static int compare(char first, char second) {
        return first < second ? -1 : first == second ? 0 : 1;
    }

    public String toString() {
        return String.valueOf(value);
    }

    public static String toString(char value) {
        return String.valueOf(value);
    }

    public static int digit(char value, int radix) {
        return digit((int)value, radix);
    }

    /**
     * Decimal digit ranges follow the JDK 25 character data.
     */
    @NativeImport(value = "jnative::character_digit", managed = true, runtimeOnly = true, bounded = true)
    public static native int digit(int codePoint, int radix);
}
