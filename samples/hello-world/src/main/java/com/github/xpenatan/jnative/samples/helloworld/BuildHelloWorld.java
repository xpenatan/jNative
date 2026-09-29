package com.github.xpenatan.jnative.samples.helloworld;

import com.github.xpenatan.jnative.BuildLog;
import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuilder;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Demonstrates bytecode generation and native compilation as separate operations.
 */
public final class BuildHelloWorld {
    private BuildHelloWorld() {
    }

    public static void main(String[] args) {
        if(args.length > 1) {
            throw new IllegalArgumentException(
                    "Expected at most one mode: describe, generate, or build");
        }

        NativeBuilder builder =
                NativeBuilder.create()
                        .classpathFromCurrentJvm()
                        .mainClass(HelloWorld.class.getName())
                        .buildRoot(Path.of(System.getProperty("jnative.buildRoot", "build")))
                        .targetFileName("hello-world")
                        .buildType(
                                BuildType.valueOf(
                                        System.getProperty("jnative.buildType", "DEBUG")
                                                .toUpperCase(Locale.ROOT)))
                        .javaSourceLocations(false)
                        .log(BuildLog.console());

        String mode = args.length == 0 ? "describe" : args[0];
        switch(mode) {
            case "describe" -> {
                var request = builder.request();
                System.out.println("Main class: " + request.mainClass());
                System.out.println("Classpath entries: " + request.classpath().size());
                System.out.println("Generated sources: " + request.generatedSourcesDirectory());
                System.out.println("Native output: " + request.releaseDirectory());
                System.out.println("Backend: C++ / CMake");
            }
            case "generate" -> {
                var generated = builder.generate();
                System.out.println("Generated files: " + generated.generatedFiles());
            }
            case "build" -> {
                var generated = builder.generate();
                var compiled = builder.compile(generated);
                System.out.println("Executable: " + compiled.executable());
            }
            default -> throw new IllegalArgumentException(
                    "Unknown mode: " + mode + ". Expected describe, generate, or build.");
        }
    }
}
