package com.github.xpenatan.jnative.samples.nativeinterop;

import com.github.xpenatan.jnative.*;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Host-side launcher. Only NativeInterop and its reachable dependencies are translated.
 */
public final class BuildNativeInterop {
    private BuildNativeInterop() {
    }

    public static NativeBuilder builder(Path module, Path output) {
        Path nativeSources = module.resolve("src/main/native");
        return NativeBuilder.create()
                .classpathFromCurrentJvm()
                .mainClass(NativeInterop.class.getName())
                .buildRoot(output)
                .targetFileName("native-interop")
                .buildType(BuildType.DEBUG)
                .javaSourceLocations(false)
                .nativeFile(nativeSources.resolve("sample_native.h"))
                .nativeFile(nativeSources.resolve("sample_c.c"))
                .nativeFile(nativeSources.resolve("sample_cpp.cpp"))
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
