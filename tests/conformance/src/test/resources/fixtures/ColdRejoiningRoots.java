public class ColdRejoiningRoots {
    int[] field;
    static class Late {
        static int value = initialize();
        static int initialize() { System.gc(); return 9; }
    }
    static class FirstEntry {
        static int number = initialize();
        static int initialize() { System.gc(); return 17; }
        static int cold(int[] kept, boolean cold) {
            if (cold) System.gc();
            return kept[0] + number;
        }
    }
    static class Collector extends Thread {
        public void run() { for (int i = 0; i < 32; i++) System.gc(); }
    }

    static int rejoin(int[] first, int[] second, boolean cold) {
        if (cold) {
            int[] temporary = new int[] { first[0] };
            System.gc();
            second[0] = temporary[0];
        }
        second[1] = first[0] + second[0];
        return second[1];
    }
    static int[] choose(int[] first, int mode) {
        int[] selected = first;
        if (mode < 0) {
            selected = new int[] { Late.value };
            if (mode == -1) {
                int[] nested = new int[] { first[0] };
                System.gc();
                selected[0] += nested[0];
            }
            System.gc();
        } else if (mode == 0) {
            selected = new int[] { first[0] + 1 };
            System.gc();
        }
        return selected;
    }
    static int multiple(int[] kept, boolean first, boolean second) {
        int[] selected = kept;
        if (first) {
            selected = new int[] { kept[0] + 1 };
            System.gc();
        }
        if (second) {
            int[] temporary = new int[] { selected[0] + 2 };
            System.gc();
            selected = temporary;
        }
        return selected[0] + kept[0];
    }
    static int failing(int[] kept, boolean cold) {
        if (cold) {
            int[] temporary = new int[] { kept[0] };
            System.gc();
            return temporary[2];
        }
        return kept[0];
    }
    static int recursive(int[] kept, boolean cold, int depth) {
        if (cold) {
            if (depth > 0) return recursive(kept, cold, depth - 1);
            System.gc();
        }
        return kept[0];
    }
    static int consumedBeforeCollection(ColdRejoiningRoots owner, boolean cold) {
        int[] early = owner.field;
        owner.field = null;
        if (cold) {
            int number = early[0];
            System.gc();
            return number;
        }
        return 0;
    }
    static int caught(int[] kept, boolean cold) {
        try { if (cold) { System.gc(); throw new IllegalStateException(); } }
        catch (IllegalStateException expected) { System.gc(); }
        return kept[0];
    }
    static int locked(int[] kept, boolean cold) {
        synchronized (kept) { if (cold) System.gc(); }
        return kept[0];
    }

    public static void main(String[] args) throws Exception {
        System.out.println(FirstEntry.cold(new int[] { 5 }, false));
        System.out.println(FirstEntry.cold(new int[] { 6 }, true));
        Collector collector = new Collector();
        collector.start();
        int total = 0;
        for (int round = 0; round < 8; round++) {
            for (int mode = -2; mode <= 1; mode++) {
                int[] kept = new int[] { 7 + round };
                int[] output = new int[] { 3, 0 };
                total += rejoin(kept, output, mode <= 0);
                total += choose(kept, mode)[0];
                total += multiple(kept, mode < 0, mode == -1 || mode == 0);
                total += recursive(kept, mode <= 0, 12);
                ColdRejoiningRoots owner = new ColdRejoiningRoots();
                owner.field = new int[] { round + 11 };
                total += consumedBeforeCollection(owner, mode <= 0);
                total += caught(kept, mode <= 0) + locked(kept, mode <= 0);
                try { total += failing(kept, mode <= 0); }
                catch (ArrayIndexOutOfBoundsException expected) {
                    System.gc(); total += kept[0];
                }
            }
        }
        collector.join();
        System.out.println(total);
    }
}
