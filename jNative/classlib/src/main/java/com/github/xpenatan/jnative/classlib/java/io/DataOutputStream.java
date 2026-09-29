package com.github.xpenatan.jnative.classlib.java.io;

import java.io.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@NativeInclude("jn_io_drivers.hpp")
@SubstituteClass("java.io.DataOutputStream")
public class DataOutputStream extends FilterOutputStream {
    @NativeImport(value = "jnative::data_write_short", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/io/OutputStream.write(I)V"})
    private static native void shortNative(OutputStream stream, int value) throws IOException;

    @NativeImport(value = "jnative::data_write_int", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/io/OutputStream.write(I)V"})
    private static native void intNative(OutputStream stream, int value) throws IOException;

    @NativeImport(value = "jnative::data_write_long", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/io/DataOutputStream.writeInt(I)V"})
    private static native void longNative(DataOutputStream stream, long value) throws IOException;

    public DataOutputStream(OutputStream output) {
        super(output);
    }

    public void writeByte(int value) throws IOException {
        out.write(value);
    }

    public void writeShort(int value) throws IOException {
        shortNative(out, value);
    }

    public void writeInt(int value) throws IOException {
        intNative(out, value);
    }

    public void writeLong(long value) throws IOException {
        longNative(this, value);
    }
}
