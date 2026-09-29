package java.util;

@FunctionalInterface
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
            java.util.function.Function<? super T, ? extends U> key) {
        return thenComparing(comparing(key));
    }

    static <T, U extends Comparable<? super U>> Comparator<T> comparing(
            java.util.function.Function<? super T, ? extends U> key) {
        Objects.requireNonNull(key);
        return (first, second) -> key.apply(first).compareTo(key.apply(second));
    }

    static <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
        return (first, second) -> first.compareTo(second);
    }

    static <T> Comparator<T> comparingInt(java.util.function.ToIntFunction<? super T> key) {
        Objects.requireNonNull(key);
        return (first, second) -> Integer.compare(key.applyAsInt(first), key.applyAsInt(second));
    }

    default Comparator<T> thenComparingInt(java.util.function.ToIntFunction<? super T> key) {
        return thenComparing(comparingInt(key));
    }

    static <T> Comparator<T> comparingLong(java.util.function.ToLongFunction<? super T> key) {
        Objects.requireNonNull(key);
        return (first, second) -> Long.compare(key.applyAsLong(first), key.applyAsLong(second));
    }

    default Comparator<T> thenComparingLong(java.util.function.ToLongFunction<? super T> key) {
        return thenComparing(comparingLong(key));
    }
}
