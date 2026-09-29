import java.util.Random;

public class BitCountsNative {
    static void trailing(int value) {
        System.out.println("trailing:" + Integer.numberOfTrailingZeros(value));
    }

    static void leading(long value) {
        System.out.println("leading:" + Long.numberOfLeadingZeros(value));
    }

    public static void main(String[] args) {
        for (int value : new int[] {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) trailing(value);
        for (long value : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) leading(value);
        for (int bit = 0; bit < 32; bit++) {
            trailing(1 << bit);
            trailing(~(1 << bit));
        }
        for (int bit = 0; bit < 64; bit++) {
            leading(1L << bit);
            leading(~(1L << bit));
        }
        Random random = new Random(1234);
        for (int i = 0; i < 1000; i++) {
            trailing(random.nextInt());
            leading(random.nextLong());
        }
    }
}
