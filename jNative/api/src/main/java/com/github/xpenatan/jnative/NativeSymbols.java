package com.github.xpenatan.jnative;

/**
 * Native compiler symbols, independent of optimization and Java source metadata.
 */
public enum NativeSymbols {
    AUTO,
    EMBEDDED,
    SEPARATE,
    NONE;

    public NativeSymbols resolve(BuildType type) {
        return this == AUTO ? (type == BuildType.DEBUG ? EMBEDDED : SEPARATE) : this;
    }
}
