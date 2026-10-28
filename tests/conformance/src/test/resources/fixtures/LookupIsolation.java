import java.util.HashMap;
import java.util.Map;

/** Each mode times one operation; all keys, values and maps are built before timing. */
public class LookupIsolation {
    static volatile long sink;
    static final class Key {
        final int id;
        Key(int id) { this.id = id; }
        public int hashCode() { return id * 0x9e3779b9; }
        public boolean equals(Object other) { return other instanceof Key && ((Key) other).id == id; }
    }
    static final class Value {
        final int number;
        Value(int number) { this.number = number; }
    }
    static final class State {
        final Object[] keys, equal, missing;
        final Value[] values;
        final Map<Object, Value> map;
        State(int size) {
            keys = new Object[size]; equal = new Object[size]; missing = new Object[size];
            values = new Value[size]; map = new HashMap<>(size * 2);
            for (int i = 0; i < size; ++i) {
                keys[i] = new Key(i); equal[i] = new Key(i); missing[i] = new Key(i + size);
                values[i] = new Value(i + 1); map.put(keys[i], values[i]);
            }
        }
    }
    static long hash(Object[] keys, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; ++r)
            for (int i = 0; i < keys.length; ++i) sum += keys[i].hashCode();
        return sum;
    }
    static long get(Map<Object, Value> map, Object[] keys, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; ++r)
            for (int i = 0; i < keys.length; ++i) {
                Value value = map.get(keys[i]);
                if (value != null) sum += value.number;
            }
        return sum;
    }
    static long replace(State state, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; ++r)
            for (int i = 0; i < state.keys.length; ++i)
                sum += state.map.put(state.keys[i], state.values[i]).number;
        return sum;
    }
    static long scan(Value[] values, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; ++r)
            for (int i = 0; i < values.length; ++i) {
                Value value = values[i];
                if ((i & 1) == 0) sum += value.number;
                else sum -= value.number;
            }
        return sum;
    }
    static long run(String operation, State state, int rounds) {
        switch (operation) {
            case "hash": return hash(state.keys, rounds);
            case "get-same": return get(state.map, state.keys, rounds);
            case "get-equal": return get(state.map, state.equal, rounds);
            case "get-miss": return get(state.map, state.missing, rounds);
            case "put-replace": return replace(state, rounds);
            case "scan": return scan(state.values, rounds);
            default: throw new IllegalArgumentException(operation);
        }
    }
    static void sample(String operation, int size, int rounds, boolean report) {
        State state = new State(size);
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
        for (int i = 0; i < 3; ++i) sample(operation, size, rounds, false);
        for (int i = 0; i < samples; ++i) sample(operation, size, rounds, true);
    }
}
