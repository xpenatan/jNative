package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.io.Serializable;
import java.util.*;

/** Identity comparisons in a flat table: no entry allocation on put or get. */
@SubstituteClass("java.util.IdentityHashMap")
public class IdentityHashMap<K, V> extends AbstractMap<K, V> implements Cloneable, Serializable {
    private static final Object NULL_KEY = new Object();
    private static final Object DELETED = new Object();
    private static final int MAX_CAPACITY = 1 << 29;
    private Object[] table;
    private int count;
    private int used;
    private int changes;

    public IdentityHashMap() { this(21); }

    public IdentityHashMap(int expectedSize) {
        if(expectedSize < 0) throw new IllegalArgumentException();
        int capacity = 4;
        while(capacity < MAX_CAPACITY && expectedSize >= capacity - capacity / 3)
            capacity <<= 1;
        table = new Object[capacity * 2];
    }

    public IdentityHashMap(Map<? extends K, ? extends V> source) {
        this(source.size());
        putAll(source);
    }

    private static Object masked(Object key) { return key == null ? NULL_KEY : key; }

    private static int start(Object key, int length) {
        return (System.identityHashCode(key) << 1) & (length - 2);
    }

    private int find(Object key) {
        Object wanted = masked(key);
        Object[] slots = table;
        int index = start(wanted, slots.length);
        while(true) {
            Object candidate = slots[index];
            if(candidate == wanted) return index;
            if(candidate == null) return -1;
            index = (index + 2) & (slots.length - 2);
        }
    }

    public int size() { return count; }
    public boolean containsKey(Object key) { return find(key) >= 0; }

    public boolean containsValue(Object value) {
        Object[] slots = table;
        for(int i = 0; i < slots.length; i += 2)
            if(slots[i] != null && slots[i] != DELETED && slots[i + 1] == value) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    public V get(Object key) {
        Object wanted = masked(key);
        Object[] slots = table;
        int index = start(wanted, slots.length);
        while(true) {
            Object candidate = slots[index];
            if(candidate == wanted) return (V)slots[index + 1];
            if(candidate == null) return null;
            index = (index + 2) & (slots.length - 2);
        }
    }

    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        Object wanted = masked(key);
        while(true) {
            Object[] slots = table;
            int index = start(wanted, slots.length);
            int deleted = -1;
            while(slots[index] != null) {
                if(slots[index] == wanted) {
                    V old = (V)slots[index + 1];
                    slots[index + 1] = value;
                    return old;
                }
                if(slots[index] == DELETED && deleted < 0) deleted = index;
                index = (index + 2) & (slots.length - 2);
            }
            int capacity = slots.length / 2;
            int threshold = capacity == MAX_CAPACITY ? capacity - 1 : capacity - capacity / 3;
            // Always retain an empty slot so unsuccessful probes terminate.
            if(count >= threshold || deleted < 0 && used >= threshold) {
                if(count >= MAX_CAPACITY - 1) throw new OutOfMemoryError();
                rebuild(count < capacity / 2 || capacity == MAX_CAPACITY ? capacity : capacity * 2);
                continue;
            }
            if(deleted >= 0) index = deleted;
            else used++;
            slots[index] = wanted;
            slots[index + 1] = value;
            count++;
            changes++;
            return null;
        }
    }

    private void rebuild(int capacity) {
        Object[] grown = new Object[capacity * 2];
        Object[] source = table;
        for(int i = 0; i < source.length; i += 2) {
            Object key = source[i];
            if(key == null || key == DELETED) continue;
            int index = start(key, grown.length);
            while(grown[index] != null) index = (index + 2) & (grown.length - 2);
            grown[index] = key;
            grown[index + 1] = source[i + 1];
        }
        table = grown;
        used = count;
    }

    @SuppressWarnings("unchecked")
    private V delete(int index) {
        V old = (V)table[index + 1];
        table[index] = DELETED;
        table[index + 1] = null;
        count--;
        changes++;
        return old;
    }

    public V remove(Object key) {
        int index = find(key);
        return index < 0 ? null : delete(index);
    }

    public boolean remove(Object key, Object value) {
        int index = find(key);
        if(index < 0 || table[index + 1] != value) return false;
        delete(index);
        return true;
    }

    public void clear() {
        Arrays.fill(table, null);
        count = 0;
        used = 0;
        changes++;
    }

    public Object clone() { return new IdentityHashMap<K, V>(this); }

    public boolean equals(Object other) {
        if(other == this) return true;
        if(!(other instanceof Map)) return false;
        Map<?, ?> map = (Map<?, ?>)other;
        if(map.size() != count) return false;
        Iterator<? extends Entry<?, ?>> entries = map.entrySet().iterator();
        while(entries.hasNext()) {
            Entry<?, ?> entry = entries.next();
            int index = find(entry.getKey());
            if(index < 0 || table[index + 1] != entry.getValue()) return false;
        }
        return true;
    }

    public int hashCode() {
        int hash = 0;
        for(int i = 0; i < table.length; i += 2) {
            Object key = table[i];
            if(key != null && key != DELETED)
                hash += System.identityHashCode(key == NULL_KEY ? null : key)
                        ^ System.identityHashCode(table[i + 1]);
        }
        return hash;
    }

    private final class LiveEntry implements Entry<K, V> {
        private final K key;
        private boolean valid = true;
        LiveEntry(K key) { this.key = key; }
        private void check() { if(!valid) throw new IllegalStateException(); }
        public K getKey() { check(); return key; }
        public V getValue() { check(); return get(key); }
        public V setValue(V value) { check(); return put(key, value); }
        public boolean equals(Object other) {
            if(!(other instanceof Entry)) return false;
            Entry<?, ?> entry = (Entry<?, ?>)other;
            return getKey() == entry.getKey() && getValue() == entry.getValue();
        }
        public int hashCode() { return System.identityHashCode(getKey()) ^ System.identityHashCode(getValue()); }
        public String toString() { return getKey() + "=" + getValue(); }
    }

    private final class Cursor<T> implements Iterator<T> {
        private final Object[] slots = table;
        private final int kind;
        private int expected = changes;
        private int next;
        private int last = -1;
        private LiveEntry entry;
        Cursor(int kind) { this.kind = kind; }
        public boolean hasNext() {
            while(next < slots.length && (slots[next] == null || slots[next] == DELETED)) next += 2;
            return next < slots.length;
        }
        @SuppressWarnings("unchecked")
        public T next() {
            if(expected != changes) throw new ConcurrentModificationException();
            if(!hasNext()) throw new NoSuchElementException();
            last = next;
            next += 2;
            Object key = slots[last] == NULL_KEY ? null : slots[last];
            if(kind == 0) return (T)key;
            if(kind == 1) return (T)slots[last + 1];
            entry = new LiveEntry((K)key);
            return (T)entry;
        }
        public void remove() {
            if(expected != changes) throw new ConcurrentModificationException();
            if(last < 0) throw new IllegalStateException();
            delete(last);
            if(entry != null) entry.valid = false;
            expected = changes;
            last = -1;
        }
    }

    public Set<K> keySet() {
        return new AbstractSet<K>() {
            public int size() { return count; }
            public boolean contains(Object key) { return containsKey(key); }
            public Iterator<K> iterator() { return new Cursor<K>(0); }
            public void clear() { IdentityHashMap.this.clear(); }
            public boolean remove(Object key) {
                int index = find(key);
                if(index < 0) return false;
                delete(index);
                return true;
            }
            public int hashCode() {
                int hash = 0;
                Iterator<K> keys = iterator();
                while(keys.hasNext()) hash += System.identityHashCode(keys.next());
                return hash;
            }
        };
    }

    public Collection<V> values() {
        return new AbstractCollection<V>() {
            public int size() { return count; }
            public boolean contains(Object value) { return containsValue(value); }
            public Iterator<V> iterator() { return new Cursor<V>(1); }
            public void clear() { IdentityHashMap.this.clear(); }
            public boolean remove(Object value) {
                for(int i = 0; i < table.length; i += 2)
                    if(table[i] != null && table[i] != DELETED && table[i + 1] == value) {
                        delete(i);
                        return true;
                    }
                return false;
            }
        };
    }

    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Entry<K, V>>() {
            public int size() { return count; }
            public Iterator<Entry<K, V>> iterator() { return new Cursor<Entry<K, V>>(2); }
            public void clear() { IdentityHashMap.this.clear(); }
            public boolean contains(Object value) {
                if(!(value instanceof Entry)) return false;
                Entry<?, ?> entry = (Entry<?, ?>)value;
                int index = find(entry.getKey());
                return index >= 0 && table[index + 1] == entry.getValue();
            }
            public boolean remove(Object value) {
                if(!contains(value)) return false;
                IdentityHashMap.this.remove(((Entry<?, ?>)value).getKey());
                return true;
            }
        };
    }
}
