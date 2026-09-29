package com.github.xpenatan.jnative.classlib.java.io;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.io.*;

@SubstituteClass("java.io.FileOutputStream")
public class FileOutputStream extends OutputStream {
    private final Object handle;

    public FileOutputStream(String path) throws FileNotFoundException {
        this(path, false);
    }

    public FileOutputStream(String path, boolean append) throws FileNotFoundException {
        try {
            handle = NativeFiles.open(path, true, append);
        } catch(IOException failure) {
            throw new FileNotFoundException(failure.getMessage());
        }
    }

    public FileOutputStream(File file) throws FileNotFoundException {
        this(file.getPath(), false);
    }

    public FileOutputStream(File file, boolean append) throws FileNotFoundException {
        this(file.getPath(), append);
    }

    public void write(int value) throws IOException {
        NativeFiles.writeByte(handle, value);
    }

    public void write(byte[] bytes, int offset, int length) throws IOException {
        NativeFiles.write(handle, bytes, offset, length);
    }

    public void flush() throws IOException {
        NativeFiles.flush(handle);
    }

    public void close() throws IOException {
        NativeFiles.close(handle);
    }
}
