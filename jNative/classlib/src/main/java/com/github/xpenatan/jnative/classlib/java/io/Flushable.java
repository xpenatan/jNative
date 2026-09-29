package com.github.xpenatan.jnative.classlib.java.io;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.io.*;

@SubstituteClass("java.io.Flushable")
public interface Flushable {
    void flush() throws IOException;
}
