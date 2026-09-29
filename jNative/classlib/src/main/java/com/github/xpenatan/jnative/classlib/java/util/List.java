package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.List")
public interface List<E> extends Collection<E> {
    default void sort(Comparator<? super E> comparator) {
        E[] values = (E[])toArray();
        Arrays.sort(values, comparator);
        NativeCollections.writeback(this, values);
    }

    static <E> List<E> copyOf(Collection<? extends E> values) {
        ArrayList<E> copy = new ArrayList<E>();
        NativeCollections.populate(copy, values.iterator());
        return Collections.unmodifiableList(copy);
    }

    static <E> List<E> of() {
        return of((E[])new Object[]{});
    }

    static <E> List<E> of(E e0) {
        return of((E[])new Object[]{e0});
    }

    static <E> List<E> of(E e0, E e1) {
        return of((E[])new Object[]{e0, e1});
    }

    static <E> List<E> of(E e0, E e1, E e2) {
        return of((E[])new Object[]{e0, e1, e2});
    }

    static <E> List<E> of(E e0, E e1, E e2, E e3) {
        return of((E[])new Object[]{e0, e1, e2, e3});
    }

    static <E> List<E> of(E e0, E e1, E e2, E e3, E e4) {
        return of((E[])new Object[]{e0, e1, e2, e3, e4});
    }

    static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5) {
        return of((E[])new Object[]{e0, e1, e2, e3, e4, e5});
    }

    static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6) {
        return of((E[])new Object[]{e0, e1, e2, e3, e4, e5, e6});
    }

    static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
        return of((E[])new Object[]{e0, e1, e2, e3, e4, e5, e6, e7});
    }

    static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
        return of((E[])new Object[]{e0, e1, e2, e3, e4, e5, e6, e7, e8});
    }

    static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
        return of((E[])new Object[]{e0, e1, e2, e3, e4, e5, e6, e7, e8, e9});
    }

    static <E> List<E> of(E... values) {
        return copyOf(Arrays.asList(values));
    }

    E get(int index);

    E set(int index, E value);

    void add(int index, E value);

    E remove(int index);

    int indexOf(Object value);

    int lastIndexOf(Object value);
}
