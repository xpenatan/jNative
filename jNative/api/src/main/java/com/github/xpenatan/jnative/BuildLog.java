package com.github.xpenatan.jnative;

/**
 * Receives build messages synchronously on the calling thread.
 * Implementations should return promptly; callback exceptions propagate to the caller.
 */
@FunctionalInterface
public interface BuildLog {
    /**
     * Message severity.
     */
    enum Level {
        /**
         * Informational progress.
         */
        INFO,
        /**
         * A recoverable issue.
         */
        WARNING,
        /**
         * A build failure.
         */
        ERROR
    }

    /**
     * Handles a build message.
     *
     * @param level   non-null severity
     * @param message non-null human-readable message
     */
    void log(Level level, String message);

    /**
     * @return a logger that discards all messages
     */
    static BuildLog silent() {
        return (level, message) -> {
        };
    }

    /**
     * @return a logger that writes warnings and errors to stderr, omitting informational progress
     */
    static BuildLog warnings() {
        return (level, message) -> {
            if(level != Level.INFO) System.err.println("[jNative " + level + "] " + message);
        };
    }

    /**
     * @return a logger that writes errors to stderr and other messages to stdout
     */
    static BuildLog console() {
        return (level, message) -> {
            var output = level == Level.ERROR ? System.err : System.out;
            output.println("[jNative " + level + "] " + message);
        };
    }
}
