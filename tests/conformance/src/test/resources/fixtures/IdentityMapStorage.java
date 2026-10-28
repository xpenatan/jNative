import java.util.AbstractMap;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class IdentityMapStorage {
    static final class Key {
        public int hashCode() { throw new IllegalStateException("identity hash called user code"); }
        public boolean equals(Object other) { throw new IllegalStateException("identity equality called user code"); }
    }
    static void require(boolean value) { if(!value) throw new AssertionError(); }
    static volatile boolean stop;

    static void exercise() {
        IdentityHashMap<Object, Object> map = new IdentityHashMap<>(0);
        Object[] keys = new Object[193];
        Object[] values = new Object[193];
        boolean[] present = new boolean[193];
        Object[] expected = new Object[193];
        for(int i = 1; i < keys.length; ++i) { keys[i] = new Key(); values[i] = new Key(); }
        int state = 0x12345678;
        for(int round = 0; round < 4000; ++round) {
            state = state * 1664525 + 1013904223;
            int key = (state >>> 1) % keys.length;
            switch(round % 5) {
                case 0: case 1: case 2:
                    Object value = values[(state >>> 9) % values.length];
                    require(map.put(keys[key], value) == expected[key]);
                    present[key] = true;
                    expected[key] = value;
                    break;
                case 3:
                    require(map.keySet().remove(keys[key]) == present[key]);
                    present[key] = false;
                    expected[key] = null;
                    break;
                default:
                    require(map.remove(keys[key]) == expected[key]);
                    present[key] = false;
                    expected[key] = null;
            }
            require(map.containsKey(keys[key]) == present[key] && map.get(keys[key]) == expected[key]);
            if(round % 127 == 0) System.gc();
            if(round % 503 == 0) {
                int count = 0;
                for(int i = 0; i < keys.length; ++i) {
                    require(map.containsKey(keys[i]) == present[i] && map.get(keys[i]) == expected[i]);
                    if(present[i]) count++;
                }
                require(map.size() == count);
            }
        }
        IdentityHashMap<Object, Object> copy = new IdentityHashMap<>(map);
        require(copy.equals(map) && map.equals(copy));
        require(copy.hashCode() == map.hashCode() && copy.entrySet().hashCode() == map.hashCode());
        IdentityHashMap<Object, Object> cloned = (IdentityHashMap<Object, Object>)map.clone();
        cloned.clear();
        require(cloned.isEmpty() && !map.isEmpty());
        int keyHash = 0;
        for(Object key : map.keySet()) keyHash += System.identityHashCode(key);
        require(keyHash == map.keySet().hashCode());
        int removed = 0;
        Iterator<Map.Entry<Object, Object>> iterator = map.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<Object, Object> entry = iterator.next();
            Object key = entry.getKey(), value = entry.getValue();
            require(entry.setValue(value) == value && map.get(key) == value);
            require(entry.equals(new AbstractMap.SimpleEntry<>(key, value)));
            iterator.remove();
            removed++;
            try { entry.getValue(); throw new AssertionError(); } catch(IllegalStateException expectedError) {}
            try { iterator.remove(); throw new AssertionError(); } catch(IllegalStateException expectedError) {}
        }
        require(map.isEmpty() && removed == copy.size());
        try { iterator.next(); throw new AssertionError(); } catch(NoSuchElementException expectedError) {}
        // Deletion markers must not turn missing-key searches into endless probes.
        for(int round = 0; round < 200; ++round) {
            for(int i = 0; i < keys.length; ++i) map.put(keys[i], values[i]);
            Iterator<Object> cursor = map.keySet().iterator();
            while(cursor.hasNext()) { cursor.next(); cursor.remove(); }
            require(map.isEmpty() && map.get(new Key()) == null);
        }
        map.put(null, null);
        require(map.containsKey(null) && map.containsValue(null));
        require(map.values().remove(null) && map.isEmpty());
        String first = new String(new char[]{'x'}), equal = new String(new char[]{'x'});
        String firstValue = new String(new char[]{'v'}), equalValue = new String(new char[]{'v'});
        map.put(first, firstValue);
        require(map.get(equal) == null && !map.containsValue(equalValue));
        require(!map.keySet().remove(equal) && !map.values().remove(equalValue));
        require(!map.entrySet().contains(new AbstractMap.SimpleEntry<>(equal, firstValue)));
        require(!map.entrySet().remove(new AbstractMap.SimpleEntry<>(first, equalValue)));
        require(!map.remove(first, equalValue) && map.get(first) == firstValue);
        require(map.entrySet().remove(new AbstractMap.SimpleEntry<>(first, firstValue)) && map.isEmpty());
        map.put(first, firstValue);
        Iterator<Object> cursor = map.keySet().iterator();
        map.put(equal, equalValue);
        try { cursor.next(); throw new AssertionError(); } catch(ConcurrentModificationException expectedError) {}
        Collection<Object> view = map.values();
        map.clear();
        require(view.isEmpty());
        try { new IdentityHashMap<>(-1); throw new AssertionError(); } catch(IllegalArgumentException expectedError) {}
    }

    public static void main(String[] args) throws Exception {
        if(args.length != 0) {
            int size = Integer.parseInt(args[0]);
            IdentityHashMap<Object, Object> map = new IdentityHashMap<>();
            for(int i = 0; i < size; i++) { Object key = new Object(); map.put(key, key); }
            require(map.size() == size);
            System.out.println("allocated");
            return;
        }
        Thread collector = new Thread(() -> { while(!stop) { System.gc(); Thread.yield(); } });
        collector.start();
        try { exercise(); }
        finally { stop = true; collector.join(); }
        System.out.println("identity-storage-ok");
    }
}
