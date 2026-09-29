package com.github.xpenatan.jnative;

/**
 * Read-only inventory of the bundled library profile; inherited methods remain supported.
 */
public final class NativeCompatibility {
    private NativeCompatibility() {
    }

    public static String libraryInventory() {
        return com.github.xpenatan.jnative.compiler.CompatibilityReport.libraryInventory();
    }
}
