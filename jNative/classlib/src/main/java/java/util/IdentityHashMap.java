package java.util;

public class IdentityHashMap<K, V> extends HashMap<K, V> {
    public IdentityHashMap() {
        super();
    }

    public IdentityHashMap(int expectedSize) {
        super(expectedSize);
    }

    protected int hash(Object key) {
        return System.identityHashCode(key);
    }

    protected boolean keysEqual(Object first, Object second) {
        return first == second;
    }
}
