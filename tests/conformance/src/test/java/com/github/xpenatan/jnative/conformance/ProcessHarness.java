package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.CrashReportMode;
import com.github.xpenatan.jnative.NativeBuilder;
import com.github.xpenatan.jnative.NativeCompilationResult;
import com.github.xpenatan.jnative.NativeSymbols;
import com.github.xpenatan.jnative.StackTraceMode;
import com.github.xpenatan.jnative.internal.Json;
import com.github.xpenatan.jnative.interop.NativeImport;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Assertions;

/**
 * Differential test support. Each process has a time limit and an independent log.
 */
final class ProcessHarness {
    static String generatedClassSource(Path generated, String owner) throws IOException {
        var report = (Map<?, ?>)Json.read(
                Files.readString(generated.resolve("source-map.json")));
        for(Object value : (List<?>)report.get("classes")) {
            var entry = (Map<?, ?>)value;
            if(owner.equals(entry.get("javaInternalName")))
                return Files.readString(generated.resolve((String)entry.get("cppFile")));
        }
        throw new AssertionError("Missing generated class source: " + owner);
    }

    static NativeBuilder behavioralBuilder() {
        String generator = System.getProperty("jnative.test.generator", "");
        var builder = NativeBuilder.create().generator(generator);
        String releaseFlags = System.getProperty("jnative.test.releaseFlags", "");
        if(!releaseFlags.isEmpty()) builder.cmakeDefine("CMAKE_CXX_FLAGS_RELEASE", releaseFlags);
        String actualGenerator =
                generator.isEmpty()
                        ? System.getenv().getOrDefault("CMAKE_GENERATOR", "")
                        : generator;
        if(actualGenerator.startsWith("Visual Studio")) {
            // These differential tests exercise Java behavior. Native diagnostic
            // archives have separate coverage and currently require DWARF tools.
            builder.nativeSymbols(NativeSymbols.NONE)
                    .stackTraces(StackTraceMode.JAVA)
                    .crashReports(CrashReportMode.OFF);
        }
        return builder;
    }

    record Output(int exitCode, String text) {
    }

    static void javac(Path source, Path classes, int release) throws IOException {
        Files.createDirectories(classes);
        int status =
                ToolProvider.getSystemJavaCompiler()
                        .run(
                                null,
                                null,
                                null,
                                "--release",
                                Integer.toString(release),
                                "-encoding",
                                "UTF-8",
                                "-g",
                                "-cp",
                                Path.of(
                                                URI.create(
                                                        NativeImport.class
                                                                .getProtectionDomain()
                                                                .getCodeSource()
                                                                .getLocation()
                                                                .toString()))
                                        .toString(),
                                "-d",
                                classes.toString(),
                                source.toString());
        if(status != 0) throw new AssertionError("javac failed: " + source);
    }

    static Output run(Path directory, List<String> command, Duration timeout) throws Exception {
        return run(directory, command, timeout, Map.of());
    }

    static Output run(
            Path directory,
            List<String> command,
            Duration timeout,
            Map<String, String> environment)
            throws Exception {
        if(command.getFirst().equals(java())) {
            var utf8Command = new ArrayList<>(command);
            utf8Command.addAll(
                    1,
                    List.of(
                            "-Dstdout.encoding=UTF-8",
                            "-Dstderr.encoding=UTF-8",
                            "-Dfile.encoding=UTF-8"));
            command = utf8Command;
        }
        Path log = Files.createTempFile(directory, "process-", ".log");
        var builder =
                new ProcessBuilder(command)
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile());
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            if(!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Process timed out: " + command);
            }
            return new Output(
                    process.exitValue(),
                    Files.readString(log, StandardCharsets.UTF_8).replace("\r\n", "\n"));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if(process.isAlive()) process.destroyForcibly();
        }
    }

    static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    static void compareLinux(
            Path directory,
            NativeCompilationResult result,
            Output expected,
            int collectionInterval)
            throws Exception {
        compareLinuxProject(
                directory,
                result.generation().request().buildRoot().resolve("native"),
                result.generation().request().targetFileName(),
                expected,
                collectionInterval);
    }

    static void compareLinuxProject(
            Path directory, Path root, String target, Output expected, int collectionInterval)
            throws Exception {
        compareLinuxProject(directory, root, target, expected, collectionInterval, List.of());
    }

    static void compareLinuxProject(
            Path directory,
            Path root,
            String target,
            Output expected,
            int collectionInterval,
            List<String> arguments)
            throws Exception {
        String windowsPath = root.toString().replace('\\', '/');
        if(windowsPath.length() < 3 || windowsPath.charAt(1) != ':')
            throw new IllegalArgumentException("WSL validation requires a Windows drive path");
        String project =
                "/mnt/" + Character.toLowerCase(windowsPath.charAt(0)) + windowsPath.substring(2);
        String build = project + "/linux-sanitized";
        var configured =
                run(
                        directory,
                        List.of(
                                "wsl",
                                "-d",
                                "Ubuntu",
                                "--",
                                "cmake",
                                "-S",
                                project,
                                "-B",
                                build,
                                "-G",
                                "Ninja",
                                "-DCMAKE_BUILD_TYPE=Release",
                                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=" + build,
                                "-DCMAKE_CXX_FLAGS=-fsanitize=address,undefined"
                                        + " -fno-omit-frame-pointer -flto"),
                        Duration.ofSeconds(60));
        Assertions.assertEquals(0, configured.exitCode(), configured.text());
        var built =
                run(
                        directory,
                        List.of(
                                "wsl",
                                "-d",
                                "Ubuntu",
                                "--",
                                "cmake",
                                "--build",
                                build,
                                "--parallel",
                                "2"),
                        Duration.ofSeconds(120));
        Assertions.assertEquals(0, built.exitCode(), built.text());
        // Merge inside Linux: WSL's stdout/stderr can have independent file offsets.
        // Transport argv as data to avoid two layers of Windows/WSL shell quoting.
        Path runner = directory.resolve("linux-run.py"), data = directory.resolve("linux-argv.txt");
        Files.writeString(
                runner,
                """
                        import base64, os, sys
                        from pathlib import Path
                        values = [base64.b64decode(v).decode('utf-8') for v in Path(sys.argv[1]).read_bytes().splitlines()]
                        os.environ['JNATIVE_GC_INTERVAL'] = values[0]
                        os.environ['ASAN_OPTIONS'] = 'detect_leaks=1'
                        os.dup2(1, 2)
                        os.execv(values[1], values[1:])
                        """);
        var values =
                new ArrayList<>(
                        List.of(Integer.toString(collectionInterval), build + "/" + target));
        values.addAll(arguments);
        Files.writeString(
                data,
                String.join(
                        "\n",
                        values.stream()
                                .map(
                                        v ->
                                                Base64.getEncoder()
                                                        .encodeToString(
                                                                v.getBytes(
                                                                        StandardCharsets
                                                                                .UTF_8)))
                                .toList())
                        + "\n");
        String folder = directory.toString().replace('\\', '/');
        folder = "/mnt/" + Character.toLowerCase(folder.charAt(0)) + folder.substring(2);
        var actual =
                run(
                        directory,
                        List.of(
                                "wsl",
                                "-d",
                                "Ubuntu",
                                "--exec",
                                "python3",
                                folder + "/linux-run.py",
                                folder + "/linux-argv.txt"),
                        Duration.ofSeconds(60));
        Assertions.assertEquals(expected, actual);
    }
}
