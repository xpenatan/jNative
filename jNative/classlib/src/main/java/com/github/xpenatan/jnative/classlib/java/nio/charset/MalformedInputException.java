package com.github.xpenatan.jnative.classlib.java.nio.charset;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.nio.charset.*;

@SubstituteClass("java.nio.charset.MalformedInputException")
public class MalformedInputException extends CharacterCodingException {
    private final int inputLength;

    public MalformedInputException(int inputLength) {
        this.inputLength = inputLength;
    }

    public int getInputLength() {
        return inputLength;
    }

    public String getMessage() {
        return "Input length = " + inputLength;
    }
}
