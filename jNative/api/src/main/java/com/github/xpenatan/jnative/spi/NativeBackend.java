package com.github.xpenatan.jnative.spi;

import com.github.xpenatan.jnative.BuildLog;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.NativeCompilationResult;
import com.github.xpenatan.jnative.NativeGenerationResult;
import com.github.xpenatan.jnative.CompilerException;

/**
 * Implementation boundary for the Java-to-C++ pipeline and native toolchain.
 * Operations are synchronous. Implementations must honor the request, report diagnostics
 * through the supplied log, and throw on failure rather than returning a partial success.
 * Implementations own any class loaders and processes they create and must close or await
 * them before returning. They must not execute the resulting application.
 */
public interface NativeBackend {
    /**
     * Reads Java bytecode and writes C++ sources, runtime support, and native project files.
     * Input classpath entries and the main method are validated by the implementation.
     *
     * @param request validated immutable configuration
     * @param log     synchronous diagnostic callback
     * @return completed generation with the same request
     * @throws CompilerException if generation fails or is not implemented
     */
    NativeGenerationResult generate(NativeBuildRequest request, BuildLog log);

    /**
     * Compiles an existing generated project using its captured configuration.
     * This stage must not regenerate Java bytecode or C++ sources. It validates that
     * required generated files exist and that the requested native toolchain succeeds.
     *
     * @param generation previously generated project, with files still present
     * @param log        synchronous diagnostic callback
     * @return completed native compilation associated with the supplied generation
     * @throws CompilerException if compilation fails or is not implemented
     */
    NativeCompilationResult compile(NativeGenerationResult generation, BuildLog log);
}
