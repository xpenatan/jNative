package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.Deque")
public interface Deque<E> extends Queue<E> {
    void addFirst(E value);

    void addLast(E value);

    boolean offerFirst(E value);

    boolean offerLast(E value);

    E removeFirst();

    E removeLast();

    E pollFirst();

    E pollLast();

    E getFirst();

    E getLast();

    E peekFirst();

    E peekLast();

    void push(E value);

    E pop();
}
