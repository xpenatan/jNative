package com.github.xpenatan.jnative.classlib.java.util.stream;

import java.util.stream.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

import com.github.xpenatan.jnative.classlib.java.util.Collection;
import com.github.xpenatan.jnative.classlib.java.util.Iterator;
import java.util.NoSuchElementException;
import com.github.xpenatan.jnative.classlib.java.util.Objects;
import com.github.xpenatan.jnative.classlib.java.util.function.Predicate;
import com.github.xpenatan.jnative.classlib.java.util.function.ToLongFunction;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.util.stream.Stream")
public interface Stream<T> extends BaseStream<T, Stream<T>> {
    Stream<T> filter(Predicate<? super T> predicate);
    long count();
    LongStream mapToLong(ToLongFunction<? super T> mapper);
}

@NativeInclude("jn_classlib_drivers.hpp")
final class StreamImpl<T> implements Stream<T> {
    @NativeImport(value = "jnative::stream_count", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;"},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    private static native long countNative(Iterator<?> source);

    @NativeImport(value = "jnative::stream_filter", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;",
                    "java/util/function/Predicate.test(Ljava/lang/Object;)Z"},
            callbackReceivers = {0, 0, 1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE,
                    NativeImport.Invocation.INTERFACE},
            fields = {"com/github/xpenatan/jnative/classlib/java/util/stream/StreamImpl$Filtered.value:Ljava/lang/Object;"})
    private static native boolean filterNative(Iterator<?> source, Predicate<?> predicate, Filtered<?> output);
    private final Iterator<T> iterator;
    private boolean consumed;

    StreamImpl(Collection<T> source) {
        this(source.iterator());
    }

    private StreamImpl(Iterator<T> iterator) {
        this.iterator = iterator;
    }

    private Iterator<T> take() {
        if(consumed) throw new IllegalStateException("Stream already operated upon or closed");
        consumed = true;
        return iterator;
    }

    public Stream<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate);
        Iterator<T> source = take();
        return new StreamImpl<T>(new Filtered<T>(source, predicate));
    }

    private static final class Filtered<T> implements Iterator<T> {
        private final Iterator<T> source;
        private final Predicate<? super T> predicate;
        private boolean ready, ended;
        private T value;

        private Filtered(Iterator<T> source, Predicate<? super T> predicate) {
            this.source = source;
            this.predicate = predicate;
        }

        public boolean hasNext() {
            if(!ready && !ended) {
                if(filterNative(source, predicate, this)) {
                    ready = true;
                    return true;
                }
                ended = true;
            }
            return ready;
        }

        public T next() {
            if(!hasNext()) throw new NoSuchElementException();
            ready = false;
            T result = value;
            value = null;
            return result;
        }
    }

    public long count() {
        return countNative(take());
    }

    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        Objects.requireNonNull(mapper);
        Iterator<T> source = take();
        return new LongStreamImpl(() -> source.hasNext(), () -> mapper.applyAsLong(source.next()));
    }

    public void close() {
        consumed = true;
    }
}
