package java.util.concurrent;

import java.util.*;

public class ConcurrentHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    private final HashMap<K, V> values = new HashMap<K, V>();

    public ConcurrentHashMap() {
    }

    public synchronized int size() {
        return values.size();
    }

    public synchronized V get(Object key) {
        return values.get(Objects.requireNonNull(key));
    }

    public synchronized boolean containsKey(Object key) {
        return values.containsKey(Objects.requireNonNull(key));
    }

    public synchronized V put(K key, V value) {
        return values.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
    }

    public synchronized V putIfAbsent(K key, V value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return values.putIfAbsent(key, value);
    }

    public synchronized V remove(Object key) {
        return values.remove(Objects.requireNonNull(key));
    }

    public synchronized boolean remove(Object key, Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(value);
        return values.remove(key, value);
    }

    public synchronized V computeIfAbsent(
            K key, java.util.function.Function<? super K, ? extends V> factory) {
        Objects.requireNonNull(key);
        return values.computeIfAbsent(key, factory);
    }

    public synchronized void clear() {
        values.clear();
    }

    private synchronized Object[] snapshot() {
        Object[] entries = new Object[values.size() * 2];
        NativeCollections.snapshot(values.entrySet().iterator(), entries);
        return entries;
    }

    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Entry<K, V>>() {
            public int size() {
                return ConcurrentHashMap.this.size();
            }

            public void clear() {
                ConcurrentHashMap.this.clear();
            }

            public Iterator<Entry<K, V>> iterator() {
                Object[] entries = snapshot();
                return new Iterator<Entry<K, V>>() {
                    int index;
                    K last;
                    boolean removable;

                    public boolean hasNext() {
                        return index < entries.length;
                    }

                    public Entry<K, V> next() {
                        if(!hasNext()) throw new NoSuchElementException();
                        K key = (K)entries[index++];
                        V value = (V)entries[index++];
                        last = key;
                        removable = true;
                        return new SimpleEntry<K, V>(key, value) {
                            public V setValue(V value) {
                                ConcurrentHashMap.this.put(key, value);
                                return super.setValue(value);
                            }
                        };
                    }

                    public void remove() {
                        if(!removable) throw new IllegalStateException();
                        ConcurrentHashMap.this.remove(last);
                        removable = false;
                    }
                };
            }
        };
    }
}
