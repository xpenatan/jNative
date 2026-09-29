package java.util;

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
                        java.lang.reflect.Array.newInstance(
                                array.getClass().getComponentType(), size());
        int index = NativeCollections.copy(iterator(), result);
        if(index < result.length) result[index] = null;
        return result;
    }

    default <T> T[] toArray(java.util.function.IntFunction<T[]> factory) {
        return toArray(factory.apply(0));
    }

    default java.util.stream.Stream<E> stream() {
        return new java.util.stream.Stream<E>(this);
    }

    boolean add(E value);

    boolean remove(Object value);

    boolean containsAll(Collection<?> other);

    boolean addAll(Collection<? extends E> other);

    boolean removeAll(Collection<?> other);

    boolean retainAll(Collection<?> other);

    void clear();
}
