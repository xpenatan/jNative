package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.Iterator")
public interface Iterator<E> {
    boolean hasNext();

    E next();

    default void remove() {
        throw new UnsupportedOperationException();
    }
}
