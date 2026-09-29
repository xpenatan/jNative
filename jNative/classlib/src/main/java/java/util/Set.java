package java.util;

public interface Set<E> extends Collection<E> {

    static <E> Set<E> of(E... values) {
        Set<E> result = new HashSet<E>();
        NativeCollections.populateSet(result, values);
        return Collections.unmodifiableSet(result);
    }
}
