package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.io.Serializable;
import java.util.*;

@SubstituteClass("java.util.ArrayList")
public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, Serializable {
    private Object[] values;
    private int count;
    private boolean defaultCapacity;

    public ArrayList() {
        values = new Object[0];
        defaultCapacity = true;
    }

    public ArrayList(int capacity) {
        if(capacity < 0) throw new IllegalArgumentException("Negative capacity");
        values = new Object[capacity];
    }

    public ArrayList(Collection<? extends E> other) {
        values = other.toArray();
        count = values.length;
    }

    public int size() {
        return count;
    }

    private void bounds(int index) {
        if(index < 0 || index >= count) throw new IndexOutOfBoundsException();
    }

    public void ensureCapacity(int capacity) {
        if(defaultCapacity && capacity <= 10) return;
        if(capacity <= values.length) return;
        grow(capacity);
        ++modCount;
    }

    private void grow(int capacity) {
        int size = values.length + (values.length >> 1) + 1;
        if(defaultCapacity && size < 10) size = 10;
        if(size < capacity) size = capacity;
        Object[] grown = new Object[size];
        System.arraycopy(values, 0, grown, 0, count);
        values = grown;
        defaultCapacity = false;
    }

    public void trimToSize() {
        Object[] trimmed = new Object[count];
        System.arraycopy(values, 0, trimmed, 0, count);
        values = trimmed;
        defaultCapacity = false;
        ++modCount;
    }

    @SuppressWarnings("unchecked")
    public E get(int index) {
        bounds(index);
        return (E)values[index];
    }

    @SuppressWarnings("unchecked")
    public E set(int index, E value) {
        bounds(index);
        E previous = (E)values[index];
        values[index] = value;
        return previous;
    }

    public void add(int index, E value) {
        if(index < 0 || index > count) throw new IndexOutOfBoundsException();
        if(count == Integer.MAX_VALUE) throw new OutOfMemoryError();
        if(count == values.length) grow(count + 1);
        if(index < count) System.arraycopy(values, index, values, index + 1, count - index);
        values[index] = value;
        ++count;
        ++modCount;
    }

    @SuppressWarnings("unchecked")
    public E remove(int index) {
        bounds(index);
        E previous = (E)values[index];
        if(index + 1 < count) System.arraycopy(values, index + 1, values, index, count - index - 1);
        values[--count] = null;
        ++modCount;
        return previous;
    }

    public void clear() {
        Arrays.fill(values, 0, count, null);
        count = 0;
        ++modCount;
    }
}
