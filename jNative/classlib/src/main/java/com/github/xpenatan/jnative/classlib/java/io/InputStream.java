package com.github.xpenatan.jnative.classlib.java.io;

import java.io.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@NativeInclude("jn_io_drivers.hpp")
@SubstituteClass("java.io.InputStream")
public abstract class InputStream implements Closeable {
    @NativeImport(value = "jnative::input_read", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/io/InputStream.read()I"})
    private static native int readNative(InputStream stream, byte[] bytes, int offset, int length) throws IOException;

    @NativeImport(value = "jnative::input_skip", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/io/InputStream.read([BII)I"})
    private static native long skipNative(InputStream stream, long count) throws IOException;

    @NativeImport(value = "jnative::input_read_all", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/io/InputStream.read([B)I"})
    private static native byte[] readAllNative(InputStream stream) throws IOException;

    public abstract int read() throws IOException;

    public int read(byte[] bytes) throws IOException {
        return read(bytes, 0, bytes.length);
    }

    public int read(byte[] bytes, int offset, int length) throws IOException {
        return readNative(this, bytes, offset, length);
    }

    public long skip(long count) throws IOException {
        return skipNative(this, count);
    }

    public int available() throws IOException {
        return 0;
    }

    public byte[] readAllBytes() throws IOException {
        return readAllNative(this);
    }

    public void close() throws IOException {
    }
}
