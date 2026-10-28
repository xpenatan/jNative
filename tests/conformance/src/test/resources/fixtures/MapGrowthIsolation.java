import java.util.HashMap;

/** Separates boxing and resizing from insertion; setup and final checks are untimed. */
public class MapGrowthIsolation {
    static volatile Object escaped;
    static volatile long sink;

    static void sample(String operation, int size, int rounds, boolean report) {
        boolean boxed = operation.startsWith("boxed-");
        boolean presized = operation.endsWith("presized");
        if (!operation.equals("boxed-grow") && !operation.equals("boxed-presized")
                && !operation.equals("preboxed-grow") && !operation.equals("preboxed-presized"))
            throw new IllegalArgumentException(operation);
        Integer[] keys = new Integer[size], values = new Integer[size];
        for (int i = 0; i < size; i++) { keys[i] = i; values[i] = i + 1; }
        int capacity = presized ? (size * 4 + 2) / 3 : 16;
        HashMap<Integer, Integer> last = null;
        long sum = 0;
        long start = System.nanoTime();
        for (int r = 0; r < rounds; r++) {
            HashMap<Integer, Integer> map = new HashMap<>(capacity);
            if (boxed) {
                for (int i = 0; i < size; i++) map.put(i, i + 1);
            } else {
                for (int i = 0; i < size; i++) map.put(keys[i], values[i]);
            }
            escaped = map;
            last = map;
            sum += map.size();
        }
        long elapsed = System.nanoTime() - start;
        for (int i = 0; i < size; i++) {
            Integer value = last.get(keys[i]);
            if (value == null || value != i + 1) throw new IllegalStateException("Lost entry");
            sum += value;
        }
        sink = sum;
        escaped = null;
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
