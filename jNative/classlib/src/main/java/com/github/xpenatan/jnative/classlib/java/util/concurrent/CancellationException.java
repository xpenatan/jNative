package com.github.xpenatan.jnative.classlib.java.util.concurrent;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.concurrent.*;

@SubstituteClass("java.util.concurrent.CancellationException")
public class CancellationException extends IllegalStateException {
    public CancellationException() {
    }

    public CancellationException(String message) {
        super(message);
    }
}
