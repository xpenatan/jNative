package com.github.xpenatan.jnative.internal;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.backend.cpp.CppEmitter;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.CompatibilityReport;
import com.github.xpenatan.jnative.spi.NativeBackend;
import com.github.xpenatan.jnative.toolchain.cmake.CMakeToolchain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Bundled bytecode → C++ → CMake pipeline.
 */
public final class CppBackend implements NativeBackend {
    @Override
    public NativeGenerationResult generate(NativeBuildRequest request, BuildLog log) {
        Path project = request.buildRoot().resolve("native");
        Path existingMetadata = project.resolve(ProjectFiles.METADATA);
        ProjectFiles.assertRegenerable(project);
        try {
            if(Files.isRegularFile(existingMetadata)) {
                String layout =
                        ProjectFiles.read(existingMetadata)
                                .getProperty(
                                        "source.layout", SourceLayout.PACKAGE_DIRECTORIES.name());
                if(!layout.equals(request.sourceLayout().name()))
                    throw new CompilerException(
                            "JN4011 Source layout is frozen: recorded "
                                    + layout
                                    + ", requested "
                                    + request.sourceLayout()
                                    + ". Metadata: "
                                    + existingMetadata
                                    + ". Generate into a new build root, or clean the owning"
                                    + " module's build directory before regenerating.");
            }
        } catch(IOException e) {
            throw new CompilerException("JN4001 Cannot read source layout", e);
        }
        log.log(BuildLog.Level.INFO, "Resolving reachable bytecode from " + request.mainClass());
        var program = new BytecodeCompiler().compile(request);
        if(request.debugInformation() && !request.stackTraces().javaFrames())
            log.log(
                    BuildLog.Level.INFO,
                    "Java source locations are inactive in "
                            + request.stackTraces()
                            + " trace mode; native C++ locations come from compiler symbols.");
        var emitter = new CppEmitter();
        var emitted = emitter.emit(program, request);
        var fallbacks = emitter.readabilityFallbacks();
        var files = new LinkedHashMap<Path, byte[]>();
        var generated = new LinkedHashSet<Path>();
        var projects = new LinkedHashSet<Path>();
        try {
            for(var source : emitted.entrySet()) {
                Path path = request.generatedSourcesDirectory().resolve(source.getKey());
                files.put(path, source.getValue().getBytes(StandardCharsets.UTF_8));
                generated.add(path);
            }
            for(String name :
                    List.of(
                            "jn_project.cmake",
                            "jn_features.cmake",
                            "jn_dependencies.cmake",
                            "jn_common.hpp",
                            "jn_platform.hpp",
                            "jn_platform.cmake",
                            "jn_platform_desktop.hpp",
                            "jn_floating_x64.asm",
                            "jn_output.cpp",
                            "jn_platform_files.hpp",
                            "jn_paths.cpp",
                            "jn_platform_windows.cpp",
                            "jn_platform_linux.cpp",
                            "jn_runtime.hpp",
                            "jn_arrays.hpp",
                            "jn_array_algorithms.hpp",
                            "jn_arrays.cpp",
                            "jn_string_kernels.hpp",
                            "jn_string_kernels.cpp",
                            "jn_codecs.hpp",
                            "jn_codecs.cpp",
                            "jn_random.hpp",
                            "jn_classlib.hpp",
                            "jn_platform_bindings.hpp",
                            "jn_maps.hpp",
                            "jn_charsets.hpp",
                            "jn_classlib_numbers.hpp",
                            "jn_classlib_numbers.cpp",
                            "jn_collections.hpp",
                            "jn_io_drivers.hpp",
                            "jn_regex_drivers.hpp",
                            "jn_classlib_drivers.hpp",
                            "jn_unicode_data.hpp",
                            "jn_zlib.cpp",
                            "jn_zlib.cmake",
                            "jn_numbers.cpp",
                            "jn_runtime.cpp",
                            "jn_regex.cpp",
                            "jn_threads.hpp",
                            "jn_threads.cpp",
                            "jn_abi.h",
                            "jn_abi.hpp",
                            "jn_native_storage.hpp",
                            "jn_abi.cpp",
                            "jn_files.hpp",
                            "jn_files.cpp",
                            "jn_buffers.hpp",
                            "jn_buffers.cpp",
                            "jn_reflection.hpp",
                            "jn_reflection.cpp",
                            "jn_console.hpp",
                            "jn_console.cpp",
                            "jn_diagnostics.hpp",
                            "jn_diagnostics.cpp",
                            "jn_diagnostics_none.cpp",
                            "jn_report_files.hpp",
                            "jn_diagnostic_common.hpp",
                            "jn_crash_protocol.hpp",
                            "jn_diagnostic_tool.cpp",
                            "jn_core_decode.py",
                            "jn_diagnostics.cmake",
                            "jn_identity.cmake",
                            "jn_archive.cmake",
                            "jn_store.cmake")) {
                try(var input = CppBackend.class.getResourceAsStream("/cpp/" + name)) {
                    if(input == null)
                        throw new CompilerException(
                                "JN4001 Missing bundled runtime resource: " + name);
                    Path path = project.resolve("runtime").resolve(name);
                    files.put(path, input.readAllBytes());
                    generated.add(path);
                }
            }
            var nativeSources = new ArrayList<Path>();
            var nativeNames = new HashSet<String>();
            for(Path file : request.nativeOptions().files()) {
                String name = file.getFileName().toString();
                if(!nativeNames.add(name.toLowerCase(Locale.ROOT)))
                    throw new CompilerException(
                            "JN2001 Native files have duplicate names: " + name);
                if(!name.matches("[A-Za-z0-9_.-]+"))
                    throw new CompilerException("JN2001 Unsupported native file name: " + name);
                Path target = project.resolve("user").resolve(name);
                files.put(target, Files.readAllBytes(file));
                projects.add(target);
                if(name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".cc"))
                    nativeSources.add(target);
            }
            for(var method : program.methods().values()) {
                var binding = method.nativeBinding();
                if(binding == null) continue;
                String include = binding.include();
                boolean available = files.containsKey(request.generatedSourcesDirectory().resolve(include))
                        || files.containsKey(project.resolve("runtime").resolve(include))
                        || files.containsKey(project.resolve("user").resolve(include));
                if(!available)
                    throw new CompilerException("JN2001 NativeInclude header is not packaged: "
                            + include + " on " + method.id()
                            + ". Supply the header with nativeFile or a bundled runtime binding.");
            }
            StringBuilder sourceList = new StringBuilder("set(JNATIVE_APPLICATION_SOURCES\n");
            var applicationSources = new ArrayList<String>();
            for(String source : new TreeSet<>(emitted.keySet())) {
                if(source.endsWith(".cpp") && !source.equals("launcher.cpp")) {
                    Path path = request.generatedSourcesDirectory().resolve(source);
                    sourceList.append("    ").append(relative(project, path)).append('\n');
                    applicationSources.add(project.relativize(path).toString().replace('\\', '/'));
                }
            }
            sourceList.append(")\nset(JNATIVE_NATIVE_SOURCES\n");
            for(Path source : nativeSources)
                sourceList.append("    ").append(relative(project, source)).append('\n');
            sourceList.append(")\nset(JNATIVE_RUNTIME_SOURCES\n");
            var runtimeSources =
                    List.of(
                            "jn_classlib_numbers.cpp",
                            "jn_arrays.cpp",
                            "jn_string_kernels.cpp",
                            "jn_codecs.cpp",
                            "jn_zlib.cpp",
                            "jn_numbers.cpp",
                            "jn_runtime.cpp",
                            "jn_regex.cpp",
                            "jn_threads.cpp",
                            "jn_abi.cpp",
                            "jn_files.cpp",
                            "jn_buffers.cpp",
                            "jn_reflection.cpp");
            for(String source : runtimeSources)
                sourceList.append("    \"runtime/").append(source).append("\"\n");
            sourceList.append(")\n");
            sourceList
                    .append("set(JNATIVE_NEEDS_ZLIB ")
                    .append(
                            program.classes().containsKey("java/util/zip/NativeZlib")
                                    ? "ON"
                                    : "OFF")
                    .append(")\n");
            Path sourceListFile = project.resolve("jnative-sources.cmake");
            files.put(sourceListFile, sourceList.toString().getBytes(StandardCharsets.UTF_8));
            projects.add(sourceListFile);
            Path sourceManifest = project.resolve("jnative-sources.json");
            files.put(
                    sourceManifest,
                    com.github.xpenatan.jnative.internal.Json.write(
                                    Map.ofEntries(
                                            Map.entry("schema", 1),
                                            Map.entry("application", applicationSources),
                                            Map.entry(
                                                    "runtime",
                                                    runtimeSources.stream()
                                                            .map(name -> "runtime/" + name)
                                                            .toList()),
                                            Map.entry(
                                                    "native",
                                                    nativeSources.stream()
                                                            .map(
                                                                    path ->
                                                                            project.relativize(path)
                                                                                    .toString()
                                                                                    .replace(
                                                                                            '\\',
                                                                                            '/'))
                                                            .toList()),
                                            Map.entry(
                                                    "platform.common",
                                                    List.of(
                                                            "runtime/jn_paths.cpp",
                                                            "runtime/jn_output.cpp")),
                                            Map.entry(
                                                    "platform.windows",
                                                    List.of("runtime/jn_platform_windows.cpp")),
                                            Map.entry(
                                                    "platform.linux",
                                                    List.of("runtime/jn_platform_linux.cpp")),
                                            Map.entry(
                                                    "diagnostics.desktop",
                                                    List.of("runtime/jn_diagnostics.cpp")),
                                            Map.entry(
                                                    "diagnostics.disabled",
                                                    List.of("runtime/jn_diagnostics_none.cpp")),
                                            Map.entry(
                                                    "launcher",
                                                    List.of(
                                                            project.relativize(
                                                                            request.generatedSourcesDirectory()
                                                                                    .resolve(
                                                                                            "launcher.cpp"))
                                                                    .toString()
                                                                    .replace('\\', '/'),
                                                            "runtime/jn_console.cpp")),
                                            Map.entry(
                                                    "includes",
                                                    List.of(
                                                            project.relativize(
                                                                            request
                                                                                    .generatedSourcesDirectory())
                                                                    .toString()
                                                                    .replace('\\', '/'),
                                                            "runtime",
                                                            "user")),
                                            Map.entry(
                                                    "required.features",
                                                    List.of("common-runtime"))))
                            .getBytes(StandardCharsets.UTF_8));
            projects.add(sourceManifest);
            StringBuilder cmake =
                    new StringBuilder(
                            """
                                    cmake_minimum_required(VERSION 3.20)
                                    project(jnative_application LANGUAGES C CXX)
                                    """);
            cmake.append("set(JNATIVE_TARGET_NAME ")
                    .append(CMakeToolchain.quoted(request.targetFileName()))
                    .append(")\n");
            cmake.append("set(JNATIVE_DEFAULT_PAUSE ")
                    .append(request.consoleMode() == ConsoleMode.PAUSE_ON_EXIT ? "ON" : "OFF")
                    .append(")\n");
            cmake.append("set(JNATIVE_DEFAULT_SYMBOLS ")
                    .append(request.nativeSymbols())
                    .append(")\n");
            cmake.append("set(JNATIVE_DEFAULT_NATIVE_TRACES ")
                    .append(request.stackTraces().nativeFrames() ? 1 : 0)
                    .append(")\n");
            cmake.append("set(JNATIVE_DEFAULT_JAVA_TRACES ")
                    .append(request.stackTraces().javaFrames() ? 1 : 0)
                    .append(")\n");
            cmake.append("set(JNATIVE_DEFAULT_CRASH_REPORTS ")
                    .append(request.crashReports() == CrashReportMode.LOCAL ? "ON" : "OFF")
                    .append(")\n");
            cmake.append("get_filename_component(JNATIVE_SOURCE_DIRECTORY ")
                    .append(relative(project, request.generatedSourcesDirectory()))
                    .append(" ABSOLUTE BASE_DIR \"${CMAKE_CURRENT_SOURCE_DIR}\")\n");
            cmake.append("include(\"runtime/jn_project.cmake\")\n");
            Path cmakeFile = project.resolve("CMakeLists.txt");
            files.put(cmakeFile, cmake.toString().getBytes(StandardCharsets.UTF_8));
            projects.add(cmakeFile);
            Path metadata = project.resolve(ProjectFiles.METADATA);
            files.put(
                    metadata,
                    ProjectFiles.properties(
                            Map.ofEntries(
                                    Map.entry("schema", "3"),
                                    Map.entry("mode", "generated"),
                                    Map.entry("runtime", ProjectFiles.RUNTIME_VERSION),
                                    Map.entry("source.required.features", "common-runtime"),
                                    Map.entry(
                                            "platform.required.services",
                                            "threads,synchronization,tls,allocation,floating,files,clocks,output"),
                                    Map.entry(
                                            "platform.optional.services",
                                            "process-exit,console,native-traces,crash-reports"),
                                    Map.entry("source.optional.features", "make_unique,charconv"),
                                    Map.entry(
                                            "build.capabilities",
                                            "b/<configuration>/<toolchain-id>/jnative-capabilities-<Config>.properties"),
                                    Map.entry("native.abi", "1"),
                                    Map.entry("target", request.targetFileName()),
                                    Map.entry("main", request.mainClass()),
                                    Map.entry(
                                            "sources",
                                            project.relativize(request.generatedSourcesDirectory())
                                                    .toString()
                                                    .replace('\\', '/')),
                                    Map.entry("profile", CompatibilityReport.PROFILE),
                                    Map.entry("class.library", CompatibilityReport.LIBRARY_VERSION),
                                    Map.entry(
                                            "class.library.sha256",
                                            CompatibilityReport.libraryFingerprint()),
                                    Map.entry(
                                            "debug.information",
                                            Boolean.toString(request.debugInformation())),
                                    Map.entry("source.layout", request.sourceLayout().name()),
                                    Map.entry("native.symbols", request.nativeSymbols().name()),
                                    Map.entry("stack.traces", request.stackTraces().name()),
                                    Map.entry("crash.reports", request.crashReports().name()),
                                    Map.entry("console.mode", request.consoleMode().name()),
                                    Map.entry("reflection.profile", "public-v1"),
                                    Map.entry(
                                            "java.inputs.sha256", fingerprint(request.classpath())),
                                    Map.entry(
                                            "native.inputs.sha256",
                                            fingerprint(request.nativeOptions().files())))));
            projects.add(metadata);
            Path dependencies = project.resolve("compatibility.tsv");
            files.put(
                    dependencies,
                    CompatibilityReport.dependencies(program).getBytes(StandardCharsets.UTF_8));
            projects.add(dependencies);
            Path inventory = project.resolve("class-library.tsv");
            files.put(
                    inventory,
                    CompatibilityReport.libraryInventory().getBytes(StandardCharsets.UTF_8));
            projects.add(inventory);
            Path readme = project.resolve("NATIVE_PROJECT.md");
            files.put(
                    readme,
                    """
                            # Native project
                            
                            Build with CMake 3.20+ and a C++ compiler:
                            
                                cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
                                cmake --build build --config Release
                            
                            jNative does not select a C++ language standard. Pass -DCMAKE_CXX_STANDARD=20
                            only when you choose that mode; toolchain and SDK settings are respected.
                            JNATIVE_FEATURES=PORTABLE disables optional runtime helpers. AUTO probes the
                            actual compiler without running target code. Sources remain unchanged.
                            jnative-capabilities-<Config>.properties records the selected implementations.
                            Targets needing an atomic support library can set JNATIVE_ATOMIC_LIBRARIES=atomic.
                            
                            An external provider supplies JNATIVE_PLATFORM_SOURCES, optional INCLUDES and
                            LIBRARIES with that same prefix, and JNATIVE_PLATFORM_SERVICES as a CMake list.
                            Required services: threads;synchronization;tls;allocation;floating;files;clocks;output.
                            Standalone launchers also require process-exit; embedded libraries do not.
                            Declare console, native-traces or crash-reports only when available. Requested
                            services fail clearly when unavailable; disable optional desktop services for a port.
                            
                            Set JNATIVE_OUTPUT_KIND=STATIC_LIBRARY or SHARED_LIBRARY to omit the launcher.
                            A parent project can add_subdirectory this project and link jNative::Application.
                            Call jn_app_initialize, jn_app_main (UTF-8 arguments without a program name),
                            and jn_app_shutdown. JN_BUSY leaves the runtime usable: join workers, detach
                            threads and release owned handles before retrying. Never unload while busy.
                            Library defaults leave diagnostics to the host; exception capture can be enabled
                            explicitly on supported desktops. Fatal handlers and final-image symbol archives
                            must be owned by the final host executable.
                            jnative-sources.cmake and jnative-sources.json enumerate reusable sources.
                            jnative-artifact-<Config>.properties records the real target kind and path.
                            Static library dependencies are listed there and in the Java result's outputFiles;
                            runtime/platform archives remain inside the isolated build directory's lib folder.
                            Runtime library copying is independent of diagnostics. Extra SDK search paths
                            can be supplied with JNATIVE_RUNTIME_LIBRARY_DIRECTORIES.
                            
                            On Windows select an installed generator, for example
                            -G "MinGW Makefiles". No Java installation is required.
                            
                            Executables, the diagnostics helper and required libraries go in debug/
                            for Debug or release/ for Release, beside this file. Both configurations
                            can coexist. Single-configuration generators default to Debug if no
                            CMAKE_BUILD_TYPE is specified. CMAKE_RUNTIME_OUTPUT_DIRECTORY provides
                            an explicit output directory override.
                            
                            Debug and Release both produce console executables. Configure with
                            -DJNATIVE_PAUSE_ON_EXIT=ON to wait for a key on Windows (Enter on other
                            platforms) at exit. OFF disables the wait. The generated default follows
                            the Java builder's console mode. Redirected input or output skips the wait.
                            
                            Application class headers and member functions live in src/classes using
                            the recorded PACKAGE_DIRECTORIES or PACKAGE_FILENAME layout. runtime_support.cpp contains initialization, dispatch,
                            factories and tracing definitions; application.cpp contains C exports and host lifecycle, and launcher.cpp contains the desktop main.
                            java-symbols.tsv maps Java methods to C++ functions and files. application.hpp
                            includes the class headers; jnative_exports.h is the stable C source boundary.
                            java_api.hpp declares readable runtime/interface adapters under jnative::java_api.
                            java-api.tsv maps retained Java calls to their exact C++ names and overloads.
                            source-readability.tsv lists structured methods, C ABI native-adapter bodies,
                            and explicit low-level fallbacks. Typed merges, nested loops/switches, exception
                            regions, scoped monitors and multidimensional arrays use readable C++.
                            Labeled Java exits may need descriptive loop_exit/loop_continue labels.
                            Irreducible or unsafe lexical shapes keep reported fallbacks.
                            Readable reconstruction is attempted in both Debug and Release.
                            BuildType selects native compiler optimization independently of source readability.
                            Release probes interprocedural optimization so the compiler can inline across
                            separate class files. JNATIVE_IPO=AUTO enables it when the toolchain supports it;
                            ON requires support, and OFF disables it. Debug keeps IPO disabled.
                            jnative-optimization.properties records the configured result.
                            reflection.cpp contains reflection metadata/adapters; reflection.tsv records
                            the explicit build registrations.
                            The metadata sources key locates sources if generation used an override.
                            
                            Native tracing is the default: no generated JavaFrame or Java line updates.
                            Reports use actual compiled C++ locations. JAVA/BOTH are explicit generation
                            options; debugInformation is only a legacy alias for Java source detail.
                            The recorded source layout cannot be silently changed by regeneration.
                            source-map.json retains Java identities, resolved class origins and hashes.
                            
                            Release keeps optimization and separates native DWARF symbols by default.
                            Configure JNATIVE_SYMBOLS=AUTO, EMBEDDED, SEPARATE or NONE independently.
                            A finalized build writes diagnostics/<build-id> with exact images, symbols,
                            source snapshots and checksums. Ship the executable, native helper and required
                            libraries from the output directory. Keep diagnostics private.
                            
                            Retain every shipped release outside disposable build output:
                            
                                cmake -S . -B b/release -DCMAKE_BUILD_TYPE=Release -DJNATIVE_ARCHIVE_STORE=/private/symbols
                                cmake --build b/release --config Release --target jnative_release
                            
                            The release target fails unless permanent copying and verification succeed.
                            jnative_archive also permits Debug. Ordinary development builds only stage
                            artifacts; clean may delete them. Never rely on rebuilding old symbols later.
                            Sources are snapshotted before compilation; edits during compilation fail
                            archive publication. JNATIVE_ARCHIVE_INPUTS names extra project-relative inputs.
                            JNATIVE_FINALIZE_COMMAND can sign images before their final hashes are retained.
                            
                            Windows x64 MinGW/GCC uses the prestarted jnative-diagnostics.exe helper for
                            minidumps. Linux x64 GCC writes a minimal signal/PC record and preserves system
                            core collection. Set JNATIVE_CRASH_REPORTS=OFF to disable local crash recording.
                            Runtime JNATIVE_REPORT_DIR overrides the per-user report folder, and
                            JNATIVE_INCLUDE_DUMP=0 omits Windows memory dumps. A Java installation is not needed.
                            MSVC/PDB, macOS and ARM64 diagnostics are not validated and are rejected.
                            
                            Developer commands: jnative decode --report report.json --symbols /private/symbols
                            Add --from-dump for Windows minidump unwinding, or --core game.core for Linux GDB.
                            jnative extract-sources --symbols /private/symbols/BUILD_ID --destination /sources/BUILD_ID
                            extracts the exact checked C++ snapshot. Java provenance after native edits
                            is marked stale; C++ locations still refer to the compiled source.
                            Symbol/source tools run on the developer machine, not on a player's machine.
                            
                            Launchers can include jn_report_files.hpp to discover/export reports locally,
                            or run jnative-diagnostics --reports DIR / --export REPORT NEW_DIRECTORY.
                            --without-dump omits memory from an export. Upload/retention belongs to the app.
                            NativeException captures owned C++ construction stacks; foreign std::exception
                            conversion is labeled as a native boundary catch with unavailable throw site.
                            JNATIVE_STRONG_UNWIND=ON retains frame pointers and limits tail-call optimization;
                            it is optional and may affect performance. Ordinary native tracing adds no
                            generated per-line/per-method trace bookkeeping, but capture/startup costs exist.
                            
                            Put handwritten C/C++ in user/. Add user/CMakeLists.txt with
                            target_sources(jnative_app PRIVATE ...) and any native libraries.
                            Resolve extension paths relative to CMAKE_CURRENT_LIST_DIR.
                            
                            A generated project protects edited files during regeneration. An exported
                            project has mode=editable and is developer-owned: edit and compile it directly.
                            Its export-snapshot.properties records the initial content for review only.
                            Regenerate Java into another directory and migrate changes deliberately.
                            
                            Managed C++ needs an attached thread in managed state. Root reference locals
                            with jnative::LocalRoot before allocations/safepoints, store shared fields through
                            jnative::ManagedField, and trace every managed field in trace(). Each concrete
                            class exposes create(...) factories, which allocate a managed shell before
                            calling its initialize(...) Java constructor body. Root the result immediately.
                            The java_api.hpp adapters root incoming references while dispatching; callers
                            must still root arguments before evaluating another allocating argument.
                            Repeated foreign callbacks may use jn_attach_thread/jn_detach_thread.
                            Native code stores retained handles, never unregistered managed pointers.
                            See jn_abi.h for ownership, copied buffers, errors and shutdown.
                            
                            Runtime sources are versioned together with this project. C++ interfaces
                            are source interfaces rebuilt with the application, not a cross-compiler ABI.
                            External dependencies are a C/C++ toolchain, CMake, platform threading and
                            any libraries added by the developer. ASM and JDK files are not embedded.
                            """
                            .getBytes(StandardCharsets.UTF_8));
            projects.add(readme);
            NativePaths.validateProject(
                    project,
                    request.generatedSourcesDirectory(),
                    files.keySet(),
                    request.targetFileName(),
                    project.resolve(
                            "b/"
                                    + request.buildType().name().toLowerCase(Locale.ROOT)
                                    + "/0000000000"),
                    request.nativeSymbols() != NativeSymbols.NONE
                            || request.stackTraces().nativeFrames()
                            || request.crashReports() != CrashReportMode.OFF);
            NativePaths.validate(
                    request.releaseDirectory().resolve(request.targetFileName() + ".exe"));
            ProjectFiles.installOwned(project, files);
            projects.add(project.resolve("jnative.manifest"));
            if(!fallbacks.isEmpty()) {
                log.log(
                        BuildLog.Level.WARNING,
                        "JN3001 "
                                + fallbacks.size()
                                + " method(s) used low-level C++ emission. Report: "
                                + request.generatedSourcesDirectory()
                                .resolve("source-readability.tsv"));
                for(ReadabilityFallback fallback : fallbacks) {
                    log.log(
                            BuildLog.Level.WARNING,
                            "JN3001 Low-level C++ fallback in "
                                    + fallback.javaMethod()
                                    + "\n    Reason: "
                                    + fallback.reason()
                                    + "\n    C++: "
                                    + fallback.cppFile());
                }
            }
            log.log(
                    BuildLog.Level.INFO,
                    "Generated " + program.methods().size() + " reachable methods into " + project);
            return new NativeGenerationResult(request, generated, projects, fallbacks);
        } catch(IOException error) {
            throw new CompilerException("JN4001 Cannot generate native project: " + project, error);
        }
    }

    @Override
    public NativeCompilationResult compile(NativeGenerationResult generation, BuildLog log) {
        return new CMakeToolchain().compile(generation, log);
    }

    private static String relative(Path project, Path file) {
        return CMakeToolchain.quoted(project.relativize(file).toString());
    }

    private static String fingerprint(List<Path> inputs) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for(int i = 0; i < inputs.size(); ++i) {
                Path input = inputs.get(i);
                digest.update((i + ":").getBytes(StandardCharsets.UTF_8));
                if(Files.isDirectory(input)) {
                    try(var walk = Files.walk(input)) {
                        for(Path file :
                                walk.filter(Files::isRegularFile)
                                        .filter(p -> p.toString().endsWith(".class"))
                                        .sorted()
                                        .toList()) {
                            digest.update(
                                    input.relativize(file)
                                            .toString()
                                            .replace('\\', '/')
                                            .getBytes(StandardCharsets.UTF_8));
                            digest.update((byte)0);
                            digest.update(Files.readAllBytes(file));
                        }
                    }
                }
                else if(Files.isRegularFile(input)) digest.update(Files.readAllBytes(input));
                else digest.update("missing".getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
