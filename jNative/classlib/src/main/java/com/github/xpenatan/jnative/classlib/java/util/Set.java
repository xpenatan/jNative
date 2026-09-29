package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.Set")
public interface Set<E> extends Collection<E> {

    static <E> Set<E> of(E... values) {
        Set<E> result = new HashSet<E>();
        NativeCollections.populateSet(result, values);
        return Collections.unmodifiableSet(result);
    }
}
