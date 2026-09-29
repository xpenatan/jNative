package com.github.xpenatan.jnative.samples.diagnostics;

import com.github.xpenatan.jnative.*;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Host-side build and permanent release archive example.
 */
public final class BuildDemo {
    private BuildDemo() {
    }

    public static void main(String[] args) {
        var builder =
                NativeBuilder.create()
                        .classpathFromCurrentJvm()
                        .mainClass(Demo.class.getName())
                        .buildRoot(Path.of(System.getProperty("jnative.buildRoot", "build")))
                        .buildType(
                                BuildType.valueOf(
                                        System.getProperty("jnative.buildType", "DEBUG")
                                                .toUpperCase(Locale.ROOT)))
                        .targetFileName("diagnostics-demo")
                        .nativeFile(Path.of("src/main/native/demo.cpp"))
                        .stackTraces(StackTraceMode.NATIVE)
                        .nativeSymbols(NativeSymbols.SEPARATE)
                        .crashReports(CrashReportMode.LOCAL)
                        .consoleMode(ConsoleMode.PAUSE_ON_EXIT)
                        .log(BuildLog.console());
        switch(args.length == 0 ? "generate" : args[0]) {
            case "generate" -> System.out.println(builder.generate().request().generatedSourcesDirectory());
            case "build" -> System.out.println(builder.build().executable());
            case "release" -> {
                String store = System.getProperty("jnative.archiveStore", "");
                if(store.isBlank())
                    throw new IllegalArgumentException(
                            "Set -PnativeArchiveStore to a permanent private directory outside build output.");
                var result = builder.buildRelease(Path.of(store));
                System.out.println("Player files: " + result.executable().getParent());
                System.out.println("Private archive: " + result.diagnostics().directory());
            }
            default -> throw new IllegalArgumentException("Expected generate, build, or release");
        }
    }
}
