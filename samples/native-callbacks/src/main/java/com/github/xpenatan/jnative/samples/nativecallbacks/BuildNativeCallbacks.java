package com.github.xpenatan.jnative.samples.nativecallbacks;

import com.github.xpenatan.jnative.*;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Adds handwritten C++ and explicitly roots the Java callback exports.
 */
public final class BuildNativeCallbacks {
    private BuildNativeCallbacks() {
    }

    public static NativeBuilder builder(Path module, Path output) {
        Path nativeSources = module.resolve("src/main/native");
        return NativeBuilder.create()
                .classpathFromCurrentJvm()
                .mainClass(NativeCallbacks.class.getName())
                .exportClass(JavaCallbacks.class.getName())
                .buildRoot(output)
                .targetFileName("native-callbacks")
                .buildType(BuildType.DEBUG)
                .javaSourceLocations(false)
                .nativeFile(nativeSources.resolve("callbacks.h"))
                .nativeFile(nativeSources.resolve("callbacks.cpp"))
                .log(BuildLog.console());
    }

    public static void main(String[] args) {
        if(args.length > 1)
            throw new IllegalArgumentException("Expected describe, generate, or build");
        var builder =
                builder(
                        Path.of("").toAbsolutePath(),
                        Path.of(System.getProperty("jnative.buildRoot", "build")))
                        .buildType(
                                BuildType.valueOf(
                                        System.getProperty("jnative.buildType", "DEBUG")
                                                .toUpperCase(Locale.ROOT)));
        switch(args.length == 0 ? "describe" : args[0]) {
            case "describe" -> System.out.println(builder.request());
            case "generate" -> System.out.println(
                    "Generated sources: "
                            + builder.generate().request().generatedSourcesDirectory());
            case "build" -> {
                var generated = builder.generate();
                System.out.println("Executable: " + builder.compile(generated).executable());
            }
            default -> throw new IllegalArgumentException("Expected describe, generate, or build");
        }
    }
}
