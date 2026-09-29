package java.util;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("jn_codecs.hpp")
public final class Base64 {
    private static final Encoder ENCODER = new Encoder();
    private static final Decoder DECODER = new Decoder();

    private Base64() {
    }

    public static Encoder getEncoder() {
        return ENCODER;
    }

    public static Decoder getDecoder() {
        return DECODER;
    }

    @NativeImport(value = "jnative::base64_encode", managed = true, runtimeOnly = true)
    private static native byte[] encodeBytes(byte[] bytes);

    @NativeImport(value = "jnative::base64_encode_string", managed = true, runtimeOnly = true)
    private static native String encodeString(byte[] bytes);

    @NativeImport(value = "jnative::base64_decode", managed = true, runtimeOnly = true)
    private static native byte[] decodeBytes(byte[] bytes);

    @NativeImport(value = "jnative::base64_decode_string", managed = true, runtimeOnly = true)
    private static native byte[] decodeString(String text);

    public static final class Encoder {
        private Encoder() {
        }

        public String encodeToString(byte[] bytes) {
            return encodeString(bytes);
        }

        public byte[] encode(byte[] bytes) {
            return encodeBytes(bytes);
        }
    }

    public static final class Decoder {
        private Decoder() {
        }

        public byte[] decode(byte[] bytes) {
            return decodeBytes(bytes);
        }

        public byte[] decode(String text) {
            return decodeString(text);
        }
    }
}
