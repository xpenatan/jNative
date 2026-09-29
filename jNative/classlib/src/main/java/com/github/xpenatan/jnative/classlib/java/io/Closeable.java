package com.github.xpenatan.jnative.classlib.java.io;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.io.*;

@SubstituteClass("java.io.Closeable")
public interface Closeable extends AutoCloseable {
    void close() throws IOException;
}
