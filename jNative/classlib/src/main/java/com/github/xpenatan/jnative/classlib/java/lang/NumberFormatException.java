package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.lang.NumberFormatException")
public class NumberFormatException extends IllegalArgumentException {
    public NumberFormatException() {
    }

    public NumberFormatException(String message) {
        super(message);
    }
}
