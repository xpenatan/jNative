public class CooperativeCallLoopPolling {
    static volatile boolean ready;
    static volatile boolean finished;
    static volatile int collections;
    static volatile int[] changing = {17};
    static int effects;

    static int cold(int[] live, int[][] rows, int[][] replacement, boolean collect) {
        if (collect) {
            int[] temporary = new int[20];
            temporary[0] = live[0];
            effects++;
            System.arraycopy(replacement, 0, rows, 0, 1);
            System.gc();
            if (temporary[0] != live[0]) throw new IllegalStateException("temporary root");
        }
        return live[0] + changing[0];
    }

    static int stable(int[] live, int[][] rows, int[][] replacement, int count, boolean collect) {
        int[] callerOnly = new int[] {23, 29};
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += live[i] + rows[i & 3][0];
            sum += cold(live, rows, replacement, collect);
            sum += rows[i & 3][1] + callerOnly[0];
        }
        return sum + callerOnly[1];
    }

    static void observe(int[] live, int[][] rows, int[][] replacement, int count, boolean collect) {
        int[] survivor = {31};
        effects = 0;
        try { System.out.println("ok " + stable(live, rows, replacement, count, collect) + ":" + effects); }
        catch (NullPointerException failure) { System.gc(); System.out.println("null " + effects + ":" + survivor[0]); }
        catch (ArrayIndexOutOfBoundsException failure) { System.gc(); System.out.println("bounds " + effects + ":" + survivor[0]); }
    }

    public static void main(String[] args) throws Exception {
        for (int count : new int[] {0, 1, 63, 64, 65, 129}) {
            for (boolean collect : new boolean[] {false, true}) {
                int[] live = new int[Math.max(1, count)];
                live[0] = 7;
                observe(live, new int[][] {{2, 3}, {2, 3}, {2, 3}, {2, 3}},
                        new int[][] {{11, 13}}, count, collect);
            }
        }
        observe(null, null, null, 0, true);
        observe(null, new int[][] {{2, 3}}, new int[][] {{11, 13}}, 1, true);
        observe(new int[] {7}, new int[][] {{2, 3}, {2, 3}, {2, 3}, {2, 3}}, new int[][] {{11, 13}}, 2, true);
        observe(new int[] {7}, new int[][] {{2, 3}, {2, 3}, {2, 3}, {2, 3}}, new int[][] {null}, 1, true);
        observe(new int[] {7}, new int[][] {{2, 3}, {2, 3}, {2, 3}, {2, 3}}, new int[][] {new int[0]}, 1, true);
        observe(new int[] {7}, new int[][] {{2, 3}, {2, 3}, {2, 3}, {2, 3}}, new int[][] {{11}}, 1, true);
        observe(new int[] {7}, new int[][] {{2, 3}, {2, 3}, {2, 3}, {2, 3}}, null, 1, true);

        int[] live = new int[4096];
        int[][] rows = {{2, 3}, {2, 3}, {2, 3}, {2, 3}};
        int[][] replacement = {{2, 3}};
        Thread collector = new Thread(() -> {
            while (!ready) Thread.yield();
            while (!finished) {
                changing = new int[] {17};
                System.arraycopy(replacement, 0, rows, 0, 1);
                System.gc();
                collections++;
            }
        });
        collector.start();
        int expected = live.length * 45 + 29;
        try {
            ready = true;
            do {
                if (stable(live, rows, replacement, live.length, false) != expected)
                    throw new IllegalStateException("caller or row root");
            } while (collections == 0);
        } finally {
            finished = true;
            collector.join();
        }
        System.out.println("concurrent " + stable(live, rows, replacement, live.length, false));
    }
}
