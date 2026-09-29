package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.AbstractCollection")
public abstract class AbstractCollection<E> implements Collection<E> {
    protected AbstractCollection() {
    }

    public abstract Iterator<E> iterator();

    public abstract int size();

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean contains(Object value) {
        return NativeCollections.find(iterator(), value, false);
    }

    public boolean add(E value) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(Object value) {
        return NativeCollections.find(iterator(), value, true);
    }

    public Object[] toArray() {
        Object[] result = new Object[size()];
        NativeCollections.copy(iterator(), result);
        return result;
    }

    public boolean containsAll(Collection<?> other) {
        return NativeCollections.bulk(this, other.iterator(), 0);
    }

    public boolean addAll(Collection<? extends E> other) {
        return NativeCollections.bulk(this, other.iterator(), 1);
    }

    public boolean removeAll(Collection<?> other) {
        Objects.requireNonNull(other);
        return NativeCollections.filter(iterator(), other, false);
    }

    public boolean retainAll(Collection<?> other) {
        Objects.requireNonNull(other);
        return NativeCollections.filter(iterator(), other, true);
    }

    public void clear() {
        NativeCollections.clear(iterator());
    }

    public String toString() {
        return NativeCollections.render(this, iterator());
    }
}
