package com.github.xpenatan.jnative.classlib.java.util.function;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.function.*;

@FunctionalInterface
@SubstituteClass("java.util.function.UnaryOperator")
public interface UnaryOperator<T> extends Function<T, T> {
}
