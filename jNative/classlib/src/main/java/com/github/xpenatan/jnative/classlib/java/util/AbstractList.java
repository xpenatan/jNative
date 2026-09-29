package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.AbstractList")
public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {
    protected int modCount;

    protected AbstractList() {
    }

    public abstract E get(int index);

    public E set(int index, E value) {
        throw new UnsupportedOperationException();
    }

    public void add(int index, E value) {
        throw new UnsupportedOperationException();
    }

    public E remove(int index) {
        throw new UnsupportedOperationException();
    }

    public boolean add(E value) {
        add(size(), value);
        return true;
    }

    public int indexOf(Object value) {
        return NativeCollections.index(this, value, false);
    }

    public int lastIndexOf(Object value) {
        return NativeCollections.index(this, value, true);
    }

    public Iterator<E> iterator() {
        return new Cursor();
    }

    private class Cursor implements Iterator<E> {
        int next, last = -1, expected = modCount;

        private void check() {
            if(expected != modCount) throw new ConcurrentModificationException();
        }

        public boolean hasNext() {
            return next < size();
        }

        public E next() {
            check();
            if(!hasNext()) throw new NoSuchElementException();
            last = next++;
            return get(last);
        }

        public void remove() {
            check();
            if(last < 0) throw new IllegalStateException();
            AbstractList.this.remove(last);
            next = last;
            last = -1;
            expected = modCount;
        }
    }

    public boolean equals(Object other) {
        if(this == other) return true;
        if(!(other instanceof List)) return false;
        List<?> values = (List<?>)other;
        if(values.size() != size()) return false;
        Iterator<?> it = values.iterator();
        return NativeCollections.equal(iterator(), it);
    }

    public int hashCode() {
        return NativeCollections.hash(iterator(), 31, 1);
    }
}
