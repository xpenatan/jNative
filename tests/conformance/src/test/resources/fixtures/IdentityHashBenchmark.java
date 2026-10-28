import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

public class IdentityHashBenchmark {
    static volatile long sink;
    static final class Key {
        public int hashCode() { return 7; }
    }
    static void require(boolean condition) {
        if (!condition) throw new AssertionError();
    }
    static void verify() {
        Object[] keys = new Object[257];
        int[] identities = new int[keys.length];
        Map<Object, Integer> identity = new IdentityHashMap<>();
        Map<Object, Integer> ordinary = new HashMap<>();
        require(System.identityHashCode(null) == 0);
        for (int i = 0; i < keys.length; i++) {
            keys[i] = i % 2 == 0 ? new Object() : new Key();
            identities[i] = System.identityHashCode(keys[i]);
            if (i % 2 == 0) require(keys[i].hashCode() == identities[i]);
            else require(keys[i].hashCode() == 7);
            identity.put(keys[i], i);
            ordinary.put(keys[i], i);
        }
        identity.put(null, -1);
        ordinary.put(null, -2);
        for (int round = 0; round < 3; round++) {
            System.gc();
            for (int i = 0; i < keys.length; i++) {
                require(System.identityHashCode(keys[i]) == identities[i]);
                require(identity.get(keys[i]) == i && ordinary.get(keys[i]) == i);
            }
        }
        require(identity.get(null) == -1 && ordinary.get(null) == -2);
        for (int i = 0; i < keys.length; i++) {
            require(identity.remove(keys[i]) == i && ordinary.remove(keys[i]) == i);
        }
        require(identity.size() == 1 && ordinary.size() == 1);
        System.out.println("identity-ok");
    }
    static void sample(String operation, int size, int rounds, boolean report) {
        Map<Object, Integer> map = operation.equals("identity-get") ? new IdentityHashMap<>() : new HashMap<>();
        Object[] keys = new Object[size];
        for (int i = 0; i < size; i++) {
            keys[i] = new Object();
            map.put(keys[i], i);
        }
        long start = System.nanoTime();
        long sum = 0;
        for (int r = 0; r < rounds; r++)
            for (int i = 0; i < size; i++) sum += map.get(keys[i]);
        long elapsed = System.nanoTime() - start;
        sink = sum;
        if (report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + sum);
    }
    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("verify")) { verify(); return; }
        String operation = args[0];
        int size = Integer.parseInt(args[1]), rounds = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]);
        for (int i = 0; i < 3; i++) sample(operation, size, rounds, false);
        for (int i = 0; i < samples; i++) sample(operation, size, rounds, true);
    }
}
