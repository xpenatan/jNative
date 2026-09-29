package com.github.xpenatan.jnative;

/**
 * Native compiler configuration. Both configurations attempt readable C++ generation.
 */
public enum BuildType {
    /**
     * Preserve native debug information and favor debugging.
     */
    DEBUG,
    /**
     * Favor native executable performance and size.
     */
    RELEASE
}
