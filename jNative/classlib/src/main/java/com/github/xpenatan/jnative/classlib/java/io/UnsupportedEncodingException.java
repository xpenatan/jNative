package com.github.xpenatan.jnative.classlib.java.io;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.io.*;

@SubstituteClass("java.io.UnsupportedEncodingException")
public class UnsupportedEncodingException extends IOException {
    public UnsupportedEncodingException() {
    }

    public UnsupportedEncodingException(String message) {
        super(message);
    }
}
