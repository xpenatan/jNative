package java.util;

/**
 * Insertion ordered hash map. HashMap's shared node chain provides stable iteration.
 */
public class LinkedHashMap<K, V> extends HashMap<K, V> {
    public LinkedHashMap() {
        super();
    }

    public LinkedHashMap(int capacity) {
        super(capacity);
    }

    public LinkedHashMap(int capacity, float loadFactor) {
        super(capacity, loadFactor);
    }

    public LinkedHashMap(Map<? extends K, ? extends V> values) {
        super(values);
    }
}
