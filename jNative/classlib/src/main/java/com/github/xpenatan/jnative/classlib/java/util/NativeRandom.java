package com.github.xpenatan.jnative.classlib.java.util;

import java.util.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/** Annotated C++ bindings for the classic uniform Random sequence. */
@NativeInclude("jn_random.hpp")
final class NativeRandom {
    private NativeRandom() {
    }

    @NativeImport(value = "jnative::random_scramble", managed = true, runtimeOnly = true)
    static native long scramble(long seed);
    @NativeImport(value = "jnative::random_advance", managed = true, runtimeOnly = true)
    static native long advance(long seed);
    @NativeImport(value = "jnative::random_bits", managed = true, runtimeOnly = true)
    static native int bits(long seed, int bits);
    @NativeImport(value = "jnative::random_uniqueSeed", managed = true, runtimeOnly = true)
    static native long uniqueSeed(long previous);
    @NativeImport(value = "jnative::random_nextBytes", managed = true, runtimeOnly = true, callbacksSynchronous = true, callbacks = {"java/util/Random.nextInt()I"})
    static native void nextBytes(Random random, byte[] bytes);
    @NativeImport(value = "jnative::random_nextInt", managed = true, runtimeOnly = true, callbacksSynchronous = true, callbacks = {"java/util/Random.next(I)I"})
    static native int nextInt(Random random);
    @NativeImport(value = "jnative::random_nextInt", managed = true, runtimeOnly = true, callbacksSynchronous = true, callbacks = {"java/util/Random.next(I)I"})
    static native int nextInt(Random random, int bound);
    @NativeImport(value = "jnative::random_nextLong", managed = true, runtimeOnly = true, callbacksSynchronous = true, callbacks = {"java/util/Random.next(I)I"})
    static native long nextLong(Random random);
    @NativeImport(value = "jnative::random_nextBoolean", managed = true, runtimeOnly = true, callbacksSynchronous = true, callbacks = {"java/util/Random.next(I)I"})
    static native boolean nextBoolean(Random random);
    @NativeImport(value = "jnative::random_nextFloat", managed = true, runtimeOnly = true, callbacksSynchronous = true, callbacks = {"java/util/Random.next(I)I"})
    static native float nextFloat(Random random);
    @NativeImport(value = "jnative::random_nextDouble", managed = true, runtimeOnly = true, callbacksSynchronous = true, callbacks = {"java/util/Random.next(I)I"})
    static native double nextDouble(Random random);
}
