import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Dense and sparse iteration, with construction and final verification outside timing. */
public class MapIterationIsolation {
    static volatile long sink;

    static void sample(String operation, int size, int rounds, boolean report) {
        boolean linked = operation.equals("linked-sparse");
        if (!linked && !operation.equals("hash-dense") && !operation.equals("hash-sparse"))
            throw new IllegalArgumentException(operation);
        int capacity = size * (operation.equals("hash-dense") ? 2 : 64);
        Map<Integer, Integer> map = linked ? new LinkedHashMap<>(capacity) : new HashMap<>(capacity);
        for (int i = 0; i < size; i++) map.put(i * 31, i + 1);
        long sum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < rounds; r++)
            for (Map.Entry<Integer, Integer> entry : map.entrySet())
                sum += entry.getKey() + entry.getValue();
        long elapsed = System.nanoTime() - start;
        long expected = ((long) size * (size - 1) * 16 + size) * rounds;
        if (sum != expected || map.size() != size) throw new IllegalStateException("Iteration mismatch");
        for (int i = 0; i < size; i++)
            if (map.get(i * 31) != i + 1) throw new IllegalStateException("Lost entry");
        sink = sum;
        if (report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + sum);
    }

    public static void main(String[] args) {
        String operation = args[0];
        int size = Integer.parseInt(args[1]), rounds = Integer.parseInt(args[2]);
        int samples = args.length > 3 ? Integer.parseInt(args[3]) : 5;
        for (int i = 0; i < 3; i++) sample(operation, size, rounds, false);
        for (int i = 0; i < samples; i++) sample(operation, size, rounds, true);
    }
}
