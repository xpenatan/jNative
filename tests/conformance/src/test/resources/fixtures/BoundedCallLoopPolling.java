public class BoundedCallLoopPolling {
    static volatile int[] changing = {3};
    static volatile boolean started;
    static volatile boolean finished;
    static volatile boolean workloadReady;
    static volatile int collections;
    static volatile int phase;
    static int effects;

    interface Step { int apply(int value); }
    static class First implements Step { public int apply(int value) { return value + 1; } }
    static class Second implements Step { public int apply(int value) { return value - 1; } }
    static class FinalStep { final int apply(int value) { return value * 2; } }
    static class Holder {
        final int[] fixed;
        final int[][] rows;
        int[] mutable;
        Holder(int[] input, int[][] rows) { fixed = input; mutable = input; this.rows = rows; }
    }
    static class Foreign {
        static int value = initialize();
        static int initialize() { System.gc(); System.out.println("foreign initialized"); return 11; }
        static int apply(int input) { return value + input; }
    }
    static class Reentrant {
        static int value = initialize();
        static int apply(int input) { return input + 1; }
        static int loop(int count) {
            int sum = 0;
            for (int i = 0; i < count; i++) sum += apply(i);
            return sum;
        }
        static int initialize() { return loop(257); }
    }

    static int apply(int value) { return value + 1; }
    static int staticLoop(int[] input, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += input[i] + apply(i);
        return sum;
    }
    static int finalLoop(FinalStep step, int[] input, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += input[i] + step.apply(i);
        return sum;
    }
    static int virtualLoop(Step step, int[] input, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += input[i] + step.apply(i);
        return sum;
    }
    static int foreignLoop(int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += Foreign.apply(i);
        return sum;
    }
    static int effectIndex() { effects++; return 0; }
    static void mark() { effects++; }
    static int index(int value) { effects++; return value; }
    static void mutate(int[] input, int index) { input[index]++; }
    static void replace(Holder holder, int[] input) { effects++; holder.mutable = input; }
    static void copyRow(int[][] source, int[][] target) { System.arraycopy(source, 0, target, 0, 1); }
    static int aliasMutation(int[] input, int[] alias, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) { mutate(alias, i); sum += input[i]; }
        return sum;
    }
    static int finalMutation(Holder holder, int[] alias, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) { mutate(alias, i); sum += holder.fixed[i]; }
        return sum;
    }
    static int mutableReplacement(Holder holder, int[] replacement, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += holder.mutable[i];
            replace(holder, replacement);
            sum += holder.mutable[i];
        }
        return sum;
    }
    static int rowReplacement(Holder holder, int[][] replacement, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += holder.rows[i][0];
            copyRow(replacement, holder.rows);
            mark();
            sum += holder.rows[i][1];
        }
        return sum;
    }
    static int rowRead(Holder holder, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++)
            sum += holder.rows[i & 3][0] + holder.rows[i & 3][1] + apply(i);
        return sum;
    }
    static int unknownIndex(int[] input, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += input[index(i)] + apply(i);
        return sum;
    }
    static int maskedIndex(int[] input, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += input[index(i) & 3] + apply(i);
        return sum;
    }
    static int changedLimit(int[] input, int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) { sum += input[i] + apply(i); count--; }
        return sum;
    }
    static void guardEffects(int[] input, int[] output, int count) {
        for (int i = 0; i < count; i++) { mark(); output[i] = input[i] + apply(i); }
    }
    static void effectLoop(int[] input, int count) {
        for (int i = 0; i < count; i++) input[effectIndex()]++;
    }
    static int divide(int value) { return 120 / value; }
    static int dividingLoop(int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) sum += divide(2 - i);
        return sum;
    }
    static int volatileLoop(int[] survivor) {
        int value = 0;
        started = true;
        while (phase == 0) value ^= survivor[0] + apply(changing[0]);
        return survivor[0] + changing[0] + (value & 0);
    }

    static void observeRows(int[][] replacement) {
        Holder holder = new Holder(new int[1], new int[][]{{2, 3}});
        effects = 0;
        try { System.out.println("row result " + rowReplacement(holder, replacement, 1)); }
        catch (NullPointerException expected) { System.out.println("row null " + effects); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("row bounds " + effects); }
        int[] row = holder.rows[0];
        System.out.println(row == null ? "null row" : "row length " + row.length + ":" + (row.length == 0 ? 0 : row[0]));
    }

    static void observeGuardEffects(int[] input, int[] output, int count) {
        effects = 0;
        try { guardEffects(input, output, count); System.out.println("guard ok " + effects); }
        catch (NullPointerException expected) { System.out.println("guard null " + effects); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("guard bounds " + effects); }
        if (output != null && output.length > 0) System.out.println("partial " + output[0]);
    }

    public static void main(String[] args) throws Exception {
        Step first = new First();
        Step second = new Second();
        FinalStep finalStep = new FinalStep();
        for (int count : new int[]{0, 1, 63, 64, 65, 129, 257}) {
            int[] input = new int[count];
            System.out.println(staticLoop(input, count) + ":" + finalLoop(finalStep, input, count)
                    + ":" + virtualLoop(first, input, count) + ":" + virtualLoop(second, input, count));
        }
        System.out.println(foreignLoop(0));
        System.out.println(foreignLoop(3));
        System.out.println(Reentrant.value);
        String survivor = new String("exception survivor");
        try { dividingLoop(3); }
        catch (ArithmeticException expected) { System.gc(); System.out.println(survivor); }
        try { virtualLoop(null, new int[]{2}, 1); }
        catch (NullPointerException expected) { System.out.println("receiver null"); }
        try { finalLoop(null, null, 1); }
        catch (NullPointerException expected) { System.out.println("array before receiver"); }
        try { staticLoop(new int[]{2}, 2); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("array bounds"); }
        try { effectLoop(null, 1); }
        catch (NullPointerException expected) { System.out.println("effects " + effects); }
        for (int count : new int[]{0, 1, 63, 64, 65, 129}) {
            int[] input = new int[count];
            Holder holder = new Holder(input, null);
            System.out.println("alias " + aliasMutation(input, input, count) + ":"
                    + finalMutation(holder, input, count));
        }
        int[] shortAlias = {0};
        try { aliasMutation(new int[2], shortAlias, 2); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("callee bounds " + shortAlias[0]); }
        Holder mutable = new Holder(new int[]{2, 3}, null);
        System.out.println("mutable " + mutableReplacement(mutable, new int[]{11, 13}, 2));
        effects = 0;
        try { mutableReplacement(mutable, null, 1); }
        catch (NullPointerException expected) { System.out.println("mutable null " + effects + ":" + (mutable.mutable == null)); }
        mutable.mutable = new int[]{7};
        effects = 0;
        try { mutableReplacement(mutable, new int[0], 1); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("mutable bounds " + effects + ":" + mutable.mutable.length); }
        observeRows(new int[][]{{11, 13}});
        observeRows(new int[][]{null});
        observeRows(new int[][]{new int[0]});
        observeRows(new int[][]{{11}});
        observeRows(new int[0][]);
        observeGuardEffects(null, new int[1], 1);
        observeGuardEffects(new int[]{5}, null, 1);
        observeGuardEffects(new int[]{5, 7}, new int[1], 2);
        observeGuardEffects(new int[]{5}, new int[2], 2);
        observeGuardEffects(null, null, 0);
        effects = 0;
        System.out.println("unknown " + unknownIndex(new int[]{2, 3}, 2) + ":" + effects);
        effects = 0;
        try { unknownIndex(new int[]{2}, 2); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("unknown bounds " + effects); }
        effects = 0;
        System.out.println("masked " + maskedIndex(new int[]{2, 3, 5, 7}, 65) + ":" + effects);
        effects = 0;
        try { maskedIndex(new int[]{2}, 2); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("masked bounds " + effects); }
        System.out.println("changed limit " + changedLimit(new int[]{2, 3, 5}, 3));
        Thread publisher = new Thread(() -> {
            while (!started) Thread.yield();
            for (int i = 0; i < 20; i++) System.gc();
            changing = new int[]{17};
            phase = 1;
        });
        publisher.start();
        System.out.println(volatileLoop(new int[]{23}));
        publisher.join();
        Holder workloadRows = new Holder(new int[1], new int[][]{{2, 3}, {2, 3}, {2, 3}, {2, 3}});
        int[][] replacementRows = {{2, 3}};
        Thread collector = new Thread(() -> {
            while (!workloadReady) Thread.yield();
            while (!finished) {
                changing = new int[]{17};
                copyRow(replacementRows, workloadRows.rows);
                System.gc();
                collections++;
            }
        });
        collector.start();
        int[] input = new int[4096];
        int expected = 6 * (input.length * (input.length - 1) / 2) + 7 * input.length;
        try {
            workloadReady = true;
            do {
                int sum = staticLoop(input, input.length);
                sum += finalLoop(finalStep, input, input.length);
                sum += virtualLoop(first, input, input.length);
                sum += virtualLoop(second, input, input.length);
                sum += rowRead(workloadRows, input.length);
                if (sum != expected) throw new IllegalStateException("call checksum");
            } while (collections == 0);
            if (collections == 0) throw new IllegalStateException("no collection during workload");
        } finally {
            finished = true;
            collector.join();
        }
        // The number of batches depends on thread scheduling; observe deterministic
        // call results after collection as well as keeping all receiver roots live.
        System.out.println("collection " + staticLoop(input, input.length) + ":"
                + finalLoop(finalStep, input, input.length) + ":"
                + virtualLoop(first, input, input.length) + ":"
                + virtualLoop(second, input, input.length) + ":" + rowRead(workloadRows, input.length));
    }
}
