import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.*;

/** Same compiled fixture is used for both retained toolchains. Timings include
 * construction only where the operation name says construct/roundtrip. */
public class FullLibraryBenchmark {
    static volatile long sink;
    static class Sequence implements CharSequence {
        final String text;
        Sequence(String text) { this.text = text; }
        public int length() { return text.length(); }
        public char charAt(int i) { return text.charAt(i); }
        public CharSequence subSequence(int a, int b) { return text.substring(a, b); }
    }
    static class Input extends InputStream {
        final byte[] bytes;
        int position;
        Input(byte[] bytes) { this.bytes = bytes; }
        public int read() { return position == bytes.length ? -1 : bytes[position++] & 255; }
    }
    static class Checksum extends CRC32 {
        public void update(int value) { super.update(value); }
    }
    static class Key {
        final int value;
        Key(int value) { this.value = value; }
        public int hashCode() { return value & 7; }
        public boolean equals(Object other) { return other instanceof Key && ((Key)other).value == value; }
    }
    static void row(boolean report, String operation, int size, int rounds, long start, long result) {
        long elapsed = System.nanoTime() - start;
        sink = result;
        if(report) System.out.println("BENCH," + operation + "," + size + "," + rounds + "," + elapsed + "," + result);
    }
    static void measure(int size, boolean report) throws Exception {
        int rounds = size < 16 ? 300 : size < 256 ? 200 : size < 4096 ? 20 : 3;
        int[] numbers = new int[size];
        byte[] bytes = new byte[size];
        ArrayList<String> list = new ArrayList<>();
        HashMap<Integer, Integer> map = new HashMap<>();
        HashMap<Key, Integer> collisions = new HashMap<>();
        ConcurrentHashMap<Integer, Integer> concurrent = new ConcurrentHashMap<>();
        for(int i = 0; i < size; ++i) {
            numbers[i] = size - i; bytes[i] = (byte)(i * 37); list.add("item"); map.put(i, i);
            collisions.put(new Key(i), i); concurrent.put(i, i);
        }
        String text = "a".repeat(size), mixed = "a,b,".repeat(size / 4);
        Sequence sequence = new Sequence(text);
        StringBuilder builder = new StringBuilder(size + 1);
        Pattern pattern = Pattern.compile(",");
        CRC32 crc = new CRC32(), subclass = new Checksum();
        long sum = 0, start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) {
            int[] copy = Arrays.copyOf(numbers, numbers.length); Arrays.sort(copy); sum += Arrays.hashCode(copy);
        }
        row(report, "array-sort-copy", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += list.indexOf("missing") + list.hashCode();
        row(report, "collection-scan", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        Key early = new Key(0), late = new Key(size - 1), missing = new Key(size + 8);
        for(int r = 0; r < rounds; ++r) sum += (collisions.get(early) == null ? 0 : 1)
                + (collisions.get(late) == null ? 0 : 1) + (collisions.get(missing) == null ? 0 : 1);
        row(report, "hashmap-collisions", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) { HashMap<Integer, Integer> copy = new HashMap<>(map); sum += copy.size(); }
        row(report, "hashmap-construct", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) { TreeMap<Integer, Integer> tree = new TreeMap<>(); tree.putAll(map); sum += tree.size(); }
        row(report, "treemap-construct", size, rounds, start, sum);
        TreeMap<Integer, Integer> tree = new TreeMap<>(); tree.putAll(map);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) {
            tree.put(size, r); sum += tree.remove(size);
        }
        row(report, "treemap-mutate", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += concurrent.entrySet().toArray().length + map.keySet().toArray().length;
        row(report, "views-snapshot", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += List.copyOf(list).size();
        row(report, "factory-populate", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += list.stream().filter(x -> x.length() == 4).mapToLong(x -> x.length()).sum();
        row(report, "stream-callbacks", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += text.replace("a", "bc").length() + text.toUpperCase(Locale.ROOT).length();
        row(report, "string-replace-case", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) { builder.setLength(0); builder.append(sequence, 0, sequence.length()); sum += builder.length(); }
        row(report, "builder-generic", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).length();
        row(report, "charset-roundtrip", size, rounds, start, sum);
        byte[] malformed = Arrays.copyOf(bytes, size + 1); malformed[size] = (byte)0xff;
        Arrays.fill(malformed, 0, size, (byte)'a');
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE).decode(ByteBuffer.wrap(malformed)).length();
        row(report, "charset-replace", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += Base64.getDecoder().decode(Base64.getEncoder().encode(bytes)).length;
        row(report, "base64-roundtrip", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) { crc.reset(); crc.update(bytes); sum += crc.getValue(); }
        row(report, "crc", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) { subclass.reset(); subclass.update(bytes); sum += subclass.getValue(); }
        row(report, "crc-subclass", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += new Input(bytes).readAllBytes().length;
        row(report, "io-generic-construct", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += pattern.split(mixed, -1).length + pattern.matcher(mixed).replaceAll("--").length();
        row(report, "regex-split-replace", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        byte[] compressed = new byte[size * 2 + 64], output = new byte[size + 1];
        for(int r = 0; r < rounds; ++r) {
            Deflater deflater = new Deflater(); deflater.setInput(bytes); deflater.finish();
            int length = deflater.deflate(compressed); deflater.end();
            Inflater inflater = new Inflater(); inflater.setInput(compressed, 0, length);
            sum += inflater.inflate(output); inflater.end();
        }
        row(report, "zlib-roundtrip", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        String digits = "1".repeat(Math.min(18, Math.max(1, size)));
        for(int r = 0; r < rounds; ++r) sum += Long.parseLong(digits) + new BigDecimal(digits + ".25").longValue();
        row(report, "numbers-construct", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) sum += String.format(Locale.ROOT, "%s:%04d", text, r).length();
        row(report, "format", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        for(int r = 0; r < rounds; ++r) {
            String encoded = Base64.getEncoder().encodeToString(text.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            ArrayList<String> records = new ArrayList<>(Arrays.asList(pattern.split(encoded + "," + text, -1)));
            records.sort(null);
            sum += records.stream().filter(x -> !x.isEmpty()).mapToLong(x -> x.length()).sum();
        }
        row(report, "combined-pipeline", size, rounds, start, sum);
        sum = 0; start = System.nanoTime();
        File file = new File("full-library-benchmark.bin");
        try {
            for(int r = 0; r < Math.min(3, rounds); ++r) {
                try(FileOutputStream out = new FileOutputStream(file)) { out.write(bytes); out.write(127); }
                try(FileInputStream in = new FileInputStream(file)) { sum += in.read(); sum += in.readAllBytes().length; }
            }
        } finally { file.delete(); }
        row(report, "file-roundtrip", size, Math.min(3, rounds), start, sum);
    }
    public static void main(String[] args) throws Exception {
        for(int size : new int[]{0, 1, 16, 256, 4096}) {
            measure(size, false);
            for(int sample = 0; sample < 3; ++sample) measure(size, true);
        }
    }
}
