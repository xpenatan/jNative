package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.Queue")
public interface Queue<E> extends Collection<E> {
    boolean offer(E value);

    E remove();

    E poll();

    E element();

    E peek();
}
