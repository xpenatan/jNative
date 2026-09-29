package com.github.xpenatan.jnative.classlib.java.io;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.io.*;

@SubstituteClass("java.io.FilterOutputStream")
public class FilterOutputStream extends OutputStream {
    protected OutputStream out;

    public FilterOutputStream(OutputStream output) {
        out = output;
    }

    public void write(int value) throws IOException {
        out.write(value);
    }

    public void write(byte[] bytes, int offset, int length) throws IOException {
        out.write(bytes, offset, length);
    }

    public void flush() throws IOException {
        out.flush();
    }

    public void close() throws IOException {
        try {
            flush();
        } finally {
            out.close();
        }
    }
}
