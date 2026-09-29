public class StaticTableArrayViews {
    static final int[] TABLE = {2, 3};
    static int effects;
    static volatile int ready;
    static volatile int complete;

    static int index() { effects++; System.gc(); return -1; }
    static int changing(int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += TABLE[i & 1];
            mutate(i & 1);
            sum += TABLE[(i + 1) & 1];
        }
        return sum;
    }
    static void mutate(int index) { TABLE[index]++; System.gc(); }
    static int published(int count) {
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += TABLE[0];
            ready = 1;
            while (complete == 0) Thread.yield();
            sum += TABLE[0];
        }
        return sum;
    }

    static class NullTable {
        static final int[] TABLE = null;
        static int read(int count) {
            int sum = 0;
            for (int i = 0; i < count; i++) sum += TABLE[index()];
            return sum;
        }
    }
    static class EmptyTable {
        static final int[] TABLE = new int[0];
        static int read(int count) {
            int sum = 0;
            for (int i = 0; i < count; i++) sum += TABLE[index()];
            return sum;
        }
    }
    static class Reentrant {
        static final int[] TABLE;
        static {
            System.out.println(read(0, 0));
            try { read(1, -1); }
            catch (NullPointerException expected) { System.out.println("reentrant null"); }
            TABLE = new int[]{7, 11};
            System.out.println(read(2, 1));
            TABLE[1] = 17;
            System.gc();
            System.out.println(read(2, 1));
        }
        static int read(int count, int offset) {
            int sum = 0;
            for (int i = 0; i < count; i++) { System.gc(); sum += TABLE[offset]; }
            return sum;
        }
    }
    static class Failed {
        static final int[] TABLE;
        static {
            System.out.println(read(0, 0));
            TABLE = new int[]{19};
            read(1, 99);
        }
        static int read(int count, int offset) {
            int sum = 0;
            for (int i = 0; i < count; i++) sum += TABLE[offset];
            return sum;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(NullTable.read(0));
        System.out.println(EmptyTable.read(0));
        System.out.println(effects);
        try { NullTable.read(1); }
        catch (NullPointerException expected) { System.out.println("null " + effects); }
        try { EmptyTable.read(1); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds " + effects); }
        System.out.println(Reentrant.read(2, 0));
        try { Reentrant.read(1, -1); }
        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("reentrant bounds"); }
        for (int i = 0; i < 2; i++) {
            try { Failed.read(0, 0); }
            catch (ExceptionInInitializerError expected) { System.out.println("initialization failed"); }
            catch (NoClassDefFoundError expected) { System.out.println("failed class"); }
        }
        System.out.println(changing(5));
        Thread worker = new Thread(() -> {
            while (ready == 0) Thread.yield();
            TABLE[0] = 101;
            System.gc();
            complete = 1;
        });
        worker.start();
        System.out.println(published(1));
        worker.join();
        System.out.println(TABLE[0] + TABLE[1]);
    }
}
