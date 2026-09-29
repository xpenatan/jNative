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

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0 && arguments[0].equals("hooks")) {
            hooks();
            return;
        }
        hashMaps();
        treeMaps();
        callbackCollection();
        concurrentCollection();
        System.out.println("maps-ok");
    }

    static void require(boolean value) { if (!value) throw new AssertionError(); }

    static void hashMaps() {
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
        require(map.hashes == 4 && map.equalities == 1);
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
