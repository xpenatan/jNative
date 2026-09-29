package java.util.stream;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

@NativeInclude("jn_classlib_drivers.hpp")
public final class Stream<T> implements AutoCloseable {
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
            fields = {"java/util/stream/Stream$Filtered.value:Ljava/lang/Object;"})
    private static native boolean filterNative(Iterator<?> source, Predicate<?> predicate, Filtered<?> output);
    private final Iterator<T> iterator;
    private boolean consumed;

    public Stream(Collection<T> source) {
        this(source.iterator());
    }

    private Stream(Iterator<T> iterator) {
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
        return new Stream<T>(new Filtered<T>(source, predicate));
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
        return new LongStream(() -> source.hasNext(), () -> mapper.applyAsLong(source.next()));
    }

    public void close() {
        consumed = true;
    }
}
