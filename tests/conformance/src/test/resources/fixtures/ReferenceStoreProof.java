public class ReferenceStoreProof {
    Object[] exact = new Object[4];
    Object[] changed = new Object[4];
    Object[] covariant = new String[4];

    void put(Object value, int index, boolean grow) {
        if (grow) {
            exact = new Object[8];
            System.gc();
        }
        exact[index] = value;
    }
    void clear(Object[] values, int index) { values[index] = null; }
    void typed(Object value, int index) { covariant[index] = value; }
    void replace(Object[] values) { changed = values; }
    void unknown(Object value) { changed[0] = value; }
    static void incoming(Object[] values, Object value) { values[0] = value; }
    static Object[] fresh(Object value, boolean first) {
        Object[] values = first ? new Object[2] : new Object[3];
        Object[] alias = values;
        alias[0] = value;
        System.gc();
        return values;
    }
    static int failures;
    static class Collector extends Thread {
        public void run() { for (int i = 0; i < 50; i++) System.gc(); }
    }
    static void fail(ReferenceStoreProof holder, int operation) {
        try {
            switch (operation) {
                case 0: holder.unknown(new Object()); break;
                case 1: incoming(new String[1], new Object()); break;
                case 2: holder.typed(new Object(), 0); break;
                case 3: holder.typed(new Object(), -1); break;
                case 4: holder.clear(new String[1], 1); break;
                default: holder.clear(null, -1);
            }
            throw new AssertionError();
        }
        catch (RuntimeException error) {
            boolean correct = operation <= 2 ? error instanceof ArrayStoreException
                    : operation <= 4 ? error instanceof ArrayIndexOutOfBoundsException
                    : error instanceof NullPointerException;
            if (!correct) throw new AssertionError();
            failures++;
            System.gc();
        }
    }
    public static void main(String[] args) throws Exception {
        ReferenceStoreProof holder = new ReferenceStoreProof();
        Thread collector = new Collector();
        collector.start();
        for (int i = 0; i < 30; i++) {
            holder.put(new String(new char[] {'o', 'k'}), i & 3, (i & 7) == 0);
            System.gc();
            if (!holder.exact[i & 3].equals("ok")) throw new AssertionError();
        }
        holder.typed("valid", 0);
        holder.clear(holder.covariant, 0);
        if (holder.covariant[0] != null) throw new AssertionError();
        holder.replace(new String[2]);
        fail(holder, 0);
        fail(holder, 1);
        fail(holder, 2);
        fail(holder, 3);
        fail(holder, 4);
        fail(holder, 5);
        Object kept = new String(new char[] {'k', 'e', 'p', 't'});
        try { holder.put(kept, -1, false); throw new AssertionError(); }
        catch (ArrayIndexOutOfBoundsException expected) {
            System.gc();
            System.out.println(kept);
        }
        System.out.println(fresh(kept, true)[0]);
        System.out.println(fresh(kept, false)[0]);
        collector.join();
        System.out.println(failures);
    }
}
