public class ArrayLengthViews {
    static int ticks;
    static Object[] changing;
    static volatile boolean done;
    static class Key {
        final int number;
        Key(int number) { this.number = number; }
        public int hashCode() { changing = new Object[0]; System.gc(); return number; }
    }
    static long scan(Object[] values, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; r++) {
            ticks++;
            for (int i = 0; i < values.length; i++) sum += values[i].hashCode();
        }
        return sum;
    }
    static long primitive(int[] values, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; r++) {
            ticks++;
            for (int i = 0; i < values.length; i++) sum += values[i];
        }
        return sum;
    }
    static long confined(int size) {
        int[] values = new int[size];
        long sum = 0;
        for (int i = 0; i < values.length; i++) { values[i] = i + 1; sum += values[i]; }
        return sum;
    }
    static long fields(Key[] values, int start, int rounds) {
        long sum = 0;
        for (int r = 0; r < rounds; r++)
            for (int i = start; i < values.length; i++) { ticks++; sum += values[i].number; }
        return sum;
    }
    static int parallel(int[] values, int[] other) {
        int sum = 0;
        for (int i = 0; i < values.length; i++) { ticks++; sum += values[i] + other[i]; }
        return sum;
    }
    static int shifted(int[] values) {
        int sum = 0;
        for (int i = 0; i < values.length; i++) { ticks++; sum += values[i + 1]; }
        return sum;
    }
    static int reassigned(int[] first, int[] second) {
        int sum = 0;
        for (int i = 0; i < first.length; i++) { sum += first[i]; first = second; }
        return sum;
    }
    static long mutable() {
        long sum = 0;
        for (int i = 0; i < changing.length; i++) sum += changing[i].hashCode();
        return sum;
    }
    public static void main(String[] args) throws Exception {
        System.out.println(scan(null, 0) + primitive(null, 0));
        System.out.println(ticks);
        try { scan(null, 1); } catch (NullPointerException expected) { System.out.println(ticks); }
        try { primitive(null, 1); } catch (NullPointerException expected) { System.out.println(ticks); }
        System.out.println(scan(new Object[0], 2) + primitive(new int[0], 2));
        System.out.println(ticks);
        changing = new Object[]{new Key(3), new Key(7)};
        System.out.println(scan(changing, 2));
        changing = new Object[]{new Key(3), new Key(7)};
        System.out.println(mutable());
        System.out.println(reassigned(new int[]{3, 5, 7}, new int[0]));
        System.out.println(reassigned(new int[]{3, 5, 7}, new int[]{11, 13}));
        System.out.println(confined(0) + confined(17));
        System.out.println(fields(null, 0, 0));
        System.out.println(fields(new Key[0], 0, 1));
        Key[] keys = new Key[130];
        for (int i = 0; i < keys.length; i++) keys[i] = new Key(i + 1);
        System.out.println(fields(keys, 130, 1));
        System.out.println(fields(keys, Integer.MAX_VALUE, 1));
        System.out.println(fields(keys, 65, 2));
        try { fields(keys, -1, 1); } catch (ArrayIndexOutOfBoundsException expected) { System.out.println("negative"); }
        try { fields(keys, Integer.MIN_VALUE, 1); } catch (ArrayIndexOutOfBoundsException expected) { System.out.println("minimum"); }
        System.out.println(parallel(new int[0], null) + shifted(new int[0]));
        System.out.println(parallel(new int[]{1, 2}, new int[]{3, 4}));
        int partial = ticks;
        try { parallel(new int[]{1, 2}, new int[]{3}); } catch (ArrayIndexOutOfBoundsException expected) { System.out.println(ticks - partial); }
        partial = ticks;
        try { shifted(new int[]{1, 2}); } catch (ArrayIndexOutOfBoundsException expected) { System.out.println(ticks - partial); }
        Key saved = keys[64];
        keys[64] = null;
        int before = ticks;
        try { fields(keys, 0, 1); } catch (NullPointerException expected) { System.out.println(ticks - before); }
        keys[64] = saved;
        Thread collector = new Thread(() -> { while (!done) System.gc(); });
        collector.start();
        int[] values = new int[1024];
        values[1023] = 9;
        long sum = primitive(values, 1000);
        sum += fields(keys, 0, 1000);
        done = true;
        collector.join();
        System.out.println(sum);
    }
}
