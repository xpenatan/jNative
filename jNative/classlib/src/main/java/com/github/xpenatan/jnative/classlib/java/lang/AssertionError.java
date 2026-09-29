package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.lang.AssertionError")
public class AssertionError extends Error {
    public AssertionError() {
    }

    public AssertionError(Object detail) {
        super(String.valueOf(detail));
        if(detail instanceof Throwable) initCause((Throwable)detail);
    }

    public AssertionError(boolean detail) {
        this((Object)Boolean.valueOf(detail));
    }

    public AssertionError(char detail) {
        this((Object)Character.valueOf(detail));
    }

    public AssertionError(int detail) {
        this((Object)Integer.valueOf(detail));
    }

    public AssertionError(long detail) {
        this((Object)Long.valueOf(detail));
    }

    public AssertionError(float detail) {
        this((Object)Float.valueOf(detail));
    }

    public AssertionError(double detail) {
        this((Object)Double.valueOf(detail));
    }

    public AssertionError(String message, Throwable cause) {
        super(message, cause);
    }
}
