package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.lang.CharSequence")
public interface CharSequence {
    int length();

    char charAt(int index);

    CharSequence subSequence(int start, int end);

    String toString();

    default boolean isEmpty() {
        return length() == 0;
    }
}
