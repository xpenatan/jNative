package com.github.xpenatan.jnative.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Independently selectable native runtime probes; these are not application/competitor scores. */
public final class RuntimeIsolationBenchmark {
    private static final List<String> OPERATIONS = List.of("pool", "adopt", "allocate", "allocate-gc", "roots", "poll",
            "array-calloc", "array-new");
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static int sequence;

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]), root = Path.of(args[1]);
        String name = args[2], filter = args[3], baseline = args[4], generator = args[5];
        if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid run name");
        if (OPERATIONS.stream().noneMatch(operation -> operation.matches(filter)))
            throw new IllegalArgumentException("The filter selected no isolated operations");
        Path output = root.resolve(name);
        Files.createDirectories(root);
        Files.createDirectory(output);
        Path nativeBuild = output.resolve("native");
        run(output, List.of("cmake", "-S", source.toString(), "-B", nativeBuild.toString(), "-G", generator,
                "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_INTERPROCEDURAL_OPTIMIZATION=ON"), 180);
        run(output, List.of("cmake", "--build", nativeBuild.toString(), "--config", "Release", "--target",
                "runtime_isolation_benchmark", "--parallel", "2"), 300);
        Path executable = nativeBuild.resolve(WINDOWS ? "Release/runtime_isolation_benchmark.exe" : "runtime_isolation_benchmark");
        if (!Files.isRegularFile(executable)) executable = nativeBuild.resolve("runtime_isolation_benchmark.exe");
        var variants = new LinkedHashMap<String, Path>();
        variants.put("Current", executable);
        if (!baseline.isBlank()) variants.put("Previous", Path.of(baseline));
        StringBuilder identities = new StringBuilder("Variant,Path,SHA256\n");
        for (var variant : variants.entrySet()) identities.append(variant.getKey()).append(',')
                .append(variant.getValue()).append(',').append(HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(variant.getValue())))).append('\n');
        Files.writeString(output.resolve("binaries.csv"), identities);
        StringBuilder samples = new StringBuilder("Operation,Count,Variant,Process,Sample,ElapsedNs,Checksum,Collections\n");
        StringBuilder summary = new StringBuilder("Operation,Count,Variant,MedianMs,MinimumMs,MaximumMs\n");
        for (String operation : OPERATIONS) {
            if (!operation.matches(filter)) continue;
            int count = operation.equals("roots") || operation.equals("poll") ? 2000000 : 524288;
            var medians = new LinkedHashMap<String, List<Long>>();
            for (String variant : variants.keySet()) medians.put(variant, new ArrayList<>());
            for (int pair = 0; pair < 6; ++pair) {
                var order = new ArrayList<>(variants.entrySet());
                if (pair % 2 == 1) Collections.reverse(order);
                for (var variant : order) {
                    Path binary = variant.getValue().toAbsolutePath();
                    List<String> command;
                    if (WINDOWS) {
                        if (binary.toString().matches(".*[&|<>^%\"!\\r\\n].*")) throw new IllegalArgumentException("Unsupported path");
                        command = List.of("cmd.exe", "/d", "/s", "/c", "start \"\" /b /wait /affinity 1 \""
                                + binary + "\" " + operation + " " + count);
                    } else command = List.of(binary.toString(), operation, "" + count);
                    String result = run(output, command, 60);
                    var times = new ArrayList<Long>();
                    for (String line : result.strip().split("\\R")) {
                        String[] fields = line.split(",");
                        if (fields.length != 6 || !fields[0].equals(operation) || Integer.parseInt(fields[1]) != count
                                || Integer.parseInt(fields[2]) != times.size() || Long.parseLong(fields[4]) != count)
                            throw new IOException("Invalid isolated result: " + line);
                        long elapsed = Long.parseLong(fields[3]);
                        if (elapsed <= 0 || Long.parseLong(fields[5]) < 0) throw new IOException("Invalid timing/count: " + line);
                        times.add(elapsed);
                        samples.append(operation).append(',').append(count).append(',').append(variant.getKey()).append(',')
                                .append(pair).append(',').append(String.join(",", fields[2], fields[3], fields[4], fields[5])).append('\n');
                    }
                    if (times.size() != 5) throw new IOException("Expected five samples");
                    times.sort(Long::compare);
                    medians.get(variant.getKey()).add(times.get(2));
                }
            }
            for (var variant : medians.entrySet()) {
                List<Long> values = variant.getValue();
                values.sort(Long::compare);
                summary.append(String.format(Locale.ROOT, "%s,%d,%s,%.6f,%.6f,%.6f%n", operation, count,
                        variant.getKey(), (values.get(2) / 2.0 + values.get(3) / 2.0) / 1e6,
                        values.get(0) / 1e6, values.get(5) / 1e6));
            }
            Files.writeString(output.resolve("samples.csv"), samples);
            Files.writeString(output.resolve("timings.csv"), summary);
            System.out.println("Completed isolated " + operation);
        }
        Files.writeString(output.resolve("metadata.txt"), "FinishedUTC=" + Instant.now()
                + "\nProcesses=6\nWarmups=3\nSamples=5\nAffinity=" + (WINDOWS ? "1" : "inherited")
                + "\nFilter=" + filter + "\nUnits=raw elapsed milliseconds; lower is faster\n"
                + "CollectionsIncludeUntimedCleanup=true\n"
                + "Pool/adopt/allocate exclude cleanup; allocate-gc includes automatic GC. Root/poll include loop/control work.\n"
                + "These layer probes are not equivalent C++/GraalVM/TeaVM application comparisons.\n");
        System.out.print(summary);
    }

    private static String run(Path output, List<String> command, int seconds) throws Exception {
        String prefix = String.format(Locale.ROOT, "%03d", sequence++);
        Path stdout = output.resolve(prefix + ".out.log"), stderr = output.resolve(prefix + ".err.log");
        Files.writeString(output.resolve(prefix + ".command.txt"), String.join("\n", command));
        ProcessBuilder builder = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        builder.environment().remove("JNATIVE_STATS");
        builder.environment().remove("JNATIVE_GC_INTERVAL");
        Process process = builder.start();
        process.getOutputStream().close();
        try {
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) throw new IOException("Timed out: " + command);
            if (process.exitValue() != 0) throw new IOException("Failed: " + command + "\n" + Files.readString(stderr)
                    + "\n" + Files.readString(stdout));
            return Files.readString(stdout);
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }
}
