package com.github.xpenatan.jnative.fixtures;

import com.github.xpenatan.jnative.BuildLog;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.NativeCompilationResult;
import com.github.xpenatan.jnative.NativeGenerationResult;
import com.github.xpenatan.jnative.CompilerException;
import com.github.xpenatan.jnative.spi.NativeBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * In-memory test double; never emits or compiles real files.
 */
public class RecordingBackend implements NativeBackend {
    public final List<String> operations = new ArrayList<>();
    public NativeBuildRequest request;
    public NativeGenerationResult generation;
    public BuildLog generationLog;
    public BuildLog compilationLog;
    public CompilerException generationFailure;
    public CompilerException compilationFailure;

    @Override
    public NativeGenerationResult generate(NativeBuildRequest request, BuildLog log) {
        operations.add("generate");
        if(generationFailure != null) {
            throw generationFailure;
        }
        this.request = request;
        generationLog = log;
        log.log(BuildLog.Level.INFO, "generation");
        return new NativeGenerationResult(
                request,
                Set.of(request.generatedSourcesDirectory().resolve("app.c")),
                Set.of(request.buildRoot().resolve("CMakeLists.txt")));
    }

    @Override
    public NativeCompilationResult compile(NativeGenerationResult generation, BuildLog log) {
        operations.add("compile");
        if(compilationFailure != null) {
            throw compilationFailure;
        }
        this.generation = generation;
        compilationLog = log;
        log.log(BuildLog.Level.INFO, "compilation");
        var executable =
                generation
                        .request()
                        .releaseDirectory()
                        .resolve(generation.request().targetFileName() + ".bin");
        return new NativeCompilationResult(generation, executable, Set.of(executable));
    }
}
