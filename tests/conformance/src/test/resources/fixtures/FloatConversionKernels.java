/* Standalone JVM/Graal/jNative conversion diagnostic.
 * CLI: verify (default) | bench CASE passes samples
 * Cases: f2i, f2l, d2i, d2l, float-control, double-control.
 * Each pass executes 64 iterations on four scalar lanes: 256 conversions (or
 * arithmetic-only lane updates for controls). No array/table access in timed loops.
 * Quarter steps and wrapping stay exact and finite in both float and double.
 * Every cast contributes to a lane's additive long sum; final mixing consumes
 * all sums and scalar states. Four lanes reduce a single recurrence dependency.
 * Controls retain recurrence/accumulation work but sum loop indices instead of
 * casts. They provide overhead context; subtracting their timings is not a
 * reliable isolated conversion cost. These probes do not predict sprite timings.
 */
public class FloatConversionKernels {
    static final int ITERATIONS = 64, LANES = 4, OPERATIONS_PER_PASS = ITERATIONS * LANES;
    static volatile long sink;

    static long mix(long a, long b, long c, long d) {
        return ((a * 31 + b) * 31 + c) * 31 + d;
    }

    static long f2i(int passes) {
        float seed = (passes & 1023) * 0.25f;
        float a = seed - 128.75f, b = seed - 64.5f, c = seed + 64.25f, d = seed + 128f;
        long sa = 0, sb = 0, sc = 0, sd = 0;
        for (int p = 0; p < passes; p++) for (int i = 0; i < ITERATIONS; i++) {
            a += 0.25f; b += 0.25f; c += 0.25f; d += 0.25f;
            if (a >= 4096f) a -= 8192f;
            if (b >= 4096f) b -= 8192f;
            if (c >= 4096f) c -= 8192f;
            if (d >= 4096f) d -= 8192f;
            sa += (int)a; sb += (int)b; sc += (int)c; sd += (int)d;
        }
        return mix(sa, sb, sc, sd) * 31 + mix(Float.floatToRawIntBits(a), Float.floatToRawIntBits(b),
                Float.floatToRawIntBits(c), Float.floatToRawIntBits(d));
    }

    static long f2l(int passes) {
        float seed = (passes & 1023) * 0.25f;
        float a = seed - 128.75f, b = seed - 64.5f, c = seed + 64.25f, d = seed + 128f;
        long sa = 0, sb = 0, sc = 0, sd = 0;
        for (int p = 0; p < passes; p++) for (int i = 0; i < ITERATIONS; i++) {
            a += 0.25f; b += 0.25f; c += 0.25f; d += 0.25f;
            if (a >= 4096f) a -= 8192f;
            if (b >= 4096f) b -= 8192f;
            if (c >= 4096f) c -= 8192f;
            if (d >= 4096f) d -= 8192f;
            sa += (long)a; sb += (long)b; sc += (long)c; sd += (long)d;
        }
        return mix(sa, sb, sc, sd) * 31 + mix(Float.floatToRawIntBits(a), Float.floatToRawIntBits(b),
                Float.floatToRawIntBits(c), Float.floatToRawIntBits(d));
    }

    static long d2i(int passes) {
        double seed = (passes & 1023) * 0.25;
        double a = seed - 128.75, b = seed - 64.5, c = seed + 64.25, d = seed + 128;
        long sa = 0, sb = 0, sc = 0, sd = 0;
        for (int p = 0; p < passes; p++) for (int i = 0; i < ITERATIONS; i++) {
            a += 0.25; b += 0.25; c += 0.25; d += 0.25;
            if (a >= 4096) a -= 8192;
            if (b >= 4096) b -= 8192;
            if (c >= 4096) c -= 8192;
            if (d >= 4096) d -= 8192;
            sa += (int)a; sb += (int)b; sc += (int)c; sd += (int)d;
        }
        return mix(sa, sb, sc, sd) * 31 + mix(Double.doubleToRawLongBits(a), Double.doubleToRawLongBits(b),
                Double.doubleToRawLongBits(c), Double.doubleToRawLongBits(d));
    }

    static long d2l(int passes) {
        double seed = (passes & 1023) * 0.25;
        double a = seed - 128.75, b = seed - 64.5, c = seed + 64.25, d = seed + 128;
        long sa = 0, sb = 0, sc = 0, sd = 0;
        for (int p = 0; p < passes; p++) for (int i = 0; i < ITERATIONS; i++) {
            a += 0.25; b += 0.25; c += 0.25; d += 0.25;
            if (a >= 4096) a -= 8192;
            if (b >= 4096) b -= 8192;
            if (c >= 4096) c -= 8192;
            if (d >= 4096) d -= 8192;
            sa += (long)a; sb += (long)b; sc += (long)c; sd += (long)d;
        }
        return mix(sa, sb, sc, sd) * 31 + mix(Double.doubleToRawLongBits(a), Double.doubleToRawLongBits(b),
                Double.doubleToRawLongBits(c), Double.doubleToRawLongBits(d));
    }

    static long floatControl(int passes) {
        float seed = (passes & 1023) * 0.25f;
        float a = seed - 128.75f, b = seed - 64.5f, c = seed + 64.25f, d = seed + 128f;
        long sa = 0, sb = 0, sc = 0, sd = 0;
        for (int p = 0; p < passes; p++) for (int i = 0; i < ITERATIONS; i++) {
            a += 0.25f; b += 0.25f; c += 0.25f; d += 0.25f;
            if (a >= 4096f) a -= 8192f;
            if (b >= 4096f) b -= 8192f;
            if (c >= 4096f) c -= 8192f;
            if (d >= 4096f) d -= 8192f;
            sa += i; sb += i + 1; sc += i + 2; sd += i + 3;
        }
        return mix(sa, sb, sc, sd) * 31 + mix(Float.floatToRawIntBits(a), Float.floatToRawIntBits(b),
                Float.floatToRawIntBits(c), Float.floatToRawIntBits(d));
    }

    static long doubleControl(int passes) {
        double seed = (passes & 1023) * 0.25;
        double a = seed - 128.75, b = seed - 64.5, c = seed + 64.25, d = seed + 128;
        long sa = 0, sb = 0, sc = 0, sd = 0;
        for (int p = 0; p < passes; p++) for (int i = 0; i < ITERATIONS; i++) {
            a += 0.25; b += 0.25; c += 0.25; d += 0.25;
            if (a >= 4096) a -= 8192;
            if (b >= 4096) b -= 8192;
            if (c >= 4096) c -= 8192;
            if (d >= 4096) d -= 8192;
            sa += i; sb += i + 1; sc += i + 2; sd += i + 3;
        }
        return mix(sa, sb, sc, sd) * 31 + mix(Double.doubleToRawLongBits(a), Double.doubleToRawLongBits(b),
                Double.doubleToRawLongBits(c), Double.doubleToRawLongBits(d));
    }

    static long run(String name, int passes) {
        switch (name) {
            case "f2i": return f2i(passes);
            case "f2l": return f2l(passes);
            case "d2i": return d2i(passes);
            case "d2l": return d2l(passes);
            case "float-control": return floatControl(passes);
            case "double-control": return doubleControl(passes);
            default: throw new IllegalArgumentException("Unknown case: " + name);
        }
    }

    static void verify() {
        int[] floatBits = {0, 0x80000000, 1, 0x80000001, 0x007fffff, 0x00800000,
                0x3f000000, 0xbf000000, 0x3f7fffff, 0xbf7fffff, 0x3f800000, 0xbf800000,
                0x3fffffff, 0xbfffffff, 0x4effffff, 0x4f000000, 0x4f000001,
                0xceffffff, 0xcf000000, 0xcf000001, 0x5effffff, 0x5f000000, 0x5f000001,
                0xdeffffff, 0xdf000000, 0xdf000001, 0x7f7fffff, 0xff7fffff,
                0x7f800000, 0xff800000, 0x7fc00007, 0xffc00007, 0x7f800001, 0xff800001};
        long fi = 1, fl = 1;
        for (int bits : floatBits) {
            float value = Float.intBitsToFloat(bits);
            fi = fi * 31 + (int)value; fl = fl * 31 + (long)value;
        }
        System.out.println("BOUNDARY,float," + floatBits.length + "," + fi + "," + fl);
        long[] doubleBits = {0L, 0x8000000000000000L, 1L, 0x8000000000000001L,
                0x000fffffffffffffL, 0x0010000000000000L, 0x3fe0000000000000L,
                0xbfe0000000000000L, 0x3fefffffffffffffL, 0xbfefffffffffffffL,
                0x3ff0000000000000L, 0xbff0000000000000L, 0x3fffffffffffffffL,
                0xbfffffffffffffffL, 0x41dfffffffc00000L, 0x41dfffffffffffffL,
                0x41e0000000000000L, 0x41e0000000000001L, 0xc1dfffffffffffffL,
                0xc1e0000000000000L, 0xc1e0000000000001L, 0xc1e0000000100000L,
                0x43dfffffffffffffL, 0x43e0000000000000L, 0x43e0000000000001L,
                0xc3dfffffffffffffL, 0xc3e0000000000000L, 0xc3e0000000000001L,
                0x7fefffffffffffffL, 0xffefffffffffffffL, 0x7ff0000000000000L,
                0xfff0000000000000L, 0x7ff8000000000007L, 0xfff8000000000007L,
                0x7ff0000000000001L, 0xfff0000000000001L};
        long di = 1, dl = 1;
        for (long bits : doubleBits) {
            double value = Double.longBitsToDouble(bits);
            di = di * 31 + (int)value; dl = dl * 31 + (long)value;
        }
        System.out.println("BOUNDARY,double," + doubleBits.length + "," + di + "," + dl);
        int state = 0x12345678;
        long wide = 0x123456789abcdef0L;
        fi = fl = di = dl = 1;
        int randomInputs = 8192;
        for (int i = 0; i < randomInputs; i++) {
            state = state * 1664525 + 1013904223;
            wide = wide * 6364136223846793005L + 1442695040888963407L;
            float f = Float.intBitsToFloat(state);
            double d = Double.longBitsToDouble(wide);
            fi = fi * 31 + (int)f; fl = fl * 31 + (long)f;
            di = di * 31 + (int)d; dl = dl * 31 + (long)d;
        }
        System.out.println("RANDOM,float," + randomInputs + "," + fi + "," + fl);
        System.out.println("RANDOM,double," + randomInputs + "," + di + "," + dl);
        for (int passes : new int[] {1, 257, 1025}) {
            if (f2i(passes) != f2l(passes) || d2i(passes) != d2l(passes))
                throw new IllegalStateException("finite int/long conversion disagreement");
            for (String name : new String[] {"f2i", "f2l", "d2i", "d2l", "float-control", "double-control"})
                System.out.println("KERNEL," + name + "," + passes + "," + run(name, passes));
        }
        System.out.println("VERIFY,ok");
    }

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("verify"))) { verify(); return; }
        if (args.length != 4 || !args[0].equals("bench"))
            throw new IllegalArgumentException("verify | bench CASE passes samples");
        String name = args[1];
        int passes = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]);
        if (passes <= 0 || samples <= 0) throw new IllegalArgumentException("positive passes/samples required");
        int warmup = 3;
        long operations = (long)passes * OPERATIONS_PER_PASS;
        System.out.println("CONFIG,case=" + name + ",lanes=" + LANES + ",operations_per_pass=" + OPERATIONS_PER_PASS
                + ",passes=" + passes + ",operations=" + operations + ",samples=" + samples + ",warmup_samples=" + warmup);
        System.out.println("HEADER,phase,sample,elapsed_ns,ns_per_op,raw_checksum");
        long expected = 0;
        for (int sample = -warmup; sample < samples; sample++) {
            long start = System.nanoTime();
            long hash = run(name, passes);
            long elapsed = System.nanoTime() - start;
            sink = hash;
            if (sample == -warmup) expected = hash;
            else if (hash != expected) throw new IllegalStateException("sample checksum changed");
            System.out.println("SAMPLE," + (sample < 0 ? "warmup" : "measure") + ","
                    + (sample < 0 ? sample + warmup : sample) + "," + elapsed + ","
                    + (double)elapsed / operations + "," + hash);
        }
    }
}
