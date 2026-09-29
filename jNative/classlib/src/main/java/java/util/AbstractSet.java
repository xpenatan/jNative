package java.util;

public abstract class AbstractSet<E> extends AbstractCollection<E> implements Set<E> {
    protected AbstractSet() {
    }

    public boolean equals(Object other) {
        return this == other
                || (other instanceof Set
                && ((Set<?>)other).size() == size()
                && containsAll((Set<?>)other));
    }

    public int hashCode() {
        return NativeCollections.hash(iterator(), 1, 0);
    }
}
