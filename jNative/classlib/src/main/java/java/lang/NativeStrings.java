package java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/**
 * Internal implementations reached through String bytecode lowering.
 */
@NativeInclude("jn_string_kernels.hpp")
public final class NativeStrings {
    private NativeStrings() {
    }

    public static int indexOf(String text, int value) {
        return indexOf(text, value, 0);
    }

    @NativeImport(value = "jnative::string_index_of_code_point", managed = true, runtimeOnly = true)
    public static native int indexOf(String text, int value, int start);

    public static int indexOf(String text, String part) {
        return indexOf(text, part, 0);
    }

    @NativeImport(value = "jnative::string_index_of_text", managed = true, runtimeOnly = true)
    public static native int indexOf(String text, String part, int start);

    public static int lastIndexOf(String text, int value) {
        return lastIndexOf(text, value, text.length() - 1);
    }

    @NativeImport(value = "jnative::string_last_index_of_code_point", managed = true, runtimeOnly = true)
    public static native int lastIndexOf(String text, int value, int start);

    public static boolean startsWith(String text, String prefix) {
        return startsWith(text, prefix, 0);
    }

    @NativeImport(value = "jnative::string_starts_with", managed = true, runtimeOnly = true)
    public static native boolean startsWith(String text, String prefix, int offset);

    public static boolean endsWith(String text, String suffix) {
        return startsWith(text, suffix, text.length() - suffix.length());
    }

    public static boolean contains(String text, CharSequence part) {
        return indexOf(text, part.toString()) >= 0;
    }

    public static boolean equalsIgnoreCase(String text, String other) {
        int length = text.length();
        return other != null && length == other.length() && compareToIgnoreCase(text, other) == 0;
    }

    @NativeImport(value = "jnative::string_compare_ignore_case", managed = true, runtimeOnly = true)
    public static native int compareToIgnoreCase(String text, String other);

    @NativeImport(value = "jnative::string_to_char_array", managed = true, runtimeOnly = true)
    public static native char[] toCharArray(String text);

    @NativeImport(value = "jnative::string_get_chars", managed = true, runtimeOnly = true)
    public static native void getChars(String text, int start, int end, char[] target, int offset);

    public static int codePointAt(String text, int index) {
        char first = text.charAt(index);
        if(Character.isHighSurrogate(first) && index + 1 < text.length()) {
            char next = text.charAt(index + 1);
            if(Character.isLowSurrogate(next)) return Character.toCodePoint(first, next);
        }
        return first;
    }

    public static int codePointBefore(String text, int index) {
        char last = text.charAt(index - 1);
        if(Character.isLowSurrogate(last) && index > 1) {
            char first = text.charAt(index - 2);
            if(Character.isHighSurrogate(first)) return Character.toCodePoint(first, last);
        }
        return last;
    }

    @NativeImport(value = "jnative::string_code_point_count", managed = true, runtimeOnly = true)
    public static native int codePointCount(String text, int start, int end);

    @NativeImport(value = "jnative::string_offset_by_code_points", managed = true, runtimeOnly = true)
    public static native int offsetByCodePoints(String text, int index, int offset);

    @NativeImport(value = "jnative::string_is_blank", managed = true, runtimeOnly = true)
    public static native boolean isBlank(String text);

    @NativeImport(value = "jnative::string_replace_char", managed = true, runtimeOnly = true)
    public static native String replace(String text, char before, char after);

    public static String replace(String text, CharSequence before, CharSequence after) {
        String needle = Objects.requireNonNull(before).toString();
        String replacement = Objects.requireNonNull(after).toString();
        return replaceText(text, needle, replacement);
    }

    @NativeImport(value = "jnative::string_replace_text", managed = true, runtimeOnly = true)
    private static native String replaceText(String text, String needle, String replacement);

    @NativeImport(value = "jnative::string_repeat", managed = true, runtimeOnly = true)
    public static native String repeat(String text, int count);

    public static String toLowerCase(String text) {
        return toLowerCase(text, Locale.getDefault());
    }

    public static String toUpperCase(String text) {
        return toUpperCase(text, Locale.getDefault());
    }

    public static String toLowerCase(String text, Locale locale) {
        return changeCase(text, locale, false);
    }

    public static String toUpperCase(String text, Locale locale) {
        return changeCase(text, locale, true);
    }

    private static String changeCase(String text, Locale locale, boolean upper) {
        return changeCase(text, Objects.requireNonNull(locale).getLanguage(), upper);
    }

    @NativeImport(value = "jnative::string_change_case", managed = true, runtimeOnly = true)
    private static native String changeCase(String text, String language, boolean upper);

    public static java.util.stream.IntStream chars(String text) {
        return new java.util.stream.IntStream(text);
    }

    public static byte[] getBytes(String text, Charset charset) {
        return charset.encodeBytes(text);
    }

    public static byte[] getBytes(String text, String charset)
            throws java.io.UnsupportedEncodingException {
        try {
            return Charset.forName(charset).encodeBytes(text);
        } catch(IllegalArgumentException failure) {
            throw new java.io.UnsupportedEncodingException(charset);
        }
    }

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
    public static native String format(Locale locale, String pattern, Object... arguments);

    public static String[] split(String text, String expression) {
        return split(text, expression, 0);
    }

    public static String[] split(String text, String expression, int limit) {
        return java.util.regex.Pattern.compile(expression).split(text, limit);
    }

    public static boolean matches(String text, String expression) {
        return java.util.regex.Pattern.matches(expression, text);
    }

    public static String replaceAll(String text, String expression, String replacement) {
        return java.util.regex.Pattern.compile(expression).matcher(text).replaceAll(replacement);
    }

    public static String replaceFirst(String text, String expression, String replacement) {
        return java.util.regex.Pattern.compile(expression).matcher(text).replaceFirst(replacement);
    }
}
