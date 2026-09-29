package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.AbstractMap")
public abstract class AbstractMap<K, V> implements Map<K, V> {

    public Set<K> keySet() {
        return new AbstractSet<K>() {
            public int size() {
                return AbstractMap.this.size();
            }

            public boolean contains(Object key) {
                return containsKey(key);
            }

            public Iterator<K> iterator() {
                Iterator<Entry<K, V>> source = entrySet().iterator();
                return new Iterator<K>() {
                    public boolean hasNext() {
                        return source.hasNext();
                    }

                    public K next() {
                        return source.next().getKey();
                    }

                    public void remove() {
                        source.remove();
                    }
                };
            }
        };
    }

    public Collection<V> values() {
        return new AbstractCollection<V>() {
            public int size() {
                return AbstractMap.this.size();
            }

            public Iterator<V> iterator() {
                Iterator<Entry<K, V>> source = entrySet().iterator();
                return new Iterator<V>() {
                    public boolean hasNext() {
                        return source.hasNext();
                    }

                    public V next() {
                        return source.next().getValue();
                    }

                    public void remove() {
                        source.remove();
                    }
                };
            }
        };
    }

    @SubstituteClass("java.util.AbstractMap$SimpleEntry")
    public static class SimpleEntry<K, V> implements Entry<K, V> {
        private final K key;
        private V value;

        public SimpleEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V value) {
            V previous = this.value;
            this.value = value;
            return previous;
        }

        public boolean equals(Object value) {
            if(!(value instanceof Entry)) return false;
            Entry<?, ?> other = (Entry<?, ?>)value;
            return Objects.equals(key, other.getKey())
                    && Objects.equals(this.value, other.getValue());
        }

        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public String toString() {
            return key + "=" + value;
        }
    }

    @SubstituteClass("java.util.AbstractMap$SimpleImmutableEntry")
    public static final class SimpleImmutableEntry<K, V> extends SimpleEntry<K, V> {
        public SimpleImmutableEntry(K key, V value) {
            super(key, value);
        }

        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
    }

    protected AbstractMap() {
    }

    public abstract Set<Entry<K, V>> entrySet();

    public int size() {
        return entrySet().size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsKey(Object key) {
        return NativeCollections.mapScan(entrySet().iterator(), key, 0) != null;
    }

    public boolean containsValue(Object value) {
        return NativeCollections.mapScan(entrySet().iterator(), value, 1) != null;
    }

    public V get(Object key) {
        return (V)NativeCollections.mapScan(entrySet().iterator(), key, 2);
    }

    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    public V remove(Object key) {
        return (V)NativeCollections.mapScan(entrySet().iterator(), key, 3);
    }

    public void putAll(Map<? extends K, ? extends V> source) {
        NativeCollections.putAll(this, source.entrySet().iterator());
    }

    public void clear() {
        entrySet().clear();
    }

    public boolean equals(Object other) {
        if(this == other) return true;
        if(!(other instanceof Map)) return false;
        Map<?, ?> map = (Map<?, ?>)other;
        if(map.size() != size()) return false;
        return NativeCollections.mapEqual(entrySet().iterator(), map);
    }

    public int hashCode() {
        return NativeCollections.hash(entrySet().iterator(), 1, 0);
    }

    public String toString() {
        return NativeCollections.mapRender(this, entrySet().iterator());
    }
}
