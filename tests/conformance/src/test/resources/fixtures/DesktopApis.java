import java.io.*;
import java.math.BigDecimal;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.*;

public class DesktopApis {
    enum Mode {
        FIRST,
        SECOND
    }

    public static void main(String[] args) throws Exception {
        System.out.println(System.setProperty("desktop.check", "first"));
        System.out.println(System.setProperty("desktop.check", "second"));
        System.out.println(System.clearProperty("desktop.check"));
        System.out.println(System.getProperty("desktop.check", "missing"));
        System.out.println(Mode.valueOf("SECOND"));
        Mode[] constants = Mode.class.getEnumConstants();
        constants[0] = null;
        System.out.println(Mode.class.getEnumConstants()[0]);
        System.out.println(Mode.class.getSimpleName());
        System.out.println(
                Character.isLetter(0x10400) + ":" + Character.isJavaIdentifierPart(0x301));
        System.out.println(Character.toLowerCase(0x10400));
        String text = "a\u00e9\ud83d\ude03z";
        System.out.println(text.codePointCount(0, text.length()) + ":" + text.indexOf(0x1f603));
        System.out.println(" hello ".contains("ell") + ":" + "ABC".equalsIgnoreCase("abc"));
        System.out.println("\u00c9cole".toLowerCase(Locale.ROOT));
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        System.out.println(Arrays.toString(utf8));
        System.out.println(new String(utf8, StandardCharsets.UTF_8).equals(text));
        System.out.println(
                new String(text.getBytes(StandardCharsets.UTF_16), StandardCharsets.UTF_16)
                        .equals(text));
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(new byte[]{(byte)0xff}));
        } catch(CharacterCodingException expected) {
            System.out.println("invalid utf8");
        }
        System.out.println(String.format(Locale.ROOT, "%08d %.2f %s", 123, 3.25, "ok"));
        System.out.println(Float.parseFloat("0x1.8p2") + ":" + Double.parseDouble(" -1.25e2 "));
        System.out.println(new BigDecimal("9223372036854775807.00").longValueExact());
        System.out.println(new BigDecimal("24").intValueExact());
        System.out.println(-2147483648L);
        System.out.println(-4294967295L);
        System.out.println(Long.MIN_VALUE);
        try {
            new BigDecimal("1.2").intValueExact();
        } catch(ArithmeticException expected) {
            System.out.println("fraction");
        }
        System.out.println(new BigDecimal("18446744073709551617").longValue());
        System.out.println(
                Arrays.toString(
                        Base64.getDecoder().decode(Base64.getEncoder().encodeToString(utf8))));
        Matcher matcher = Pattern.compile("([a-z]+)=([0-9]+)").matcher("a=12; bb=34");
        while(matcher.find())
            System.out.println(matcher.start() + ":" + matcher.group(1) + ":" + matcher.group(2));
        System.out.println("a  b\tc".replaceAll("\\s+", "-") + ":" + "abc".matches("a.*"));

        byte[] backing = new byte[24];
        ByteBuffer bytes = ByteBuffer.wrap(backing).order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer ints = bytes.asIntBuffer();
        ints.put(new int[]{7, -2, 123456789});
        System.out.println(bytes.getInt(4) + ":" + Arrays.toString(Arrays.copyOf(backing, 8)));
        bytes.position(4);
        ByteBuffer slice = bytes.slice().order(ByteOrder.LITTLE_ENDIAN);
        slice.putInt(0, 15);
        System.out.println(
                ints.get(1) + ":" + (slice.array() == backing) + ":" + slice.arrayOffset());
        FloatBuffer floats =
                ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asFloatBuffer();
        floats.put(new float[]{1.5f, -2.25f});
        floats.flip();
        float[] values = new float[2];
        floats.get(values);
        System.out.println(Arrays.toString(values));
        try {
            floats.asReadOnlyBuffer().put(0, 9);
        } catch(ReadOnlyBufferException expected) {
            System.out.println("readonly");
        }

        TreeMap<Integer, String> tree = new TreeMap<>();
        for(int i = 199; i >= 0; i--) tree.put((i * 71) % 200, "v" + i);
        for(int i = 0; i < 200; i += 3) tree.remove(i);
        int sum = 0;
        for(Map.Entry<Integer, String> entry : tree.entrySet()) sum += entry.getKey();
        System.out.println(tree.size() + ":" + tree.firstKey() + ":" + tree.lastKey() + ":" + sum);
        List<String> words = new ArrayList<>(List.of("bbb", "a", "cc"));
        words.sort(Comparator.comparingInt(String::length).reversed());
        System.out.println(words);
        System.out.println(words.stream().filter(s -> s.length() > 1).count());
        System.out.println(words.stream().mapToLong(String::length).sum());
        System.out.println(Arrays.toString(words.toArray(String[]::new)));
        try {
            Collections.unmodifiableList(words).add("no");
        } catch(UnsupportedOperationException expected) {
            System.out.println("unmodifiable");
        }
        Map<Object, Integer> identities = new IdentityHashMap<>();
        identities.put(new String("same"), 1);
        identities.put(new String("same"), 2);
        System.out.println(identities.size());
        ConcurrentHashMap<String, Integer> concurrent = new ConcurrentHashMap<>();
        concurrent.putIfAbsent("value", 7);
        System.out.println(concurrent.putIfAbsent("value", 8) + ":" + concurrent.get("value"));
        Iterator<Map.Entry<String, Integer>> snapshot = concurrent.entrySet().iterator();
        concurrent.clear();
        boolean valid = true;
        while(snapshot.hasNext()) valid &= snapshot.next().getValue() != null;
        System.out.println(valid);

        byte[] payload =
                "compressed text with accents \u00e9\u00e8"
                        .repeat(100)
                        .getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater();
        deflater.setInput(payload);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] chunk = new byte[17];
        while(!deflater.finished()) output.write(chunk, 0, deflater.deflate(chunk));
        deflater.end();
        Inflater inflater = new Inflater();
        inflater.setInput(output.toByteArray());
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        while(!inflater.finished()) decoded.write(chunk, 0, inflater.inflate(chunk));
        inflater.end();
        System.out.println(Arrays.equals(payload, decoded.toByteArray()));
        CRC32 crc = new CRC32();
        crc.update(payload);
        System.out.println(crc.getValue());
        File file = new File("desktop-api.bin");
        try(OutputStream stream = new FileOutputStream(file)) {
            stream.write(payload);
        }
        try(InputStream stream = new FileInputStream(file)) {
            System.out.println(Arrays.equals(payload, stream.readAllBytes()) + ":" + file.length());
        }
        System.out.println(file.isFile());
        Files.delete(file.toPath());
        System.gc();
    }
}
