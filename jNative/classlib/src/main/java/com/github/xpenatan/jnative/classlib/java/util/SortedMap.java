package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.SortedMap")
public interface SortedMap<K, V> extends Map<K, V> {
    Comparator<? super K> comparator();

    K firstKey();

    K lastKey();
}
