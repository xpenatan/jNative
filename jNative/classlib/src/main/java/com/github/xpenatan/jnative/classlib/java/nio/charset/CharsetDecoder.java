package com.github.xpenatan.jnative.classlib.java.nio.charset;

import java.nio.charset.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import java.nio.ByteBuffer;
import com.github.xpenatan.jnative.classlib.java.nio.CharBuffer;
import com.github.xpenatan.jnative.classlib.java.util.Objects;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@NativeInclude("jn_charsets.hpp")
@SubstituteClass("java.nio.charset.CharsetDecoder")
public final class CharsetDecoder {
    @NativeImport(value = "jnative::charset_decode_buffer", managed = true, runtimeOnly = true, callbacksSynchronous = true,
            callbacks = {"java/nio/charset/Charset.malformed(I)V"},
            callbackKinds = {NativeImport.Invocation.STATIC})
    private static native String decodeNative(ByteBuffer source, int encoding, int malformed, int unmappable)
            throws CharacterCodingException;

    private static int action(CodingErrorAction value) {
        return value == CodingErrorAction.REPORT ? 0 : value == CodingErrorAction.REPLACE ? 1 : 2;
    }

    private final Charset charset;
    private CodingErrorAction malformed = CodingErrorAction.REPORT;
    private CodingErrorAction unmappable = CodingErrorAction.REPORT;

    CharsetDecoder(Charset charset) {
        this.charset = charset;
    }

    public CharsetDecoder onMalformedInput(CodingErrorAction action) {
        malformed = Objects.requireNonNull(action);
        return this;
    }

    public CharsetDecoder onUnmappableCharacter(CodingErrorAction action) {
        unmappable = Objects.requireNonNull(action);
        return this;
    }

    public CharBuffer decode(ByteBuffer source) throws CharacterCodingException {
        return CharBuffer.wrap(decodeNative(source, charset.encoding(), action(malformed), action(unmappable)));
    }
}
