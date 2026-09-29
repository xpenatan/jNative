package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.CppBackend;
import com.github.xpenatan.jnative.spi.NativeBackend;
import com.github.xpenatan.jnative.toolchain.cmake.CMakeToolchain;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fluent entry point for C++ generation and native compilation.
 * This mutable builder is not thread-safe. Each
 * {@link #request()} or {@link #generate()} call captures an immutable configuration.
 * The default backend emits portable C++ and builds CMake projects.
 */
public final class NativeBuilder {
    private final ArrayList<Path> classpath = new ArrayList<>();
    private final ArrayList<Path> substitutionPaths = new ArrayList<>();
    private final ArrayList<Path> substitutionDependencies = new ArrayList<>();
    private final ArrayList<SubstitutionProvider> substitutionProviders = new ArrayList<>();
    private final Map<String, String> classSubstitutionPreferences = new LinkedHashMap<>();
    private final Map<MethodReference, String> methodSubstitutionPreferences = new LinkedHashMap<>();
    private boolean builtinSubstitutions = true;
    private String mainClass;
    private Path buildRoot;
    private Path generatedSourcesDirectory;
    private Path releaseDirectory;
    private String targetFileName = "app";
    private BuildType buildType = BuildType.DEBUG;
    private ConsoleMode consoleMode = ConsoleMode.NORMAL;
    private boolean debugInformation;
    private SourceLayout sourceLayout = SourceLayout.PACKAGE_FILENAME;
    private NativeSymbols nativeSymbols = NativeSymbols.AUTO;
    private StackTraceMode stackTraces = StackTraceMode.NATIVE;
    private CrashReportMode crashReports = CrashReportMode.LOCAL;
    private final ArrayList<Path> nativeFiles = new ArrayList<>();
    private final ArrayList<String> exportClasses = new ArrayList<>();
    private final ArrayList<ReflectionRegistration> reflection = new ArrayList<>();
    private String cmake = "cmake";
    private String generator = "";
    private final ArrayList<String> cmakeArguments = new ArrayList<>();
    private final ArrayList<String> cmakeBuildArguments = new ArrayList<>();
    private final ArrayList<String> buildToolArguments = new ArrayList<>();
    private Duration timeout = Duration.ofMinutes(2);
    private BuildLog log = BuildLog.warnings();
    private NativeBackend backend = new CppBackend();

    private NativeBuilder() {
    }

    /**
     * @return an independent builder with the default C++ backend
     */
    public static NativeBuilder create() {
        return new NativeBuilder();
    }

    /**
     * Appends a compiled class directory or JAR to the input classpath.
     *
     * @param entry non-null classpath entry
     * @return this builder
     */
    public NativeBuilder classpath(Path entry) {
        classpath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    /**
     * Appends classpath entries in iteration order and copies the collection.
     *
     * @param entries non-null class directories and JARs
     * @return this builder
     */
    public NativeBuilder classpath(Collection<Path> entries) {
        classpath.addAll(List.copyOf(Objects.requireNonNull(entries, "entries")));
        return this;
    }

    /** Activates exactly the provider artifact at this path. */
    public NativeBuilder substitutionPath(Path path) {
        substitutionPaths.add(Objects.requireNonNull(path, "path"));
        return this;
    }

    /** Activates providers at the supplied paths, copying the collection. */
    public NativeBuilder substitutionPath(Collection<Path> paths) {
        substitutionPaths.addAll(List.copyOf(Objects.requireNonNull(paths, "paths")));
        return this;
    }

    /** Supplies helper bytecode without activating embedded provider indexes. */
    public NativeBuilder substitutionDependencies(Path... paths) {
        substitutionDependencies.addAll(List.of(paths));
        return this;
    }

    /** Enables the bundled provider; enabled by default. */
    public NativeBuilder useBuiltinSubstitutions(boolean enabled) {
        builtinSubstitutions = enabled;
        return this;
    }

    /** Registers explicit rules from an artifact that need not have an index. */
    public NativeBuilder substitutions(SubstitutionProvider provider) {
        substitutionProviders.add(Objects.requireNonNull(provider, "provider"));
        return this;
    }

    /** Selects one provider for an exact class conflict. */
    public NativeBuilder preferClass(String target, String providerId) {
        // Reuse the immutable public validator and canonical class name.
        var preference = new SubstitutionOptions(true, List.of(), List.of(),
                List.of(), Map.of(target, providerId), Map.of());
        preference.classPreferences().forEach((name, id) -> {
            String previous = classSubstitutionPreferences.putIfAbsent(name, id);
            if(previous != null && !previous.equals(id))
                throw new IllegalArgumentException("Conflicting class substitution preferences: " + name);
        });
        return this;
    }

    /** Selects one provider for an exact declared method conflict. */
    public NativeBuilder preferMethod(MethodReference target, String providerId) {
        var preference = new SubstitutionOptions(true, List.of(), List.of(),
                List.of(), Map.of(), Map.of(target, providerId));
        preference.methodPreferences().forEach((method, id) -> {
            String previous = methodSubstitutionPreferences.putIfAbsent(method, id);
            if(previous != null && !previous.equals(id))
                throw new IllegalArgumentException("Conflicting method substitution preferences: " + method);
        });
        return this;
    }

    /**
     * Appends entries from {@code java.class.path}, including empty entries as the
     * current directory. The JVM module path and custom class loaders are not inspected.
     *
     * @return this builder
     */
    public NativeBuilder classpathFromCurrentJvm() {
        String current = System.getProperty("java.class.path", "");
        for(String entry : current.split(Pattern.quote(File.pathSeparator), -1)) {
            classpath(Path.of(entry));
        }
        return this;
    }

    /**
     * Selects the class containing {@code public static void main(String[])}.
     *
     * @param mainClass binary class name; the class is not loaded by this setter
     * @return this builder
     */
    public NativeBuilder mainClass(String mainClass) {
        this.mainClass = Objects.requireNonNull(mainClass, "mainClass");
        return this;
    }

    /**
     * Sets the required root directory for generated project and native build output.
     *
     * @param buildRoot non-null output root
     * @return this builder
     */
    public NativeBuilder buildRoot(Path buildRoot) {
        this.buildRoot = Objects.requireNonNull(buildRoot, "buildRoot");
        return this;
    }

    /**
     * Overrides {@code buildRoot/native/src}. Relative paths use the current working directory.
     *
     * @param directory non-null C++ source directory
     * @return this builder
     */
    public NativeBuilder generatedSourcesDirectory(Path directory) {
        generatedSourcesDirectory = Objects.requireNonNull(directory, "directory");
        return this;
    }

    /**
     * Overrides the executable directory for the selected build configuration.
     * Defaults to {@code buildRoot/native/debug} for Debug or {@code buildRoot/native/release}
     * for Release. An explicit override is used as-is for either configuration.
     * Relative paths use the current working directory.
     *
     * @param directory non-null native artifact directory
     * @return this builder
     */
    public NativeBuilder releaseDirectory(Path directory) {
        releaseDirectory = Objects.requireNonNull(directory, "directory");
        return this;
    }

    /**
     * Sets the executable base name; defaults to {@code app}.
     *
     * @param targetFileName file base name without a path or platform-specific extension
     * @return this builder
     */
    public NativeBuilder targetFileName(String targetFileName) {
        this.targetFileName = Objects.requireNonNull(targetFileName, "targetFileName");
        return this;
    }

    /**
     * Sets the native compiler configuration; defaults to {@link BuildType#DEBUG}.
     *
     * @param buildType non-null native build type
     * @return this builder
     */
    public NativeBuilder buildType(BuildType buildType) {
        this.buildType = Objects.requireNonNull(buildType, "buildType");
        return this;
    }

    /**
     * Selects console behavior for generation, independently of Debug or Release.
     * Pause mode waits only with interactive standard input and output.
     *
     * @param consoleMode non-null mode; defaults to {@link ConsoleMode#NORMAL}
     * @return this builder
     */
    public NativeBuilder consoleMode(ConsoleMode consoleMode) {
        this.consoleMode = Objects.requireNonNull(consoleMode, "consoleMode");
        return this;
    }

    /**
     * Controls Java source debug information in generated code; defaults to false.
     *
     * @param enabled whether to retain Java debug information
     * @return this builder
     * @deprecated use {@link #javaSourceLocations(boolean)} with JAVA or BOTH traces
     */
    @Deprecated
    public NativeBuilder debugInformation(boolean enabled) {
        debugInformation = enabled;
        return this;
    }

    /**
     * Retains Java locations when JAVA or BOTH tracing is selected.
     */
    public NativeBuilder javaSourceLocations(boolean enabled) {
        debugInformation = enabled;
        return this;
    }

    /**
     * Selects physical source paths without changing C++ namespaces.
     */
    public NativeBuilder sourceLayout(SourceLayout value) {
        sourceLayout = Objects.requireNonNull(value);
        return this;
    }

    /**
     * Selects native compiler symbols independently of Release optimization.
     */
    public NativeBuilder nativeSymbols(NativeSymbols value) {
        nativeSymbols = Objects.requireNonNull(value);
        return this;
    }

    /**
     * Selects exception stack capture. Defaults to native addresses.
     */
    public NativeBuilder stackTraces(StackTraceMode value) {
        stackTraces = Objects.requireNonNull(value);
        return this;
    }

    /**
     * Enables local crash reports without network delivery.
     */
    public NativeBuilder crashReports(CrashReportMode value) {
        crashReports = Objects.requireNonNull(value);
        return this;
    }

    /**
     * Adds a C/C++ source or header to the generated native project.
     */
    public NativeBuilder nativeFile(Path file) {
        nativeFiles.add(Objects.requireNonNull(file));
        return this;
    }

    /**
     * Retains annotated native exports in an additional class without loading it on the JVM.
     */
    public NativeBuilder exportClass(String name) {
        exportClasses.add(Objects.requireNonNull(name));
        return this;
    }

    /**
     * Selects the CMake executable.
     */
    public NativeBuilder cmake(String command) {
        cmake = Objects.requireNonNull(command);
        return this;
    }

    /**
     * Selects a CMake generator; an empty value uses the platform default.
     */
    public NativeBuilder generator(String name) {
        generator = Objects.requireNonNull(name);
        return this;
    }

    /**
     * Appends a CMake definition at this position in the configure argument list.
     */
    public NativeBuilder cmakeDefine(String name, String value) {
        if(name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*(?::[A-Za-z]+)?"))
            throw new IllegalArgumentException("Invalid CMake definition name: " + name);
        return cmakeArgs("-D" + name + "=" + Objects.requireNonNull(value));
    }

    /**
     * Appends exact configure arguments. Each string is one argument, including spaces.
     */
    public NativeBuilder cmakeArgs(String... arguments) {
        cmakeArguments.addAll(List.of(arguments));
        return this;
    }

    /**
     * Appends exact arguments to cmake --build, such as --parallel and its value.
     */
    public NativeBuilder cmakeBuildArgs(String... arguments) {
        cmakeBuildArguments.addAll(List.of(arguments));
        return this;
    }

    /**
     * Appends native build-tool arguments after CMake's -- separator.
     */
    public NativeBuilder buildToolArgs(String... arguments) {
        buildToolArguments.addAll(List.of(arguments));
        return this;
    }

    /**
     * Appends a normalized toolchain path without quoting or shell interpretation.
     */
    public NativeBuilder cmakeToolchain(Path file) {
        return cmakeArgs(
                "--toolchain",
                Objects.requireNonNull(file).toAbsolutePath().normalize().toString());
    }

    /**
     * Selects an executable or reusable native library in the bundled CMake backend.
     */
    public NativeBuilder outputKind(NativeArtifactKind kind) {
        if(Objects.requireNonNull(kind) == NativeArtifactKind.PACKAGE)
            throw new IllegalArgumentException(
                    "The bundled CMake backend does not produce platform packages");
        return cmakeDefine("JNATIVE_OUTPUT_KIND", kind.name());
    }

    private NativeOptions nativeOptions(List<Path> files) {
        return new NativeOptions(
                files,
                cmake,
                generator,
                timeout,
                cmakeArguments,
                cmakeBuildArguments,
                buildToolArguments);
    }

    /**
     * Sets the time limit for each native tool invocation.
     */
    public NativeBuilder timeout(Duration value) {
        timeout = Objects.requireNonNull(value);
        return this;
    }

    /**
     * Sets the synchronous diagnostic callback; defaults to {@link BuildLog#warnings()}.
     *
     * @param log non-null callback
     * @return this builder
     */
    public NativeBuilder log(BuildLog log) {
        this.log = Objects.requireNonNull(log, "log");
        return this;
    }

    /**
     * Supplies a backend implementing the generation and compilation contracts.
     *
     * @param backend non-null backend; it is retained, not copied
     * @return this builder
     */
    public NativeBuilder backend(NativeBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        return this;
    }

    /**
     * Validates and snapshots configuration without loading input or creating output.
     *
     * @return immutable request with normalized paths
     * @throws IllegalArgumentException if classpath, main class, build root, or target name is invalid
     */
    public NativeBuildRequest request() {
        return new NativeBuildRequest(
                classpath,
                mainClass,
                buildRoot,
                generatedSourcesDirectory,
                releaseDirectory,
                targetFileName,
                buildType,
                debugInformation,
                nativeOptions(nativeFiles),
                exportClasses,
                reflection,
                consoleMode,
                sourceLayout,
                new DiagnosticsOptions(nativeSymbols, stackTraces, crashReports),
                new SubstitutionOptions(builtinSubstitutions, substitutionPaths,
                        substitutionDependencies, substitutionProviders,
                        classSubstitutionPreferences, methodSubstitutionPreferences));
    }

    /**
     * Retains public methods, constructors and field access for a binary class name.
     */
    public NativeBuilder reflectClass(String name) {
        return reflection(
                new ReflectionRegistration(
                        name, ReflectionRegistration.Kind.PUBLIC_MEMBERS, "", ""));
    }

    /**
     * Retains public member descriptions without enabling invocation or field value access.
     */
    public NativeBuilder reflectMetadata(String name) {
        return reflection(
                new ReflectionRegistration(name, ReflectionRegistration.Kind.METADATA, "", ""));
    }

    /**
     * Retains one public method, selected by its name and complete JVM descriptor.
     */
    public NativeBuilder reflectMethod(String owner, String name, String descriptor) {
        return reflection(
                new ReflectionRegistration(
                        owner, ReflectionRegistration.Kind.METHOD, name, descriptor));
    }

    /**
     * Retains one public constructor, selected by its JVM descriptor ending in V.
     */
    public NativeBuilder reflectConstructor(String owner, String descriptor) {
        return reflection(
                new ReflectionRegistration(
                        owner, ReflectionRegistration.Kind.CONSTRUCTOR, "", descriptor));
    }

    /**
     * Enables reading and, for non-final fields, writing one public field.
     */
    public NativeBuilder reflectField(String owner, String name) {
        return reflection(
                new ReflectionRegistration(owner, ReflectionRegistration.Kind.FIELD, name, ""));
    }

    /**
     * Appends a reflection registration; request() snapshots and deduplicates it.
     */
    public NativeBuilder reflection(ReflectionRegistration registration) {
        reflection.add(Objects.requireNonNull(registration, "registration"));
        return this;
    }

    /**
     * Generates C++ sources and native project files, without invoking a native compiler.
     *
     * @return completed generation
     * @throws IllegalArgumentException if the request is invalid
     * @throws CompilerException        if generation fails, returns an invalid result, or is not implemented
     */
    public NativeGenerationResult generate() {
        NativeBuildRequest request = request();
        NativeGenerationResult result = backend.generate(request, log);
        if(result == null || !request.equals(result.request())) {
            throw new CompilerException(
                    "NativeBackend.generate() must return a result for the supplied request");
        }
        return result;
    }

    /**
     * Compiles an existing generation without regenerating it. Uses the configuration
     * captured in {@code generation}; this builder's classpath and output settings are
     * ignored. Only its current backend and log are used, so a fresh builder can compile
     * a previously generated project. The caller must select a compatible backend.
     *
     * @param generation non-null generated project whose files are still present
     * @return completed compilation, without launching the executable
     * @throws CompilerException if compilation fails, returns an invalid result, or is not implemented
     */
    public NativeCompilationResult compile(NativeGenerationResult generation) {
        Objects.requireNonNull(generation, "generation");
        NativeCompilationResult result = backend.compile(generation, log);
        if(result == null || !generation.equals(result.generation())) {
            throw new CompilerException(
                    "NativeBackend.compile() must return a result for the supplied generation");
        }
        return result;
    }

    /**
     * Generates and then compiles. A generation failure prevents compilation.
     * Use {@link #generate()} for the generation-only operation.
     *
     * @return completed native compilation
     * @throws IllegalArgumentException if the request is invalid
     * @throws CompilerException        if either stage fails or is not implemented
     */
    public NativeCompilationResult build() {
        return compile(generate());
    }

    /**
     * Builds an optimized Release and verifies a permanent copy of its private diagnostics.
     * The returned diagnostics descriptor points into the supplied store, outside build output.
     */
    public NativeCompilationResult buildRelease(Path privateStore) {
        buildType(BuildType.RELEASE);
        var result = build();
        if(result.diagnostics() == null)
            throw new CompilerException(
                    "JN4102 buildRelease requires a diagnostic archive from this build. "
                            + "Use buildType(RELEASE).build() for a library or diagnostics-disabled executable.");
        var retained =
                NativeDiagnostics.archiveBuild(
                        result.generation().request().buildRoot().resolve("native"),
                        result.diagnostics(),
                        privateStore);
        var files = new LinkedHashSet<>(result.outputFiles());
        try(var walk = Files.walk(retained.directory())) {
            files.addAll(walk.filter(Files::isRegularFile).toList());
        } catch(IOException e) {
            throw new CompilerException("JN4102 Cannot enumerate retained release", e);
        }
        return new NativeCompilationResult(result.generation(), result.artifact(), files, retained);
    }

    /**
     * Transfers the current native sources into a new, developer-owned snapshot.
     */
    public NativeProject exportProject(NativeGenerationResult generation, Path destination) {
        return NativeProjects.export(generation, destination);
    }

    /**
     * Compiles a versioned native project without Java inputs or regeneration.
     * Uses this builder's CMake command, generator, timeout, build type and log.
     * The standalone CMake toolchain is used independently of the backend SPI.
     */
    public NativeProjectCompilationResult compileProject(Path directory) {
        return new CMakeToolchain()
                .compile(
                        NativeProjects.open(directory),
                        buildType,
                        nativeOptions(List.of()),
                        log);
    }
}
