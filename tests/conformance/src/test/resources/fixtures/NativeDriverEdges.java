import java.io.*;
import java.math.BigDecimal;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.CRC32;

public class NativeDriverEdges {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static String units(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) result.append((int)text.charAt(i)).append(',');
        return result.toString();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) { policies(); return; }
        charsets(); sequences(); streams(); input(); concurrentScan();
    }

    static void charsets() throws Exception {
        Charset[] encodings = {StandardCharsets.UTF_8, StandardCharsets.US_ASCII, StandardCharsets.ISO_8859_1,
                StandardCharsets.UTF_16BE, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16};
        byte[][] cases = {{}, {65}, {65, (byte)0xe2, (byte)0x82}, {(byte)0xc0, (byte)0xaf},
                {(byte)0xe0, (byte)0x80, 66}, {(byte)0xed, (byte)0xa0, (byte)0x80},
                {(byte)0xf0, (byte)0x9f, (byte)0x98, (byte)0x83},
                {(byte)0xf4, (byte)0x90, (byte)0x80, (byte)0x80},
                {(byte)0xd8, 0, 0, 65}, {(byte)0xdc, 0}, {(byte)0xd8, 0, 0},
                {(byte)0xfe, (byte)0xff, 0, 65, (byte)0xd8, 0},
                {(byte)0xff, (byte)0xfe, 65, 0, 0, (byte)0xd8},
                {0, 65, (byte)0xd8, 0, (byte)0xdc, 0}, {65, 0, 0, (byte)0xd8, 0, (byte)0xdc}};
        CodingErrorAction[] actions = {CodingErrorAction.REPORT, CodingErrorAction.REPLACE, CodingErrorAction.IGNORE};
        for (int encoding = 0; encoding < encodings.length; encoding++) {
            for (int sample = 0; sample < cases.length; sample++) {
                for (int malformed = 0; malformed < actions.length; malformed++) {
                    for (int unmappable = 0; unmappable < actions.length; unmappable++) {
                        for (int direct = 0; direct < 2; direct++) {
                            byte[] bytes = cases[sample];
                            ByteBuffer source = direct == 0 ? ByteBuffer.allocate(bytes.length + 2)
                                    : ByteBuffer.allocateDirect(bytes.length + 2);
                            source.put((byte)99).put(bytes).put((byte)98);
                            source.position(1); source.limit(bytes.length + 1);
                            if (sample % 3 == 0) source = source.asReadOnlyBuffer();
                            CharsetDecoder decoder = encodings[encoding].newDecoder()
                                    .onMalformedInput(actions[malformed]).onUnmappableCharacter(actions[unmappable]);
                            String result;
                            try { result = "ok:" + units(decoder.decode(source).toString()); }
                            catch (MalformedInputException error) { result = "bad:" + error.getInputLength(); }
                            System.out.println("decode:" + encoding + ":" + sample + ":" + malformed + ":"
                                    + unmappable + ":" + direct + ":" + source.position() + ":" + source.limit() + ":" + result);
                        }
                    }
                }
            }
            for (String text : new String[] {"", "a\u00e9\u03a9\ud83d\ude03", "x\ud800y\udc00z"}) {
                System.out.println("encode:" + encoding + ":" + Arrays.toString(text.getBytes(encodings[encoding])));
            }
        }
    }

    static final class Sequence implements CharSequence {
        final StringBuilder trace = new StringBuilder();
        final char[] values = {'a', 'b', 'c', 'd'};
        CharBuffer buffer;
        boolean inside, reentrant, failure;
        public int length() { return values.length; }
        public char charAt(int index) {
            trace.append(index); System.gc();
            if (failure && index == 2) throw new IllegalStateException("character");
            if (reentrant && index == 1 && !inside) {
                inside = true;
                check(buffer.subSequence(0, 1).toString().equals("a"));
                values[2] = 'Z'; inside = false;
            }
            return values[index];
        }
        public CharSequence subSequence(int from, int to) {
            StringBuilder result = new StringBuilder();
            for (int i = from; i < to; i++) result.append(charAt(i));
            return result;
        }
        public String toString() { return subSequence(0, length()).toString(); }
    }
    static void sequences() {
        Sequence sequence = new Sequence();
        sequence.buffer = CharBuffer.wrap(sequence); sequence.reentrant = true;
        System.out.println("reentrant:" + sequence.buffer + ":" + sequence.trace);
        sequence.trace.setLength(0); sequence.reentrant = false; sequence.failure = true;
        try { sequence.buffer.toString(); throw new AssertionError(); }
        catch (IllegalStateException expected) { System.out.println("throwing:" + sequence.trace); }
        sequence.failure = false; sequence.trace.setLength(0);
        System.out.println("retry:" + sequence.buffer.subSequence(1, 4) + ":" + sequence.trace);
    }

    static void streams() {
        StringBuilder trace = new StringBuilder();
        Stream<Integer> filtered = Arrays.asList(1, 2, 3, 4).stream().filter(value -> {
            trace.append(value); System.gc(); return value % 2 == 0;
        });
        System.out.println("lazy:" + trace.length());
        long sum = filtered.mapToLong(value -> { trace.append('m').append(value); System.gc(); return value; }).sum();
        System.out.println("stream:" + sum + ":" + trace);
        trace.setLength(0);
        try {
            Arrays.asList(1, 2, 3, 4).stream().filter(value -> {
                trace.append(value); System.gc(); if (value == 3) throw new IllegalStateException(); return true;
            }).mapToLong(value -> { trace.append('m').append(value); return value; }).sum();
            throw new AssertionError();
        } catch (IllegalStateException expected) { System.out.println("filter-failure:" + trace); }
        trace.setLength(0);
        try {
            Arrays.asList(1, 2, 3).stream().mapToLong(value -> {
                trace.append(value); System.gc(); if (value == 2) throw new IllegalStateException(); return value;
            }).sum();
            throw new AssertionError();
        } catch (IllegalStateException expected) { System.out.println("mapper-failure:" + trace); }
        trace.setLength(0);
        boolean matches = "ab1z".chars().allMatch(value -> { trace.append((char)value); System.gc(); return value >= 'a'; });
        System.out.println("short-circuit:" + matches + ":" + trace);
        trace.setLength(0);
        check("".chars().allMatch(value -> { throw new AssertionError(); }));
        System.out.println("overflow:" + Arrays.asList(Long.MAX_VALUE, 1L).stream().mapToLong(value -> value).sum());
    }

    static final class ThrowingInput extends InputStream {
        int count;
        final int failAt;
        ThrowingInput(int failAt) { this.failAt = failAt; }
        public int read() throws IOException { System.gc(); if (count == failAt) throw new IOException("failure"); return ++count; }
    }
    static final class ZeroInput extends InputStream {
        int zeros = 3, position;
        public int read() { return position < 7 ? ++position : -1; }
        public int read(byte[] bytes, int offset, int length) {
            System.gc(); if (zeros-- > 0) return 0;
            if (position == 7) return -1;
            int count = Math.min(length, 7 - position);
            for (int i = 0; i < count; i++) bytes[offset + i] = (byte)++position;
            return count;
        }
    }
    static void input() throws Exception {
        byte[] bytes = new byte[8]; Arrays.fill(bytes, (byte)99);
        ThrowingInput input = new ThrowingInput(2);
        System.out.println("partial-read:" + input.read(bytes, 2, 4) + ":" + Arrays.toString(bytes));
        try { input.read(bytes, 1, 2); throw new AssertionError(); }
        catch (IOException expected) { System.out.println("first-failure:" + Arrays.toString(bytes)); }
        check(new ThrowingInput(0).read(bytes, 0, 0) == 0);
        System.out.println("zero-read-all:" + Arrays.toString(new ZeroInput().readAllBytes()));
        ZeroInput skip = new ZeroInput();
        System.out.println("zero-skip:" + skip.skip(5) + ":" + skip.read());
    }

    static volatile boolean ready, done;
    static void concurrentScan() throws Exception {
        String text = "a\u03a9\ud83d\ude03".repeat(32769);
        Thread collector = new Thread(() -> {
            ready = true;
            for (int i = 0; i < 64 && !done; i++) { System.gc(); Thread.yield(); }
        });
        collector.start();
        while (!ready) Thread.yield();
        try {
            for (int i = 0; i < 3; i++) {
                byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
                check(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(encoded)).toString().equals(text));
                check(text.compareToIgnoreCase(text.toUpperCase(Locale.ROOT)) == 0);
                check(text.codePointCount(0, text.length()) == 98307);
                check(text.replace("\u03a9", "\u03a9\u03a9").length() == 163845);
                check(Integer.parseInt("0".repeat(65537)) == 0);
                check(new BigDecimal("0".repeat(65537) + "1").longValueExact() == 1);
                try { Integer.parseInt("0".repeat(65537) + "!"); throw new AssertionError(); }
                catch (NumberFormatException expected) {
                    check(expected.getMessage().length() >= 65538);
                    check(expected.toString().endsWith(expected.getMessage()));
                }
            }
        } finally { done = true; collector.join(); }
        System.out.println("concurrent-scan:ok");
    }

    static final class IntOverride extends DataOutputStream {
        final StringBuilder calls = new StringBuilder();
        IntOverride(OutputStream output) { super(output); }
        // The test renames this method to writeInt in bytecode for the native
        // profile, whose existing writeInt is overridable; the JDK marks it final.
        public void recordInt(int value) throws IOException { calls.append(value).append(','); System.gc(); super.writeInt(value); }
    }
    static final class ReentrantCRC extends CRC32 {
        int calls;
        public void update(int value) {
            calls++; System.gc(); super.update(value);
            if (calls == 1) super.update(77);
            if (calls == 2) throw new IllegalStateException();
        }
    }
    static final class BulkFile extends FileInputStream {
        int calls;
        BulkFile(String path) throws FileNotFoundException { super(path); }
        public int read(byte[] bytes, int offset, int length) {
            System.gc(); calls++;
            if (calls > 1) return -1;
            bytes[offset] = 81; return 1;
        }
    }
    static void policies() throws Exception {
        // Existing emulation dispatches these bulk operations through overrides;
        // the host JDK uses concrete/final methods that bypass them.
        ReentrantCRC crc = new ReentrantCRC();
        try { crc.update(new byte[] {1, 2, 3}); throw new AssertionError(); }
        catch (IllegalStateException expected) { }
        CRC32 expected = new CRC32(); expected.update(1); expected.update(77); expected.update(2);
        check(crc.calls == 2 && crc.getValue() == expected.getValue());
        System.out.println("crc-after-write:ok");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        IntOverride output = new IntOverride(bytes);
        output.writeLong(0x0123456789abcdefL);
        check(output.calls.toString().equals("19088743,-1985229329,"));
        check(Arrays.equals(bytes.toByteArray(), new byte[] {1, 35, 69, 103, -119, -85, -51, -17}));
        System.out.println("write-long-dispatch:ok");
        String path = "driver-edge.bin";
        try (FileOutputStream file = new FileOutputStream(path)) { file.write(15); }
        try (BulkFile file = new BulkFile(path)) {
            check(file.read() == 81 && file.read() == -1 && file.calls == 2);
        }
        System.out.println("file-bulk-dispatch:ok");
        String invalid = "x\ud800y\udc00z";
        try { Integer.parseInt(invalid); throw new AssertionError(); }
        catch (NumberFormatException error) { check(error.getMessage() == invalid); }
        System.out.println("numeric-utf16-message:ok");
        try { Long.parseLong(invalid); throw new AssertionError(); }
        catch (NumberFormatException error) { check(error.getMessage() == invalid); }
        try { new BigDecimal(invalid); throw new AssertionError(); }
        catch (NumberFormatException error) { check(error.getMessage() == invalid); }
        System.out.println("decimal-utf16-message:ok");
    }
}
