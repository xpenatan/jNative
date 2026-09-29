package com.github.xpenatan.jnative.classlib.java.util.function;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.function.*;

@FunctionalInterface
@SubstituteClass("java.util.function.BiPredicate")
public interface BiPredicate<T, U> {
    boolean test(T first, U second);
}
