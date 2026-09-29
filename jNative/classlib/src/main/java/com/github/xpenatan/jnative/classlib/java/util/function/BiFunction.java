package com.github.xpenatan.jnative.classlib.java.util.function;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.function.*;

@SubstituteClass("java.util.function.BiFunction")
public interface BiFunction<T, U, R> {
    R apply(T first, U second);
}
