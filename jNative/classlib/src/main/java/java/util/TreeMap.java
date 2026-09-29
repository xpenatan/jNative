package java.util;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/**
 * A balanced ordered map with logarithmic key lookup and insertion.
 */
@NativeInclude("jn_maps.hpp")
public class TreeMap<K, V> extends AbstractMap<K, V> implements SortedMap<K, V> {
    private final Comparator<? super K> comparator;
    private Node<K, V> root;
    private int count, changes;

    private static final class Node<K, V> extends SimpleEntry<K, V> {
        Node<K, V> left, right;
        int height = 1;

        Node(K key, V value) {
            super(key, value);
        }
    }

    public TreeMap() {
        this((Comparator<? super K>)null);
    }

    public TreeMap(Comparator<? super K> comparator) {
        this.comparator = comparator;
    }

    public TreeMap(Map<? extends K, ? extends V> values) {
        this();
        putAll(values);
    }

    public TreeMap(SortedMap<K, ? extends V> values) {
        this(values.comparator());
        putAll(values);
    }

    public Comparator<? super K> comparator() {
        return comparator;
    }

    public int size() {
        return count;
    }

    @SuppressWarnings("unchecked")
    private int compare(Object key, K other) {
        return comparator == null
                ? ((Comparable<? super K>)Objects.requireNonNull(key)).compareTo(other)
                : comparator.compare((K)key, other);
    }

    @SuppressWarnings("unchecked")
    private Node<K, V> find(Object key) {
        if(comparator == null) Objects.requireNonNull(key);
        return (Node<K, V>)findNative(this, key);
    }

    public boolean containsKey(Object key) {
        return find(key) != null;
    }

    public V get(Object key) {
        Node<K, V> node = find(key);
        return node == null ? null : node.getValue();
    }

    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
        return (V)putNative(this, key, value);
    }

    @SuppressWarnings("unchecked")
    public V remove(Object key) {
        if(comparator == null) Objects.requireNonNull(key);
        return (V)removeNative(this, key);
    }

    @SuppressWarnings("unchecked")
    private Node<K, V> first(Node<K, V> node) {
        return (Node<K, V>)extremeNative(node, false);
    }

    @SuppressWarnings("unchecked")
    private Node<K, V> higher(K key) {
        return (Node<K, V>)higherNative(this, key);
    }

    public K firstKey() {
        if(root == null) throw new NoSuchElementException();
        return first(root).getKey();
    }

    @SuppressWarnings("unchecked")
    public K lastKey() {
        if(root == null) throw new NoSuchElementException();
        return ((Node<K, V>)extremeNative(root, true)).getKey();
    }

    public void clear() {
        clearNative(this);
    }

    private static Node newNode(Object key, Object value) {
        return new Node(key, value);
    }

    @NativeImport(value = "jnative::treemap_find", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/TreeMap.compare(Ljava/lang/Object;Ljava/lang/Object;)I"},
            callbackKinds = {NativeImport.Invocation.SPECIAL},
            fields = {"java/util/TreeMap.root:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap.count:I",
                    "java/util/TreeMap.changes:I",
                    "java/util/TreeMap$Node.left:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.right:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.height:I",
                    "java/util/AbstractMap$SimpleEntry.key:Ljava/lang/Object;",
                    "java/util/AbstractMap$SimpleEntry.value:Ljava/lang/Object;"})
    private static native Node findNative(TreeMap self, Object key);

    @NativeImport(value = "jnative::treemap_put", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/TreeMap.compare(Ljava/lang/Object;Ljava/lang/Object;)I",
                    "java/util/TreeMap.newNode(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/TreeMap$Node;"},
            callbackKinds = {NativeImport.Invocation.SPECIAL, NativeImport.Invocation.STATIC},
            fields = {"java/util/TreeMap.root:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap.count:I",
                    "java/util/TreeMap.changes:I",
                    "java/util/TreeMap$Node.left:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.right:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.height:I",
                    "java/util/AbstractMap$SimpleEntry.key:Ljava/lang/Object;",
                    "java/util/AbstractMap$SimpleEntry.value:Ljava/lang/Object;"})
    private static native Object putNative(TreeMap self, Object key, Object value);

    @NativeImport(value = "jnative::treemap_remove", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/TreeMap.compare(Ljava/lang/Object;Ljava/lang/Object;)I"},
            callbackKinds = {NativeImport.Invocation.SPECIAL},
            fields = {"java/util/TreeMap.root:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap.count:I",
                    "java/util/TreeMap.changes:I",
                    "java/util/TreeMap$Node.left:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.right:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.height:I",
                    "java/util/AbstractMap$SimpleEntry.key:Ljava/lang/Object;",
                    "java/util/AbstractMap$SimpleEntry.value:Ljava/lang/Object;"})
    private static native Object removeNative(TreeMap self, Object key);

    @NativeImport(value = "jnative::treemap_higher", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/TreeMap.compare(Ljava/lang/Object;Ljava/lang/Object;)I"},
            callbackKinds = {NativeImport.Invocation.SPECIAL},
            fields = {"java/util/TreeMap.root:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap.count:I",
                    "java/util/TreeMap.changes:I",
                    "java/util/TreeMap$Node.left:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.right:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.height:I",
                    "java/util/AbstractMap$SimpleEntry.key:Ljava/lang/Object;",
                    "java/util/AbstractMap$SimpleEntry.value:Ljava/lang/Object;"})
    private static native Node higherNative(TreeMap self, Object key);

    @NativeImport(value = "jnative::treemap_extreme", managed = true, runtimeOnly = true, managesRoots = true,
            fields = {"java/util/TreeMap$Node.left:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap$Node.right:Ljava/util/TreeMap$Node;"})
    private static native Node extremeNative(Node node, boolean last);

    @NativeImport(value = "jnative::treemap_clear", managed = true, runtimeOnly = true, managesRoots = true,
            fields = {"java/util/TreeMap.root:Ljava/util/TreeMap$Node;",
                    "java/util/TreeMap.count:I", "java/util/TreeMap.changes:I"})
    private static native void clearNative(TreeMap self);

    public Set<Entry<K, V>> entrySet() {
        return new AbstractSet<Entry<K, V>>() {
            public int size() {
                return count;
            }

            public void clear() {
                TreeMap.this.clear();
            }

            public Iterator<Entry<K, V>> iterator() {
                return new Iterator<Entry<K, V>>() {
                    Node<K, V> next = first(root), last;
                    int expected = changes;

                    public boolean hasNext() {
                        return next != null;
                    }

                    public Entry<K, V> next() {
                        if(expected != changes) throw new ConcurrentModificationException();
                        if(next == null) throw new NoSuchElementException();
                        last = next;
                        next = higher(next.getKey());
                        return last;
                    }

                    public void remove() {
                        if(expected != changes) throw new ConcurrentModificationException();
                        if(last == null) throw new IllegalStateException();
                        TreeMap.this.remove(last.getKey());
                        last = null;
                        expected = changes;
                    }
                };
            }
        };
    }
}
