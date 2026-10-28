public class NestedReferenceStoreProof {
    static class Base { }
    static final class Leaf extends Base { }
    static class Collector extends Thread {
        public void run() { for(int i = 0; i < 30; i++) System.gc(); }
    }
    final float[][] field = new float[2][];
    static int evaluations;
    static int failures;

    static void store(float[][] target, int index, float[] value) { target[index] = value; }
    static void deep(float[][][] target, float[][] value) { target[0] = value; }
    static void leaves(Leaf[] target, int index, Leaf value) { target[index] = value; }
    static void leafRows(Leaf[][] target, Leaf[] value) { target[0] = value; }
    void fieldStore(float[] value) { field[0] = value; }
    float[] result() { return new float[] {7, 9}; }
    static void returned(float[][] target, NestedReferenceStoreProof holder) { target[1] = holder.result(); }
    static void cast(Object target, Object value) { ((float[][])target)[0] = (float[])value; }
    static void nullable(float[][] target, float[] value, boolean choose) {
        float[][] destination = choose ? target : null;
        float[] selected = choose ? value : null;
        destination[0] = selected;
    }
    static void unknown(Object[] target, Object value) { target[0] = value; }
    static void covariant(Object[][] target, Object[] value) { target[0] = value; }
    static void base(Base[] target, Base value) { target[0] = value; }
    static void clones(Cloneable[] target, Cloneable value) { target[0] = value; }
    static void mixedTargets(float[][] first, int[][] second, float[] value, boolean choose) {
        Object[] target = choose ? first : second;
        target[0] = value;
    }
    static void mixedValues(Object[] target, float[] first, int[] second, boolean choose) {
        Object value = choose ? first : second;
        target[0] = value;
    }
    static void loopMerge() {
        Object[] target = new float[1][];
        for(int i = 0; i < 2; i++) {
            target[0] = new float[1];
            target = new int[1][];
        }
    }
    static int index() { evaluations = evaluations * 10 + 1; return -1; }
    static float[] value() { evaluations = evaluations * 10 + 2; System.gc(); return new float[1]; }
    static void effects(float[][] target) { target[index()] = value(); }

    static void loop(float[][] source, float[][] target) {
        for(int pass = 0; pass < 1000; pass++)
            for(int i = 0; i < source.length; i++) target[i] = source[i];
    }

    static float replaceAndRead(NestedReferenceStoreProof holder, float[] row, int count) {
        float sum = 0;
        for(int i = 0; i < count; i++) {
            holder.fieldStore(row);
            sum += holder.field[0][i];
        }
        return sum;
    }

    static void bad(int operation) {
        try {
            switch(operation) {
                case 0: store(null, -1, new float[1]); break;
                case 1: store(new float[1][], 1, new float[1]); break;
                case 2: unknown(new float[0][], new int[1]); break;
                case 3: unknown(new float[1][], new int[1]); break;
                case 4: covariant(new Leaf[1][], new Base[1]); break;
                case 5: base(new Leaf[1], new Base()); break;
                case 6: clones(new float[1][], new int[1]); break;
                case 7: mixedTargets(new float[1][], new int[1][], new float[1], false); break;
                case 8: mixedValues(new float[1][], new float[1], new int[1], false); break;
                case 9: cast(new int[1][], new float[1]); break;
                case 10: leaves(null, -1, new Leaf()); break;
                case 11: cast(new float[1][], new int[1]); break;
                case 12: loopMerge(); break;
                case 13: effects(null); break;
                case 14: store(new float[0][], -1, null); break;
                case 15: covariant(null, new Base[0]); break;
                case 16: base(new Leaf[0], new Base()); break;
                case 17: store(new float[1][], Integer.MAX_VALUE, new float[0]); break;
                case 18: replaceAndRead(new NestedReferenceStoreProof(), new float[0], 1); break;
                default: replaceAndRead(new NestedReferenceStoreProof(), null, 1);
            }
            throw new AssertionError();
        } catch(RuntimeException error) {
            boolean correct = switch(operation) {
                case 0, 10, 13, 15, 19 -> error instanceof NullPointerException;
                case 1, 2, 14, 16, 17, 18 -> error instanceof ArrayIndexOutOfBoundsException;
                case 9, 11 -> error instanceof ClassCastException;
                default -> error instanceof ArrayStoreException;
            };
            if(!correct) throw new AssertionError();
            failures++;
            System.gc();
        }
    }

    static void primitives() {
        boolean[][] z = new boolean[1][]; boolean[] zv = {true}; z[0] = zv;
        byte[][] b = new byte[1][]; byte[] bv = {1}; b[0] = bv;
        char[][] c = new char[1][]; char[] cv = {'a'}; c[0] = cv;
        short[][] s = new short[1][]; short[] sv = {2}; s[0] = sv;
        int[][] i = new int[1][]; int[] iv = {3}; i[0] = iv;
        long[][] j = new long[1][]; long[] jv = {4}; j[0] = jv;
        double[][] d = new double[1][]; double[] dv = {5}; d[0] = dv;
        System.gc();
        if(z[0] != zv || b[0] != bv || c[0] != cv || s[0] != sv || i[0] != iv || j[0] != jv || d[0] != dv)
            throw new AssertionError();
    }

    public static void main(String[] args) throws Exception {
        NestedReferenceStoreProof holder = new NestedReferenceStoreProof();
        float[] kept = {1, 2};
        float[][] target = new float[2][];
        store(target, 0, kept); nullable(target, kept, true);
        holder.fieldStore(kept); returned(target, holder); cast(target, kept);
        float[][][] deeper = new float[1][][];
        deep(deeper, target);
        Leaf leaf = new Leaf(); Leaf[] leaves = new Leaf[1]; Leaf[][] rows = new Leaf[1][];
        leaves(leaves, 0, leaf); leafRows(rows, leaves);
        System.gc();
        if(target[0] != kept || target[1][1] != 9 || holder.field[0] != kept
                || deeper[0] != target || leaves[0] != leaf || rows[0] != leaves)
            throw new AssertionError();
        store(target, 0, null); leaves(leaves, 0, null);
        if(target[0] != null || leaves[0] != null) throw new AssertionError();
        primitives();
        if(replaceAndRead(holder, new float[]{42}, 1) != 42) throw new AssertionError();
        if(replaceAndRead(null, null, 0) != 0) throw new AssertionError();
        for(int operation = 0; operation < 20; operation++) bad(operation);
        if(evaluations != 12) throw new AssertionError();
        float[][] source = new float[32][1], copied = new float[32][];
        for(int i = 0; i < source.length; i++) source[i][0] = i;
        Thread collector = new Collector();
        collector.start();
        loop(source, copied);
        collector.join();
        System.gc();
        for(int i = 0; i < source.length; i++)
            if(copied[i] != source[i] || copied[i][0] != i) throw new AssertionError();
        System.out.println("verified exceptions=" + failures + " evaluations=" + evaluations + " rows=" + copied.length);
    }
}
