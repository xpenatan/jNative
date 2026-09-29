package com.github.xpenatan.jnative.toolchain.cmake;

import com.github.xpenatan.jnative.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Invokes CMake directly with argument lists and bounded process lifetime.
 */
public final class CMakeToolchain {
    public NativeCompilationResult compile(NativeGenerationResult generation, BuildLog log) {
        NativeBuildRequest request = generation.request();
        Path project = request.buildRoot().resolve("native");
        NativeArtifact artifact =
                compile(
                        project,
                        request.targetFileName(),
                        request.buildType(),
                        request.releaseDirectory(),
                        request.nativeOptions(),
                        request.consoleMode() == ConsoleMode.PAUSE_ON_EXIT,
                        log);
        return new NativeCompilationResult(
                generation,
                artifact,
                artifacts(project, artifact, request.buildType()),
                currentBundle(project, artifact, request.buildType()));
    }

    public NativeProjectCompilationResult compile(
            NativeProject project, BuildType buildType, NativeOptions options, BuildLog log) {
        NativeArtifact artifact =
                compile(
                        project.directory(),
                        project.targetFileName(),
                        buildType,
                        project.directory().resolve(buildType.name().toLowerCase(Locale.ROOT)),
                        options,
                        null,
                        log);
        return new NativeProjectCompilationResult(
                project,
                artifact,
                artifacts(project.directory(), artifact, buildType),
                currentBundle(project.directory(), artifact, buildType));
    }

    private static NativeDiagnosticsBundle currentBundle(
            Path project, NativeArtifact artifact, BuildType type) {
        if(artifact.kind() != NativeArtifactKind.EXECUTABLE) return null;
        Path settings = artifact.buildDirectory().resolve("jnative-diagnostics.properties");
        try {
            if(Files.isRegularFile(settings)
                    && Files.readString(settings).contains("provider=disabled")) return null;
        } catch(IOException error) {
            throw new CompilerException("JN4100 Cannot read diagnostics capability", error);
        }
        return bundle(project, type);
    }

    private static NativeDiagnosticsBundle bundle(Path project, BuildType type) {
        Path pointer =
                project.resolve(
                        "diagnostics/latest-"
                                + (type == BuildType.DEBUG ? "Debug" : "Release")
                                + ".txt");
        if(!Files.exists(pointer)) return null; // Legacy retained project.
        try {
            String id = Files.readString(pointer).strip();
            if(!id.matches("[0-9a-f]{32}"))
                throw new CompilerException("JN4100 Invalid build identity");
            Path root = project.resolve("diagnostics").resolve(id);
            if(!Files.isRegularFile(root.resolve("manifest.json")))
                throw new CompilerException("JN4100 Incomplete diagnostic archive");
            return new NativeDiagnosticsBundle(
                    id, root, root.resolve("manifest.json"), root.resolve("sources.zip"));
        } catch(IOException e) {
            throw new CompilerException("JN4100 Cannot read diagnostic bundle", e);
        }
    }

    private static Set<Path> artifacts(Path project, NativeArtifact artifact, BuildType buildType) {
        try {
            var result = new LinkedHashSet<Path>();
            result.add(artifact.path());
            String configuration = buildType == BuildType.DEBUG ? "Debug" : "Release";
            for(var target : targetDescriptions(artifact.buildDirectory(), configuration)) {
                for(Object entry :
                        com.github.xpenatan.jnative.internal.Json.array(
                                target.getOrDefault("artifacts", List.of()))) {
                    var value = com.github.xpenatan.jnative.internal.Json.object(entry);
                    Path path =
                            artifact.buildDirectory()
                                    .resolve(value.get("path").toString())
                                    .toAbsolutePath()
                                    .normalize();
                    if(path.startsWith(artifact.path().getParent()) && Files.isRegularFile(path))
                        result.add(path);
                }
            }
            Path dependencyManifest =
                    artifact.buildDirectory()
                            .resolve("jnative-dependencies-" + configuration + ".properties");
            if(Files.isRegularFile(dependencyManifest)) {
                var values = new Properties();
                try(var reader = Files.newBufferedReader(dependencyManifest)) {
                    values.load(reader);
                }
                for(String key : values.stringPropertyNames()) {
                    Path path = Path.of(values.getProperty(key)).toAbsolutePath().normalize();
                    if(!path.startsWith(artifact.path().getParent()) || !Files.isRegularFile(path))
                        throw new CompilerException(
                                "JN3002 Missing or invalid runtime dependency: " + path);
                    result.add(path);
                }
            }
            if(artifact.kind() == NativeArtifactKind.STATIC_LIBRARY) {
                Path manifest =
                        artifact.buildDirectory()
                                .resolve(
                                        "jnative-artifact-"
                                                + (buildType == BuildType.DEBUG
                                                ? "Debug"
                                                : "Release")
                                                + ".properties");
                if(Files.isRegularFile(manifest)) {
                    var values = new Properties();
                    try(var reader = Files.newBufferedReader(manifest)) {
                        values.load(reader);
                    }
                    for(String key : List.of("runtime", "platform")) {
                        Path dependency = Path.of(values.getProperty(key));
                        if(!Files.isRegularFile(dependency))
                            throw new CompilerException(
                                    "JN3002 Missing library dependency: " + dependency);
                        result.add(dependency);
                    }
                }
            }
            var bundle = currentBundle(project, artifact, buildType);
            if(bundle != null) {
                // Dependency copies belong to this exact finalized image. Files
                // left by other configurations are retained but not reported.
                for(Object entry :
                        com.github.xpenatan.jnative.internal.Json.array(
                                readJson(bundle.manifest()).get("modules"))) {
                    var module = com.github.xpenatan.jnative.internal.Json.object(entry);
                    Path path =
                            artifact.path()
                                    .getParent()
                                    .resolve(module.get("name").toString())
                                    .normalize();
                    if(path.getParent().equals(artifact.path().getParent())
                            && Files.isRegularFile(path)) result.add(path);
                }
                try(var walk = Files.walk(bundle.directory())) {
                    walk.filter(Files::isRegularFile).forEach(result::add);
                }
            }
            return result;
        } catch(IOException | IllegalArgumentException | NoSuchElementException e) {
            throw new CompilerException("JN4100 Cannot enumerate native artifacts", e);
        }
    }

    private NativeArtifact compile(
            Path project,
            String target,
            BuildType type,
            Path outputDirectory,
            NativeOptions options,
            Boolean pauseOnExit,
            BuildLog log) {
        validateArguments(options);
        Path build =
                buildDirectory(
                        project.resolve("b").resolve(type.name().toLowerCase(Locale.ROOT)),
                        configurationKey(project, options));
        if(!Files.isRegularFile(project.resolve("CMakeLists.txt")))
            throw new CompilerException("JN3001 Generated CMakeLists.txt is missing: " + project);
        try {
            var metadata = new Properties();
            try(var input =
                        Files.newBufferedReader(project.resolve("jnative-project.properties"))) {
                metadata.load(input);
            }
            Path sources = project.resolve(metadata.getProperty("sources", "src")).normalize();
            var inputs = new ArrayList<Path>();
            for(Path directory :
                    List.of(sources, project.resolve("runtime"), project.resolve("user")))
                if(Files.isDirectory(directory))
                    try(var walk = Files.walk(directory)) {
                        inputs.addAll(walk.filter(Files::isRegularFile).toList());
                    }
            com.github.xpenatan.jnative.internal.NativePaths.validateProject(
                    project,
                    sources,
                    inputs,
                    target,
                    build,
                    !metadata.getProperty("native.symbols", "AUTO").equals("NONE")
                            || metadata.getProperty("stack.traces", "NATIVE").contains("NATIVE")
                            || metadata.getProperty("stack.traces", "NATIVE").equals("BOTH")
                            || !metadata.getProperty("crash.reports", "LOCAL").equals("OFF"));
            com.github.xpenatan.jnative.internal.NativePaths.validate(
                    outputDirectory.resolve(target));
        } catch(IOException e) {
            throw new CompilerException("JN4010 Cannot validate native build paths", e);
        }
        boolean windows =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        String configuration = type == BuildType.DEBUG ? "Debug" : "Release";
        var configure =
                new ArrayList<>(
                        List.of(
                                options.cmake(),
                                "-S",
                                project.toString(),
                                "-B",
                                build.toString(),
                                "-DCMAKE_BUILD_TYPE=" + configuration,
                                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=" + outputDirectory,
                                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_"
                                        + configuration.toUpperCase(Locale.ROOT)
                                        + "="
                                        + outputDirectory,
                                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=" + outputDirectory,
                                "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=" + outputDirectory,
                                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_"
                                        + configuration.toUpperCase(Locale.ROOT)
                                        + "="
                                        + outputDirectory,
                                "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY_"
                                        + configuration.toUpperCase(Locale.ROOT)
                                        + "="
                                        + outputDirectory));
        String generator = options.generator();
        if(pauseOnExit != null)
            configure.add("-DJNATIVE_PAUSE_ON_EXIT=" + (pauseOnExit ? "ON" : "OFF"));
        boolean rawGenerator =
                options.cmakeArguments().stream().anyMatch(argument -> argument.startsWith("-G"));
        if(rawGenerator && !generator.isEmpty())
            throw new IllegalArgumentException("Choose generator() or a raw -G argument, not both");
        if(!rawGenerator) {
            if(generator.isEmpty())
                generator =
                        System.getenv()
                                .getOrDefault(
                                        "CMAKE_GENERATOR",
                                        windows ? "MinGW Makefiles" : "Unix Makefiles");
            configure.addAll(List.of("-G", generator));
        }
        configure.addAll(options.cmakeArguments());
        try {
            Path queries = build.resolve(".cmake/api/v1/query");
            Files.createDirectories(queries);
            Files.writeString(queries.resolve("codemodel-v2"), "");
            Files.writeString(queries.resolve("cmakeFiles-v1"), "");
        } catch(IOException error) {
            throw new CompilerException("JN3020 Cannot request target artifact metadata", error);
        }
        run(configure, project, options.timeout(), log, "configure");
        verifyConfiguration(build, project, configuration);
        saveToolchainIdentity(build, project);
        NativeArtifact artifact = artifactPath(build, configuration, outputDirectory);
        var command =
                new ArrayList<>(
                        List.of(
                                options.cmake(),
                                "--build",
                                build.toString(),
                                "--config",
                                configuration));
        if(System.getenv().getOrDefault("CMAKE_BUILD_PARALLEL_LEVEL", "").isBlank()
                && options.cmakeBuildArguments().stream()
                .noneMatch(
                        argument ->
                                argument.equals("--parallel")
                                        || argument.startsWith("--parallel=")
                                        || argument.startsWith("-j")))
            command.addAll(List.of("--parallel", "2"));
        command.addAll(options.cmakeBuildArguments());
        if(!options.buildToolArguments().isEmpty()) {
            command.add("--");
            command.addAll(options.buildToolArguments());
        }
        run(command, project, options.timeout(), log, "compile");
        verifyCompletion(build, configuration);
        if(!Files.isRegularFile(artifact.path()))
            throw new CompilerException(
                    "JN3002 Native build reported success without producing " + artifact.path());
        return artifact;
    }

    private static void verifyCompletion(Path build, String configuration) {
        Path manifest = build.resolve("jnative-artifact-" + configuration + ".properties");
        if(!Files.isRegularFile(manifest))
            return; // Retained project predating completion markers.
        try {
            var values = new Properties();
            try(var reader = Files.newBufferedReader(manifest)) {
                values.load(reader);
            }
            String request = values.getProperty("request");
            if(request == null) return;
            Path completed = build.resolve("jnative-completed-" + configuration + ".txt");
            if(!Files.isRegularFile(completed)
                    || !request.equals(Files.readString(completed).strip()))
                throw new CompilerException(
                        "JN3002 The requested build target did not compile jnative_app for this configuration. "
                                + "Choose jnative_app or a target that depends on it.");
        } catch(IOException error) {
            throw new CompilerException("JN3002 Cannot verify native build completion", error);
        }
    }

    private static NativeArtifact artifactPath(Path build, String configuration, Path output) {
        Path manifest = build.resolve("jnative-artifact-" + configuration + ".properties");
        // Older retained exports keep their original project and runtime intact.
        if(!Files.isRegularFile(manifest)) return legacyArtifact(build, configuration, output);
        try {
            var values = new Properties();
            try(var reader = Files.newBufferedReader(manifest)) {
                values.load(reader);
            }
            Path artifact =
                    Path.of(Objects.requireNonNull(values.getProperty("path"), "artifact path"))
                            .toAbsolutePath()
                            .normalize();
            if(!artifact.startsWith(output.toAbsolutePath().normalize()))
                throw new CompilerException(
                        "JN3020 Target artifact escaped the managed output directory: " + artifact);
            return new NativeArtifact(
                    NativeArtifactKind.valueOf(values.getProperty("kind")), artifact, build);
        } catch(IOException | IllegalArgumentException | NullPointerException error) {
            throw new CompilerException(
                    "JN3020 Invalid target artifact manifest: " + manifest, error);
        }
    }

    private static void validateArguments(NativeOptions options) {
        var arguments = options.cmakeArguments();
        for(int index = 0; index < arguments.size(); ++index) {
            String argument = arguments.get(index);
            if(argument.startsWith("-S")
                    || argument.startsWith("-B")
                    || argument.startsWith("-H")
                    || argument.startsWith("--build")
                    || argument.startsWith("--install")
                    || argument.startsWith("--preset")
                    || argument.equals("--")
                    || argument.equals("-P")
                    || argument.startsWith("--fresh")) throw conflict(argument);
            String definition = argument.startsWith("-D") ? argument.substring(2) : null;
            if(argument.equals("-D")) {
                if(++index == arguments.size())
                    throw new IllegalArgumentException("-D requires a definition");
                definition = arguments.get(index);
            }
            if(definition != null) {
                String key = definition.split("[:=]", 2)[0].toUpperCase(Locale.ROOT);
                if(key.equals("CMAKE_BUILD_TYPE")
                        || key.equals("CMAKE_CONFIGURATION_TYPES")
                        || key.equals("CMAKE_SOURCE_DIR")
                        || key.equals("CMAKE_BINARY_DIR")
                        || key.matches("CMAKE_(RUNTIME|LIBRARY|ARCHIVE)_OUTPUT_DIRECTORY(?:_.*)?"))
                    throw conflict(key);
            }
        }
        for(String argument : options.cmakeBuildArguments())
            if(argument.startsWith("--build")
                    || argument.startsWith("--config")
                    || argument.startsWith("--preset")
                    || argument.equals("--")
                    || argument.startsWith("-B")
                    || argument.startsWith("-S")) throw conflict(argument);
    }

    private static NativeArtifact legacyArtifact(Path build, String configuration, Path output) {
        try {
            for(var details : targetDescriptions(build, configuration)) {
                if(!"jnative_app".equals(details.get("name"))) continue;
                var artifact =
                        com.github.xpenatan.jnative.internal.Json.object(
                                com.github.xpenatan.jnative.internal.Json.array(
                                                details.get("artifacts"))
                                        .getFirst());
                Path path =
                        build.resolve(artifact.get("path").toString()).toAbsolutePath().normalize();
                if(!path.startsWith(output.toAbsolutePath().normalize()))
                    throw conflict("Target artifact " + path);
                return new NativeArtifact(
                        NativeArtifactKind.valueOf(details.get("type").toString()), path, build);
            }
            throw new CompilerException("JN3020 CMake did not report the jnative_app artifact");
        } catch(IOException | IllegalArgumentException | NoSuchElementException error) {
            throw new CompilerException(
                    "JN3020 Cannot read the retained project's target artifact from CMake", error);
        }
    }

    private static List<Map<String, Object>> targetDescriptions(Path build, String configuration)
            throws IOException {
        Path replies = build.resolve(".cmake/api/v1/reply"), index;
        try(var files = Files.list(replies)) {
            index =
                    files.filter(file -> file.getFileName().toString().startsWith("index-"))
                            .max(Comparator.comparing(file -> file.getFileName().toString()))
                            .orElseThrow();
        }
        var reply = com.github.xpenatan.jnative.internal.Json.object(readJson(index).get("reply"));
        var model = com.github.xpenatan.jnative.internal.Json.object(reply.get("codemodel-v2"));
        var configurations =
                com.github.xpenatan.jnative.internal.Json.array(
                        readJson(replies.resolve(model.get("jsonFile").toString()))
                                .get("configurations"));
        for(Object entry : configurations) {
            var value = com.github.xpenatan.jnative.internal.Json.object(entry);
            if(!configuration.equals(value.get("name")) && !"".equals(value.get("name"))) continue;
            var targets = new ArrayList<Map<String, Object>>();
            for(Object target :
                    com.github.xpenatan.jnative.internal.Json.array(value.get("targets"))) {
                var selected = com.github.xpenatan.jnative.internal.Json.object(target);
                targets.add(readJson(replies.resolve(selected.get("jsonFile").toString())));
            }
            return targets;
        }
        throw new CompilerException(
                "JN3020 CMake did not report target configuration " + configuration);
    }

    private static Map<String, Object> readJson(Path path) throws IOException {
        return com.github.xpenatan.jnative.internal.Json.object(
                com.github.xpenatan.jnative.internal.Json.read(Files.readString(path)));
    }

    private static void verifyConfiguration(Path build, Path project, String configuration) {
        try {
            var cache = readCache(build);
            if(!configuration.equals(cache.get("CMAKE_BUILD_TYPE"))
                    || !project.toAbsolutePath()
                    .normalize()
                    .equals(
                            Path.of(cache.getOrDefault("CMAKE_HOME_DIRECTORY", ""))
                                    .toAbsolutePath()
                                    .normalize()))
                throw conflict("Effective CMake configuration");
        } catch(IOException error) {
            throw new CompilerException("JN3020 Cannot verify configured project", error);
        }
    }

    private static Map<String, String> readCache(Path build) throws IOException {
        var cache = new HashMap<String, String>();
        for(String line : Files.readAllLines(build.resolve("CMakeCache.txt"))) {
            int colon = line.indexOf(':'), equals = line.indexOf('=');
            if(colon > 0 && equals > colon)
                cache.put(line.substring(0, colon), line.substring(equals + 1));
        }
        return cache;
    }

    // Track the compiler and included SDK/toolchain files CMake actually resolved,
    // including compilers selected indirectly and absent from the host PATH.
    private static Path buildDirectory(Path parent, String key) {
        try {
            Path candidate = parent.resolve(key);
            for(int attempt = 0; attempt < 32; ++attempt) {
                Path identity = candidate.resolve("jnative-toolchain.properties");
                if(!Files.isRegularFile(identity)) return candidate;
                var values = new Properties();
                try(var reader = Files.newBufferedReader(identity)) {
                    values.load(reader);
                }
                List<Path> inputs =
                        values.stringPropertyNames().stream()
                                .filter(name -> name.startsWith("input."))
                                .sorted()
                                .map(name -> Path.of(values.getProperty(name)))
                                .toList();
                String fingerprint = fingerprint(inputs);
                if(fingerprint.equals(values.getProperty("sha256"))) return candidate;
                String next = key + candidate.getFileName() + fingerprint;
                candidate =
                        parent.resolve(
                                java.util.HexFormat.of()
                                        .formatHex(
                                                java.security.MessageDigest.getInstance("SHA-256")
                                                        .digest(
                                                                next.getBytes(
                                                                        StandardCharsets.UTF_8)))
                                        .substring(0, 10));
            }
            throw new CompilerException("JN3020 Native toolchain inputs are changing repeatedly");
        } catch(IOException | java.security.NoSuchAlgorithmException error) {
            throw new CompilerException("JN3020 Cannot check native toolchain identity", error);
        }
    }

    private static void saveToolchainIdentity(Path build, Path project) {
        try {
            var inputs = new TreeSet<Path>();
            var cache = readCache(build);
            for(String key :
                    List.of(
                            "CMAKE_C_COMPILER",
                            "CMAKE_CXX_COMPILER",
                            "CMAKE_MAKE_PROGRAM",
                            "CMAKE_AR",
                            "CMAKE_LINKER",
                            "CMAKE_OBJCOPY",
                            "CMAKE_STRIP")) {
                String value = cache.get(key);
                if(value != null && Files.isRegularFile(Path.of(value)))
                    inputs.add(Path.of(value).toAbsolutePath().normalize());
            }
            Path replies = build.resolve(".cmake/api/v1/reply"), index;
            try(var files = Files.list(replies)) {
                index =
                        files.filter(file -> file.getFileName().toString().startsWith("index-"))
                                .max(Comparator.comparing(file -> file.getFileName().toString()))
                                .orElseThrow();
            }
            var reply =
                    com.github.xpenatan.jnative.internal.Json.object(readJson(index).get("reply"));
            var model =
                    com.github.xpenatan.jnative.internal.Json.object(reply.get("cmakeFiles-v1"));
            for(Object item :
                    com.github.xpenatan.jnative.internal.Json.array(
                            readJson(replies.resolve(model.get("jsonFile").toString()))
                                    .get("inputs"))) {
                var input = com.github.xpenatan.jnative.internal.Json.object(item);
                if(!Boolean.TRUE.equals(input.get("isGenerated")))
                    inputs.add(
                            project.resolve(input.get("path").toString())
                                    .toAbsolutePath()
                                    .normalize());
            }
            var values = new Properties();
            int indexNumber = 0;
            for(Path input : inputs)
                values.setProperty(
                        "input." + String.format(Locale.ROOT, "%05d", indexNumber++),
                        input.toString());
            values.setProperty("sha256", fingerprint(new ArrayList<>(inputs)));
            try(var writer =
                        Files.newBufferedWriter(build.resolve("jnative-toolchain.properties"))) {
                values.store(writer, "Resolved native toolchain inputs");
            }
        } catch(IOException | IllegalArgumentException | NoSuchElementException error) {
            throw new CompilerException(
                    "JN3020 Cannot record the resolved native toolchain", error);
        }
    }

    private static String fingerprint(List<Path> inputs) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            for(Path path : inputs) {
                digest.update(path.toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);
                if(Files.isRegularFile(path))
                    try(var stream = Files.newInputStream(path)) {
                        int count;
                        while((count = stream.read(buffer)) != -1) digest.update(buffer, 0, count);
                    }
                digest.update((byte)0);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch(java.security.NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    private static IllegalArgumentException conflict(String argument) {
        return new IllegalArgumentException(
                "JN3020 "
                        + argument
                        + " conflicts with the managed project, configuration or output directory. "
                        + "Use the builder's buildRoot, buildType and releaseDirectory settings; configure an exported project directly for independent builds.");
    }

    private static String configurationKey(Path project, NativeOptions options) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var values =
                    new ArrayList<>(
                            List.of(
                                    options.cmake(),
                                    options.generator(),
                                    System.getProperty("os.name"),
                                    System.getenv().getOrDefault("PATH", ""),
                                    System.getenv().getOrDefault("CC", ""),
                                    System.getenv().getOrDefault("CXX", ""),
                                    System.getenv().getOrDefault("CMAKE_GENERATOR", "")));
            for(String name :
                    List.of(
                            "CFLAGS",
                            "CXXFLAGS",
                            "LDFLAGS",
                            "SDKROOT",
                            "CMAKE_PREFIX_PATH",
                            "CMAKE_TOOLCHAIN_FILE",
                            "CMAKE_GENERATOR_PLATFORM",
                            "CMAKE_GENERATOR_TOOLSET"))
                values.add(System.getenv().getOrDefault(name, ""));
            values.addAll(options.cmakeArguments());
            for(String value : values) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte)0);
            }
            var commands =
                    new LinkedHashSet<>(List.of(options.cmake(), "c++", "g++", "clang++", "cl"));
            if(!System.getenv().getOrDefault("CXX", "").isBlank())
                commands.add(System.getenv("CXX"));
            for(String argument : options.cmakeArguments())
                if(argument.matches("-DCMAKE_(C|CXX)_COMPILER(?::[^=]+)?=.*"))
                    commands.add(argument.substring(argument.indexOf('=') + 1));
            for(String command : commands) {
                Path executable = findExecutable(command);
                if(executable != null) {
                    digest.update(executable.toString().getBytes(StandardCharsets.UTF_8));
                    digest.update(Files.readAllBytes(executable));
                }
            }
            for(int i = 0; i < options.cmakeArguments().size(); ++i) {
                String value = options.cmakeArguments().get(i);
                String path =
                        value.startsWith("--toolchain=")
                                ? value.substring(12)
                                : value.equals("--toolchain")
                                && i + 1 < options.cmakeArguments().size()
                                ? options.cmakeArguments().get(++i)
                                : value.startsWith("-DCMAKE_TOOLCHAIN_FILE=")
                                ? value.substring(
                                "-DCMAKE_TOOLCHAIN_FILE=".length())
                                : null;
                if(path != null) {
                    Path file = project.resolve(path).normalize();
                    if(Files.isRegularFile(file)) digest.update(Files.readAllBytes(file));
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 10);
        } catch(java.security.NoSuchAlgorithmException | IOException error) {
            throw new CompilerException("JN3020 Cannot identify native configuration", error);
        }
    }

    private static Path findExecutable(String command) {
        try {
            Path direct = Path.of(command);
            if(Files.isRegularFile(direct)) return direct.toAbsolutePath().normalize();
            for(String directory :
                    System.getenv()
                            .getOrDefault("PATH", "")
                            .split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                for(String suffix :
                        System.getProperty("os.name").startsWith("Windows")
                                ? List.of("", ".exe", ".cmd", ".bat")
                                : List.of("")) {
                    Path candidate = Path.of(directory).resolve(command + suffix);
                    if(Files.isRegularFile(candidate))
                        return candidate.toAbsolutePath().normalize();
                }
        } catch(InvalidPathException ignored) {
            /* CMake reports invalid compiler command settings. */
        }
        return null;
    }

    private static void run(
            List<String> command, Path directory, Duration timeout, BuildLog log, String stage) {
        Process process = null;
        Path output = null;
        try {
            output = Files.createTempFile(directory, stage + "-", ".log");
            log.log(
                    BuildLog.Level.INFO,
                    "CMake "
                            + stage
                            + " argv: "
                            + command.stream()
                            .map(
                                    value ->
                                            "\""
                                                    + value.replace("\\", "\\\\")
                                                    .replace("\"", "\\\"")
                                                    + "\"")
                            .toList());
            process =
                    new ProcessBuilder(command)
                            .directory(directory.toFile())
                            .redirectErrorStream(true)
                            .redirectOutput(output.toFile())
                            .start();
            if(!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS))
                throw new CompilerException(
                        "JN3003 Native "
                                + stage
                                + " timed out after "
                                + timeout
                                + "; log: "
                                + output);
            String text = Files.readString(output, StandardCharsets.UTF_8);
            if(process.exitValue() != 0)
                throw new CompilerException(
                        "JN3002 Native "
                                + stage
                                + " exited with "
                                + process.exitValue()
                                + ":\n"
                                + text);
            log.log(BuildLog.Level.INFO, text.strip());
        } catch(IOException error) {
            throw new CompilerException(
                    "JN3001 Cannot execute native " + stage + ": " + command.getFirst(), error);
        } catch(InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new CompilerException(
                    "JN3003 Native " + stage + " interrupted; log: " + output, error);
        } finally {
            if(process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                if(process.isAlive()) process.destroyForcibly();
            }
        }
    }

    public static String quoted(String text) {
        return "\""
                + text.replace("\\", "/")
                .replace("\"", "\\\"")
                .replace("$", "\\$")
                .replace(";", "\\;")
                + "\"";
    }
}
