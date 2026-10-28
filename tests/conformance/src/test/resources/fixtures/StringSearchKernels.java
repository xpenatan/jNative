public class StringSearchKernels {
    static long checksum;
    static int cases;
    static volatile boolean stopCollector;

    static int temporarySearch() {
        char[] text = new char[8193];
        for (int i = 0; i < text.length; i++) text[i] = 'a';
        text[text.length - 1] = 'z';
        return new String(text).indexOf(new String(new char[]{'a', 'a', 'z'}));
    }

    static void concurrentSearch() throws InterruptedException {
        Thread collector = new Thread(() -> {
            while (!stopCollector) { System.gc(); Thread.yield(); }
        });
        collector.start();
        try {
            for (int i = 0; i < 16; i++) checksum = checksum * 31 + temporarySearch();
        } finally {
            stopCollector = true;
            collector.join();
        }
    }

    static void check(String text, String pattern) {
        int[] starts = {Integer.MIN_VALUE, -1, 0, 1, 2, 15, 1023, 1024, text.length() - 1, text.length(), text.length() + 1, Integer.MAX_VALUE};
        for (int start : starts) {
            checksum = checksum * 31 + text.indexOf(pattern, start);
            cases++;
        }
        checksum = checksum * 31 + text.hashCode();
        checksum = checksum * 31 + text.hashCode();
    }

    public static void main(String[] args) throws Exception {
        for (int length : new int[]{0, 1, 2, 3, 4, 5, 15, 16, 17, 31, 32, 33, 255, 256, 257, 1023, 1024, 1025, 4097}) {
            String prefix = "a".repeat(length);
            check(prefix + "b" + prefix + "b", prefix + "b");
            check(prefix + "b", "a".repeat(Math.max(0, length / 8)) + "b");
            check(prefix + "b", "a".repeat(Math.max(0, length / 8)) + "c");
            check(prefix, "");
            check(prefix, prefix + "b");
            check("ab".repeat(length) + "ac", "ab".repeat(length / 4) + "ac");
        }
        int random = 0x12345678;
        for (int i = 0; i < 400; i++) {
            char[] chars = new char[i * 7 % 2057];
            for (int j = 0; j < chars.length; j++) {
                random = random * 1664525 + 1013904223;
                chars[j] = (char) (i % 2 == 0 ? random >>> 16 : 'a' + (random >>> 29));
            }
            String text = new String(chars);
            int start = chars.length == 0 ? 0 : i % chars.length;
            check(text, text.substring(start, Math.min(chars.length, start + i % 129)));
            check(text, "\u0000\ud800\udfff");
        }
        check("\u0000\ud800\udfff\ud800x\udfff\u0000", "\ud800");
        check("\u0000\ud800\udfff\ud800x\udfff\u0000", "\udfff");
        // Every vector lane, chunk edge and final candidate, with a false
        // candidate sharing both anchors before the actual match.
        for (int padding : new int[]{33, 63, 511, 512, 513, 1023, 1024}) {
            for (int lane = 0; lane < 8; lane++) {
                String pattern = "\udfff\u0000\ud800";
                String text = "x".repeat(padding + lane) + "\udfffx\ud800" + pattern;
                check(text, pattern);
                check(text, "\udfffx\udfff");
                check(text, "\ud800");
                check("x".repeat(padding + lane) + "aaxba" + "aaaba", "aaaba");
            }
        }
        // Equal end characters do not imply a match: exercise the adaptive
        // fallback after many long interior prefix matches.
        check("a".repeat(24000), "a".repeat(1024) + "ba");
        try { "abc".indexOf((String) null); }
        catch (NullPointerException expected) { checksum++; }
        concurrentSearch();
        System.out.println(cases + ":" + checksum);
    }
}
