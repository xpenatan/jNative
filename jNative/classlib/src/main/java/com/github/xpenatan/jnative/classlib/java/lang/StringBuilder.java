package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.classlib.java.util.Arrays;
import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.io.Serializable;
import java.lang.CharSequence;

@NativeInclude("jn_string_kernels.hpp")
@SubstituteClass("java.lang.StringBuilder")
public final class StringBuilder implements CharSequence, Serializable {
    private char[] data;
    private int count;

    public StringBuilder appendCodePoint(int value) {
        if(value < 0 || value > 0x10ffff) throw new IllegalArgumentException("Invalid code point");
        int width = Character.charCount(value);
        ensure(count + width);
        if(width == 1) {
            data[count++] = (char)value;
        }
        else {
            data[count++] = (char)(0xd800 + ((value - 0x10000) >>> 10));
            data[count++] = (char)(0xdc00 + (value & 1023));
        }
        return this;
    }

    public StringBuilder append(CharSequence value, int start, int end) {
        if(value == null) value = "null";
        if(start < 0 || end < start || end > value.length()) throw new IndexOutOfBoundsException();
        if(start == end) return this;
        if(value instanceof String) {
            int length = end - start;
            ensure(count + length);
            NativeStrings.getChars((String)value, start, end, data, count);
            count += length;
            return this;
        }
        if(value instanceof StringBuilder) {
            int length = end - start;
            ensure(count + length);
            System.arraycopy(((StringBuilder)value).data, start, data, count, length);
            count += length;
            return this;
        }
        appendSequence(this, value, start, end);
        return this;
    }

    @NativeImport(value = "jnative::builder_append_sequence", managed = true, runtimeOnly = true,
            callbacksSynchronous = true, managesRoots = true,
            callbacks = {"java/lang/CharSequence.charAt(I)C"},
            callbackReceivers = {1},
            callbackKinds = {NativeImport.Invocation.INTERFACE},
            fields = {"java/lang/StringBuilder.data:[C", "java/lang/StringBuilder.count:I"})
    private static native void appendSequence(StringBuilder target, CharSequence value, int start, int end);

    @NativeImport(value = "jnative::string_from_char_range", managed = true, runtimeOnly = true)
    private static native String fromRange(char[] data, int start, int end);

    public StringBuilder delete(int start, int end) {
        if(start < 0 || start > count || end < start) throw new StringIndexOutOfBoundsException();
        end = Math.min(end, count);
        System.arraycopy(data, end, data, start, count - end);
        count -= end - start;
        return this;
    }

    public StringBuilder insert(int offset, String text) {
        if(offset < 0 || offset > count) throw new StringIndexOutOfBoundsException();
        if(text == null) text = "null";
        ensure(Math.addExact(count, text.length()));
        System.arraycopy(data, offset, data, offset + text.length(), count - offset);
        NativeStrings.getChars(text, 0, text.length(), data, offset);
        count += text.length();
        return this;
    }

    public StringBuilder() {
        data = new char[16];
    }

    public StringBuilder(int capacity) {
        data = new char[capacity];
    }

    public StringBuilder(String value) {
        this(value.length() + 16);
        append(value);
    }

    private void ensure(int size) {
        if(size < 0) throw new OutOfMemoryError();
        if(size <= data.length) return;
        int grownSize = data.length * 2 + 2;
        if(grownSize < size) grownSize = size;
        char[] grown = new char[grownSize];
        System.arraycopy(data, 0, grown, 0, count);
        data = grown;
    }

    public int length() {
        return count;
    }

    public int capacity() {
        return data.length;
    }

    public StringBuilder append(String value) {
        if(value == null) value = "null";
        ensure(count + value.length());
        NativeStrings.getChars(value, 0, value.length(), data, count);
        count += value.length();
        return this;
    }

    public StringBuilder append(Object value) {
        return append(String.valueOf(value));
    }

    public StringBuilder append(char value) {
        ensure(count + 1);
        data[count++] = value;
        return this;
    }

    public StringBuilder append(boolean value) {
        return append(String.valueOf(value));
    }

    public StringBuilder append(int value) {
        return append(String.valueOf(value));
    }

    public StringBuilder append(long value) {
        return append(String.valueOf(value));
    }

    public StringBuilder append(float value) {
        return append(String.valueOf(value));
    }

    public StringBuilder append(double value) {
        return append(String.valueOf(value));
    }

    public char charAt(int index) {
        if(index < 0 || index >= count) throw new StringIndexOutOfBoundsException();
        return data[index];
    }

    public void setCharAt(int index, char value) {
        if(index < 0 || index >= count) throw new StringIndexOutOfBoundsException();
        data[index] = value;
    }

    public void setLength(int size) {
        if(size < 0) throw new StringIndexOutOfBoundsException();
        ensure(size);
        if(count < size) Arrays.fill(data, count, size, '\0');
        count = size;
    }

    public String toString() {
        return fromRange(data, 0, count);
    }

    public String substring(int start) {
        return substring(start, count);
    }

    public String substring(int start, int end) {
        if(start < 0 || end < start || end > count) throw new StringIndexOutOfBoundsException();
        return fromRange(data, start, end);
    }

    public CharSequence subSequence(int start, int end) {
        return substring(start, end);
    }
}
