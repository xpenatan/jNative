import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Iterator;
import java.util.zip.CRC32;

/** Retained before/after workload; run without forced-GC settings. */
public class NativeLibraryBenchmark {
    static volatile long sink;

    public static void main(String[] args) {
        for (int size : new int[] {16, 256, 4096}) {
            measure(size, false);
            for (int sample = 0; sample < 3; sample++) measure(size, true);
        }
    }

    static void row(boolean report, String operation, int size, int rounds, long start, long checksum) {
        long elapsed = System.nanoTime() - start;
        sink = checksum;
        if (report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + checksum);
    }

    static void measure(int size, boolean report) {
        byte[] bytes = new byte[size];
        for (int i = 0; i < size; i++) bytes[i] = (byte)(i * 37);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        byte[] encodedBytes = Base64.getEncoder().encode(bytes);
        int rounds = size == 16 ? 4000 : size == 256 ? 1000 : 100;
        long checksum = 0, start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            bytes[0] = (byte)i;
            byte[] result = Base64.getEncoder().encode(bytes);
            checksum += result[i % result.length];
        }
        row(report, "base64-encode", size, rounds, start, checksum);
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            byte[] result = Base64.getDecoder().decode(encodedBytes);
            checksum += result[i % result.length];
        }
        row(report, "base64-decode", size, rounds, start, checksum);
        CRC32 crc = new CRC32();
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < rounds * 10; i++) {
            bytes[0] = (byte)i;
            crc.reset();
            crc.update(bytes);
            checksum += crc.getValue();
        }
        row(report, "crc32", size, rounds * 10, start, checksum);
        String text = "a".repeat(size) + "xyz";
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < rounds * 10; i++) checksum += text.indexOf("xyz", i & 7);
        row(report, "string-search", size, rounds * 10, start, checksum);
        StringBuilder builder = new StringBuilder(size + 3);
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < rounds * 10; i++) {
            builder.setLength(0);
            builder.append(text);
            checksum += builder.charAt(i % size);
        }
        row(report, "builder-append", size, rounds * 10, start, checksum);
        ArrayDeque<String> deque = new ArrayDeque<String>(size + 1);
        for (int i = 0; i < size; i++) deque.addLast(encoded);
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            Iterator<String> iterator = deque.iterator();
            checksum += iterator.next().length();
            iterator.remove();
            deque.addLast(encoded);
        }
        row(report, "deque-remove", size, rounds, start, checksum);
    }
}
