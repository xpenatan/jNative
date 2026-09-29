package com.github.xpenatan.jnative.classlib.java.util.function;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.function.*;

@SubstituteClass("java.util.function.ToIntFunction")
public interface ToIntFunction<T> {
    int applyAsInt(T value);
}
