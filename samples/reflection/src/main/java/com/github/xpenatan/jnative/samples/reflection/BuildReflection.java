package com.github.xpenatan.jnative.samples.reflection;

import com.github.xpenatan.jnative.*;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Host-side generation and compilation launcher.
 */
public final class BuildReflection {
    private BuildReflection() {
    }

    public static NativeBuilder builder(Path output) {
        return NativeBuilder.create()
                .classpathFromCurrentJvm()
                .mainClass(ReflectionExample.class.getName())
                .reflectClass(Player.class.getName())
                .buildRoot(output)
                .targetFileName("reflection")
                .sourceLayout(SourceLayout.PACKAGE_FILENAME)
                .buildType(BuildType.RELEASE)
                .consoleMode(ConsoleMode.PAUSE_ON_EXIT)
                .javaSourceLocations(false)
                .log(BuildLog.console());
    }

    public static void main(String[] args) {
        if(args.length > 1)
            throw new IllegalArgumentException("Expected describe, generate, or build");
        var builder =
                builder(Path.of(System.getProperty("jnative.buildRoot", "build")))
                        .buildType(
                                BuildType.valueOf(
                                        System.getProperty("jnative.buildType", "DEBUG")
                                                .toUpperCase(Locale.ROOT)));
        switch(args.length == 0 ? "describe" : args[0]) {
            case "describe" -> System.out.println(builder.request());
            case "generate" -> System.out.println(builder.generate().request().generatedSourcesDirectory());
            case "build" -> System.out.println(builder.build().executable());
            default -> throw new IllegalArgumentException("Expected describe, generate, or build");
        }
    }
}
