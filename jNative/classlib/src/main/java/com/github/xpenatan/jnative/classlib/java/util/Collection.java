package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.classlib.java.lang.Iterable;
import com.github.xpenatan.jnative.classlib.java.util.function.IntFunction;
import com.github.xpenatan.jnative.classlib.java.util.stream.NativeStreams;
import com.github.xpenatan.jnative.classlib.java.util.stream.Stream;
import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.lang.reflect.Array;
import java.util.*;

@SubstituteClass("java.util.Collection")
public interface Collection<E> extends Iterable<E> {
    int size();

    boolean isEmpty();

    boolean contains(Object value);

    Iterator<E> iterator();

    Object[] toArray();

    @SuppressWarnings("unchecked")
    default <T> T[] toArray(T[] array) {
        T[] result =
                array.length >= size()
                        ? array
                        : (T[])
                        Array.newInstance(
                                array.getClass().getComponentType(), size());
        int index = NativeCollections.copy(iterator(), result);
        if(index < result.length) result[index] = null;
        return result;
    }

    default <T> T[] toArray(IntFunction<T[]> factory) {
        return toArray(factory.apply(0));
    }

    default Stream<E> stream() {
        return NativeStreams.stream(this);
    }

    boolean add(E value);

    boolean remove(Object value);

    boolean containsAll(Collection<?> other);

    boolean addAll(Collection<? extends E> other);

    boolean removeAll(Collection<?> other);

    boolean retainAll(Collection<?> other);

    void clear();
}
