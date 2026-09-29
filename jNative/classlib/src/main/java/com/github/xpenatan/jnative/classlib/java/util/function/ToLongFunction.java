package com.github.xpenatan.jnative.classlib.java.util.function;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.function.*;

@FunctionalInterface
@SubstituteClass("java.util.function.ToLongFunction")
public interface ToLongFunction<T> {
    long applyAsLong(T value);
}
