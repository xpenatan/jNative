package com.github.xpenatan.jnative.classlib.java.util.concurrent;

import com.github.xpenatan.jnative.classlib.java.util.Map;
import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.util.concurrent.*;

@SubstituteClass("java.util.concurrent.ConcurrentMap")
public interface ConcurrentMap<K, V> extends Map<K, V> {
    V putIfAbsent(K key, V value);

    boolean remove(Object key, Object value);
}
