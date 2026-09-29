package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.io.Serializable;
import java.util.*;

/**
 * Uniform pseudorandom values using the Java Random 48-bit seed sequence.
 * Seed updates are synchronized; subclasses may override {@link #next(int)}.
 */
@SubstituteClass("java.util.Random")
public class Random implements Serializable {
    private static final long serialVersionUID = 3905348978240129619L;
    private static long seedUniquifier = 8682522807148012L;

    private long seed;

    public Random() {
        this(uniqueSeed() ^ System.nanoTime());
    }

    public Random(long seed) {
        if(getClass() == Random.class) this.seed = NativeRandom.scramble(seed);
        else setSeed(seed);
    }

    private static synchronized long uniqueSeed() {
        seedUniquifier = NativeRandom.uniqueSeed(seedUniquifier);
        return seedUniquifier;
    }

    public synchronized void setSeed(long seed) {
        this.seed = NativeRandom.scramble(seed);
    }

    /** Advances the seed once and returns the requested 1 to 32 high bits. */
    protected synchronized int next(int bits) {
        seed = NativeRandom.advance(seed);
        return NativeRandom.bits(seed, bits);
    }

    public void nextBytes(byte[] bytes) {
        NativeRandom.nextBytes(this, bytes);
    }

    public int nextInt() {
        return NativeRandom.nextInt(this);
    }

    public int nextInt(int bound) {
        return NativeRandom.nextInt(this, bound);
    }

    public long nextLong() {
        return NativeRandom.nextLong(this);
    }

    public boolean nextBoolean() {
        return NativeRandom.nextBoolean(this);
    }

    public float nextFloat() {
        return NativeRandom.nextFloat(this);
    }

    public double nextDouble() {
        return NativeRandom.nextDouble(this);
    }
}
