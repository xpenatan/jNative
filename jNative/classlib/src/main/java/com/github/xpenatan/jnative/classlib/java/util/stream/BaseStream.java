package com.github.xpenatan.jnative.classlib.java.util.stream;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

/** The supported common stream interface; concrete state belongs to implementation companions. */
@SubstituteClass("java.util.stream.BaseStream")
public interface BaseStream<T, S extends BaseStream<T, S>> extends AutoCloseable {
    void close();
}
