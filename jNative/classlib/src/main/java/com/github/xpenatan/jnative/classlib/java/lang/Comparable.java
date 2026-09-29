package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.lang.Comparable")
public interface Comparable<T> {
    int compareTo(T other);
}
