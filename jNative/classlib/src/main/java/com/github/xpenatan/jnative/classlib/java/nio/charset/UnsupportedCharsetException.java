package com.github.xpenatan.jnative.classlib.java.nio.charset;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.nio.charset.*;

@SubstituteClass("java.nio.charset.UnsupportedCharsetException")
public class UnsupportedCharsetException extends IllegalArgumentException {
    private final String charsetName;

    public UnsupportedCharsetException(String charsetName) {
        super(charsetName);
        this.charsetName = charsetName;
    }

    public String getCharsetName() {
        return charsetName;
    }
}
