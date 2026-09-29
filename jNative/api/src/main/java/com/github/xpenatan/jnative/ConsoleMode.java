package com.github.xpenatan.jnative;

/**
 * Console behavior for generated executables, independent of Debug or Release builds.
 */
public enum ConsoleMode {
    /**
     * Use a console and exit as soon as the application finishes.
     */
    NORMAL,

    /**
     * Wait for a key on Windows, or Enter on other platforms, before exiting.
     * Waiting is skipped when standard input or output is redirected.
     */
    PAUSE_ON_EXIT
}
