package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.lang.CharSequence;

@SubstituteClass("java.lang.StringBuffer")
public final class StringBuffer implements CharSequence {
    private final StringBuilder text;

    public StringBuffer() {
        text = new StringBuilder();
    }

    public StringBuffer(String value) {
        text = new StringBuilder(value);
    }

    public synchronized StringBuffer append(String value) {
        text.append(value);
        return this;
    }

    public synchronized StringBuffer append(char value) {
        text.append(value);
        return this;
    }

    public synchronized StringBuffer append(Object value) {
        text.append(value);
        return this;
    }

    public synchronized int length() {
        return text.length();
    }

    public synchronized char charAt(int index) {
        return text.charAt(index);
    }

    public synchronized CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
    }

    public synchronized String toString() {
        return text.toString();
    }
}
