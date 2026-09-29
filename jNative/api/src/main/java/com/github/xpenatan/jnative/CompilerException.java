package com.github.xpenatan.jnative;

/**
 * Signals a failed generation or native compilation operation.
 */
public final class CompilerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates a failure with a diagnostic message.
     *
     * @param message the failure description
     */
    public CompilerException(String message) {
        super(message);
    }

    /**
     * Creates a failure retaining its original cause.
     *
     * @param message the failure description
     * @param cause   the original failure
     */
    public CompilerException(String message, Throwable cause) {
        super(message, cause);
    }
}
