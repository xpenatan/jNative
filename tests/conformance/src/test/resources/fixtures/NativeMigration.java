import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

public class NativeMigration {
    static void check(boolean value) { if(!value) throw new AssertionError(); }
    static class Items extends AbstractCollection<String> {
        final ArrayList<String> values = new ArrayList<>();
        public Iterator<String> iterator() { return values.iterator(); }
        public int size() { return values.size(); }
        public boolean add(String value) { System.gc(); return values.add(value); }
    }
    static class Bytes extends InputStream {
        int position;
        final int limit;
        final boolean failure;
        Bytes(int limit, boolean failure) { this.limit = limit; this.failure = failure; }
        public int read() throws IOException {
            System.gc();
            if(failure && position == 3) throw new IOException("read-failure");
            return position < limit ? position++ & 255 : -1;
        }
    }
    static class Sink extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        public void write(int value) { System.gc(); bytes.write(value); }
    }
    static void collections() {
        Items values = new Items();
        values.addAll(Arrays.asList("a", null, "b", "c"));
        check(values.contains(null));
        check(values.containsAll(Arrays.asList("c", "a")));
        check(values.remove(null));
        check(values.retainAll(Arrays.asList("b", "c")));
        check(Arrays.equals(values.toArray(new String[0]), new String[]{"b", "c"}));
        String[] extra = new String[]{"x", "x", "x", "x"};
        check(values.toArray(extra) == extra && extra[2] == null && extra[3].equals("x"));
        check(values.toString().equals("[b, c]"));
        final StringBuilder trace = new StringBuilder();
        values.forEach(x -> { System.gc(); trace.append(x); });
        check(trace.toString().equals("bc"));
        check(values.stream().filter(x -> { System.gc(); return x.equals("c"); }).count() == 1);
        check(values.stream().mapToLong(x -> { System.gc(); return x.length(); }).sum() == 2);
        check("hello".chars().allMatch(x -> x >= 'a'));
        check(!"ab1c".chars().allMatch(x -> x >= 'a'));
        List<String> list = new ArrayList<>(Arrays.asList("c", null, "a", "c"));
        check(list.indexOf("c") == 0 && list.lastIndexOf("c") == 3);
        list.remove(1);
        list.sort((a, b) -> { System.gc(); return a.compareTo(b); });
        check(list.equals(Arrays.asList("a", "c", "c")));
        check(list.hashCode() == Arrays.asList("a", "c", "c").hashCode());
        check(List.copyOf(list).toString().equals("[a, c, c]"));
        check(Set.of(new String[]{"a", "b"}).containsAll(Arrays.asList("b", "a")));
        check(Map.of("a", 1, "b", 2).get("b") == 2);
        check(new HashSet<>(list).hashCode() == 'a' + 'c');
        Map<String, Integer> map = new AbstractMap<>() {
            public Set<Entry<String, Integer>> entrySet() {
                Set<Entry<String, Integer>> entries = new HashSet<>();
                entries.add(new SimpleEntry<>("a", 1));
                entries.add(new SimpleEntry<>("b", 2));
                return entries;
            }
        };
        check(map.containsKey("a") && map.containsValue(2));
        check(map.get("b") == 2 && map.equals(Map.of("a", 1, "b", 2)));
        System.out.println(map.toString());
        values.clear(); check(values.isEmpty());
        System.out.println("collections-ok");
    }
    static void arrays() {
        int[] ints = {5, -1, 3, 3, 0}; Arrays.sort(ints);
        check(Arrays.equals(ints, new int[]{-1, 0, 3, 3, 5}));
        check(Arrays.binarySearch(ints, 4) == -5);
        long[] longs = {99, 5, -1, Long.MIN_VALUE, 2, 99}; Arrays.sort(longs, 1, 5);
        check(longs[1] == Long.MIN_VALUE && longs[4] == 5);
        String[] words = {"c", "b", "a", "b"};
        Arrays.sort(words, (a, b) -> { System.gc(); return a.compareTo(b); });
        check(Arrays.equals(words, new String[]{"a", "b", "b", "c"}));
        check(Arrays.binarySearch(words, "c") == 3);
        System.out.println(Arrays.toString(new char[]{'a', '\u03a9'}));
        System.out.println(Arrays.toString(new float[]{Float.NaN, -0.0f, 1.25f}));
        check(Arrays.equals(Arrays.copyOfRange(new byte[]{1, 2}, 1, 4), new byte[]{2, 0, 0}));
        System.out.println("arrays-ok");
    }
    static void io() throws Exception {
        byte[] data = new byte[8];
        check(new Bytes(8, true).read(data, 1, 6) == 3);
        check(data[1] == 0 && data[3] == 2);
        Bytes input = new Bytes(24, false);
        check(input.skip(9) == 9);
        byte[] remaining = input.readAllBytes();
        check(remaining.length == 15 && remaining[0] == 9 && remaining[14] == 23);
        Sink sink = new Sink();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeShort(0x1234); out.writeInt(0x89abcdef); out.writeLong(0x0123456789abcdefL);
        check(Arrays.equals(sink.bytes.toByteArray(), new byte[]{18,52,-119,-85,-51,-17,1,35,69,103,-119,-85,-51,-17}));
        System.out.println("io-ok");
    }
    static void charsets() throws Exception {
        String text = "a\u03a9\ud83d\ude03";
        for(Charset charset : new Charset[]{StandardCharsets.UTF_8, StandardCharsets.UTF_16,
                StandardCharsets.UTF_16BE, StandardCharsets.UTF_16LE}) {
            check(new String(text.getBytes(charset), charset).equals(text));
            check(charset.newDecoder().decode(ByteBuffer.wrap(text.getBytes(charset))).toString().equals(text));
        }
        check(Charset.forName("utf8") == StandardCharsets.UTF_8);
        byte[] malformed = {'a', (byte)0xe2, (byte)0x82};
        ByteBuffer source = ByteBuffer.wrap(malformed);
        try { StandardCharsets.UTF_8.newDecoder().decode(source); throw new AssertionError(); }
        catch(MalformedInputException expected) { check(expected.getInputLength() == 2 && source.position() == 1); }
        source.rewind();
        check(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE).decode(source).toString().equals("a"));
        check(source.position() == 3);
        source.rewind();
        check(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).decode(source).toString().equals("a\ufffd"));
        source = ByteBuffer.wrap(new byte[]{(byte)0xd8, 0, 0, 65});
        try { StandardCharsets.UTF_16BE.newDecoder().decode(source); throw new AssertionError(); }
        catch(MalformedInputException expected) { check(expected.getInputLength() == 4 && source.position() == 0); }
        check(CharBuffer.wrap(new StringBuilder("abc")).subSequence(1, 3).toString().equals("bc"));
        System.out.println("charsets-ok");
    }
    public static void main(String[] args) throws Exception {
        arrays(); collections(); io(); charsets();
    }
}
