package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.classlib.java.util.function.Function;
import com.github.xpenatan.jnative.classlib.java.util.function.ToIntFunction;
import com.github.xpenatan.jnative.classlib.java.util.function.ToLongFunction;
import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.util.*;

@FunctionalInterface
@SubstituteClass("java.util.Comparator")
public interface Comparator<T> {
    int compare(T first, T second);

    default Comparator<T> reversed() {
        return (first, second) -> compare(second, first);
    }

    default Comparator<T> thenComparing(Comparator<? super T> next) {
        Objects.requireNonNull(next);
        return (first, second) -> {
            int result = compare(first, second);
            return result != 0 ? result : next.compare(first, second);
        };
    }

    default <U extends Comparable<? super U>> Comparator<T> thenComparing(
            Function<? super T, ? extends U> key) {
        return thenComparing(comparing(key));
    }

    static <T, U extends Comparable<? super U>> Comparator<T> comparing(
            Function<? super T, ? extends U> key) {
        Objects.requireNonNull(key);
        return (first, second) -> key.apply(first).compareTo(key.apply(second));
    }

    static <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
        return (first, second) -> first.compareTo(second);
    }

    static <T> Comparator<T> comparingInt(ToIntFunction<? super T> key) {
        Objects.requireNonNull(key);
        return (first, second) -> Integer.compare(key.applyAsInt(first), key.applyAsInt(second));
    }

    default Comparator<T> thenComparingInt(ToIntFunction<? super T> key) {
        return thenComparing(comparingInt(key));
    }

    static <T> Comparator<T> comparingLong(ToLongFunction<? super T> key) {
        Objects.requireNonNull(key);
        return (first, second) -> Long.compare(key.applyAsLong(first), key.applyAsLong(second));
    }

    default Comparator<T> thenComparingLong(ToLongFunction<? super T> key) {
        return thenComparing(comparingLong(key));
    }
}
