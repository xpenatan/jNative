import java.util.*;

public class NativeMaps {
    static boolean collect;
    static volatile boolean stopCollector;

    static final class Key {
        final int value;
        Key(int value) { this.value = value; }
        public int hashCode() { if (collect) System.gc(); return 7; }
        public boolean equals(Object other) {
            if (collect) { Object[] temporary = new Object[8]; temporary[0] = this; System.gc(); }
            return other instanceof Key && ((Key)other).value == value;
        }
    }

    static final class HashProbe {
        int calls;
        boolean fail;
        public int hashCode() {
            calls++;
            System.gc();
            if (fail) throw new IllegalStateException("hash");
            return 7;
        }
    }

    static final class FlatKey {
        final int value;
        int hashes, equalities;
        FlatKey(int value) { this.value = value; }
        public int hashCode() { hashes++; return 7; }
        public boolean equals(Object other) {
            equalities++;
            return other instanceof FlatKey && ((FlatKey) other).value == value;
        }
    }

    static HashMap<CollectingEquality, Object[]> reentrantMap;
    static final class CollectingEquality {
        final int value;
        boolean clear;
        CollectingEquality(int value) { this.value = value; }
        public int hashCode() { return 7; }
        public boolean equals(Object other) {
            if (clear) reentrantMap.clear();
            System.gc();
            return other instanceof CollectingEquality && ((CollectingEquality) other).value == value;
        }
    }

    static final class BadHash {
        int calls;
        int[] state = {7};
        public int hashCode() { calls++; return state[0]; }
    }

    static class InheritedHash {
        public final int hashCode() { return 43; }
    }

    static class InheritedIdentity {}

    static Object[] detachedQueries;
    static final class DetachedQuery {
        final boolean detach;
        DetachedQuery(boolean detach) { this.detach = detach; }
        public int hashCode() {
            if (detach) { detachedQueries[0] = null; System.gc(); }
            return 71;
        }
        public boolean equals(Object other) { return other instanceof DetachedQuery; }
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0 && arguments[0].equals("hooks")) {
            hooks();
            return;
        }
        hashMaps();
        hashCallbacks();
        boxedKeys();
        growthFactors();
        boundedPuts();
        boundedLookups();
        treeMaps();
        callbackCollection();
        concurrentCollection();
        System.out.println("maps-ok");
    }

    static void require(boolean value) { if (!value) throw new AssertionError(); }

    static void growthFactors() {
        for (float factor : new float[]{0.5f, 1.25f, Float.POSITIVE_INFINITY}) {
            for (int capacity : new int[]{0, 17}) {
                HashMap<Integer, Integer> map = new HashMap<>(capacity, factor);
                for (int i = -32; i <= 32; i++) map.put(i, i + 1);
                require(map.size() == 65);
                for (int i = -32; i <= 32; i++) require(map.get(i) == i + 1);
                for (int i = -32; i <= 32; i += 2) require(map.remove(i) == i + 1);
                map.clear();
                for (int i = 0; i < 65; i++) map.put(i, i + 2);
                for (int i = 0; i < 65; i++) require(map.get(i) == i + 2);
            }
        }
    }

    static void boxedKeys() {
        Object[] values = {Integer.MIN_VALUE, Integer.MAX_VALUE, -129, -128, 127, 128,
                Long.MIN_VALUE, Long.MAX_VALUE, -129L, -128L, 127L, 128L,
                Short.MIN_VALUE, Short.MAX_VALUE, Byte.MIN_VALUE, Byte.MAX_VALUE,
                Character.MIN_VALUE, Character.MAX_VALUE, Boolean.FALSE, Boolean.TRUE};
        int[] hashes = {Integer.MIN_VALUE, Integer.MAX_VALUE, -129, -128, 127, 128,
                Integer.MIN_VALUE, Integer.MIN_VALUE, 128, 127, 127, 128,
                Short.MIN_VALUE, Short.MAX_VALUE, Byte.MIN_VALUE, Byte.MAX_VALUE, 0, 65535, 1237, 1231};
        HashMap<Object, Object> map = new HashMap<>();
        for (int i = 0; i < values.length; i++) {
            require(values[i].hashCode() == hashes[i]);
            map.put(values[i], values[i]);
        }
        System.gc();
        for (Object value : values) require(map.get(value) == value);
        for (int value = -128; value <= 127; value++) {
            require(Byte.valueOf((byte)value) == Byte.valueOf((byte)value));
            require(Short.valueOf((short)value) == Short.valueOf((short)value));
            require(Integer.valueOf(value) == Integer.valueOf(value));
            require(Long.valueOf(value) == Long.valueOf(value));
        }
        for (char value = 0; value < 128; value++)
            require(Character.valueOf(value) == Character.valueOf(value));
    }

    static void hashCallbacks() {
        HashMap<HashProbe, Object[]> map = new HashMap<>(1);
        HashProbe key = new HashProbe();
        Object[] first = {"first"}, second = {"second"};
        require(map.put(key, first) == null && key.calls == 1);
        require(map.put(key, second) == first && key.calls == 2);
        key.fail = true;
        try { map.put(key, first); throw new AssertionError(); }
        catch (IllegalStateException expected) {}
        require(key.calls == 3 && map.size() == 1);
        key.fail = false;
        require(map.get(key) == second && key.calls == 4);
    }

    static void boundedPuts() {
        HashMap<FlatKey, Object[]> map = new HashMap<>(16);
        FlatKey first = new FlatKey(1), equal = new FlatKey(1), collision = new FlatKey(2);
        Object[] one = {"one"}, two = {"two"}, three = {"three"};
        require(map.put(first, one) == null && first.hashes == 1 && first.equalities == 0);
        require(map.put(first, two) == one && first.hashes == 2 && first.equalities == 0);
        require(map.put(equal, three) == two && equal.hashes == 1 && equal.equalities == 1);
        require(map.put(collision, one) == null && collision.hashes == 1 && collision.equalities == 1);
        int before = equal.equalities;
        require(map.put(equal, two) == three && equal.hashes == 2);
        require(equal.equalities > before && equal.equalities <= before + 2);
        require(map.put(null, one) == null && map.put(null, two) == one);
        System.gc();
        require(map.size() == 3 && map.get(first) == two && map.get(collision) == one && map.get(null) == two);
    }

    static void boundedLookups() {
        HashMap<Object, Object> nativeKeys = new HashMap<>();
        Object[] runtimeKeys = {new Object(), new InheritedIdentity(), new Object[3], "",
                new String("cached hash"), new String(new char[2049])};
        for (Object key : runtimeKeys) nativeKeys.put(key, key);
        for (Object key : runtimeKeys) require(nativeKeys.get(key) == key && nativeKeys.containsKey(key));
        require(nativeKeys.get(new String("cached hash")) == runtimeKeys[4]);
        require(nativeKeys.get(new String(new char[2049])) == runtimeKeys[5]);
        require(nativeKeys.get(new InheritedIdentity()) == null);

        // Unrelated receiver classes must not displace another bounded target.
        // Inherited implementations should share one generated callback arm.
        Object[] inherited = {new InheritedHash() {}, new InheritedHash() {}, new InheritedHash() {},
                new InheritedHash() {}, new InheritedHash() {}, new InheritedHash() {},
                new InheritedHash() {}, new InheritedHash() {}, new InheritedHash() {}};
        HashMap<Object, Object> inheritedMap = new HashMap<>();
        for (Object key : inherited) inheritedMap.put(key, key);
        for (Object key : inherited) require(inheritedMap.get(key) == key);

        HashMap<Object, Integer> detachedMap = new HashMap<>();
        detachedMap.put(new DetachedQuery(false), 71);
        detachedQueries = new Object[1];
        for (int i = 0; i < 3; i++) {
            detachedQueries[0] = new DetachedQuery(true);
            require(detachedMap.get(detachedQueries[0]) == 71);
            require(detachedQueries[0] == null);
        }
        detachedQueries = null;

        HashMap<FlatKey, Object[]> map = new HashMap<>(1);
        FlatKey stored = new FlatKey(19), query = new FlatKey(19);
        Object[] value = {"bounded"};
        map.put(stored, value);
        require(map.get(query) == value && query.hashes == 1 && query.equalities == 1);
        require(map.get(stored) == value && stored.hashes == 2 && stored.equalities == 0);
        require(map.containsKey(query) && query.hashes == 2 && query.equalities == 2);
        map.put(null, value);
        require(map.get(null) == value && map.containsKey(null));
        for (int i = 0; i < 4; i++) map.put(new FlatKey(i), new Object[]{i});
        for (int i = 0; i < 4; i++) require(((Integer) map.get(new FlatKey(i))[0]) == i);
        require(map.get(new FlatKey(99)) == null);

        reentrantMap = new HashMap<>(1);
        reentrantMap.put(new CollectingEquality(23), new Object[]{"detached"});
        CollectingEquality collecting = new CollectingEquality(23);
        collecting.clear = true;
        require(reentrantMap.get(collecting)[0].equals("detached"));
        require(reentrantMap.isEmpty());
        reentrantMap = null;

        HashMap<BadHash, Integer> throwing = new HashMap<>();
        BadHash bad = new BadHash();
        throwing.put(bad, 1);
        bad.state = null;
        try { throwing.get(bad); throw new AssertionError(); }
        catch (NullPointerException expected) { }
        require(bad.calls == 2);
    }

    static void hashMaps() {
        reusableViews(new HashMap<>());
        reusableViews(new LinkedHashMap<>());
        Set<Integer> detached = detachedKeys();
        System.gc();
        require(detached.contains(123) && detached.remove(123) && detached.isEmpty());
        HashMap<Key, String> map = new HashMap<>(1, .25f);
        for (int i = 0; i < 300; ++i) require(map.put(new Key(i), "v" + i) == null);
        require(map.size() == 300);
        for (int i = 0; i < 300; ++i) require(map.get(new Key(i)).equals("v" + i));
        require(map.put(new Key(125), "updated").equals("v125"));
        Map.Entry<Key, String> live = null;
        for (Map.Entry<Key, String> entry : map.entrySet()) if (entry.getKey().value == 125) live = entry;
        require(live != null && live.setValue("live").equals("updated"));
        require(map.get(new Key(125)).equals("live"));
        for (int i = 0; i < 300; i += 2) require(map.remove(new Key(i)) != null);
        require(map.size() == 150 && live.getValue().equals("live"));
        Iterator<Key> cursor = map.keySet().iterator();
        while (cursor.hasNext()) { cursor.next(); cursor.remove(); }
        require(map.isEmpty());
        map.put(null, null);
        require(map.containsKey(null) && map.remove(null) == null && map.isEmpty());
        map.put(new Key(1), "one");
        cursor = map.keySet().iterator();
        map.clear();
        try { cursor.next(); throw new AssertionError(); }
        catch (ConcurrentModificationException expected) {}
        for (int i = 0; i < 17; ++i) map.put(new Key(i), "after" + i);
        require(map.size() == 17);

        IdentityHashMap<Key, Integer> identities = new IdentityHashMap<>();
        Key first = new Key(1), second = new Key(1);
        identities.put(first, 2); identities.put(second, 3);
        require(identities.size() == 2 && identities.get(first) == 2 && identities.get(second) == 3);
        require(identities.remove(new Key(1)) == null);

        LinkedHashMap<Integer, Integer> ordered = new LinkedHashMap<>();
        for (int i = 0; i < 40; ++i) ordered.put(i, i);
        ordered.remove(12); ordered.put(12, -1);
        int expected = 0;
        for (int key : ordered.keySet()) {
            if (expected == 12) ++expected;
            if (expected < 40) require(key == expected++);
            else require(key == 12);
        }
        LinkedHashMap<Integer, Integer> copied = new LinkedHashMap<>(ordered);
        Iterator<Integer> original = ordered.keySet().iterator(), copy = copied.keySet().iterator();
        while (original.hasNext()) require(original.next().equals(copy.next()));
        require(!copy.hasNext());
        Iterator<Map.Entry<Integer, Integer>> linkedEntries = copied.entrySet().iterator();
        int removed = 0;
        while (linkedEntries.hasNext()) {
            Map.Entry<Integer, Integer> entry = linkedEntries.next();
            System.gc();
            if ((entry.getKey() & 1) == 0) { linkedEntries.remove(); removed++; }
            else require(entry.setValue(-entry.getKey()) != null);
        }
        require(copied.size() == 40 - removed && ordered.size() == 40);
        copied.clear();
        copied.put(5, 5); copied.put(3, 3);
        copy = copied.keySet().iterator();
        require(copy.next() == 5 && copy.next() == 3 && !copy.hasNext());

        // Bucket iteration must cross empty ranges and collision links exactly once.
        HashMap<Integer, Integer> sparse = new HashMap<>(65536);
        int[] sparseKeys = {0, 1, 32768, 65535, 65536};
        for (int key : sparseKeys) sparse.put(key, key + 1);
        int visited = 0;
        Iterator<Map.Entry<Integer, Integer>> sparseEntries = sparse.entrySet().iterator();
        while (sparseEntries.hasNext()) {
            Map.Entry<Integer, Integer> entry = sparseEntries.next();
            require(entry.getValue() == entry.getKey() + 1);
            visited++;
            sparseEntries.remove();
            System.gc();
        }
        require(visited == sparseKeys.length && sparse.isEmpty());
    }

    static Set<Integer> detachedKeys() {
        HashMap<Integer, String> map = new HashMap<>();
        map.put(123, "kept by view");
        return map.keySet();
    }

    static void reusableViews(HashMap<Integer, String> map) {
        Set<Integer> keys = map.keySet();
        Collection<String> values = map.values();
        Set<Map.Entry<Integer, String>> entries = map.entrySet();
        require(keys == map.keySet() && values == map.values() && entries == map.entrySet());
        require(keys.isEmpty() && values.isEmpty() && entries.isEmpty());
        for (int i = 0; i < 80; i++) map.put(i, "value" + i);
        System.gc();
        require(keys.size() == 80 && values.contains("value79") && entries.size() == 80);
        require(keys.remove(3) && map.get(3) == null);
        require(values.remove("value4") && !map.containsKey(4));
        require(entries.remove(new AbstractMap.SimpleEntry<>(5, "value5")));
        Iterator<Map.Entry<Integer, String>> cursor = entries.iterator();
        Map.Entry<Integer, String> entry = cursor.next();
        entry.setValue("replacement");
        require(map.get(entry.getKey()).equals("replacement"));
        cursor.remove();
        require(!map.containsKey(entry.getKey()));
        Iterator<Integer> stale = keys.iterator();
        map.clear();
        try { stale.next(); throw new AssertionError(); }
        catch (ConcurrentModificationException expected) { }
        require(keys.isEmpty() && values.isEmpty() && entries.isEmpty());
        map.put(101, "again");
        System.gc();
        require(keys.contains(101) && values.contains("again"));
        require(entries.contains(new AbstractMap.SimpleEntry<>(101, "again")));
        require(keys == map.keySet() && values == map.values() && entries == map.entrySet());
        values.clear();
        require(map.isEmpty() && keys.isEmpty());
    }

    static void treeMaps() {
        TreeMap<Integer, String> map = new TreeMap<>();
        for (int i = 0; i < 257; ++i) map.put((i * 73) % 257, "v" + ((i * 73) % 257));
        require(map.firstKey() == 0 && map.lastKey() == 256 && map.size() == 257);
        for (int i = 0; i < 257; ++i) require(map.get(i).equals("v" + i));
        Map.Entry<Integer, String> live = null;
        for (Map.Entry<Integer, String> entry : map.entrySet()) if (entry.getKey() == 128) live = entry;
        require(live != null && live.setValue("live").equals("v128"));
        for (int i = 1; i < 257; i += 2) require(map.remove(i).equals("v" + i));
        require(map.get(128).equals("live") && live.getKey() == 128);
        int previous = -1;
        Iterator<Map.Entry<Integer, String>> cursor = map.entrySet().iterator();
        while (cursor.hasNext()) {
            Map.Entry<Integer, String> entry = cursor.next();
            require(entry.getKey() > previous);
            previous = entry.getKey();
            cursor.remove();
        }
        require(map.isEmpty());
        try { map.firstKey(); throw new AssertionError(); } catch (NoSuchElementException expected) {}
        try { map.lastKey(); throw new AssertionError(); } catch (NoSuchElementException expected) {}
        try { map.get(null); throw new AssertionError(); } catch (NullPointerException expected) {}
        try { ((Map)map).put(new Object(), "wrong"); throw new AssertionError(); } catch (ClassCastException expected) {}

        TreeMap<Integer, Integer> reverse = new TreeMap<>((a, b) -> b.compareTo(a));
        for (int i = 0; i < 70; ++i) reverse.put(i, i);
        require(reverse.firstKey() == 69 && reverse.lastKey() == 0);
        previous = 70;
        for (int key : reverse.keySet()) { require(key < previous); previous = key; }
        Iterator<Integer> keys = reverse.keySet().iterator();
        reverse.clear();
        try { keys.next(); throw new AssertionError(); } catch (ConcurrentModificationException expected) {}

        TreeMap<Integer, Integer> throwing = new TreeMap<>((a, b) -> {
            if (a == 999 && b != 999) throw new IllegalStateException("comparison");
            return a.compareTo(b);
        });
        throwing.put(1, 1); throwing.put(2, 2);
        try { throwing.put(999, 9); throw new AssertionError(); } catch (IllegalStateException expected) {}
        require(throwing.size() == 2 && throwing.get(1) == 1 && throwing.get(2) == 2);
    }

    static void callbackCollection() {
        collect = true;
        HashMap<Key, Object[]> map = new HashMap<>(1);
        for (int i = 0; i < 35; ++i) map.put(new Key(i), new Object[] { "v" + i });
        for (int i = 0; i < 35; ++i) require(map.get(new Key(i))[0].equals("v" + i));
        for (int i = 0; i < 35; ++i) require(map.remove(new Key(i)) != null);
        collect = false;
        TreeMap<Integer, Object[]> tree = new TreeMap<>((a, b) -> {
            Object[] temporary = new Object[] {a, b};
            System.gc();
            return ((Integer)temporary[0]).compareTo((Integer)temporary[1]);
        });
        for (int i = 0; i < 35; ++i) tree.put(i, new Object[] { "t" + i });
        for (int i = 0; i < 35; ++i) require(tree.remove(i)[0].equals("t" + i));
        require(tree.isEmpty());
    }

    static void concurrentCollection() throws Exception {
        stopCollector = false;
        Thread collector = new Thread(() -> { while (!stopCollector) { System.gc(); Thread.yield(); } });
        collector.start();
        try {
            HashMap<Key, Integer> hash = new HashMap<>(1);
            TreeMap<Integer, Integer> tree = new TreeMap<>();
            for (int i = 0; i < 120; ++i) { hash.put(new Key(i), i); tree.put(i, i); }
            for (int i = 0; i < 120; ++i) { require(hash.remove(new Key(i)) == i); require(tree.remove(i) == i); }
            HashMap<FlatKey, Integer> fast = new HashMap<>();
            for (int i = 0; i < 40; i++) fast.put(new FlatKey(i), i);
            for (int round = 0; round < 25; round++)
                for (int i = 0; i < 40; i++) require(fast.get(new FlatKey(i)) == i);
        } finally { stopCollector = true; collector.join(); }
    }

    // This emulation exposes protected hash/equality hooks; the JVM HashMap does not invoke them.
    static class HookMap extends HashMap<Object, Integer> {
        int hashes, equalities;
        boolean fail;
        boolean clearOnEquality;
        protected int hash(Object key) { ++hashes; System.gc(); return 3; }
        protected boolean keysEqual(Object first, Object second) {
            ++equalities;
            System.gc();
            if (fail) throw new IllegalStateException("equality");
            if (clearOnEquality) { clearOnEquality = false; clear(); }
            return first == second;
        }
    }

    static void hooks() {
        HookMap map = new HookMap();
        Object first = new Object(), second = new Object();
        map.put(first, 1); map.put(second, 2);
        require(map.hashes == 2 && map.equalities == 1);
        map.fail = true;
        try { map.put(new Object(), 3); throw new AssertionError(); } catch (IllegalStateException expected) {}
        map.fail = false;
        require(map.size() == 2 && map.get(first) == 1 && map.get(second) == 2);
        map.clearOnEquality = true;
        require(map.get(second) == 2 && map.isEmpty());

        final TreeMap<Integer, Integer>[] holder = new TreeMap[1];
        final boolean[] reentered = {false};
        holder[0] = new TreeMap<>((a, b) -> {
            System.gc();
            if (a == 77 && b != 77 && !reentered[0]) { reentered[0] = true; holder[0].clear(); }
            return a.compareTo(b);
        });
        holder[0].put(1, 1); holder[0].put(2, 2);
        holder[0].put(77, 9);
        require(holder[0].size() == 1 && holder[0].get(77) == 9 && holder[0].get(1) == null);

        TreeMap<Integer, Integer> identity = new TreeMap<>();
        identity.put(2, 2); identity.put(1, 1); identity.put(3, 3);
        Map.Entry<Integer, Integer> removed = null, successor = null;
        for (Map.Entry<Integer, Integer> entry : identity.entrySet()) {
            if (entry.getKey() == 2) removed = entry;
            if (entry.getKey() == 3) successor = entry;
        }
        identity.remove(2);
        require(removed.getKey() == 2 && removed.getValue() == 2);
        require(successor.setValue(30) == 3 && identity.get(3) == 30);
        System.out.println("map-hooks-ok");
    }
}
