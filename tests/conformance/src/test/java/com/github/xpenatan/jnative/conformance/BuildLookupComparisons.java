package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.CrashReportMode;
import com.github.xpenatan.jnative.NativeBuilder;
import com.github.xpenatan.jnative.NativeSymbols;
import com.github.xpenatan.jnative.StackTraceMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;

/** Preserves a new baseline and builds competitors from exactly the same class files. */
public final class BuildLookupComparisons {
    private static int sequence;

    public static void main(String[] args) throws Exception {
        if ((args.length != 3 && args.length != 4) || args[0].isBlank() || !args[2].matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("Use -PlookupTools=<properties> and a new -PlookupRun=<name>");
        String suite = args.length == 4 ? args[3] : "lookup";
        String main = switch (suite) {
            case "lookup" -> "LookupIsolation";
            case "map-probe" -> "MapProbeIsolation";
            case "allocation" -> "AllocationIsolation";
            case "map-growth" -> "MapGrowthIsolation";
            case "map-iteration" -> "MapIterationIsolation";
            case "nested-array" -> "NestedArrayIsolation";
            case "runtime-cost" -> "RuntimeCostIsolation";
            case "numeric" -> "NumericPipelineIsolation";
            case "transfer" -> "TransferIsolation";
            default -> throw new IllegalArgumentException("Unknown probe suite: " + suite);
        };
        var tools = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(args[0]))) { tools.load(reader); }
        Path root = Path.of(args[1]);
        Files.createDirectories(root);
        Path output = Files.createDirectory(root.resolve(args[2]));
        Files.copy(Path.of(args[0]), output.resolve("tools.properties"));
        Path source = output.resolve(main + ".java"), classes = Files.createDirectory(output.resolve("classes"));
        try (var input = BuildLookupComparisons.class.getResourceAsStream("/fixtures/" + main + ".java")) {
            if (input == null) throw new IOException("Missing lookup fixture");
            Files.copy(input, source);
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "--release", "17", "-g", "-d", classes.toString(), source.toString()) != 0)
            throw new IOException("Fixture compilation failed");
        StringBuilder hashes = new StringBuilder("File,SHA256\n");
        try (var files = Files.list(classes)) {
            for (Path file : files.sorted().toList()) hashes.append(file.getFileName()).append(',')
                    .append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))).append('\n');
        }
        Files.writeString(output.resolve("inputs.csv"), hashes);
        String generator = tools.getProperty("generator", "Visual Studio 18 2026");
        var nativeResult = NativeBuilder.create().classpath(classes).mainClass(main)
                .buildRoot(output.resolve("jnative")).buildType(BuildType.RELEASE).generator(generator)
                .cmakeDefine("JNATIVE_IPO", "ON").nativeSymbols(NativeSymbols.NONE)
                .stackTraces(StackTraceMode.NONE).crashReports(CrashReportMode.OFF)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(10)).build();
        System.out.println("Built current jNative " + suite + " probe.");
        Path priorExecutable = null;
        String priorRuntime = tools.getProperty("priorRuntime");
        if (priorRuntime != null) {
            var priorBuilder = NativeBuilder.create().classpath(classes).mainClass(main)
                    .buildRoot(output.resolve("prior")).buildType(BuildType.RELEASE).generator(generator)
                    .cmakeDefine("JNATIVE_IPO", "ON").nativeSymbols(NativeSymbols.NONE)
                    .stackTraces(StackTraceMode.NONE).crashReports(CrashReportMode.OFF)
                    .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(10));
            var generation = priorBuilder.generate();
            Path runtime = generation.request().buildRoot().resolve("native/runtime");
            StringBuilder priorHashes = new StringBuilder("File,SHA256\n");
            for (Path target : generation.generatedFiles()) {
                if (!target.getParent().equals(runtime)) continue;
                Path sourceFile = Path.of(priorRuntime).resolve(target.getFileName());
                Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
                priorHashes.append(target.getFileName()).append(',').append(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)))).append('\n');
            }
            if (priorHashes.toString().lines().count() < 2) throw new IOException("No prior runtime files copied");
            Files.writeString(output.resolve("prior-runtime.csv"), priorHashes);
            priorExecutable = priorBuilder.compile(generation).executable();
            System.out.println("Built matching fixture with the preserved prior runtime; source hashes recorded.");
        }
        Path graal = output.resolve("graal-" + suite);
        String command = "call " + quote(Path.of(required(tools, "vcvars"))) + " >nul && call "
                + quote(Path.of(required(tools, "nativeImage")))
                + " --no-fallback -O3 -march=native --gc=serial -cp " + quote(classes)
                + " -o " + quote(graal) + " " + main;
        run(output, List.of("cmd.exe", "/d", "/s", "/c", command), 600);
        System.out.println("Built GraalVM lookup comparator.");
        Path tea = Files.createDirectory(output.resolve("teavm"));
        run(output, List.of(ProcessHarness.java(), "-cp", System.getProperty("java.class.path"),
                CompileLookupTeaVm.class.getName(), classes.toString(), tea.resolve("generated").toString(), main), 300);
        Files.writeString(tea.resolve("CMakeLists.txt"), """
                cmake_minimum_required(VERSION 3.24)
                project(lookup_teavm C)
                add_executable(lookup_teavm generated/all.c)
                set_property(TARGET lookup_teavm PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
                target_compile_options(lookup_teavm PRIVATE /O2 /Ob3 /fp:strict /wd4996 /wd4146)
                """);
        Path teaBuild = tea.resolve("native");
        run(output, List.of("cmake", "-S", tea.toString(), "-B", teaBuild.toString(), "-G", generator), 120);
        run(output, List.of("cmake", "--build", teaBuild.toString(), "--config", "Release", "--parallel", "2"), 300);
        var config = new Properties();
        config.setProperty("runName", suite + "-" + args[2]);
        config.setProperty("generator", generator);
        String referenceKey = switch (suite) {
            case "lookup" -> "reference.lookupClasses";
            case "map-probe" -> "reference.mapProbeClasses";
            case "allocation" -> "reference.allocationClasses";
            case "map-growth" -> "reference.mapGrowthClasses";
            case "map-iteration" -> "reference.mapIterationClasses";
            case "nested-array" -> "reference.nestedArrayClasses";
            case "runtime-cost" -> "reference.runtimeCostClasses";
            case "numeric" -> "reference.numericClasses";
            case "transfer" -> "reference.transferClasses";
            default -> throw new IllegalArgumentException(suite);
        };
        config.setProperty(referenceKey, classes.toString());
        config.setProperty(suite + ".jNative", nativeResult.executable().toString());
        if (priorExecutable != null) config.setProperty(suite + ".Baseline", priorExecutable.toString());
        config.setProperty(suite + ".GraalVM", graal + ".exe");
        config.setProperty(suite + ".TeaVM", teaBuild.resolve("Release/lookup_teavm.exe").toString());
        config.setProperty("cases", suite + ":.*");
        config.setProperty("pairs", "6");
        config.setProperty("profileGc", "true");
        try (var writer = Files.newBufferedWriter(output.resolve("benchmark.properties"))) {
            config.store(writer, "All variants use the same fixture bytecode. Raw times only.");
        }
        System.out.println("Ready: " + output.resolve("benchmark.properties"));
    }

    private static String required(Properties tools, String key) {
        String value = tools.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static String quote(Path path) {
        String value = path.toAbsolutePath().toString();
        if (value.matches(".*[&|<>^%\"!\\r\\n].*")) throw new IllegalArgumentException("Unsupported path");
        return "\"" + value + "\"";
    }

    static void run(Path output, List<String> command, int seconds) throws Exception {
        String prefix = String.format("%02d", sequence++);
        Path stdout = output.resolve(prefix + ".out.log"), stderr = output.resolve(prefix + ".err.log");
        Files.writeString(output.resolve(prefix + ".command.txt"), String.join("\n", command));
        Process process = new ProcessBuilder(command).directory(output.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        process.getOutputStream().close();
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) throw new IOException("Build timed out: " + prefix);
            if (process.exitValue() != 0) throw new IOException("Build failed: " + prefix + "\n"
                    + Files.readString(stderr) + "\n" + Files.readString(stdout));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) process.destroyForcibly();
        }
    }
}
