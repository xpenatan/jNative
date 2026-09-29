import java.io.Serializable;
import java.util.Random;

public class RandomUniform {
    static long hash = 7;

    static void mix(long value) {
        hash = hash * 31 + value;
    }

    static void uniform(Random random) {
        int[] bounds = {1, 2, 16, 1024, 1 << 30, 3, 7, 1000, (1 << 30) + 1, Integer.MAX_VALUE};
        for(int i = 0; i < 128; ++i) {
            mix(random.nextInt());
            mix(random.nextLong());
            mix(random.nextBoolean() ? 1 : 0);
            mix(Float.floatToRawIntBits(random.nextFloat()));
            mix(Double.doubleToRawLongBits(random.nextDouble()));
            for(int bound : bounds) mix(random.nextInt(bound));
        }
        for(int length : new int[]{0, 1, 2, 3, 4, 5, 7, 8, 9, 31}) {
            byte[] bytes = new byte[length];
            random.nextBytes(bytes);
            for(byte value : bytes) mix(value);
            mix(random.nextInt());
            System.gc();
        }
    }

    static class Tracking extends Random {
        int seeds;
        int calls;
        long bitsHash;

        Tracking(long seed) { super(seed); }
        Tracking() { super(); }

        public synchronized void setSeed(long seed) {
            ++seeds;
            super.setSeed(seed);
        }

        protected synchronized int next(int bits) {
            ++calls;
            bitsHash = bitsHash * 31 + bits;
            return super.next(bits);
        }
    }

    static class Scripted extends Random {
        int[] values;
        int calls;
        int requested;

        Scripted(int[] values) { super(1); this.values = values; }

        protected int next(int bits) {
            requested = bits;
            return values[calls++];
        }
    }

    static class BytesOverride extends Random {
        int calls;

        public int nextInt() { ++calls; return 0xFEDCBA98; }
    }

    static volatile boolean start;

    static class Collecting extends Random {
        private final int[] state = new int[2];
        byte[] retained;
        int calls;
        int failAt;

        Collecting() { super(321); }

        protected synchronized int next(int bits) {
            retained = new byte[17];
            retained[0] = (byte)bits;
            System.gc();
            state[0] += retained[0];
            state[1]++;
            if(++calls == failAt) throw new IllegalStateException("draw failure");
            return super.next(bits);
        }

        int state() { return state[0] + state[1]; }
    }

    static class CollectingBytes extends Random {
        private final int[] state = new int[2];
        byte[] retained;
        int calls;
        int failAt;

        public int nextInt() {
            retained = new byte[19];
            System.gc();
            state[0] += retained.length;
            state[1]++;
            if(++calls == failAt) throw new IllegalStateException("byte failure");
            return 0xFEDCBA98 ^ calls;
        }

        int state() { return state[0] + state[1]; }
    }

    static void callbackGc() {
        Random random = new Collecting();
        mix(random.nextInt());
        mix(random.nextInt((1 << 30) + 1));
        mix(random.nextLong());
        mix(random.nextBoolean() ? 1 : 0);
        mix(Float.floatToRawIntBits(random.nextFloat()));
        mix(Double.doubleToRawLongBits(random.nextDouble()));
        mix(((Collecting)random).state());
        byte[] bytes = new byte[11];
        Random byteRandom = new CollectingBytes();
        byteRandom.nextBytes(bytes);
        for(byte value : bytes) mix(value);
        mix(((CollectingBytes)byteRandom).state());

        Collecting failing = new Collecting();
        failing.failAt = 2;
        try { failing.nextLong(); throw new IllegalStateException("missing draw failure"); }
        catch(IllegalStateException expected) {
            if(!expected.getMessage().equals("draw failure")) throw expected;
        }
        if(Thread.holdsLock(failing)) throw new IllegalStateException("monitor retained");
        System.gc();
        mix(failing.nextInt());
        mix(failing.state());
        CollectingBytes failingBytes = new CollectingBytes();
        failingBytes.failAt = 2;
        try { failingBytes.nextBytes(bytes); throw new IllegalStateException("missing byte failure"); }
        catch(IllegalStateException expected) {
            if(!expected.getMessage().equals("byte failure")) throw expected;
        }
        System.gc();
        for(byte value : bytes) mix(value);
        failingBytes.nextBytes(bytes);
        for(byte value : bytes) mix(value);
        mix(failingBytes.state());
        System.out.println("callbacks=" + hash);
    }

    static class Work extends Thread {
        final Random random;
        long sum;
        int xor;

        Work(Random random) { this.random = random; }

        public void run() {
            while(!start) Thread.yield();
            for(int i = 0; i < 2000; ++i) {
                int value = random.nextInt();
                sum += value;
                xor ^= value;
                if(i % 128 == 0) System.gc();
            }
        }
    }

    static class BoundedRejection extends Random {
        volatile boolean entered;
        volatile boolean released;

        protected int next(int bits) {
            entered = true;
            return released ? 42 : Integer.MAX_VALUE;
        }
    }

    static class RejectionCollector extends Thread {
        final BoundedRejection random;

        RejectionCollector(BoundedRejection random) { this.random = random; }

        public void run() {
            while(!random.entered) Thread.yield();
            System.gc();
            random.released = true;
        }
    }

    static void rejectionPolling() throws InterruptedException {
        BoundedRejection random = new BoundedRejection();
        Thread collector = new RejectionCollector(random);
        collector.start();
        // Rejection cannot finish until the collector finishes. The override is
        // bounded, so the native retry loop itself must cooperate with GC.
        int result = random.nextInt((1 << 30) + 1);
        collector.join();
        if(result != 42) throw new IllegalStateException("collector release");
        System.out.println("polling=true");
    }

    static void concurrent() throws InterruptedException {
        Random shared = new Random(Long.MIN_VALUE);
        Work first = new Work(shared), second = new Work(shared), third = new Work(shared);
        first.start(); second.start(); third.start(); start = true;
        first.join(); second.join(); third.join();
        Random sequential = new Random(Long.MIN_VALUE);
        long sum = 0;
        int xor = 0;
        for(int i = 0; i < 6000; ++i) {
            int value = sequential.nextInt();
            sum += value;
            xor ^= value;
        }
        System.out.println("concurrent=" + (sum == first.sum + second.sum + third.sum
                && xor == (first.xor ^ second.xor ^ third.xor)
                && shared.nextLong() == sequential.nextLong()));
    }

    public static void main(String[] args) throws InterruptedException {
        long[] seeds = {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789ABCDEF0L,
                (1L << 48) - 1, 1L << 48};
        for(long seed : seeds) {
            Random random = new Random(seed);
            uniform(random);
            random.setSeed(seed);
            uniform(random);
            System.out.println("seed=" + seed + ":" + hash);
        }
        Random invalid = new Random(7), unchanged = new Random(7);
        for(int bound : new int[]{0, -1, Integer.MIN_VALUE}) {
            try { invalid.nextInt(bound); throw new IllegalStateException("accepted invalid bound"); }
            catch(IllegalArgumentException expected) { }
        }
        try { invalid.nextBytes(null); throw new IllegalStateException("accepted null bytes"); }
        catch(NullPointerException expected) { }
        if(invalid.nextLong() != unchanged.nextLong()) throw new IllegalStateException("invalid call advanced seed");
        Random empty = new Random(8), emptyReference = new Random(8);
        empty.nextBytes(new byte[0]);
        if(empty.nextInt() != emptyReference.nextInt()) throw new IllegalStateException("empty bytes advanced seed");

        Scripted rejection = new Scripted(new int[]{Integer.MAX_VALUE, 42});
        if(rejection.nextInt((1 << 30) + 1) != 42 || rejection.calls != 2 || rejection.requested != 31)
            throw new IllegalStateException("bounded rejection");
        Scripted signed = new Scripted(new int[]{0x12345678, -1});
        mix(signed.nextLong());
        mix(signed.calls);
        mix(signed.requested);
        Scripted power = new Scripted(new int[]{Integer.MAX_VALUE});
        mix(power.nextInt(16));
        mix(power.calls);
        BytesOverride bytesOverride = new BytesOverride();
        byte[] overridden = new byte[7];
        bytesOverride.nextBytes(overridden);
        for(byte value : overridden) mix(value);
        mix(bytesOverride.calls);

        Tracking tracking = new Tracking(123);
        if(tracking.seeds != 1) throw new IllegalStateException("constructor setSeed dispatch");
        uniform(tracking);
        tracking.setSeed(123);
        mix(tracking.seeds);
        mix(tracking.calls);
        mix(tracking.bitsHash);
        mix(tracking.nextInt());
        if(!(tracking instanceof Serializable)) throw new IllegalStateException("serializable marker");
        Tracking defaultTracking = new Tracking();
        if(defaultTracking.seeds != 1) throw new IllegalStateException("default constructor setSeed dispatch");
        defaultTracking.setSeed(123);
        mix(defaultTracking.nextInt());
        Random defaultRandom = new Random();
        defaultRandom.setSeed(123);
        mix(defaultRandom.nextInt());
        System.out.println("dispatch=" + hash);
        callbackGc();
        rejectionPolling();
        concurrent();
    }
}
