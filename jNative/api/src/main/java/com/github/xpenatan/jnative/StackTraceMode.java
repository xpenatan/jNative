package com.github.xpenatan.jnative;

/**
 * Exception capture policy. Fatal crash reporting is configured separately.
 */
public enum StackTraceMode {
    NATIVE,
    JAVA,
    BOTH,
    NONE;

    public boolean javaFrames() {
        return this == JAVA || this == BOTH;
    }

    public boolean nativeFrames() {
        return this == NATIVE || this == BOTH;
    }
}
