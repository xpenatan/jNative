package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Immutable configuration shared by generation and native compilation.
 * Input is compiled Java bytecode: class directories and JARs in classpath order.
 * Creating a request validates configuration without loading classes or accessing files.
 * All paths become absolute, normalized paths relative to the current working directory.
 * Collections are defensively copied.
 *
 * @param classpath                 nonempty ordered class directories and JARs; duplicates are removed
 * @param mainClass                 binary name of the class containing {@code public static void main(String[])}
 * @param buildRoot                 required root for generated project and build output
 * @param generatedSourcesDirectory C++ source directory, or null for {@code buildRoot/native/src}
 * @param releaseDirectory          executable directory override, or null for {@code buildRoot/native/debug}
 *                                  in Debug and {@code buildRoot/native/release} in Release
 * @param targetFileName            executable base name without a path or platform-specific extension
 * @param buildType                 native compiler configuration
 * @param debugInformation          whether generated code should retain Java source debug information
 * @param nativeOptions             native source files and toolchain invocation settings
 * @param exportClasses             additional binary class names whose annotated exports are retained
 * @param reflection                explicit public reflection registrations
 * @param consoleMode               console behavior, independent of the native build configuration
 * @param sourceLayout              physical C++ class file layout
 * @param diagnostics               native symbols, exception traces, and local crash reports
 */
public record NativeBuildRequest(
        List<Path> classpath,
        String mainClass,
        Path buildRoot,
        Path generatedSourcesDirectory,
        Path releaseDirectory,
        String targetFileName,
        BuildType buildType,
        boolean debugInformation,
        NativeOptions nativeOptions,
        List<String> exportClasses,
        List<ReflectionRegistration> reflection,
        ConsoleMode consoleMode,
        SourceLayout sourceLayout,
        DiagnosticsOptions diagnostics) {

    /**
     * Compatibility constructor retaining Java frame tracing.
     */
    public NativeBuildRequest(
            List<Path> classpath,
            String mainClass,
            Path buildRoot,
            Path generatedSourcesDirectory,
            Path releaseDirectory,
            String targetFileName,
            BuildType buildType,
            boolean debugInformation,
            NativeOptions nativeOptions,
            List<String> exportClasses,
            List<ReflectionRegistration> reflection,
            ConsoleMode consoleMode) {
        this(
                classpath,
                mainClass,
                buildRoot,
                generatedSourcesDirectory,
                releaseDirectory,
                targetFileName,
                buildType,
                debugInformation,
                nativeOptions,
                exportClasses,
                reflection,
                consoleMode,
                SourceLayout.PACKAGE_DIRECTORIES,
                DiagnosticsOptions.legacy());
    }

    public StackTraceMode stackTraces() {
        return diagnostics.stackTraces();
    }

    public NativeSymbols nativeSymbols() {
        return diagnostics.symbols();
    }

    public CrashReportMode crashReports() {
        return diagnostics.crashReports();
    }

    public boolean javaSourceLocations() {
        return debugInformation && stackTraces().javaFrames();
    }

    public NativeBuildRequest(
            List<Path> classpath,
            String mainClass,
            Path buildRoot,
            Path generatedSourcesDirectory,
            Path releaseDirectory,
            String targetFileName,
            BuildType buildType,
            boolean debugInformation,
            NativeOptions nativeOptions,
            List<String> exportClasses,
            List<ReflectionRegistration> reflection) {
        this(
                classpath,
                mainClass,
                buildRoot,
                generatedSourcesDirectory,
                releaseDirectory,
                targetFileName,
                buildType,
                debugInformation,
                nativeOptions,
                exportClasses,
                reflection,
                ConsoleMode.NORMAL);
    }

    public NativeBuildRequest(
            List<Path> classpath,
            String mainClass,
            Path buildRoot,
            Path generatedSourcesDirectory,
            Path releaseDirectory,
            String targetFileName,
            BuildType buildType,
            boolean debugInformation,
            NativeOptions nativeOptions,
            List<String> exportClasses) {
        this(
                classpath,
                mainClass,
                buildRoot,
                generatedSourcesDirectory,
                releaseDirectory,
                targetFileName,
                buildType,
                debugInformation,
                nativeOptions,
                exportClasses,
                List.of());
    }

    public NativeBuildRequest(
            List<Path> classpath,
            String mainClass,
            Path buildRoot,
            Path generatedSourcesDirectory,
            Path releaseDirectory,
            String targetFileName,
            BuildType buildType,
            boolean debugInformation,
            NativeOptions nativeOptions) {
        this(
                classpath,
                mainClass,
                buildRoot,
                generatedSourcesDirectory,
                releaseDirectory,
                targetFileName,
                buildType,
                debugInformation,
                nativeOptions,
                List.of());
    }

    public NativeBuildRequest(
            List<Path> classpath,
            String mainClass,
            Path buildRoot,
            Path generatedSourcesDirectory,
            Path releaseDirectory,
            String targetFileName,
            BuildType buildType,
            boolean debugInformation) {
        this(
                classpath,
                mainClass,
                buildRoot,
                generatedSourcesDirectory,
                releaseDirectory,
                targetFileName,
                buildType,
                debugInformation,
                NativeOptions.defaults());
    }

    /**
     * Validates and snapshots a request without creating output.
     *
     * @throws IllegalArgumentException if required inputs are missing or the target name contains a path
     * @throws NullPointerException     if a collection or enum is null
     */
    public NativeBuildRequest {
        Objects.requireNonNull(classpath, "classpath");
        var entries = new LinkedHashSet<Path>();
        for(Path entry : classpath) {
            entries.add(BuildPaths.absolute(entry, "classpath entry"));
        }
        if(entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "Classpath is empty. Add class directories/JARs or call classpathFromCurrentJvm().");
        }
        classpath = List.copyOf(entries);
        mainClass = BuildPaths.text(mainClass, "mainClass");
        buildRoot = BuildPaths.absolute(buildRoot, "buildRoot");
        Objects.requireNonNull(buildType, "buildType");
        generatedSourcesDirectory =
                generatedSourcesDirectory == null
                        ? buildRoot.resolve("native/src")
                        : BuildPaths.absolute(
                        generatedSourcesDirectory, "generatedSourcesDirectory");
        releaseDirectory =
                releaseDirectory == null
                        ? buildRoot.resolve(
                        buildType == BuildType.DEBUG ? "native/debug" : "native/release")
                        : BuildPaths.absolute(releaseDirectory, "releaseDirectory");
        targetFileName = BuildPaths.fileName(targetFileName);
        Objects.requireNonNull(nativeOptions, "nativeOptions");
        Objects.requireNonNull(consoleMode, "consoleMode");
        Objects.requireNonNull(sourceLayout, "sourceLayout");
        Objects.requireNonNull(diagnostics, "diagnostics");
        exportClasses =
                List.copyOf(
                        new LinkedHashSet<>(
                                Objects.requireNonNull(exportClasses, "exportClasses").stream()
                                        .map(value -> BuildPaths.text(value, "exportClass"))
                                        .toList()));
        reflection =
                List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(reflection, "reflection")));
    }
}
