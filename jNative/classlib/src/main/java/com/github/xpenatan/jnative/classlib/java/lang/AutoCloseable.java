package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.lang.AutoCloseable")
public interface AutoCloseable {
    void close() throws Exception;
}
