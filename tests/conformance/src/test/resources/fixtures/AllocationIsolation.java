/** Allocation size and lifetime probes. Setup and final verification are untimed. */
public class AllocationIsolation {
    static volatile Object escaped;
    static volatile long sink;

    static final class Cell {
        final int value;
        Cell next;
        Cell(int value) { this.value = value; }
    }

    static void sample(String operation, int size, int rounds, boolean report) {
        Cell[] ring = operation.equals("shell-ring") ? new Cell[size] : null;
        Cell graph = null;
        if (operation.equals("retained-graph")) {
            for (int i = 0; i < size; i++) {
                Cell cell = new Cell(i);
                cell.next = graph;
                graph = cell;
            }
        }
        Object marker = new Object();
        long sum = 0;
        long start = System.nanoTime();
        if (operation.equals("shell-drop") || operation.equals("retained-graph")) {
            for (int r = 0; r < rounds; r++) {
                Cell cell = new Cell(r);
                escaped = cell;
                sum += cell.value;
            }
        } else if (operation.equals("shell-ring")) {
            int slot = 0;
            for (int r = 0; r < rounds; r++) {
                Cell cell = new Cell(r);
                ring[slot] = cell;
                if (++slot == size) slot = 0;
                escaped = cell;
                sum += cell.value;
            }
        } else if (operation.equals("reference-array")) {
            for (int r = 0; r < rounds; r++) {
                Object[] array = new Object[size];
                sum += array[0] == null ? 1 : 0;
                array[size - 1] = marker;
                escaped = array;
                sum += array.length + (array[size - 1] == marker ? 1 : 0);
            }
        } else if (operation.equals("typed-array")) {
            for (int r = 0; r < rounds; r++) {
                Cell[] array = new Cell[size];
                sum += array[0] == null ? 1 : 0;
                escaped = array;
                sum += array.length;
            }
        } else if (operation.equals("primitive-array")) {
            for (int r = 0; r < rounds; r++) {
                int[] array = new int[size];
                sum += array[0];
                array[size - 1] = r;
                escaped = array;
                sum += array.length + array[size - 1];
            }
        } else throw new IllegalArgumentException(operation);
        long elapsed = System.nanoTime() - start;
        if (operation.equals("shell-ring")) {
            for (int i = 0; i < Math.min(size, rounds); i++) {
                int expected = i + (rounds - 1 - i) / size * size;
                if (ring[i].value != expected) throw new IllegalStateException("Lost retained cell");
                sum += ring[i].value;
            }
        }
        if (operation.equals("retained-graph")) {
            for (int i = size - 1; i >= 0; i--) {
                if (graph == null || graph.value != i) throw new IllegalStateException("Lost retained graph");
                sum += graph.value;
                graph = graph.next;
            }
            if (graph != null) throw new IllegalStateException("Unexpected graph tail");
        }
        sink = sum;
        escaped = null;
        if (report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + sum);
    }

    public static void main(String[] args) {
        String operation = args[0];
        int size = Integer.parseInt(args[1]);
        int rounds = Integer.parseInt(args[2]);
        int samples = Integer.parseInt(args[3]);
        if (size <= 0 || rounds <= 0 || samples <= 0) throw new IllegalArgumentException("Positive sizes required");
        for (int i = 0; i < 3; i++) sample(operation, size, rounds, false);
        for (int i = 0; i < samples; i++) sample(operation, size, rounds, true);
    }
}
