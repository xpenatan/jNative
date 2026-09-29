package java.nio.charset;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import java.util.Objects;

@NativeInclude("jn_charsets.hpp")
public class Charset {
    @NativeImport(value = "jnative::charset_lookup", managed = true, runtimeOnly = true)
    private static native int lookup(String name);

    @NativeImport(value = "jnative::charset_encode", managed = true, runtimeOnly = true)
    private static native byte[] encodeNative(String text, int encoding);

    @NativeImport(value = "jnative::charset_decode_bytes", managed = true, runtimeOnly = true, callbacksSynchronous = true,
            callbacks = {"java/nio/charset/Charset.malformed(I)V"},
            callbackKinds = {NativeImport.Invocation.STATIC})
    private static native String decodeNative(byte[] bytes, int offset, int length, int encoding, int action);

    private final String name;
    private final int encoding;

    Charset(String name, int encoding) {
        this.name = name;
        this.encoding = encoding;
    }

    public String name() {
        return name;
    }

    public String toString() {
        return name;
    }

    public static Charset defaultCharset() {
        return StandardCharsets.UTF_8;
    }

    public static Charset forName(String name) {
        switch(lookup(name)) {
            case 0: return StandardCharsets.UTF_8;
            case 1: return StandardCharsets.US_ASCII;
            case 2: return StandardCharsets.ISO_8859_1;
            case 3: return StandardCharsets.UTF_16BE;
            case 4: return StandardCharsets.UTF_16LE;
            case 5: return StandardCharsets.UTF_16;
            default: throw new UnsupportedCharsetException(name);
        }
    }

    public CharsetDecoder newDecoder() {
        return new CharsetDecoder(this);
    }

    public byte[] encodeBytes(String text) {
        return encodeNative(text, encoding);
    }

    public String decodeBytes(byte[] bytes, int offset, int length, boolean report) {
        return decodeNative(bytes, offset, length, encoding, report ? 0 : 1);
    }

    int encoding() {
        return encoding;
    }

    private static void malformed(int length) {
        Charset.<RuntimeException>throwUnchecked(new MalformedInputException(length));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable error) throws T {
        throw (T)error;
    }
}
