package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.io.Serializable;
import java.util.*;

@SubstituteClass("java.util.HashSet")
public class HashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable, Serializable {
    private final HashMap<E, Object> values;
    private static final Object PRESENT = new Object();

    public HashSet() {
        values = new HashMap<E, Object>();
    }

    public HashSet(int capacity) {
        values = new HashMap<E, Object>(capacity);
    }

    public HashSet(Collection<? extends E> source) {
        this();
        addAll(source);
    }

    public int size() {
        return values.size();
    }

    public boolean add(E value) {
        return values.put(value, PRESENT) == null;
    }

    public boolean contains(Object value) {
        return values.containsKey(value);
    }

    public boolean remove(Object value) {
        return values.remove(value) != null;
    }

    public Iterator<E> iterator() {
        return values.keySet().iterator();
    }

    public void clear() {
        values.clear();
    }
}
