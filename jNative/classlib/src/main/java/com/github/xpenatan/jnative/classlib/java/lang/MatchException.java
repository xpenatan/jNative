package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.lang.MatchException")
public final class MatchException extends RuntimeException {
    public MatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
