package com.github.xpenatan.jnative.classlib.java.util.stream;

import java.util.stream.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

import com.github.xpenatan.jnative.classlib.java.util.function.BooleanSupplier;
import com.github.xpenatan.jnative.classlib.java.util.function.LongSupplier;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.util.stream.LongStream")
public interface LongStream extends BaseStream<Long, LongStream> {
    long sum();
}

@NativeInclude("jn_classlib_drivers.hpp")
final class LongStreamImpl implements LongStream {
    @NativeImport(value = "jnative::stream_sum", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/function/BooleanSupplier.getAsBoolean()Z",
                    "java/util/function/LongSupplier.getAsLong()J"},
            callbackReceivers = {0, 1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    private static native long sumNative(BooleanSupplier hasNext, LongSupplier next);
    private final BooleanSupplier hasNext;
    private final LongSupplier next;
    private boolean consumed;

    LongStreamImpl(BooleanSupplier hasNext, LongSupplier next) {
        this.hasNext = hasNext;
        this.next = next;
    }

    public long sum() {
        if(consumed) throw new IllegalStateException("Stream already operated upon or closed");
        consumed = true;
        return sumNative(hasNext, next);
    }

    public void close() {
        consumed = true;
    }
}
