package com.github.xpenatan.jnative.classlib.java.util.zip;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.zip.*;

@SubstituteClass("java.util.zip.Deflater")
public class Deflater {
    public static final int DEFAULT_COMPRESSION = -1,
            NO_COMPRESSION = 0,
            BEST_SPEED = 1,
            BEST_COMPRESSION = 9;
    private final Object state;
    private boolean finish;

    public Deflater() {
        this(DEFAULT_COMPRESSION, false);
    }

    public Deflater(int level) {
        this(level, false);
    }

    public Deflater(int level, boolean nowrap) {
        if(level < -1 || level > 9)
            throw new IllegalArgumentException("Invalid compression level");
        state = NativeZlib.open(true, level, nowrap);
    }

    public synchronized void setInput(byte[] bytes) {
        setInput(bytes, 0, bytes.length);
    }

    public synchronized void setInput(byte[] bytes, int offset, int length) {
        NativeZlib.input(state, bytes, offset, length);
    }

    public synchronized void finish() {
        finish = true;
    }

    public synchronized boolean finished() {
        return NativeZlib.status(state, 1) != 0;
    }

    public synchronized boolean needsInput() {
        return NativeZlib.status(state, 0) == 0;
    }

    public synchronized int deflate(byte[] bytes) {
        return deflate(bytes, 0, bytes.length);
    }

    public synchronized int deflate(byte[] bytes, int offset, int length) {
        return NativeZlib.process(state, bytes, offset, length, finish);
    }

    public synchronized void end() {
        NativeZlib.close(state);
    }
}
