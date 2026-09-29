public class LastUseArgumentRoots {
    static int total;
    static int[] holder;
    interface Sink { void accept(int[] a, boolean cold); }
    static class First implements Sink {
        public void accept(int[] a, boolean cold) { if (cold) System.gc(); total += a[0]; }
    }
    static class Second implements Sink {
        public void accept(int[] a, boolean cold) { System.gc(); total += a[0]; }
    }
    static class FirstUse {
        static int value = initialize();
        static int initialize() {
            System.gc();
            accept(new int[] {13}, true);
            return 17;
        }
        static void accept(int[] a, boolean collect) {
            if (collect) System.gc();
            value += a[0];
        }
    }
    static class FailingInitialization {
        static int value = initialize();
        static int initialize() { System.gc(); throw new IllegalStateException("init"); }
        static void accept(int[] a, boolean collect) {
            if (collect) System.gc();
            value += a[0];
        }
    }
    static class Collector extends Thread {
        public void run() { for (int i = 0; i < 128; i++) System.gc(); }
    }
    static class Parent {
        static int counter;
        static void accept(int[] a, boolean collect) { if (collect) System.gc(); counter += a[0]; }
        static void inside(int[] a, boolean collect) { Child.accept(a, collect); counter++; }
    }
    static class Child extends Parent {
        static void insideChild(int[] a, boolean collect) { Child.accept(a, collect); }
    }
    static void cold(int[] a, boolean collect) {
        if (collect) { holder = null; System.gc(); }
        total += a[0];
    }
    static void coldTwo(int[] a, int[] b, boolean collect) {
        if (collect) { holder = null; System.gc(); }
        total += a[0] + b[0];
    }
    static void coldScalar(int[] a, int value, boolean collect) {
        if (collect) System.gc();
        total += a[0] + value;
    }
    static void reassigned(int[] a, boolean collect) {
        if (collect) { a = new int[] {47}; System.gc(); }
        total += a[0];
    }
    static void recursive(int[] a, boolean collect, int depth) {
        if (collect) { if (depth > 0) recursive(a, collect, depth - 1); System.gc(); }
        total += a[0];
    }
    static void reassignCaller(boolean collect) { reassigned(new int[] {7}, collect); total++; }
    static void recurseCaller(boolean collect) { recursive(new int[] {7}, collect, 2); total++; }
    static void inheritedCaller(boolean collect) {
        Child.accept(new int[] {53}, collect);
        Parent.inside(new int[] {59}, collect);
        Child.insideChild(new int[] {61}, collect);
        total += Parent.counter;
    }
    static void detachedRow(int[] a, int[][] rows, int index, boolean collect) {
        if (collect) { rows[index] = null; System.gc(); }
        total += a[0];
    }
    static void failure(int[] a, boolean collect) {
        if (collect) { System.gc(); total += a[9]; }
        total += a[0];
    }
    static void delegated(int[] a, boolean collect) { cold(a, collect); }
    static int[] fresh() { System.gc(); return new int[] {11}; }
    static int scalarCollector() { System.gc(); return 3; }
    static void positive(boolean collect) { cold(new int[] {7}, collect); total++; }
    static void firstUseCall(boolean collect) { FirstUse.accept(new int[] {5}, collect); total += FirstUse.value; }
    static void failingInitializationCall() { FailingInitialization.accept(new int[] {7}, true); total++; }
    static void holderCall(boolean collect) {
        holder = new int[] {37}; cold(holder, collect); total++;
    }
    static void rowCalls(int[][] rows, boolean collect) {
        for (int i = 0; i < rows.length; i++) cold(rows[i], collect);
        total += rows.length;
    }
    static void rejectedRow(int[][] rows, boolean collect) { detachedRow(rows[0], rows, 0, collect); total++; }
    static void laterArgument(boolean collect) { coldTwo(new int[] {7}, fresh(), collect); total++; }
    static void inlineScalar(boolean collect) { coldScalar(new int[] {7}, scalarCollector(), collect); total++; }
    static void liveAfter(boolean collect) {
        int[] a = new int[] {7}; cold(a, collect); total += a[0];
    }
    static void liveAlias(boolean collect) {
        Object alias;
        cold((int[]) (alias = new int[] {7}), collect); total += ((int[]) alias)[0];
    }
    static void aliases(boolean collect) {
        int[] a;
        coldTwo(a = new int[] {19}, a, collect); total++;
    }
    static void caught(boolean collect) {
        int[] survivor = new int[] {23};
        try { failure(new int[] {7}, collect); }
        catch (ArrayIndexOutOfBoundsException expected) { System.gc(); total += survivor[0]; }
    }
    static synchronized void monitored(boolean collect) { cold(new int[] {7}, collect); total++; }
    static void wrapper(boolean collect) { delegated(new int[] {7}, collect); total++; }
    // The test replaces this body with an equivalent irreducible bytecode loop.
    static void fallback(int[] a, int count) {
        while (count > 0) {
            if ((count & 1) == 0) { System.gc(); total += a[0]; count--; }
            count--;
        }
    }
    static void fallbackCall() { fallback(new int[] {43}, 4); total++; }
    static void mixedDispatch(Sink sink, boolean collect) { sink.accept(new int[] {7}, collect); total++; }
    static void nativeCall(int count) {
        Object[] destination = new Object[1];
        System.arraycopy(new Object[] {new int[] {31}}, 0, destination, 0, count);
        System.gc();
        total += ((int[]) destination[0])[0];
    }
    public static void main(String[] args) throws Exception {
        firstUseCall(false);
        firstUseCall(true);
        try { failingInitializationCall(); }
        catch (ExceptionInInitializerError expected) { System.gc(); total += 29; }
        Collector collector = new Collector();
        collector.start();
        for (int round = 0; round < 16; round++) {
            for (boolean collect : new boolean[] {false, true}) {
                positive(collect);
                reassignCaller(collect); recurseCaller(collect); inheritedCaller(collect);
                holderCall(collect);
                rowCalls(new int[][] {new int[] {7}, new int[] {11}}, collect);
                rejectedRow(new int[][] {new int[] {41}}, collect);
                laterArgument(collect); inlineScalar(collect); liveAfter(collect); liveAlias(collect);
                aliases(collect); caught(collect); monitored(collect); wrapper(collect);
                fallbackCall();
                mixedDispatch(new First(), collect); mixedDispatch(new Second(), collect);
                nativeCall(1);
            }
        }
        collector.join();
        System.out.println(total);
    }
}
