package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.classlib.java.io.UnsupportedEncodingException;
import com.github.xpenatan.jnative.classlib.java.nio.charset.Charset;
import com.github.xpenatan.jnative.classlib.java.util.ArrayList;
import com.github.xpenatan.jnative.classlib.java.util.Locale;
import com.github.xpenatan.jnative.classlib.java.util.Objects;
import com.github.xpenatan.jnative.classlib.java.util.regex.Pattern;
import com.github.xpenatan.jnative.classlib.java.util.stream.IntStream;
import com.github.xpenatan.jnative.classlib.java.util.stream.NativeStreams;
import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteMethod;

/**
 * String implementations registered through the public substitution API.
 */
@NativeInclude("jn_string_kernels.hpp")
public final class NativeStrings {
    private NativeStrings() {
    }

    @SubstituteMethod(owner = "java.lang.String", name = "indexOf", descriptor = "(I)I")
    public static int indexOf(String text, int value) {
        return indexOf(text, value, 0);
    }

    @NativeImport(value = "jnative::string_index_of_code_point", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "indexOf", descriptor = "(II)I")
    public static native int indexOf(String text, int value, int start);

    @SubstituteMethod(owner = "java.lang.String", name = "indexOf", descriptor = "(Ljava/lang/String;)I")
    public static int indexOf(String text, String part) {
        return indexOf(text, part, 0);
    }

    @NativeImport(value = "jnative::string_index_of_text", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "indexOf", descriptor = "(Ljava/lang/String;I)I")
    public static native int indexOf(String text, String part, int start);

    @SubstituteMethod(owner = "java.lang.String", name = "lastIndexOf", descriptor = "(I)I")
    public static int lastIndexOf(String text, int value) {
        return lastIndexOf(text, value, text.length() - 1);
    }

    @NativeImport(value = "jnative::string_last_index_of_code_point", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "lastIndexOf", descriptor = "(II)I")
    public static native int lastIndexOf(String text, int value, int start);

    @SubstituteMethod(owner = "java.lang.String", name = "startsWith", descriptor = "(Ljava/lang/String;)Z")
    public static boolean startsWith(String text, String prefix) {
        return startsWith(text, prefix, 0);
    }

    @NativeImport(value = "jnative::string_starts_with", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "startsWith", descriptor = "(Ljava/lang/String;I)Z")
    public static native boolean startsWith(String text, String prefix, int offset);

    @SubstituteMethod(owner = "java.lang.String", name = "endsWith", descriptor = "(Ljava/lang/String;)Z")
    public static boolean endsWith(String text, String suffix) {
        return startsWith(text, suffix, text.length() - suffix.length());
    }

    @SubstituteMethod(owner = "java.lang.String", name = "contains", descriptor = "(Ljava/lang/CharSequence;)Z")
    public static boolean contains(String text, CharSequence part) {
        return indexOf(text, part.toString()) >= 0;
    }

    @SubstituteMethod(owner = "java.lang.String", name = "equalsIgnoreCase", descriptor = "(Ljava/lang/String;)Z")
    public static boolean equalsIgnoreCase(String text, String other) {
        int length = text.length();
        return other != null && length == other.length() && compareToIgnoreCase(text, other) == 0;
    }

    @NativeImport(value = "jnative::string_compare_ignore_case", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "compareToIgnoreCase", descriptor = "(Ljava/lang/String;)I")
    public static native int compareToIgnoreCase(String text, String other);

    @NativeImport(value = "jnative::string_to_char_array", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "toCharArray", descriptor = "()[C")
    public static native char[] toCharArray(String text);

    @NativeImport(value = "jnative::string_get_chars", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "getChars", descriptor = "(II[CI)V")
    public static native void getChars(String text, int start, int end, char[] target, int offset);

    @SubstituteMethod(owner = "java.lang.String", name = "codePointAt", descriptor = "(I)I")
    public static int codePointAt(String text, int index) {
        char first = text.charAt(index);
        if(Character.isHighSurrogate(first) && index + 1 < text.length()) {
            char next = text.charAt(index + 1);
            if(Character.isLowSurrogate(next)) return Character.toCodePoint(first, next);
        }
        return first;
    }

    @SubstituteMethod(owner = "java.lang.String", name = "codePointBefore", descriptor = "(I)I")
    public static int codePointBefore(String text, int index) {
        char last = text.charAt(index - 1);
        if(Character.isLowSurrogate(last) && index > 1) {
            char first = text.charAt(index - 2);
            if(Character.isHighSurrogate(first)) return Character.toCodePoint(first, last);
        }
        return last;
    }

    @NativeImport(value = "jnative::string_code_point_count", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "codePointCount", descriptor = "(II)I")
    public static native int codePointCount(String text, int start, int end);

    @NativeImport(value = "jnative::string_offset_by_code_points", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "offsetByCodePoints", descriptor = "(II)I")
    public static native int offsetByCodePoints(String text, int index, int offset);

    @NativeImport(value = "jnative::string_is_blank", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "isBlank", descriptor = "()Z")
    public static native boolean isBlank(String text);

    @NativeImport(value = "jnative::string_replace_char", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "replace", descriptor = "(CC)Ljava/lang/String;")
    public static native String replace(String text, char before, char after);

    @SubstituteMethod(owner = "java.lang.String", name = "replace", descriptor = "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;")
    public static String replace(String text, CharSequence before, CharSequence after) {
        String needle = Objects.requireNonNull(before).toString();
        String replacement = Objects.requireNonNull(after).toString();
        return replaceText(text, needle, replacement);
    }

    @NativeImport(value = "jnative::string_replace_text", managed = true, runtimeOnly = true)
    private static native String replaceText(String text, String needle, String replacement);

    @NativeImport(value = "jnative::string_repeat", managed = true, runtimeOnly = true)
    @SubstituteMethod(owner = "java.lang.String", name = "repeat", descriptor = "(I)Ljava/lang/String;")
    public static native String repeat(String text, int count);

    @SubstituteMethod(owner = "java.lang.String", name = "toLowerCase", descriptor = "()Ljava/lang/String;")
    public static String toLowerCase(String text) {
        return toLowerCase(text, Locale.getDefault());
    }

    @SubstituteMethod(owner = "java.lang.String", name = "toUpperCase", descriptor = "()Ljava/lang/String;")
    public static String toUpperCase(String text) {
        return toUpperCase(text, Locale.getDefault());
    }

    @SubstituteMethod(owner = "java.lang.String", name = "toLowerCase", descriptor = "(Ljava/util/Locale;)Ljava/lang/String;")
    public static String toLowerCase(String text, Locale locale) {
        return changeCase(text, locale, false);
    }

    @SubstituteMethod(owner = "java.lang.String", name = "toUpperCase", descriptor = "(Ljava/util/Locale;)Ljava/lang/String;")
    public static String toUpperCase(String text, Locale locale) {
        return changeCase(text, locale, true);
    }

    private static String changeCase(String text, Locale locale, boolean upper) {
        return changeCase(text, Objects.requireNonNull(locale).getLanguage(), upper);
    }

    @NativeImport(value = "jnative::string_change_case", managed = true, runtimeOnly = true)
    private static native String changeCase(String text, String language, boolean upper);

    @SubstituteMethod(owner = "java.lang.String", name = "chars", descriptor = "()Ljava/util/stream/IntStream;")
    public static IntStream chars(String text) {
        return NativeStreams.chars(text);
    }

    @SubstituteMethod(owner = "java.lang.String", name = "getBytes", descriptor = "(Ljava/nio/charset/Charset;)[B")
    public static byte[] getBytes(String text, Charset charset) {
        return charset.encodeBytes(text);
    }

    @SubstituteMethod(owner = "java.lang.String", name = "getBytes", descriptor = "(Ljava/lang/String;)[B")
    public static byte[] getBytes(String text, String charset)
            throws UnsupportedEncodingException {
        try {
            return Charset.forName(charset).encodeBytes(text);
        } catch(IllegalArgumentException failure) {
            throw new UnsupportedEncodingException(charset);
        }
    }

    @SubstituteMethod(owner = "java.lang.String", name = "getBytes", descriptor = "()[B")
    public static byte[] getBytes(String text) {
        return getBytes(text, Charset.defaultCharset());
    }

    public static void initialize(String target, byte[] bytes, Charset charset) {
        assign(target, charset.decodeBytes(bytes, 0, bytes.length, false));
    }

    public static void initialize(String target, byte[] bytes, int offset, int length) {
        assign(target, Charset.defaultCharset().decodeBytes(bytes, offset, length, false));
    }

    public static void initialize(String target, byte[] bytes) {
        initialize(target, bytes, 0, bytes.length);
    }

    @NativeImport(value = "jnative::string_assign", managed = true, runtimeOnly = true)
    private static native void assign(String target, String value);

    @NativeImport(value = "jnative::format_decimal", managed = true, runtimeOnly = true)
    private static native String decimal(double value, int precision, char conversion);

    @SubstituteMethod(owner = "java.lang.String", name = "format", descriptor = "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")
    public static String format(String pattern, Object... arguments) {
        return format(Locale.getDefault(), pattern, arguments);
    }

    @NativeImport(value = "jnative::string_format", managed = true, runtimeOnly = true, callbacksSynchronous = true,
            callbacks = {"java/lang/Object.toString()Ljava/lang/String;",
                    "java/lang/Number.longValue()J", "java/lang/Number.intValue()I",
                    "java/lang/Number.doubleValue()D", "java/lang/Boolean.booleanValue()Z",
                    "java/util/Locale.getLanguage()Ljava/lang/String;",
                    "java/lang/System.getProperty(Ljava/lang/String;)Ljava/lang/String;"},
            callbackReceivers = {-1, -1, -1, -1, -1, 0, -1},
            callbackKinds = {NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.VIRTUAL,
                    NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.VIRTUAL,
                    NativeImport.Invocation.VIRTUAL,
                    NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.STATIC})
    @SubstituteMethod(owner = "java.lang.String", name = "format", descriptor = "(Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")
    public static native String format(Locale locale, String pattern, Object... arguments);

    @SubstituteMethod(owner = "java.lang.String", name = "split", descriptor = "(Ljava/lang/String;)[Ljava/lang/String;")
    public static String[] split(String text, String expression) {
        return split(text, expression, 0);
    }

    @SubstituteMethod(owner = "java.lang.String", name = "split", descriptor = "(Ljava/lang/String;I)[Ljava/lang/String;")
    public static String[] split(String text, String expression, int limit) {
        return Pattern.compile(expression).split(text, limit);
    }

    @SubstituteMethod(owner = "java.lang.String", name = "matches", descriptor = "(Ljava/lang/String;)Z")
    public static boolean matches(String text, String expression) {
        return Pattern.matches(expression, text);
    }

    @SubstituteMethod(owner = "java.lang.String", name = "replaceAll", descriptor = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    public static String replaceAll(String text, String expression, String replacement) {
        return Pattern.compile(expression).matcher(text).replaceAll(replacement);
    }

    @SubstituteMethod(owner = "java.lang.String", name = "replaceFirst", descriptor = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    public static String replaceFirst(String text, String expression, String replacement) {
        return Pattern.compile(expression).matcher(text).replaceFirst(replacement);
    }
}
