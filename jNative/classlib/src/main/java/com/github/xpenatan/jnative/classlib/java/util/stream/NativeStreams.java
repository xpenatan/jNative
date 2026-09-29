package com.github.xpenatan.jnative.classlib.java.util.stream;

import com.github.xpenatan.jnative.classlib.java.util.Collection;

/** Internal factories without an original JDK class or member identity. */
public final class NativeStreams {
    private NativeStreams() {}

    public static <T> Stream<T> stream(Collection<T> source) {
        return new StreamImpl<>(source);
    }

    public static IntStream chars(CharSequence source) {
        return new IntStreamImpl(source);
    }
}
