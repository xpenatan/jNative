package java.util;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/** Internal native drivers shared by collection facades. */
@NativeInclude("jn_collections.hpp")
public final class NativeCollections {
    private NativeCollections() {}

    @NativeImport(value = "jnative::collection_find", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/lang/Object.equals(Ljava/lang/Object;)Z", "java/util/Iterator.remove()V"},
            callbackReceivers = {0, 0, -1, 0},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.INTERFACE})
    public static native boolean find(Iterator<?> iterator, Object value, boolean erase);

    @NativeImport(value = "jnative::collection_bulk", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Collection.contains(Ljava/lang/Object;)Z", "java/util/Collection.add(Ljava/lang/Object;)Z"},
            callbackReceivers = {1, 1, 0, 0},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native boolean bulk(Collection<?> target, Iterator<?> iterator, int mode);

    @NativeImport(value = "jnative::collection_filter", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Collection.contains(Ljava/lang/Object;)Z", "java/util/Iterator.remove()V"},
            callbackReceivers = {0, 0, 1, 0},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native boolean filter(Iterator<?> iterator, Collection<?> other, boolean retain);

    @NativeImport(value = "jnative::collection_clear", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Iterator.remove()V"},
            callbackReceivers = {0, 0, 0},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native void clear(Iterator<?> iterator);

    @NativeImport(value = "jnative::collection_copy", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;"},
            callbackReceivers = {0, 0},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native int copy(Iterator<?> iterator, Object[] output);

    @NativeImport(value = "jnative::collection_hash", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/lang/Object.hashCode()I"},
            callbackReceivers = {0, 0, -1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.VIRTUAL})
    public static native int hash(Iterator<?> iterator, int multiplier, int seed);

    @NativeImport(value = "jnative::collection_string", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {1, 1, -1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.VIRTUAL})
    public static native String render(Object self, Iterator<?> iterator);

    @NativeImport(value = "jnative::list_index", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/List.size()I", "java/util/List.get(I)Ljava/lang/Object;", "java/lang/Object.equals(Ljava/lang/Object;)Z"},
            callbackReceivers = {0, 0, -1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.VIRTUAL})
    public static native int index(List<?> list, Object value, boolean reverse);

    @NativeImport(value = "jnative::list_equal", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/lang/Object.equals(Ljava/lang/Object;)Z"},
            callbackReceivers = {0, 0, -1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.VIRTUAL})
    public static native boolean equal(Iterator<?> first, Iterator<?> second);

    @NativeImport(value = "jnative::iterable_each", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/function/Consumer.accept(Ljava/lang/Object;)V"},
            callbackReceivers = {0, 0, 1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native void forEach(Iterator<?> iterator, java.util.function.Consumer<?> action);

    @NativeImport(value = "jnative::map_scan", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Map$Entry.getKey()Ljava/lang/Object;", "java/util/Map$Entry.getValue()Ljava/lang/Object;", "java/lang/Object.equals(Ljava/lang/Object;)Z", "java/util/Iterator.remove()V"},
            callbackReceivers = {0, 0, -1, -1, -1, 0},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.VIRTUAL, NativeImport.Invocation.INTERFACE})
    public static native Object mapScan(Iterator<?> iterator, Object wanted, int mode);

    @NativeImport(value = "jnative::map_put_all", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Map$Entry.getKey()Ljava/lang/Object;", "java/util/Map$Entry.getValue()Ljava/lang/Object;", "java/util/Map.put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"},
            callbackReceivers = {1, 1, -1, -1, 0},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native void putAll(Map<?, ?> target, Iterator<?> iterator);

    @NativeImport(value = "jnative::map_equal", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Map$Entry.getKey()Ljava/lang/Object;", "java/util/Map$Entry.getValue()Ljava/lang/Object;", "java/util/Map.get(Ljava/lang/Object;)Ljava/lang/Object;", "java/util/Map.containsKey(Ljava/lang/Object;)Z", "java/lang/Object.equals(Ljava/lang/Object;)Z"},
            callbackReceivers = {0, 0, -1, -1, 1, 1, -1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.VIRTUAL})
    public static native boolean mapEqual(Iterator<?> iterator, Map<?, ?> other);

    @NativeImport(value = "jnative::map_string", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Map$Entry.getKey()Ljava/lang/Object;", "java/util/Map$Entry.getValue()Ljava/lang/Object;", "java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {1, 1, -1, -1, -1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.VIRTUAL})
    public static native String mapRender(Object self, Iterator<?> iterator);

    @NativeImport(value = "jnative::collection_populate", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Collection.add(Ljava/lang/Object;)Z"},
            callbackReceivers = {1, 1, 0},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native void populate(Collection<?> target, Iterator<?> iterator);

    @NativeImport(value = "jnative::set_populate", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Collection.add(Ljava/lang/Object;)Z"},
            callbackReceivers = {0},
            callbackKinds = {NativeImport.Invocation.INTERFACE})
    public static native void populateSet(Collection<?> target, Object[] values);

    @NativeImport(value = "jnative::map_populate", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Map.put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"},
            callbackReceivers = {0},
            callbackKinds = {NativeImport.Invocation.INTERFACE})
    public static native void populateMap(Map<?, ?> target, Object[] pairs);

    @NativeImport(value = "jnative::list_writeback", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/List.set(ILjava/lang/Object;)Ljava/lang/Object;"},
            callbackReceivers = {0},
            callbackKinds = {NativeImport.Invocation.INTERFACE})
    public static native void writeback(List<?> list, Object[] values);

    @NativeImport(value = "jnative::collection_nth", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.next()Ljava/lang/Object;"},
            callbackReceivers = {0},
            callbackKinds = {NativeImport.Invocation.INTERFACE})
    public static native Object nth(Iterator<?> iterator, int index);

    @NativeImport(value = "jnative::map_snapshot", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Iterator.hasNext()Z", "java/util/Iterator.next()Ljava/lang/Object;", "java/util/Map$Entry.getKey()Ljava/lang/Object;", "java/util/Map$Entry.getValue()Ljava/lang/Object;"},
            callbackReceivers = {0, 0, -1, -1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native void snapshot(Iterator<?> iterator, Object[] output);
}
