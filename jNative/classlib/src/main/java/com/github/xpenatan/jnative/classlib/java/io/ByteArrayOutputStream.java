package com.github.xpenatan.jnative.classlib.java.io;

import java.io.*;

import com.github.xpenatan.jnative.classlib.java.util.Arrays;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.io.ByteArrayOutputStream")
public class ByteArrayOutputStream extends OutputStream {
    protected byte[] buf;
    protected int count;

    public ByteArrayOutputStream() {
        this(32);
    }

    public ByteArrayOutputStream(int capacity) {
        if(capacity < 0) throw new IllegalArgumentException();
        buf = new byte[capacity];
    }

    private void ensure(int size) {
        if(size < 0) throw new OutOfMemoryError();
        if(size > buf.length)
            buf =
                    Arrays.copyOf(
                            buf,
                            Math.max(
                                    size,
                                    buf.length <= Integer.MAX_VALUE / 2
                                            ? buf.length * 2
                                            : Integer.MAX_VALUE));
    }

    public synchronized void write(int value) {
        ensure(count + 1);
        buf[count++] = (byte)value;
    }

    public synchronized void write(byte[] bytes, int offset, int length) {
        if(offset < 0 || length < 0 || offset > bytes.length - length)
            throw new IndexOutOfBoundsException();
        ensure(count + length);
        System.arraycopy(bytes, offset, buf, count, length);
        count += length;
    }

    public void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    public synchronized byte[] toByteArray() {
        return Arrays.copyOf(buf, count);
    }

    public synchronized int size() {
        return count;
    }

    public synchronized void reset() {
        count = 0;
    }

    public void close() {
    }
}
