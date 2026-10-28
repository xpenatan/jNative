import java.util.ArrayList;
import java.util.HashMap;

/** Reusable workloads: setup and output are outside each measured region. */
public class GeneralPerformanceBenchmark {
    static volatile Object escaped;
    static volatile long sink;

    interface Operation { int apply(int value); }
    static final class Add implements Operation {
        public int apply(int value) { return value + 17; }
    }
    static final class Multiply implements Operation {
        public int apply(int value) { return value * 3; }
    }

    static long run(String operation, int rounds, int size) {
        long sum = 0;
        if (operation.equals("reference-array")) {
            Object marker = new Object();
            for (int r = 0; r < rounds; r++) {
                Object[] array = new Object[size];
                if (size != 0) array[size - 1] = marker;
                escaped = array;
                sum += array.length;
                if (size != 0 && array[size - 1] == marker) sum++;
            }
        } else if (operation.equals("typed-array")) {
            for (int r = 0; r < rounds; r++) {
                String[] array = new String[size];
                if (size != 0) array[size - 1] = "marker";
                escaped = array;
                sum += array.length;
                if (size != 0) sum += array[size - 1].length();
            }
        } else if (operation.equals("list-grow")) {
            for (int r = 0; r < rounds; r++) {
                ArrayList<String> list = new ArrayList<>();
                for (int i = 0; i < size; i++) list.add("value");
                escaped = list;
                sum += list.size();
            }
        } else if (operation.equals("map-grow")) {
            for (int r = 0; r < rounds; r++) {
                HashMap<Integer, Integer> map = new HashMap<>();
                for (int i = 0; i < size; i++) map.put(i, i + 1);
                escaped = map;
                sum += map.size();
            }
        } else if (operation.equals("dispatch")) {
            Operation[] operations = {new Add(), new Multiply()};
            for (int r = 0; r < rounds; r++)
                for (int i = 0; i < size; i++) sum += operations[i & 1].apply(i);
        } else if (operation.equals("arithmetic")) {
            for (int r = 0; r < rounds; r++) {
                int value = r;
                for (int i = 0; i < size; i++) value = value * 1664525 + 1013904223;
                sum += value;
            }
        } else {
            throw new IllegalArgumentException(operation);
        }
        return sum;
    }

    static String text(int size, boolean repeated) {
        char[] value = new char[size];
        for (int i = 0; i < size; i++) value[i] = repeated ? 'a' : (char) ('a' + (i * 13 + i / 7) % 23);
        return new String(value);
    }

    static void sample(String operation, int size, int rounds, boolean report) {
        // Construct inputs before timing; hashing uses independent immutable strings.
        boolean search = operation.startsWith("search-");
        boolean hash = operation.equals("hash-cold");
        String haystack = search ? text(size, operation.equals("search-prefix")) + "z" : "";
        String needle = operation.equals("search-prefix") ? text(Math.max(1, size / 8), true) + "z" : "absent";
        String[] texts = hash ? new String[rounds] : null;
        if (hash) {
            char[] characters = text(size, false).toCharArray();
            for (int r = 0; r < rounds; r++) {
                if (size != 0) characters[size - 1] = (char) r;
                texts[r] = new String(characters);
            }
        }
        long start = System.nanoTime();
        long sum = 0;
        if (search) {
            for (int r = 0; r < rounds; r++) sum += haystack.indexOf(needle, r & 1);
        } else if (hash) {
            for (int r = 0; r < rounds; r++) sum += texts[r].hashCode();
        } else sum = run(operation, rounds, size);
        long elapsed = System.nanoTime() - start;
        sink = sum;
        if (report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + sum);
    }

    public static void main(String[] args) {
        String operation = args.length == 0 ? "reference-array" : args[0];
        int size = args.length < 2 ? 16 : Integer.parseInt(args[1]);
        int rounds = args.length < 3 ? 10000 : Integer.parseInt(args[2]);
        int samples = args.length < 4 ? 5 : Integer.parseInt(args[3]);
        for (int i = 0; i < 3; i++) sample(operation, size, rounds, false);
        for (int i = 0; i < samples; i++) sample(operation, size, rounds, true);
    }
}
