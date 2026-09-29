public class InitializedStaticEntries {
    static volatile boolean initializationStarted;
    static volatile boolean secondEntered;
    static volatile boolean allowFinish;
    static int firstResult;
    static int secondResult;

    static class Reentrant {
        static int value = initialize();
        static int read(int[] values) { return values[0]; }
        static int initialize() {
            int[] kept = {21};
            int result = read(kept);
            System.gc();
            return result + kept[0];
        }
    }
    static class Concurrent {
        static int value = initialize();
        static int read(int[] values) { return values[0]; }
        static int inside(int[] values) { return read(values); }
        static int initialize() {
            initializationStarted = true;
            while (!allowFinish) Thread.yield();
            System.gc();
            return 53;
        }
    }
    static class Failing {
        static int value = initialize();
        static int read(int[] values) { return values[0]; }
        static int inside(int[] values) { return read(values); }
        static int initialize() { System.gc(); throw new IllegalStateException("initialization"); }
    }
    static class CycleA {
        static int value = CycleB.entry(new int[] {5}) + 7;
        static int read(int[] values) { return values[0]; }
        static int entry(int[] values) { return read(values) + value; }
    }
    static class CycleB {
        static int value = initialize();
        static int read(int[] values) { return values[0]; }
        static int entry(int[] values) { return read(values) + value; }
        static int initialize() { System.gc(); return CycleA.entry(new int[] {5}) + 3; }
    }
    static class Parent {
        static int value = initialize();
        static int read(int[] values) { return values[0]; }
        static int inside(int[] values) { return Child.read(values); }
        static int initialize() { System.gc(); return 11; }
    }
    static class Child extends Parent {
        static int insideChild(int[] values) { return Child.read(values); }
    }

    static int divide(int value) { return 1 / value; }
    static int callDivide(int value) { return divide(value); }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            try { callDivide(0); }
            catch (ArithmeticException failure) { failure.printStackTrace(); }
            return;
        }
        System.out.println("reentrant " + Reentrant.value);
        System.out.println("cycles " + CycleA.entry(new int[] {2}) + ":" + CycleB.entry(new int[] {2}));
        System.out.println("inherited " + Parent.inside(new int[] {13}) + ":" + Child.insideChild(new int[] {17}));
        int[] survivor = {31};
        try { Failing.inside(survivor); }
        catch (ExceptionInInitializerError failure) { System.gc(); System.out.println("first failure " + survivor[0]); }
        try { Failing.inside(survivor); }
        catch (NoClassDefFoundError failure) { System.gc(); System.out.println("later failure " + survivor[0]); }

        Thread first = new Thread(() -> firstResult = Concurrent.inside(new int[] {37}));
        Thread second = new Thread(() -> {
            secondEntered = true;
            secondResult = Concurrent.inside(new int[] {41});
        });
        first.start();
        while (!initializationStarted) Thread.yield();
        second.start();
        while (!secondEntered) Thread.yield();
        for (int i = 0; i < 16; i++) System.gc();
        allowFinish = true;
        first.join(); second.join();
        System.out.println("concurrent " + firstResult + ":" + secondResult + ":" + Concurrent.value);
    }
}
