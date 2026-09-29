package com.github.xpenatan.jnative.classlib.java.util.function;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.function.*;

@SubstituteClass("java.util.function.IntBinaryOperator")
public interface IntBinaryOperator {
    int applyAsInt(int first, int second);
}
