/* Scale arithmetic adapted from libGDX Sprite (Apache-2.0, Copyright 2011 AUTHORS).
 * CLI: verify (default), or bench CASE passes samples [collect]; 256 element updates per pass.
 * Cases: trig-cast, scale-chain, scale-fused, copy-array, copy-scalar, roots-cold,
 * roots-plain. roots-cold includes a conditional cold collecting branch; timed
 * samples normally take its noncollecting path. Optional collect=true enables
 * collection on that branch; use false for normal-path timing.
 * Standalone compiler probes: these kernels do not predict the full sprite benchmark.
 */
public class SpriteOptimizationKernels {
    static final int SIZE = 256, COUNT = 1 << 14, MASK = COUNT - 1;
    static final float DEG_TO_INDEX = COUNT / 360f;
    static final float[] TABLE = new float[COUNT];
    static volatile long sink;
    static volatile boolean collect;
    static {
        // Exact binary fractions isolate indexing from platform transcendental accuracy.
        for (int i = 0; i < COUNT; i++) TABLE[i] = ((i & 255) - 128) / 128f;
    }

    static class Sprite {
        final float[] vertices = new float[20];
        float x, y, width = 32, height = 32, originX = 16, originY = 16;
        float scaleX = 1, scaleY = 1;
        boolean dirty = true;
        void setScale(float scale) { scaleX = scale; scaleY = scale; dirty = true; }
        float[] getVertices() {
            if (dirty) {
                dirty = false;
                float localX = -originX, localY = -originY;
                float localX2 = localX + width, localY2 = localY + height;
                float worldOriginX = x - localX, worldOriginY = y - localY;
                if (scaleX != 1 || scaleY != 1) {
                    localX *= scaleX; localY *= scaleY;
                    localX2 *= scaleX; localY2 *= scaleY;
                }
                float x1 = localX + worldOriginX, y1 = localY + worldOriginY;
                float x2 = localX2 + worldOriginX, y2 = localY2 + worldOriginY;
                vertices[0] = x1; vertices[1] = y1;
                vertices[5] = x1; vertices[6] = y2;
                vertices[10] = x2; vertices[11] = y2;
                vertices[15] = x2; vertices[16] = y1;
            }
            return vertices;
        }
    }

    static class State {
        final Sprite[] sprites = new Sprite[SIZE];
        final float[] input = new float[SIZE];
        final float[][] source = new float[SIZE][20], output = new float[SIZE][];
        State() {
            for (int i = 0; i < SIZE; i++) {
                Sprite s = new Sprite(); s.x = i / 8f; s.y = -i / 16f;
                sprites[i] = s; output[i] = s.vertices; input[i] = (i - 128) / 8f;
                for (int j = 0; j < 20; j++) source[i][j] = s.vertices[j] = (i + j) / 16f;
            }
        }
    }

    static void fused(Sprite s, float scale) {
        s.scaleX = scale; s.scaleY = scale; s.dirty = false;
        float localX = -s.originX, localY = -s.originY;
        float localX2 = localX + s.width, localY2 = localY + s.height;
        float worldOriginX = s.x - localX, worldOriginY = s.y - localY;
        if (scale != 1) {
            localX *= scale; localY *= scale; localX2 *= scale; localY2 *= scale;
        }
        float x1 = localX + worldOriginX, y1 = localY + worldOriginY;
        float x2 = localX2 + worldOriginX, y2 = localY2 + worldOriginY;
        float[] v = s.vertices;
        v[0] = x1; v[1] = y1; v[5] = x1; v[6] = y2;
        v[10] = x2; v[11] = y2; v[15] = x2; v[16] = y1;
    }

    static void checked20(float[] src, int sp, float[] dst, int dp) {
        System.arraycopy(src, sp, dst, dp, 20);
    }
    static void scalar20(float[] src, int sp, float[] dst, int dp) {
        int sl = src.length, dl = dst.length;
        if (sp < 0 || dp < 0 || sp > sl - 20 || dp > dl - 20)
            throw new ArrayIndexOutOfBoundsException();
        if (src == dst && dp > sp && dp < sp + 20) {
            for (int j = 19; j >= 0; j--) dst[dp + j] = src[sp + j];
        } else {
            for (int j = 0; j < 20; j++) dst[dp + j] = src[sp + j];
        }
    }
    static void rootCold(float[] live, float[] result, boolean cold) {
        if (cold) {
            float[] temporary = new float[20];
            temporary[0] = live[1];
            System.gc();
            if (Float.floatToRawIntBits(temporary[0]) != Float.floatToRawIntBits(live[1]))
                throw new IllegalStateException("lost temporary root");
        }
        result[0] = live[0] + live[1]; result[1] = live[1] - live[0];
    }
    static void rootPlain(float[] live, float[] result, boolean cold) {
        if (cold) sink++; // Retain a cold branch without allocation or collection.
        result[0] = live[0] + live[1]; result[1] = live[1] - live[0];
    }

    static void run(State s, String name, int passes) {
        switch (name) {
            case "trig-cast":
                for (int p = 0; p < passes; p++) for (int i = 0; i < SIZE; i++) {
                    float degrees = s.input[i] + 0.25f;
                    if (degrees > 360) degrees -= 720;
                    s.input[i] = degrees;
                    s.output[i][0] = TABLE[(int)(degrees * DEG_TO_INDEX) & MASK];
                    s.output[i][1] = TABLE[(int)((degrees + 90) * DEG_TO_INDEX) & MASK];
                }
                break;
            case "scale-chain":
                for (int p = 0; p < passes; p++) for (int i = 0; i < SIZE; i++) {
                    s.input[i] += 0.25f;
                    if (s.input[i] >= 4) s.input[i] -= 4;
                    s.sprites[i].setScale(s.input[i]); s.sprites[i].getVertices();
                }
                break;
            case "scale-fused":
                for (int p = 0; p < passes; p++) for (int i = 0; i < SIZE; i++) {
                    s.input[i] += 0.25f;
                    if (s.input[i] >= 4) s.input[i] -= 4;
                    fused(s.sprites[i], s.input[i]);
                }
                break;
            case "copy-array":
                for (int p = 0; p < passes; p++) for (int i = 0; i < SIZE; i++) {
                    s.source[i][0] += 0.25f; s.source[i][1] = s.output[i][0];
                    checked20(s.source[i], 0, s.output[i], 0);
                }
                break;
            case "copy-scalar":
                for (int p = 0; p < passes; p++) for (int i = 0; i < SIZE; i++) {
                    s.source[i][0] += 0.25f; s.source[i][1] = s.output[i][0];
                    scalar20(s.source[i], 0, s.output[i], 0);
                }
                break;
            case "roots-cold":
                for (int p = 0; p < passes; p++) for (int i = 0; i < SIZE; i++) {
                    s.source[i][0] += 0.25f; s.source[i][1] = s.output[i][1] * 0.5f;
                    rootCold(s.source[i], s.output[i], collect);
                }
                break;
            case "roots-plain":
                for (int p = 0; p < passes; p++) for (int i = 0; i < SIZE; i++) {
                    s.source[i][0] += 0.25f; s.source[i][1] = s.output[i][1] * 0.5f;
                    rootPlain(s.source[i], s.output[i], collect);
                }
                break;
            default: throw new IllegalArgumentException("Unknown case: " + name);
        }
    }

    static long raw(float[] values) {
        long hash = 1;
        for (float v : values) hash = hash * 31 + Float.floatToRawIntBits(v);
        return hash;
    }
    static long checksum(State s) {
        long hash = raw(s.input);
        for (int i = 0; i < SIZE; i++) {
            hash = (hash * 31 + raw(s.source[i])) * 31 + raw(s.output[i]);
            Sprite sprite = s.sprites[i];
            hash = hash * 31 + Float.floatToRawIntBits(sprite.scaleX);
            hash = hash * 31 + Float.floatToRawIntBits(sprite.scaleY);
            hash = hash * 31 + (sprite.dirty ? 1 : 0);
            hash = hash * 31 + Float.floatToRawIntBits(sprite.x);
            hash = hash * 31 + Float.floatToRawIntBits(sprite.y);
            hash = hash * 31 + Float.floatToRawIntBits(sprite.width);
            hash = hash * 31 + Float.floatToRawIntBits(sprite.height);
            hash = hash * 31 + Float.floatToRawIntBits(sprite.originX);
            hash = hash * 31 + Float.floatToRawIntBits(sprite.originY);
        }
        return hash;
    }
    static void equal(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalStateException("length");
        for (int i = 0; i < a.length; i++)
            if (Float.floatToRawIntBits(a[i]) != Float.floatToRawIntBits(b[i]))
                throw new IllegalStateException("bits at " + i);
    }
    static void pair(String first, String second) {
        State a = new State(), b = new State(); run(a, first, 17); run(b, second, 17);
        equal(a.input, b.input);
        for (int i = 0; i < SIZE; i++) {
            equal(a.source[i], b.source[i]); equal(a.output[i], b.output[i]);
            equalSprite(a.sprites[i], b.sprites[i]);
        }
        System.out.println("PAIR," + first + "," + second + "," + checksum(a));
    }
    static void equalSprite(Sprite a, Sprite b) {
        equalBits(a.scaleX, b.scaleX); equalBits(a.scaleY, b.scaleY);
        equalBits(a.x, b.x); equalBits(a.y, b.y);
        equalBits(a.width, b.width); equalBits(a.height, b.height);
        equalBits(a.originX, b.originX); equalBits(a.originY, b.originY);
        if (a.dirty != b.dirty) throw new IllegalStateException("dirty");
    }
    static void equalBits(float a, float b) {
        if (Float.floatToRawIntBits(a) != Float.floatToRawIntBits(b)) throw new IllegalStateException("field bits");
    }
    static String copyFailure(boolean scalar, float[] src, int sp, float[] dst, int dp) {
        try {
            if (scalar) scalar20(src, sp, dst, dp); else checked20(src, sp, dst, dp);
            return "ok";
        } catch (RuntimeException e) { return e.getClass().getName(); }
    }
    static void verify() {
        float[] casts = {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
                -0f, 0f, -1.99f, 1.99f, Float.intBitsToFloat(0x4effffff),
                0x1p31f, -0x1p31f, Float.intBitsToFloat(0xcf000001), Float.MAX_VALUE};
        int[] expected = {0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, 0, -1, 1,
                2147483520, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int i = 0; i < casts.length; i++) {
            if ((int)casts[i] != expected[i]) throw new IllegalStateException("cast " + i);
            System.out.println("CAST," + i + "," + (int)casts[i] + ","
                    + Float.floatToRawIntBits(TABLE[(int)(casts[i] * DEG_TO_INDEX) & MASK]));
        }
        pair("scale-chain", "scale-fused"); pair("copy-array", "copy-scalar");
        pair("roots-cold", "roots-plain");
        for (float scale : new float[] {-0f, 0f, 1, -1, Float.MIN_VALUE, 0.99999994f,
                Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.MAX_VALUE}) {
            Sprite a = new Sprite(), b = new Sprite(); a.setScale(scale); a.getVertices(); fused(b, scale);
            equal(a.vertices, b.vertices); equalSprite(a, b);
            a.getVertices(); equal(a.vertices, b.vertices); equalSprite(a, b);
            System.out.println("SCALE," + Float.floatToRawIntBits(scale) + "," + raw(a.vertices));
        }
        for (int offset : new int[] {-3, 0, 3}) {
            float[] a = new float[26], b = new float[26];
            for (int i = 0; i < 26; i++) a[i] = b[i] = Float.intBitsToFloat(0x7fc12345 + i);
            a[3] = b[3] = -0f; a[4] = b[4] = Float.POSITIVE_INFINITY;
            checked20(a, 3, a, 3 + offset); scalar20(b, 3, b, 3 + offset); equal(a, b);
            System.out.println("OVERLAP," + offset + "," + raw(a));
        }
        for (int[] positions : new int[][] {{-1, 0}, {1, 0}, {Integer.MAX_VALUE, 0},
                {0, -1}, {0, 1}, {0, Integer.MAX_VALUE}}) {
            float[] a = new float[20], b = new float[20];
            int sp = positions[0], dp = positions[1];
            String one = copyFailure(false, a, sp, a, dp), two = copyFailure(true, b, sp, b, dp);
            if (!one.equals(two) || one.equals("ok")) throw new IllegalStateException("copy bounds");
            equal(a, b); System.out.println("BOUNDS," + sp + "," + dp + "," + one);
        }
        String one = copyFailure(false, null, 0, new float[20], 0);
        if (!one.equals(copyFailure(true, null, 0, new float[20], 0))) throw new IllegalStateException("null");
        System.out.println("NULL," + one);
        one = copyFailure(false, new float[20], 0, null, 0);
        if (!one.equals(copyFailure(true, new float[20], 0, null, 0))) throw new IllegalStateException("null destination");
        System.out.println("NULL_DEST," + one);
        // These local arrays have no global owner and must survive allocation and collection.
        float[] live = {3.5f, -0f}, result = new float[20], control = new float[20];
        rootCold(live, result, true); rootPlain(live, control, false); equal(result, control);
        System.out.println("COLLECT," + raw(live) + "," + raw(result));
        State trig = new State(); run(trig, "trig-cast", 17);
        System.out.println("TRIG," + checksum(trig));
        System.out.println("VERIFY,ok");
    }

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("verify"))) { verify(); return; }
        if ((args.length != 4 && args.length != 5) || !args[0].equals("bench"))
            throw new IllegalArgumentException("verify | bench CASE passes samples [collect]");
        String name = args[1]; int passes = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]);
        if (passes <= 0 || samples <= 0) throw new IllegalArgumentException("positive passes/samples required");
        collect = args.length == 5 && "true".equals(args[4]);
        int warmup = 3; long operations = (long)passes * SIZE;
        System.out.println("CONFIG,case=" + name + ",size=" + SIZE + ",passes=" + passes
                + ",samples=" + samples + ",warmup_samples=" + warmup + ",cold_collect=" + collect);
        System.out.println("HEADER,phase,sample,elapsed_ns,ns_per_op,raw_checksum");
        for (int sample = -warmup; sample < samples; sample++) {
            State s = new State(); long start = System.nanoTime(); run(s, name, passes);
            long elapsed = System.nanoTime() - start, hash = checksum(s); sink = hash;
            System.out.println("SAMPLE," + (sample < 0 ? "warmup" : "measure") + ","
                    + (sample < 0 ? sample + warmup : sample) + "," + elapsed + ","
                    + (double)elapsed / operations + "," + hash);
        }
    }
}




