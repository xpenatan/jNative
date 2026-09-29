package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.classlib.java.util.function.Function;
import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.util.*;

@SubstituteClass("java.util.Collections")
public final class Collections {
    private Collections() {
    }

    public static <T> List<T> unmodifiableList(List<? extends T> values) {
        return new ReadOnlyList<T>(Objects.requireNonNull(values));
    }

    public static <T> Set<T> unmodifiableSet(Set<? extends T> values) {
        return new ReadOnlySet<T>(Objects.requireNonNull(values));
    }

    public static <K, V> Map<K, V> unmodifiableMap(Map<? extends K, ? extends V> values) {
        return new ReadOnlyMap<K, V>(Objects.requireNonNull(values));
    }

    private static <T> Iterator<T> readOnly(Iterator<? extends T> iterator) {
        return new Iterator<T>() {
            public boolean hasNext() {
                return iterator.hasNext();
            }

            public T next() {
                return iterator.next();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static final class ReadOnlyList<T> extends AbstractList<T> {
        private final List<? extends T> values;

        ReadOnlyList(List<? extends T> values) {
            this.values = values;
        }

        public int size() {
            return values.size();
        }

        public T get(int index) {
            return values.get(index);
        }

        public Iterator<T> iterator() {
            return readOnly(values.iterator());
        }

        public T set(int index, T value) {
            throw new UnsupportedOperationException();
        }

        public void add(int index, T value) {
            throw new UnsupportedOperationException();
        }

        public boolean add(T value) {
            throw new UnsupportedOperationException();
        }

        public T remove(int index) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object value) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection<? extends T> source) {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> source) {
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> source) {
            throw new UnsupportedOperationException();
        }

        public void sort(Comparator<? super T> comparator) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ReadOnlySet<T> extends AbstractSet<T> {
        private final Set<? extends T> values;

        ReadOnlySet(Set<? extends T> values) {
            this.values = values;
        }

        public int size() {
            return values.size();
        }

        public boolean contains(Object value) {
            return values.contains(value);
        }

        public Iterator<T> iterator() {
            return readOnly(values.iterator());
        }

        public boolean add(T value) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object value) {
            throw new UnsupportedOperationException();
        }

        public boolean addAll(Collection<? extends T> source) {
            throw new UnsupportedOperationException();
        }

        public boolean removeAll(Collection<?> source) {
            throw new UnsupportedOperationException();
        }

        public boolean retainAll(Collection<?> source) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ReadOnlyMap<K, V> extends AbstractMap<K, V> {
        private final Map<? extends K, ? extends V> values;

        ReadOnlyMap(Map<? extends K, ? extends V> values) {
            this.values = values;
        }

        public int size() {
            return values.size();
        }

        public V get(Object key) {
            return values.get(key);
        }

        public boolean containsKey(Object key) {
            return values.containsKey(key);
        }

        public V put(K key, V value) {
            throw new UnsupportedOperationException();
        }

        public V remove(Object key) {
            throw new UnsupportedOperationException();
        }

        public boolean remove(Object key, Object value) {
            throw new UnsupportedOperationException();
        }

        public V putIfAbsent(K key, V value) {
            throw new UnsupportedOperationException();
        }

        public void putAll(Map<? extends K, ? extends V> source) {
            throw new UnsupportedOperationException();
        }

        public V computeIfAbsent(
                K key, Function<? super K, ? extends V> factory) {
            throw new UnsupportedOperationException();
        }

        public void clear() {
            throw new UnsupportedOperationException();
        }

        public Set<K> keySet() {
            return Collections.unmodifiableSet(super.keySet());
        }

        public Collection<V> values() {
            return Collections.unmodifiableList(new ValueList());
        }

        private final class ValueList extends AbstractList<V> {
            public int size() {
                return values.size();
            }

            public V get(int index) {
                if(index < 0 || index >= size()) throw new IndexOutOfBoundsException();
                Iterator<? extends V> iterator = values.values().iterator();
                return (V)NativeCollections.nth(iterator, index);
            }
        }

        public Set<Entry<K, V>> entrySet() {
            return Collections.unmodifiableSet(
                    new AbstractSet<Entry<K, V>>() {
                        public int size() {
                            return values.size();
                        }

                        public Iterator<Entry<K, V>> iterator() {
                            Iterator<? extends Entry<? extends K, ? extends V>> source =
                                    values.entrySet().iterator();
                            return new Iterator<Entry<K, V>>() {
                                public boolean hasNext() {
                                    return source.hasNext();
                                }

                                public Entry<K, V> next() {
                                    Entry<? extends K, ? extends V> entry = source.next();
                                    return new SimpleImmutableEntry<K, V>(
                                            entry.getKey(), entry.getValue());
                                }
                            };
                        }
                    });
        }
    }
}
