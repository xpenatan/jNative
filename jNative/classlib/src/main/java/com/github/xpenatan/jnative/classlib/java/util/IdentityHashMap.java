package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.IdentityHashMap")
public class IdentityHashMap<K, V> extends HashMap<K, V> {
    public IdentityHashMap() {
        super();
    }

    public IdentityHashMap(int expectedSize) {
        super(expectedSize);
    }

    protected int hash(Object key) {
        return System.identityHashCode(key);
    }

    protected boolean keysEqual(Object first, Object second) {
        return first == second;
    }
}
