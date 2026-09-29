import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.zip.CRC32;

public class NativeLibraryKernels {
    static void attempt(String label, Runnable action) {
        try { action.run(); System.out.println(label + ":ok"); }
        catch (RuntimeException error) { System.out.println(label + ":" + error.getClass().getName()); }
    }

    static void text(String label, CharSequence value) {
        System.out.println(label + ":" + value.length() + ":" + Arrays.hashCode(value.toString().toCharArray()));
    }

    static void strings() {
        for (String value : new String[] {"", "a", "abaaba", "a\ud83d\ude42b\ud83dX\ude42", "a".repeat(2051) + "xy"}) {
            text("chars", value);
            for (int start : new int[] {Integer.MIN_VALUE, -1, 0, 1, value.length(), Integer.MAX_VALUE}) {
                for (int code : new int[] {-1, 0, 'a', 'x', 0xd83d, 0xde42, 0x1f642, 0x10ffff, 0x110000}) {
                    System.out.println("cp-search:" + value.indexOf(code, start) + ":" + value.lastIndexOf(code, start));
                }
                for (String needle : new String[] {"", "a", "ab", "xy", "\ud83d\ude42", "missing"}) {
                    System.out.println("search:" + value.indexOf(needle, start) + ":" + value.startsWith(needle, start));
                }
            }
            for (int start = 0; start <= Math.min(7, value.length()); start++) {
                for (int end = start; end <= Math.min(7, value.length()); end++)
                    System.out.println("cp-count:" + value.codePointCount(start, end));
                final int index = start;
                for (int offset : new int[] {-3, -1, 0, 1, 3}) {
                    attempt("cp-offset", () -> System.out.println(value.offsetByCodePoints(index, offset)));
                }
            }
            char[] destination = new char[value.length() + 4];
            Arrays.fill(destination, '!');
            value.getChars(0, value.length(), destination, 2);
            System.out.println("getChars:" + Arrays.hashCode(destination));
        }
        String large = "a\ud83d\ude42".repeat(2049);
        System.out.println("cp-long:" + large.codePointCount(0, large.length()) + ":"
                + large.offsetByCodePoints(0, 4098) + ":" + large.offsetByCodePoints(large.length(), -4098));
        System.out.println("prefix-long:" + large.startsWith(large) + ":" + large.startsWith(large + "x"));
        attempt("search-null", () -> "a".indexOf((String)null, -1));
        attempt("prefix-null", () -> "a".startsWith(null, -1));
        attempt("prefix-null-zero", () -> "a".startsWith(null, 0));
        attempt("getChars-null", () -> "a".getChars(0, 1, null, 0));
        attempt("getChars-null-range", () -> "a".getChars(-1, 1, null, -1));
        attempt("getChars-empty-null", () -> "a".getChars(0, 0, null, 0));
        attempt("getChars-destination", () -> "a".getChars(0, 1, new char[1], -1));
        attempt("getChars-overflow", () -> "a".getChars(0, 1, new char[1], Integer.MAX_VALUE));
        attempt("getChars-order", () -> "a".getChars(1, 0, new char[1], 0));
        attempt("count-range", () -> "a".codePointCount(1, 0));
        attempt("offset-range", () -> "a".offsetByCodePoints(-1, 0));
        attempt("offset-max", () -> "a".offsetByCodePoints(0, Integer.MAX_VALUE));
        attempt("offset-min", () -> "a".offsetByCodePoints(1, Integer.MIN_VALUE));
    }

    static final class Sequence implements CharSequence {
        final StringBuilder target;
        int calls;
        int observedLength;
        Sequence(StringBuilder target) { this.target = target; }
        public int length() { return 4; }
        public char charAt(int index) {
            System.gc();
            observedLength = observedLength * 10 + target.length();
            if (++calls == 3) throw new IllegalStateException();
            return (char)('a' + index);
        }
        public CharSequence subSequence(int start, int end) { throw new UnsupportedOperationException(); }
    }

    static final class CountingCRC32 extends CRC32 {
        int calls;
        int arguments;
        public void update(int value) {
            System.gc();
            ++calls;
            arguments += value;
            if (calls == 3) throw new IllegalStateException();
            super.update(value);
        }
    }

    // Preserve the existing emulation's callback/partial-mutation contract.
    // The JDK uses different internals for these extensibility edge cases.
    static void retainedCallbacks() {
        StringBuilder target = new StringBuilder("base");
        Sequence sequence = new Sequence(target);
        try { target.append(sequence, 0, 4); throw new AssertionError(); }
        catch (IllegalStateException expected) { }
        if (!target.toString().equals("baseab") || sequence.calls != 3 || sequence.observedLength != 456)
            throw new AssertionError();
        CountingCRC32 crc = new CountingCRC32();
        try { crc.update(new byte[] {-1, 2, 3, 4}); throw new AssertionError(); }
        catch (IllegalStateException expected) { }
        CRC32 reference = new CRC32();
        reference.update(-1); reference.update(2);
        if (crc.calls != 3 || crc.arguments != 4 || crc.getValue() != reference.getValue())
            throw new AssertionError();
        ArrayDeque<String> deque = new ArrayDeque<String>();
        deque.add("a"); deque.add("b");
        Iterator<String> iterator = deque.iterator();
        iterator.next(); deque.add("c");
        try { iterator.next(); throw new AssertionError(); }
        catch (ConcurrentModificationException expected) { }
        System.out.println("callbacks-ok");
    }

    static void builders() {
        String input = "a\ud83d\ude42z".repeat(800);
        StringBuilder builder = new StringBuilder(0);
        builder.append(input).insert(1, "inserted");
        text("builder", builder);
        String snapshot = builder.toString();
        builder.append((CharSequence)builder, 1, 1027);
        text("self", builder);
        builder.append((CharSequence)input, 1, input.length() - 1);
        StringBuilder source = new StringBuilder("start" + input);
        builder.append((CharSequence)source, 2, source.length());
        text("builder-range", builder);
        builder.delete(2, 2055);
        builder.setLength(builder.length() + 2051);
        text("zero-extend", builder);
        builder.setLength(3);
        builder.setLength(2052);
        text("truncate-extend", builder);
        text("snapshot", snapshot);
        builder.setLength(0);
        builder.append((String)null).insert(0, (String)null).append((CharSequence)null, 1, 3);
        text("null-builder", builder);
        attempt("builder-negative", () -> builder.append((CharSequence)source, -1, 2));
        attempt("builder-order", () -> builder.append((CharSequence)source, 2, 1));
        attempt("builder-large", () -> builder.append((CharSequence)source, 0, Integer.MAX_VALUE));
        StringBuffer buffer = new StringBuffer();
        buffer.append(input).append(input);
        text("buffer", buffer);
    }

    static void deques() {
        for (int capacity : new int[] {1, 2, 3, 16, 1025}) {
            for (int rotate : new int[] {0, 1, capacity - 1}) {
                ArrayDeque<String> deque = new ArrayDeque<String>(capacity);
                for (int i = 0; i < capacity; i++) deque.addLast("v" + i);
                for (int i = 0; i < rotate; i++) deque.addLast(deque.removeFirst());
                Iterator<String> iterator = deque.iterator();
                int seen = 0;
                while (iterator.hasNext()) {
                    iterator.next();
                    if ((seen++ % 3) != 1) iterator.remove();
                }
                System.out.println("deque-remove:" + capacity + ":" + rotate + ":" + Arrays.hashCode(deque.toArray()));
                for (int i = 0; i <= capacity; i++) deque.addFirst("n" + i);
                System.out.println("deque-grow:" + deque.size() + ":" + Arrays.hashCode(deque.toArray()));
                deque.clear();
                deque.addLast("reused");
                System.out.println("deque-clear:" + deque.removeFirst() + ":" + deque.isEmpty());
            }
        }
        ArrayDeque<String> deque = new ArrayDeque<String>(3);
        deque.add("a"); deque.add("b"); deque.add("c");
        Iterator<String> iterator = deque.iterator();
        attempt("remove-before-next", () -> iterator.remove());
        iterator.next(); iterator.remove();
        attempt("remove-twice", () -> iterator.remove());
        deque.clear();
        attempt("deque-fail-fast", () -> iterator.next());
        attempt("deque-null", () -> deque.add(null));
    }

    static void codecs() {
        for (int length : new int[] {0, 1, 2, 3, 4, 63, 64, 65, 1023, 1024, 1025, 8193}) {
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) bytes[i] = (byte)(i * 37 + 13);
            byte[] encoded = Base64.getEncoder().encode(bytes);
            String encodedString = Base64.getEncoder().encodeToString(bytes);
            byte[] decoded = Base64.getDecoder().decode(encoded);
            byte[] fromString = Base64.getDecoder().decode(encodedString);
            System.out.println("base64:" + length + ":" + Arrays.hashCode(encoded) + ":" + encodedString.hashCode()
                    + ":" + Arrays.equals(bytes, decoded) + ":" + Arrays.equals(bytes, fromString));
            CRC32 crc = new CRC32();
            crc.update(bytes);
            long whole = crc.getValue();
            crc.reset();
            int middle = length / 2;
            crc.update(bytes, 0, middle);
            crc.update(bytes, middle, length - middle);
            long split = crc.getValue();
            crc.reset();
            for (int i = 0; i < length; i++) crc.update(bytes[i]);
            System.out.println("crc:" + length + ":" + whole + ":" + split + ":" + crc.getValue());
        }
        for (String encoded : new String[] {"", "AA", "AAA", "AAAA", "AA==", "AAA=", "AB==", "AAB=",
                "A", "=", "AA=", "A===", "AA===", "AAAA=", "AA==A", "A=A=", "AA A", "AA\nA", "____", "éé", "\ud83d\ude42"}) {
            attempt("decode-string", () -> System.out.println(Arrays.hashCode(Base64.getDecoder().decode(encoded))));
            byte[] bytes = new byte[encoded.length()];
            for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)encoded.charAt(i);
            attempt("decode-bytes", () -> System.out.println(Arrays.hashCode(Base64.getDecoder().decode(bytes))));
        }
        attempt("encode-null", () -> Base64.getEncoder().encode((byte[])null));
        attempt("encode-string-null", () -> Base64.getEncoder().encodeToString(null));
        attempt("decode-null", () -> Base64.getDecoder().decode((byte[])null));
        attempt("decode-string-null", () -> Base64.getDecoder().decode((String)null));
        CRC32 crc = new CRC32();
        for (int value : new int[] {-1, 0, 255, 256, 257, Integer.MIN_VALUE, Integer.MAX_VALUE}) crc.update(value);
        System.out.println("crc-scalar:" + crc.getValue());
        attempt("crc-null", () -> crc.update((byte[])null));
        attempt("crc-negative", () -> crc.update(new byte[1], -1, 1));
        attempt("crc-length", () -> crc.update(new byte[1], 0, -1));
        attempt("crc-overflow", () -> crc.update(new byte[1], Integer.MAX_VALUE, 1));
        attempt("crc-null-range", () -> crc.update(null, -1, 0));
        System.out.println("crc-unchanged:" + crc.getValue());
    }

    static void collectionStress() throws Exception {
        Thread collector = new Thread(() -> { for (int i = 0; i < 32; i++) System.gc(); });
        collector.start();
        long checksum = 0;
        for (int round = 0; round < 16; round++) {
            String value = new String(new char[] {'a', '\ud83d', '\ude42'}).repeat(4097);
            char[] chars = value.toCharArray();
            byte[] bytes = Base64.getEncoder().encode(new byte[8193]);
            CRC32 crc = new CRC32();
            crc.update(Base64.getDecoder().decode(bytes));
            StringBuilder builder = new StringBuilder(value);
            builder.append((CharSequence)builder, 0, builder.length());
            checksum += crc.getValue() + chars.length + value.codePointCount(0, value.length())
                    + value.indexOf("missing") + builder.length();
        }
        collector.join();
        System.out.println("collection:" + checksum);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0) { retainedCallbacks(); return; }
        strings(); builders(); deques(); codecs(); collectionStress();
    }
}
