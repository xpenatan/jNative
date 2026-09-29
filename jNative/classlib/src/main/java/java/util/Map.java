package java.util;

public interface Map<K, V> {

    default V computeIfAbsent(K key, java.util.function.Function<? super K, ? extends V> factory) {
        Objects.requireNonNull(factory);
        V value = get(key);
        if(value == null) {
            value = factory.apply(key);
            if(value != null) put(key, value);
        }
        return value;
    }

    static <K, V> Map<K, V> of() {
        return Collections.unmodifiableMap(new HashMap<K, V>());
    }

    static <K, V> Map<K, V> of(K k0, V v0) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(K k0, V v0, K k1, V v1) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(K k0, V v0, K k1, V v1, K k2, V v2) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1, k2, v2});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(K k0, V v0, K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1, k2, v2, k3, v3});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(K k0, V v0, K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1, k2, v2, k3, v3, k4, v4});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(
            K k0, V v0, K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(
            K k0, V v0, K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(
            K k0,
            V v0,
            K k1,
            V v1,
            K k2,
            V v2,
            K k3,
            V v3,
            K k4,
            V v4,
            K k5,
            V v5,
            K k6,
            V v6,
            K k7,
            V v7) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(
            K k0,
            V v0,
            K k1,
            V v1,
            K k2,
            V v2,
            K k3,
            V v3,
            K k4,
            V v4,
            K k5,
            V v5,
            K k6,
            V v6,
            K k7,
            V v7,
            K k8,
            V v8) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8});
        return Collections.unmodifiableMap(map);
    }

    static <K, V> Map<K, V> of(
            K k0,
            V v0,
            K k1,
            V v1,
            K k2,
            V v2,
            K k3,
            V v3,
            K k4,
            V v4,
            K k5,
            V v5,
            K k6,
            V v6,
            K k7,
            V v7,
            K k8,
            V v8,
            K k9,
            V v9) {
        Map<K, V> map = new HashMap<K, V>();
        NativeCollections.populateMap(map, new Object[]{k0, v0, k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6, k7, v7, k8, v8, k9, v9});
        return Collections.unmodifiableMap(map);
    }

    int size();

    boolean isEmpty();

    boolean containsKey(Object key);

    boolean containsValue(Object value);

    V get(Object key);

    V put(K key, V value);

    V remove(Object key);

    void clear();

    void putAll(Map<? extends K, ? extends V> values);

    Set<K> keySet();

    Collection<V> values();

    Set<Entry<K, V>> entrySet();

    default V getOrDefault(Object key, V fallback) {
        V value = get(key);
        return value != null || containsKey(key) ? value : fallback;
    }

    default V putIfAbsent(K key, V value) {
        V previous = get(key);
        if(previous == null) previous = put(key, value);
        return previous;
    }

    default boolean remove(Object key, Object value) {
        V current = get(key);
        if(!Objects.equals(current, value) || (current == null && !containsKey(key))) return false;
        remove(key);
        return true;
    }

    interface Entry<K, V> {
        K getKey();

        V getValue();

        V setValue(V value);
    }
}
