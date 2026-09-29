import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;

/** Larger bounded workloads, shared as identical compiled classes by both tools. */
public class LibraryStressBenchmark {
    static volatile long sink;

    static void row(boolean report, String operation, int size, int rounds, long elapsed, long checksum) {
        sink = checksum;
        if (report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + checksum);
    }

    static void sort(int[] source, String operation, int rounds, boolean report) {
        int[][] work = new int[rounds][];
        for (int r = 0; r < rounds; r++) work[r] = Arrays.copyOf(source, source.length);
        long start = System.nanoTime();
        for (int r = 0; r < rounds; r++) Arrays.sort(work[r]);
        long elapsed = System.nanoTime() - start;
        // Input copies and checksum scans are outside the sorting timer.
        long checksum = 0;
        for (int r = 0; r < rounds; r++) checksum += Arrays.hashCode(work[r]);
        row(report, operation, source.length, rounds, elapsed, checksum);
    }

    static void search(ArrayList<Integer> list, Integer query, String operation, int rounds, boolean report) {
        long checksum = 0, start = System.nanoTime();
        for (int r = 0; r < rounds; r++) checksum += list.indexOf(query) + 1;
        row(report, operation, list.size(), rounds, System.nanoTime() - start, checksum);
    }

    static boolean heavier(int value) {
        int mixed = value;
        for (int i = 0; i < 12; i++) {
            mixed = mixed * 1664525 + 1013904223;
            mixed ^= mixed >>> 13;
        }
        return (mixed & 7) == 0;
    }

    static void measure(int size, boolean report) {
        int sortRounds = size == 65536 ? 2 : 8;
        int scanRounds = size == 65536 ? 64 : 256;
        int callbackRounds = size == 65536 ? 4 : 16;
        int textRounds = size == 65536 ? 4 : 16;
        int[] sorted = new int[size], reverse = new int[size], duplicates = new int[size];
        ArrayList<Integer> list = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            sorted[i] = i; reverse[i] = size - i; duplicates[i] = (i * 37) & 15;
            list.add(i);
        }
        int[] equal = Arrays.copyOf(sorted, size), mismatch = Arrays.copyOf(sorted, size);
        mismatch[size - 1] ^= 1;
        Integer early = 0, late = size - 1, missing = -1;
        String shortCircuit = "ab!" + "a".repeat(size - 3);
        String text = "a\u03a9\ud83d\ude03".repeat(size / 4);
        byte[] encodedText = text.getBytes(StandardCharsets.UTF_8);
        Base64.Encoder encoder = Base64.getEncoder();
        Base64.Decoder decoder = Base64.getDecoder();
        // One bounded regular-expression match; no long recursive repetition or
        // many-match traversal that repeatedly stages an entire large input.
        Pattern groups = Pattern.compile("^([a-z]{1,8})([0-9]{1,4})");
        String regexText = "abc1234:" + "q".repeat(size - 8);

        sort(sorted, "array-sort-sorted", sortRounds, report);
        sort(reverse, "array-sort-reverse", sortRounds, report);
        sort(duplicates, "array-sort-duplicates", sortRounds, report);
        long checksum = 0, start = System.nanoTime();
        for (int r = 0; r < scanRounds; r++) checksum += Arrays.equals(sorted, equal) ? 1 : 0;
        row(report, "array-equal", size, scanRounds, System.nanoTime() - start, checksum);
        checksum = 0; start = System.nanoTime();
        for (int r = 0; r < scanRounds; r++) checksum += Arrays.equals(sorted, mismatch) ? 1 : 0;
        row(report, "array-late-mismatch", size, scanRounds, System.nanoTime() - start, checksum);
        search(list, early, "list-search-early", scanRounds, report);
        search(list, late, "list-search-late", scanRounds, report);
        search(list, missing, "list-search-missing", scanRounds, report);

        checksum = 0; start = System.nanoTime();
        for (int r = 0; r < callbackRounds; r++) {
            checksum += list.stream().filter(value -> (value & 1) == 0).count();
            checksum += shortCircuit.chars().allMatch(value -> value >= 'a') ? 1 : 0;
        }
        row(report, "stream-cheap-filter-short", size, callbackRounds, System.nanoTime() - start, checksum);
        checksum = 0; start = System.nanoTime();
        for (int r = 0; r < callbackRounds; r++) checksum += list.stream().filter(value -> heavier(value)).count();
        row(report, "stream-heavy-filter", size, callbackRounds, System.nanoTime() - start, checksum);

        checksum = 0; start = System.nanoTime();
        for (int r = 0; r < textRounds; r++) {
            String decoded = new String(encodedText, StandardCharsets.UTF_8);
            byte[] roundtrip = decoder.decode(encoder.encode(encodedText));
            checksum += decoded.length() + decoded.replace("\u03a9", "\u03a9\u03a9").length()
                    + Arrays.hashCode(roundtrip);
        }
        row(report, "text-codecs-roundtrip", size, textRounds, System.nanoTime() - start, checksum);
        checksum = 0; start = System.nanoTime();
        for (int r = 0; r < textRounds; r++) {
            String replaced = groups.matcher(regexText).replaceAll("$2/$1");
            String[] parts = groups.split(regexText, 2);
            checksum += replaced.length() + replaced.charAt(0) + replaced.charAt(4) + replaced.charAt(7)
                    + parts[0].length() + parts[1].length();
        }
        row(report, "regex-bounded-groups", size, textRounds, System.nanoTime() - start, checksum);
    }

    public static void main(String[] args) {
        for (int size : new int[] {4096, 65536}) {
            measure(size, false);
            for (int sample = 0; sample < 3; sample++) measure(size, true);
        }
    }
}
