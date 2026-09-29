package com.github.xpenatan.jnative.classlib.java.util.zip;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.zip.*;

@SubstituteClass("java.util.zip.Checksum")
public interface Checksum {
    void update(int value);

    void update(byte[] bytes, int offset, int length);

    long getValue();

    void reset();

    default void update(byte[] bytes) {
        update(bytes, 0, bytes.length);
    }
}
