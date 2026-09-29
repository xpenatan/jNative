import java.util.Arrays;

public class FinalReceiverArrayViews {
    static final int[] TABLE = {2, 3, 5, 7};
    static volatile boolean finished;
    static volatile int ready;
    static volatile int changed;
    static volatile int construction;
    static volatile Unsafe leaked;
    static int effects;

    static class State {
        final int[] input;
        final int[][] rows;
        State(int[] input, int[][] rows) { this.input = input; this.rows = rows; }
    }
    static class ShortTable {
        static final int[] TABLE = {2};
        static void kernel(State s, int count, int index) {
            for (int i = 0; i < count; i++) {
                s.input[i]++;
                int[] row = s.rows[i];
                row[0] = TABLE[index & 3];
                row[1] = s.input[i];
            }
        }
    }
    static class Reentrant {
        final int[] input;
        Reentrant(int[] values) {
            System.out.println(reentrant(this, 0));
            try { reentrant(this, 1); }
            catch (NullPointerException expected) { System.out.println("constructor null"); }
            input = values;
        }
    }
    static class Unsafe {
        final int[] input;
        Unsafe(int[] input) {
            leaked = this;
            construction = 1;
            while (construction != 2) Thread.yield();
            this.input = input;
            construction = 3;
        }
    }
    static int unsafeRead(Unsafe s, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += s.input[i];
        return sum;
    }
    static int reentrant(Reentrant s, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += s.input[i];
        return sum;
    }
    static void kernel(State s, int count, int index) {
        for (int i = 0; i < count; i++) {
            int value = s.input[i] + 1;
            if (value > 5) value -= 3;
            s.input[i] = value;
            int[] row = s.rows[i];
            row[0] = TABLE[index & 3];
            row[1] = value;
        }
    }
    static int effectIndex() { effects++; return 0; }
    static void effectful(State s, int count) {
        for (int i = 0; i < count; i++) s.input[effectIndex()]++;
    }
    static void observe(State s, int count, int index) {
        try { kernel(s, count, index); System.out.println("ok"); }
        catch (NullPointerException expected) { System.out.println("null"); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
        if (s != null) {
            System.out.println(Arrays.toString(s.input));
            if (s.rows != null) for (int[] row : s.rows) System.out.println(Arrays.toString(row));
            else System.out.println("null rows");
        }
    }
    static int reassigned(State s, State other, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) { sum += s.input[i]; s = other; }
        return sum;
    }
    static int publication(State s) {
        int sum = 0;
        for (int i = 0; i < 1; i++) {
            sum += s.input[0] + s.rows[0][0];
            ready = 1;
            while (changed == 0) Thread.yield();
            sum += s.input[0] + s.rows[0][0];
        }
        return sum;
    }
    static void racing(State s, int count) {
        for (int i = 0; i < count; i++) {
            int[] row = s.rows[i & 3];
            row[0] = TABLE[0]; row[1] = TABLE[1];
        }
    }
    static void continuing(State s, int count) {
        for (int i = 0; i < count; i++) {
            if (s.input[i] == 0) continue;
            s.rows[i][0] = TABLE[i & 3];
            s.rows[i][1] = s.input[i];
        }
    }
    public static void main(String[] args) throws Exception {
        observe(null, 0, 0);
        observe(new State(null, null), 0, 0);
        observe(new State(null, null), -1, 0);
        observe(null, 1, 0);
        observe(new State(null, null), 1, 0);
        observe(new State(new int[0], null), 1, 0);
        observe(new State(new int[]{1, 2}, new int[][]{{0, 0}, {0, 0}}), 2, -1);
        observe(new State(new int[]{1}, new int[][]{{0, 0}, {0, 0}}), 2, 1);
        observe(new State(new int[]{1, 2}, new int[][]{{0, 0}}), 2, 1);
        observe(new State(new int[]{1, 2}, new int[][]{{0, 0}, null}), 2, 2);
        observe(new State(new int[]{1, 2}, new int[][]{{0, 0}, {}}), 2, 2);
        observe(new State(new int[]{1, 2}, new int[][]{{0, 0}, {0}}), 2, 2);
        ShortTable.kernel(null, 0, 3);
        State shortTable = new State(new int[]{1}, new int[][]{{0, 0}});
        try { ShortTable.kernel(shortTable, 1, 3); }
        catch (ArrayIndexOutOfBoundsException expected) {
            System.out.println("table bounds " + shortTable.input[0] + ":" + shortTable.rows[0][0]);
        }
        for (int size : new int[]{1, 63, 64, 65, 129, 257}) {
            int[] input = new int[size];
            int[][] rows = new int[size][2];
            kernel(new State(input, rows), size, 3);
            System.out.println(input[size - 1] + ":" + rows[size - 1][0] + ":" + rows[size - 1][1]);
        }
        try { effectful(null, 1); }
        catch (NullPointerException expected) { System.out.println("receiver effects " + effects); }
        try { effectful(new State(null, null), 1); }
        catch (NullPointerException expected) { System.out.println("array effects " + effects); }
        System.out.println(reentrant(new Reentrant(new int[]{11, 13}), 2));
        System.out.println(reassigned(new State(new int[]{1, 2}, null),
                new State(new int[]{10, 20}, null), 2));
        int[] continued = new int[65];
        continued[64] = 9;
        int[][] continuedRows = new int[65][2];
        continuing(new State(continued, continuedRows), 65);
        System.out.println(Arrays.toString(continuedRows[64]));
        Thread initializer = new Thread(() -> new Unsafe(new int[]{17}));
        initializer.start();
        while (construction != 1) Thread.yield();
        try { unsafeRead(leaked, 1); }
        catch (NullPointerException expected) { System.out.println("published constructor null"); }
        construction = 2;
        while (construction != 3) Thread.yield();
        System.out.println(unsafeRead(leaked, 1));
        initializer.join();
        State shared = new State(new int[]{1}, new int[][]{{2, 3}});
        Thread publisher = new Thread(() -> {
            while (ready == 0) Thread.yield();
            shared.input[0] = 10;
            shared.rows[0] = new int[]{20, 30};
            System.gc();
            changed = 1;
        });
        publisher.start();
        System.out.println(publication(shared));
        publisher.join();
        State racing = new State(new int[]{1, 2, 3, 4}, new int[][]{{2, 3}, {2, 3}, {2, 3}, {2, 3}});
        Thread collector = new Thread(() -> {
            int index = 0;
            while (!finished) {
                racing.rows[index++ & 3] = new int[]{2, 3};
                System.gc();
            }
        });
        collector.start();
        for (int i = 0; i < 40; i++) racing(racing, 4096);
        finished = true;
        collector.join();
        for (int[] row : racing.rows)
            if (row[0] != 2 || row[1] != 3) throw new IllegalStateException("stale row storage");
        System.out.println("collection ok");
    }
}
