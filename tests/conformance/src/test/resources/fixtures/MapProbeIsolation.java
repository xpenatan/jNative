import java.util.HashMap;
import java.util.Map;

/** Independently selected probes; setup and expected hashes are outside timing. */
public class MapProbeIsolation {
    static volatile long sink;

    static final class Key {
        final int id, hash;
        Key(int id, int hash) { this.id = id; this.hash = hash; }
        public int hashCode() { return hash; }
        public boolean equals(Object other) { return other instanceof Key && ((Key) other).id == id; }
    }

    static final class Value {
        final int number;
        Value(int number) { this.number = number; }
    }

    static final class State {
        final Object[] keys, queries;
        final Key[] typed;
        final int[] expectedHashes;
        final Map<Object, Value> map;

        State(String operation, int size) {
            keys = new Object[size]; queries = new Object[size]; typed = new Key[size];
            expectedHashes = new int[size];
            boolean lookup = operation.startsWith("get-");
            boolean objects = operation.equals("hash-object") || operation.equals("hash-identity")
                    || operation.equals("get-object");
            boolean strings = operation.equals("hash-string") || operation.equals("get-string");
            int chain = operation.startsWith("get-collision8-") ? 8 : 4;
            boolean equal = operation.endsWith("-equal"), missing = operation.endsWith("-miss");
            map = lookup ? new HashMap<>(size * 2) : null;
            for (int i = 0; i < size; i++) {
                if (objects) keys[i] = new Object();
                else if (strings) keys[i] = "probe-key-" + i;
                else {
                    int hash = lookup ? i / chain : i * 0x9e3779b9;
                    typed[i] = new Key(i, hash);
                    keys[i] = typed[i];
                }
                expectedHashes[i] = keys[i].hashCode();
                queries[i] = equal || missing ? new Key(missing ? i + size : i, i / chain) : keys[i];
                if (lookup) map.put(keys[i], new Value(i + 1));
            }
        }
    }

    static long hashErased(Object[] keys, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; r++)
            for (int i = 0; i < keys.length; i++) sum += keys[i].hashCode();
        return sum;
    }

    static long hashTyped(Key[] keys, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; r++)
            for (int i = 0; i < keys.length; i++) sum += keys[i].hashCode();
        return sum;
    }

    // Identity hashes differ between processes/tools. Compare each against its
    // own setup value, yielding a deterministic checksum of hash stability.
    static long hashObject(Object[] keys, int[] expected, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; r++)
            for (int i = 0; i < keys.length; i++)
                sum += keys[i].hashCode() == expected[i] ? i + 1 : -1;
        return sum;
    }

    static long hashIdentity(Object[] keys, int[] expected, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; r++)
            for (int i = 0; i < keys.length; i++)
                sum += System.identityHashCode(keys[i]) == expected[i] ? i + 1 : -1;
        return sum;
    }

    static long lookup(Map<Object, Value> map, Object[] keys, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; r++)
            for (int i = 0; i < keys.length; i++) {
                Value value = map.get(keys[i]);
                if (value != null) sum += value.number;
            }
        return sum;
    }

    static long run(String operation, State state, int rounds) {
        switch (operation) {
            case "hash-erased": case "hash-string": return hashErased(state.keys, rounds);
            case "hash-typed": return hashTyped(state.typed, rounds);
            case "hash-object": return hashObject(state.keys, state.expectedHashes, rounds);
            case "hash-identity": return hashIdentity(state.keys, state.expectedHashes, rounds);
            case "get-object": case "get-string":
            case "get-collision4-same": case "get-collision4-equal": case "get-collision4-miss":
            case "get-collision8-same": case "get-collision8-equal": case "get-collision8-miss":
                return lookup(state.map, state.queries, rounds);
            default: throw new IllegalArgumentException(operation);
        }
    }

    static void sample(String operation, int size, int rounds, boolean report) {
        State state = new State(operation, size);
        long start = System.nanoTime();
        long sum = run(operation, state, rounds);
        long elapsed = System.nanoTime() - start;
        sink = sum;
        if (report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + sum);
    }

    public static void main(String[] args) {
        String operation = args[0];
        int size = Integer.parseInt(args[1]), rounds = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]);
        if (size < 1 || rounds < 1 || samples < 1) throw new IllegalArgumentException();
        for (int i = 0; i < 3; i++) sample(operation, size, rounds, false);
        for (int i = 0; i < samples; i++) sample(operation, size, rounds, true);
    }
}
