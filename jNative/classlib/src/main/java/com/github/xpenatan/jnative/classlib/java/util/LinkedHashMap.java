package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

/**
 * Insertion ordered hash map. HashMap's shared node chain provides stable iteration.
 */
@SubstituteClass("java.util.LinkedHashMap")
public class LinkedHashMap<K, V> extends HashMap<K, V> {
    public LinkedHashMap() {
        super();
    }

    public LinkedHashMap(int capacity) {
        super(capacity);
    }

    public LinkedHashMap(int capacity, float loadFactor) {
        super(capacity, loadFactor);
    }

    public LinkedHashMap(Map<? extends K, ? extends V> values) {
        super(values);
    }
}
