package java.nio.charset;

public final class StandardCharsets {
    private StandardCharsets() {
    }

    public static final Charset UTF_8 = new Charset("UTF-8", 0);
    public static final Charset US_ASCII = new Charset("US-ASCII", 1);
    public static final Charset ISO_8859_1 = new Charset("ISO-8859-1", 2);
    public static final Charset UTF_16BE = new Charset("UTF-16BE", 3);
    public static final Charset UTF_16LE = new Charset("UTF-16LE", 4);
    public static final Charset UTF_16 = new Charset("UTF-16", 5);
}
