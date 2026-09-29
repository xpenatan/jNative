package com.github.xpenatan.jnative.classlib.java.util.function;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.function.*;

@FunctionalInterface
@SubstituteClass("java.util.function.IntPredicate")
public interface IntPredicate {
    boolean test(int value);
}
