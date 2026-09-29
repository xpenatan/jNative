package java.util;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("jn_maps.hpp")
public class HashMap<K, V> extends AbstractMap<K, V>
        implements Map<K, V>, Cloneable, java.io.Serializable {
    private Node<K, V>[] buckets;
    private Node<K, V> head, tail;
    private int count, changes;
    private final float loadFactor;

    public HashMap() {
        this(16, 0.75f);
    }

    public HashMap(int capacity) {
        this(capacity, 0.75f);
    }

    @SuppressWarnings("unchecked")
    public HashMap(int capacity, float loadFactor) {
        if(capacity < 0 || !(loadFactor > 0)) throw new IllegalArgumentException();
        int size = capacityNative(capacity);
        buckets = (Node<K, V>[])new Node[size];
        this.loadFactor = loadFactor;
    }

    public HashMap(Map<? extends K, ? extends V> values) {
        this();
        putAll(values);
    }

    static final class Node<K, V> implements Map.Entry<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;
        Node<K, V> before, after;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V value) {
            V old = this.value;
            this.value = value;
            return old;
        }

        public boolean equals(Object other) {
            if(!(other instanceof Map.Entry)) return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>)other;
            return Objects.equals(key, entry.getKey()) && Objects.equals(value, entry.getValue());
        }

        public int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        public String toString() {
            return String.valueOf(key) + "=" + String.valueOf(value);
        }
    }

    protected boolean keysEqual(Object first, Object second) {
        return Objects.equals(first, second);
    }

    protected int hash(Object key) {
        int hash = Objects.hashCode(key);
        return hash ^ (hash >>> 16);
    }

    @SuppressWarnings("unchecked")
    private Node<K, V> find(Object key) {
        return (Node<K, V>)findNative(this, key);
    }

    public int size() {
        return count;
    }

    public boolean containsKey(Object key) {
        return find(key) != null;
    }

    public V get(Object key) {
        Node<K, V> node = find(key);
        return node == null ? null : node.value;
    }

    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        return (V)putNative(this, key, value);
    }

    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        return (V)removeNative(this, key);
    }

    public void clear() {
        clearNative(this);
    }

    private static Node newNode(int hash, Object key, Object value, Node next) {
        return new Node(hash, key, value, next);
    }

    private static Node[] newBuckets(int capacity) {
        return new Node[capacity];
    }

    @NativeImport(value = "jnative::hashmap_capacity", managed = true, runtimeOnly = true, bounded = true)
    private static native int capacityNative(int capacity);

    @NativeImport(value = "jnative::hashmap_find", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/HashMap.hash(Ljava/lang/Object;)I",
                    "java/util/HashMap.keysEqual(Ljava/lang/Object;Ljava/lang/Object;)Z"},
            callbackKinds = {NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.VIRTUAL},
            fields = {"java/util/HashMap.buckets:[Ljava/util/HashMap$Node;",
                    "java/util/HashMap.head:Ljava/util/HashMap$Node;",
                    "java/util/HashMap.tail:Ljava/util/HashMap$Node;",
                    "java/util/HashMap.count:I",
                    "java/util/HashMap.changes:I",
                    "java/util/HashMap.loadFactor:F",
                    "java/util/HashMap$Node.hash:I",
                    "java/util/HashMap$Node.key:Ljava/lang/Object;",
                    "java/util/HashMap$Node.value:Ljava/lang/Object;",
                    "java/util/HashMap$Node.next:Ljava/util/HashMap$Node;",
                    "java/util/HashMap$Node.before:Ljava/util/HashMap$Node;",
                    "java/util/HashMap$Node.after:Ljava/util/HashMap$Node;"})
    private static native Node findNative(HashMap self, Object key);

    @NativeImport(value = "jnative::hashmap_put", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/HashMap.hash(Ljava/lang/Object;)I",
                    "java/util/HashMap.keysEqual(Ljava/lang/Object;Ljava/lang/Object;)Z",
                    "java/util/HashMap.newNode(ILjava/lang/Object;Ljava/lang/Object;Ljava/util/HashMap$Node;)Ljava/util/HashMap$Node;",
                    "java/util/HashMap.newBuckets(I)[Ljava/util/HashMap$Node;"},
            callbackKinds = {NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.STATIC, NativeImport.Invocation.STATIC},
            fields = {"java/util/HashMap.buckets:[Ljava/util/HashMap$Node;",
                    "java/util/HashMap.head:Ljava/util/HashMap$Node;",
                    "java/util/HashMap.tail:Ljava/util/HashMap$Node;",
                    "java/util/HashMap.count:I",
                    "java/util/HashMap.changes:I",
                    "java/util/HashMap.loadFactor:F",
                    "java/util/HashMap$Node.hash:I",
                    "java/util/HashMap$Node.key:Ljava/lang/Object;",
                    "java/util/HashMap$Node.value:Ljava/lang/Object;",
                    "java/util/HashMap$Node.next:Ljava/util/HashMap$Node;",
                    "java/util/HashMap$Node.before:Ljava/util/HashMap$Node;",
                    "java/util/HashMap$Node.after:Ljava/util/HashMap$Node;"})
    private static native Object putNative(HashMap self, Object key, Object value);

    @NativeImport(value = "jnative::hashmap_remove", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/HashMap.hash(Ljava/lang/Object;)I",
                    "java/util/HashMap.keysEqual(Ljava/lang/Object;Ljava/lang/Object;)Z"},
            callbackKinds = {NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.VIRTUAL},
            fields = {"java/util/HashMap.buckets:[Ljava/util/HashMap$Node;",
                    "java/util/HashMap.head:Ljava/util/HashMap$Node;",
                    "java/util/HashMap.tail:Ljava/util/HashMap$Node;",
                    "java/util/HashMap.count:I",
                    "java/util/HashMap.changes:I",
                    "java/util/HashMap.loadFactor:F",
                    "java/util/HashMap$Node.hash:I",
                    "java/util/HashMap$Node.key:Ljava/lang/Object;",
                    "java/util/HashMap$Node.value:Ljava/lang/Object;",
                    "java/util/HashMap$Node.next:Ljava/util/HashMap$Node;",
                    "java/util/HashMap$Node.before:Ljava/util/HashMap$Node;",
                    "java/util/HashMap$Node.after:Ljava/util/HashMap$Node;"})
    private static native Object removeNative(HashMap self, Object key);

    @NativeImport(value = "jnative::hashmap_clear", managed = true, runtimeOnly = true, managesRoots = true,
            fields = {"java/util/HashMap.buckets:[Ljava/util/HashMap$Node;",
                    "java/util/HashMap.head:Ljava/util/HashMap$Node;",
                    "java/util/HashMap.tail:Ljava/util/HashMap$Node;",
                    "java/util/HashMap.count:I", "java/util/HashMap.changes:I"})
    private static native void clearNative(HashMap self);

    private class Cursor<T> implements Iterator<T> {
        final int mode;
        int expected = changes;
        Node<K, V> next, last;

        Cursor(int mode) {
            this.mode = mode;
            next = head;
        }

        void check() {
            if(expected != changes) throw new ConcurrentModificationException();
        }

        public boolean hasNext() {
            return next != null;
        }

        @SuppressWarnings("unchecked")
        public T next() {
            check();
            if(next == null) throw new NoSuchElementException();
            last = next;
            next = next.after;
            return (T)(mode == 0 ? last.key : mode == 1 ? last.value : last);
        }

        public void remove() {
            check();
            if(last == null) throw new IllegalStateException();
            HashMap.this.remove(last.key);
            last = null;
            expected = changes;
        }
    }

    public Set<K> keySet() {
        return new Keys();
    }

    private class Keys extends AbstractSet<K> {
        public int size() {
            return count;
        }

        public Iterator<K> iterator() {
            return new Cursor<K>(0);
        }

        public boolean contains(Object key) {
            return containsKey(key);
        }

        public boolean remove(Object key) {
            if(!containsKey(key)) return false;
            HashMap.this.remove(key);
            return true;
        }

        public void clear() {
            HashMap.this.clear();
        }
    }

    public Collection<V> values() {
        return new Values();
    }

    private class Values extends AbstractCollection<V> {
        public int size() {
            return count;
        }

        public Iterator<V> iterator() {
            return new Cursor<V>(1);
        }

        public void clear() {
            HashMap.this.clear();
        }
    }

    public Set<Entry<K, V>> entrySet() {
        return new Entries();
    }

    private class Entries extends AbstractSet<Entry<K, V>> {
        public int size() {
            return count;
        }

        public Iterator<Entry<K, V>> iterator() {
            return new Cursor<Entry<K, V>>(2);
        }

        public void clear() {
            HashMap.this.clear();
        }

        public boolean contains(Object value) {
            if(!(value instanceof Map.Entry)) return false;
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>)value;
            Node<K, V> node = find(entry.getKey());
            return node != null && Objects.equals(node.value, entry.getValue());
        }

        public boolean remove(Object value) {
            if(!contains(value)) return false;
            HashMap.this.remove(((Map.Entry<?, ?>)value).getKey());
            return true;
        }
    }
}
