package com.github.xpenatan.jnative.classlib.java.io;

import java.io.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@NativeInclude("jn_io_drivers.hpp")
@SubstituteClass("java.io.OutputStream")
public abstract class OutputStream implements Closeable, Flushable {
    @NativeImport(value = "jnative::output_write", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/io/OutputStream.write(I)V"})
    private static native void writeNative(OutputStream stream, byte[] bytes, int offset, int length) throws IOException;

    public abstract void write(int value) throws IOException;

    public void write(byte[] bytes) throws IOException {
        write(bytes, 0, bytes.length);
    }

    public void write(byte[] bytes, int offset, int length) throws IOException {
        writeNative(this, bytes, offset, length);
    }

    public void flush() throws IOException {
    }

    public void close() throws IOException {
    }
}
