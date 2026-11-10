package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.CrashReportMode;
import com.github.xpenatan.jnative.NativeBuilder;
import com.github.xpenatan.jnative.NativeSymbols;
import com.github.xpenatan.jnative.StackTraceMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.tools.ToolProvider;

/** Raw elapsed-time comparisons; timing is never a correctness assertion. */
public final class BenchmarkScores {
    private static final List<String> GENERAL = List.of("reference-array", "typed-array", "list-grow",
            "map-grow", "dispatch", "arithmetic", "hash-cold", "search-miss", "search-prefix");
    private static final List<String> CPP = List.of("dispatch", "arithmetic", "hash-cold", "search-miss", "search-prefix");
    private static final List<String> ALLOCATION = List.of("shell-drop", "shell-ring", "retained-graph",
            "reference-array", "typed-array", "primitive-array");
    private static final List<String> MAP_GROWTH = List.of("boxed-grow", "boxed-presized",
            "preboxed-grow", "preboxed-presized");
    private static final List<String> MAP_ITERATION = List.of("hash-dense", "hash-sparse", "linked-sparse");
    private static final List<String> NESTED_ARRAY = List.of("rows-8", "rows-24", "rows-variable", "flat-24");
    private static final List<String> RUNTIME_COST = List.of("store-rows", "store-objects", "store-cells", "buffer-bulk", "buffer-scalar");
    private static final List<String> TRANSFER = List.of("array-copy", "buffer-copy", "buffer-swap");
    private static final List<String> NUMERIC = List.of("fields", "lookup", "pack", "pipeline", "repeat-local", "repeat-field");
    private static final List<String> LOOKUP = List.of("hash", "get-same", "get-equal", "get-miss", "put-replace", "scan");
    private static final List<String> MAP_PROBE = List.of("hash-erased", "hash-typed", "hash-object", "hash-identity",
            "hash-string", "get-object", "get-string", "get-collision4-same", "get-collision4-equal",
            "get-collision4-miss", "get-collision8-same", "get-collision8-equal", "get-collision8-miss");
    private static final int SAMPLES = 5;
    private static final int PILOT_SAMPLES = 3;
    private final int pairs;
    private final Properties config = new Properties();
    private final BenchmarkCalibration calibration;
    private final Path output;
    private final Path classes;
    private final Map<String, Map<String, Path>> executables = new LinkedHashMap<>();
    private final List<Sample> samples = new ArrayList<>();
    private final List<Summary> summaries = new ArrayList<>();
    private final List<String> verification = new ArrayList<>();
    private final Map<Case, String> expectedChecksums = new LinkedHashMap<>();
    private final Map<Case, String> calibrationReasons = new LinkedHashMap<>();
    private final StringBuilder calibrationRows = new StringBuilder(
            "Suite,Operation,Size,Attempt,Rounds,Variant,MedianNs\n");
    private int sequence;

    record Case(String suite, String operation, int size, int rounds) {
        String key() { return suite + ":" + operation + ":" + size; }
        List<String> arguments(int count) {
            return List.of(operation, "" + size, "" + rounds, "" + count);
        }
    }
    private record Sample(Case work, String variant, int pair, long nanos, String checksum) {}
    private record Summary(Case work, String variant, double median, double minimum, double maximum) {}
    private record Result(int exitCode, String text, String error) {}
    record Reading(long nanos, String checksum) {}

    private BenchmarkScores(Path configuration, Path buildDirectory) throws Exception {
        try (var input = Files.newBufferedReader(configuration, StandardCharsets.UTF_8)) { config.load(input); }
        calibration = BenchmarkCalibration.from(config);
        pairs = Integer.parseInt(config.getProperty("pairs", "4"));
        if (pairs < 1 || pairs > 32) throw new IllegalArgumentException("pairs must be between 1 and 32");
        String name = required("runName");
        if (!name.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid run name");
        output = buildDirectory.toAbsolutePath().normalize().resolve(name);
        Files.createDirectories(output.getParent());
        Files.createDirectory(output); // Never overwrite a previous run.
        classes = Files.createDirectory(output.resolve("classes"));
        Files.copy(configuration, output.resolve("configuration.properties"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || args[0].isBlank())
            throw new IllegalArgumentException("Use -PbenchmarkConfig=<properties file> with benchmarkScores");
        new BenchmarkScores(Path.of(args[0]), Path.of(args[1])).run();
    }

    private void run() throws Exception {
        Instant started = Instant.now();
        List<Case> selected = selectedCases().stream().map(this::boundedInitialCase).toList();
        if (selected.isEmpty()) throw new IllegalArgumentException("The case filter selected no workloads");
        compileFixtures();
        if (selected.stream().anyMatch(work -> work.suite().equals("general"))) prepare("general", "GeneralPerformanceBenchmark");
        if (selected.stream().anyMatch(work -> work.suite().equals("identity"))) prepare("identity", "IdentityHashBenchmark");
        if (selected.stream().anyMatch(work -> work.suite().equals("lookup"))) prepare("lookup", "LookupIsolation");
        if (selected.stream().anyMatch(work -> work.suite().equals("map-probe"))) prepare("map-probe", "MapProbeIsolation");
        if (selected.stream().anyMatch(work -> work.suite().equals("allocation"))) prepare("allocation", "AllocationIsolation");
        if (selected.stream().anyMatch(work -> work.suite().equals("map-growth"))) prepare("map-growth", "MapGrowthIsolation");
        if (selected.stream().anyMatch(work -> work.suite().equals("map-iteration"))) prepare("map-iteration", "MapIterationIsolation");
        if (selected.stream().anyMatch(work -> work.suite().equals("nested-array"))) prepare("nested-array", "NestedArrayIsolation");
        if (selected.stream().anyMatch(work -> work.suite().equals("runtime-cost"))) prepare("runtime-cost", "RuntimeCostIsolation");
        if (selected.stream().anyMatch(work -> work.suite().equals("numeric"))) prepare("numeric", "NumericPipelineIsolation");
        if (selected.stream().anyMatch(work -> work.suite().equals("transfer"))) prepare("transfer", "TransferIsolation");
        var binaries = new StringBuilder("Suite,Variant,Path,SHA256\n");
        for (var suite : executables.entrySet())
            for (var entry : suite.getValue().entrySet())
                binaries.append(csv(suite.getKey(), entry.getKey(), entry.getValue(), hash(entry.getValue())));
        Files.writeString(output.resolve("binaries.csv"), binaries);
        verify(selected);
        if(calibration.enabled()) selected = calibrate(selected);
        for(Case work : selected)
            config.setProperty("rounds." + work.suite() + "." + work.operation() + "." + work.size(), Integer.toString(work.rounds()));
        try(var writer = Files.newBufferedWriter(output.resolve("resolved-configuration.properties"))) {
            config.store(writer, "Resolved identical work counts for every implementation; disable calibration to repeat exactly");
        }
        for (Case work : selected) measure(work);
        writeComparisons();
        if (Boolean.parseBoolean(config.getProperty("profileGc", "false"))) profileGc();
        Files.writeString(output.resolve("metadata.txt"), "StartedUTC=" + started + "\nFinishedUTC=" + Instant.now()
                + "\nOS=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + "\nJava=" + System.getProperty("java.runtime.version")
                + "\nProcessesPerVariant=" + pairs + "\nWarmups=3\nSamplesPerProcess=" + SAMPLES
                + "\nCalibration=" + calibration.enabled() + "\nTargetSampleMillis=" + calibration.targetNanos() / 1_000_000
                + "\nMaximumPilotSampleMillis=" + calibration.maximumNanos() / 1_000_000
                + "\nRoundScaledSetupBudgetMiB=" + calibration.setupBytes() / (1024 * 1024)
                + "\nCaseFilter=" + config.getProperty("cases", "general:.*")
                + "\nAffinity=1\nGC=default\nOrder=forward/reverse alternating\n"
                + "Timing=median of process medians, nanoseconds; lower is faster\n");
        System.out.println("Benchmark results: " + output);
        System.out.print(Files.readString(output.resolve("timings.csv")));
    }

    private List<Case> selectedCases() {
        return cases(config);
    }

    static List<Case> cases(Properties config) {
        var cases = new ArrayList<Case>();
        for (String operation : GENERAL) {
            for (int size : List.of(16, 256, 4096)) {
                int work = operation.equals("arithmetic") || operation.equals("dispatch") ? 20000000 : 2000000;
                int rounds = Math.max(40, (int) Math.rint((double) work / Math.max(16, size)));
                if (operation.equals("map-grow")) rounds = Math.max(20, (int) Math.rint(rounds / 8.0));
                if (operation.equals("hash-cold")) rounds = Math.min(rounds, 4096);
                if (operation.equals("search-prefix")) rounds = Math.min(rounds, 100);
                cases.add(new Case("general", operation, size, rounds));
            }
        }
        for (String operation : List.of("identity-get", "object-get"))
            for (int size : List.of(16, 256, 4096))
                cases.add(new Case("identity", operation, size, (int) Math.rint(2000000.0 / size)));
        for (String operation : LOOKUP)
            for (int size : List.of(16, 256, 4096))
                cases.add(new Case("lookup", operation, size, (int) Math.rint(2000000.0 / size)));
        for (String operation : MAP_PROBE)
            for (int size : List.of(16, 256, 4096))
                cases.add(new Case("map-probe", operation, size, (int) Math.rint(2000000.0 / size)));
        for (String operation : MAP_GROWTH)
            for (int size : List.of(16, 256, 4096))
                cases.add(new Case("map-growth", operation, size, Math.max(20, (int) Math.rint(250000.0 / size))));
        for (String operation : MAP_ITERATION)
            for (int size : List.of(16, 256, 4096))
                cases.add(new Case("map-iteration", operation, size, (int) Math.rint(2000000.0 / size)));
        for (String operation : NESTED_ARRAY)
            for (int size : List.of(16, 256, 4096))
                cases.add(new Case("nested-array", operation, size, Math.max(1, 500000 / size)));
        List<Integer> bufferSizes = Arrays.stream(config.getProperty("buffer.sizes", "4096,65536").split(","))
                .map(String::strip).map(Integer::parseInt).toList();
        if (bufferSizes.isEmpty() || bufferSizes.stream().anyMatch(size -> size < 1 || size > 1048576))
            throw new IllegalArgumentException("buffer.sizes must contain positive sizes up to 1048576");
        for (String operation : RUNTIME_COST)
            for (int size : operation.startsWith("buffer") ? bufferSizes : List.of(256, 4096))
                cases.add(new Case("runtime-cost", operation, size,
                        Math.max(1, (operation.startsWith("buffer") ? 16777216 : 2000000) / size)));
        for (String operation : NUMERIC)
            for (int size : List.of(256, 4096))
                cases.add(new Case("numeric", operation, size, Math.max(1, 2000000 / size)));
        for (String operation : TRANSFER)
            for (int size : List.of(20, 4096, 163820))
                cases.add(new Case("transfer", operation, size, Math.max(1, 16777216 / size)));
        List<Integer> allocationSizes = Arrays.stream(config.getProperty("allocation.sizes", "16,256,4096").split(","))
                .map(String::strip).map(Integer::parseInt).toList();
        if (allocationSizes.isEmpty() || allocationSizes.stream().anyMatch(size -> size < 1 || size > 1048576))
            throw new IllegalArgumentException("allocation.sizes must contain positive sizes up to 1048576");
        for (String operation : ALLOCATION)
            for (int size : allocationSizes)
                cases.add(new Case("allocation", operation, size, operation.endsWith("array")
                        ? Math.max(8, (int) Math.rint(2000000.0 / size)) : 262144));
        return cases.stream().filter(work -> work.key().matches(config.getProperty("cases", "general:.*")))
                .map(work -> configuredRounds(work, config)).toList();
    }

    private static Case configuredRounds(Case work, Properties config) {
        String property = "rounds." + work.suite() + "." + work.operation() + "." + work.size();
        String value = config.getProperty(property);
        if (value == null) return work;
        int rounds = Integer.parseInt(value);
        if (rounds < 1) throw new IllegalArgumentException(property + " must be positive");
        return new Case(work.suite(), work.operation(), work.size(), rounds);
    }

    private String required(String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing property: " + key);
        return value;
    }

    private void compileFixtures() throws Exception {
        Path sources = Files.createDirectory(output.resolve("fixtures"));
        var arguments = new ArrayList<>(List.of("--release", "17", "-g", "-encoding", "UTF-8", "-d", classes.toString()));
        boolean lookup = selectedCases().stream().anyMatch(work -> work.suite().equals("lookup"));
        var fixtures = new ArrayList<String>();
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("general"))) fixtures.add("GeneralPerformanceBenchmark");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("identity"))) fixtures.add("IdentityHashBenchmark");
        if (lookup) fixtures.add("LookupIsolation");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("map-probe"))) fixtures.add("MapProbeIsolation");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("allocation"))) fixtures.add("AllocationIsolation");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("map-growth"))) fixtures.add("MapGrowthIsolation");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("map-iteration"))) fixtures.add("MapIterationIsolation");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("nested-array"))) fixtures.add("NestedArrayIsolation");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("runtime-cost"))) fixtures.add("RuntimeCostIsolation");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("numeric"))) fixtures.add("NumericPipelineIsolation");
        if (selectedCases().stream().anyMatch(work -> work.suite().equals("transfer"))) fixtures.add("TransferIsolation");
        for (String name : fixtures) {
            Path source = sources.resolve(name + ".java");
            try (var input = BenchmarkScores.class.getResourceAsStream("/fixtures/" + name + ".java")) {
                if (input == null) throw new IOException("Missing fixture " + name);
                Files.copy(input, source);
            }
            arguments.add(source.toString());
        }
        if (ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)) != 0)
            throw new IOException("Fixture compilation failed");
        var hashes = new StringBuilder("File,SHA256\n");
        try (var files = Files.list(classes)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                String referenceKey = name.startsWith("LookupIsolation") ? "reference.lookupClasses"
                        : name.startsWith("MapProbeIsolation") ? "reference.mapProbeClasses"
                        : name.startsWith("AllocationIsolation") ? "reference.allocationClasses"
                        : name.startsWith("MapGrowthIsolation") ? "reference.mapGrowthClasses"
                        : name.startsWith("MapIterationIsolation") ? "reference.mapIterationClasses"
                        : name.startsWith("NestedArrayIsolation") ? "reference.nestedArrayClasses"
                        : name.startsWith("RuntimeCostIsolation") ? "reference.runtimeCostClasses"
                        : name.startsWith("NumericPipelineIsolation") ? "reference.numericClasses"
                        : name.startsWith("TransferIsolation") ? "reference.transferClasses"
                        : name.startsWith("IdentityHashBenchmark") ? "reference.identityClasses" : "reference.classes";
                Path reference = Path.of(required(referenceKey)).resolve(file.getFileName());
                String sha = hash(file);
                if (!sha.equals(hash(reference)))
                    throw new IOException("Fixture differs from competitor build input: " + file.getFileName());
                hashes.append(csv(file.getFileName(), sha));
            }
        }
        Files.writeString(output.resolve("inputs.csv"), hashes);
        System.out.println("Fresh fixtures match retained competitor bytecode exactly.");
    }

    private void prepare(String suite, String main) {
        var variants = new LinkedHashMap<String, Path>();
        String existing = config.getProperty(suite + ".jNative");
        if (existing == null) {
            var builder = NativeBuilder.create().classpath(classes).mainClass(main)
                    .buildRoot(output.resolve("j").resolve(suite)).buildType(BuildType.RELEASE)
                    .generator(required("generator")).cmakeDefine("JNATIVE_IPO", "ON")
                    .nativeSymbols(NativeSymbols.NONE).stackTraces(StackTraceMode.NONE).crashReports(CrashReportMode.OFF)
                    .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(10));
            if (Boolean.parseBoolean(config.getProperty("linkMap", "false"))) {
                if (!required("generator").startsWith("Visual Studio"))
                    throw new IllegalArgumentException("linkMap currently requires MSVC");
                builder.cmakeDefine("CMAKE_EXE_LINKER_FLAGS", "/MAP");
            }
            var result = builder.build();
            variants.put("jNative", result.executable().toAbsolutePath());
        } else {
            Path executable = Path.of(existing).toAbsolutePath();
            if (!Files.isRegularFile(executable)) throw new IllegalArgumentException("Missing executable: " + executable);
            variants.put("jNative", executable);
        }
        for (String variant : List.of("GraalVM", "Cpp", "CppStack", "CppSmall", "CppSmall32", "CppAtomic", "Baseline", "Previous")) {
            String value = config.getProperty(suite + "." + variant);
            if (value != null) {
                Path executable = Path.of(value).toAbsolutePath();
                if (!Files.isRegularFile(executable)) throw new IllegalArgumentException("Missing executable: " + executable);
                variants.put(variant, executable);
            }
        }
        if (!variants.keySet().containsAll(List.of("jNative", "GraalVM")))
            throw new IllegalArgumentException("Missing comparison implementations for " + suite);
        executables.put(suite, variants);
    }

    private void verifyMapSuite(List<Case> selected, String suite, String fixture, List<String> operations) throws Exception {
        for (String operation : operations) {
            if (selected.stream().noneMatch(work -> work.suite().equals(suite) && work.operation().equals(operation))) continue;
            for (int size : List.of(17, 257)) {
                Case work = new Case(suite, operation, size, 10);
                Result expected = jvm(fixture, work.arguments(1));
                requireSuccess(expected);
                String checksum = expected.text().strip().split(",")[5];
                for (var variant : executables.get(suite).entrySet()) {
                    Result actual = execute(variant.getValue(), work.arguments(1), "verify-" + suite + "-" + variant.getKey());
                    requireSuccess(actual);
                    if (!actual.text().strip().split(",")[5].equals(checksum))
                        throw new IOException("JVM checksum mismatch: " + suite + " " + operation + " " + variant.getKey());
                }
            }
        }
        if (executables.containsKey(suite))
            verification.add(suite + ": all implementations match JVM checksums on independent small sizes.");
    }

    private void verify(List<Case> selected) throws Exception {
        for (Case work : selected)
            for (String variant : List.of("GraalVM", "Cpp"))
                if (unavailable(work, variant)) verification.add("Declared unavailable comparator: " + work.key() + " " + variant);
        verifyMapSuite(selected, "map-growth", "MapGrowthIsolation", MAP_GROWTH);
        verifyMapSuite(selected, "map-iteration", "MapIterationIsolation", MAP_ITERATION);
        verifyMapSuite(selected, "nested-array", "NestedArrayIsolation", NESTED_ARRAY);
        verifyMapSuite(selected, "runtime-cost", "RuntimeCostIsolation", RUNTIME_COST);
        verifyMapSuite(selected, "numeric", "NumericPipelineIsolation", NUMERIC);
        verifyMapSuite(selected, "transfer", "TransferIsolation", TRANSFER);
        for (String operation : ALLOCATION) {
            if (selected.stream().noneMatch(work -> work.suite().equals("allocation") && work.operation().equals(operation))) continue;
            for (int rounds : List.of(10, 73)) {
                Case work = new Case("allocation", operation, 17, rounds);
                Result expected = jvm("AllocationIsolation", work.arguments(1));
                requireSuccess(expected);
                String checksum = expected.text().strip().split(",")[5];
                for (var variant : executables.get("allocation").entrySet()) {
                    Result actual = execute(variant.getValue(), work.arguments(1), "verify-allocation-" + variant.getKey());
                    requireSuccess(actual);
                    if (!actual.text().strip().split(",")[5].equals(checksum))
                        throw new IOException("JVM checksum mismatch: allocation " + operation + " " + variant.getKey());
                }
            }
        }
        if (executables.containsKey("allocation"))
            verification.add("Allocation probes: all implementations match JVM checksums, including partial/wrapped retention rings.");
        for (String operation : MAP_PROBE) {
            if (selected.stream().noneMatch(work -> work.suite().equals("map-probe") && work.operation().equals(operation))) continue;
            Case work = new Case("map-probe", operation, 17, 10);
            Result expected = jvm("MapProbeIsolation", work.arguments(1));
            requireSuccess(expected);
            String checksum = expected.text().strip().split(",")[5];
            for (var variant : executables.get("map-probe").entrySet()) {
                Result actual = execute(variant.getValue(), work.arguments(1), "verify-map-probe-" + variant.getKey());
                requireSuccess(actual);
                if (!actual.text().strip().split(",")[5].equals(checksum))
                    throw new IOException("JVM checksum mismatch: map-probe " + operation + " " + variant.getKey());
            }
        }
        if (executables.containsKey("map-probe"))
            verification.add("Selected hashing/collision probes: every implementation's checksums match the JVM.");
        for (String operation : LOOKUP) {
            if (selected.stream().noneMatch(work -> work.suite().equals("lookup") && work.operation().equals(operation))) continue;
            Case work = new Case("lookup", operation, 17, 10);
            Result expected = jvm("LookupIsolation", work.arguments(1));
            requireSuccess(expected);
            String checksum = expected.text().strip().split(",")[5];
            for (var variant : executables.get("lookup").entrySet()) {
                Result actual = execute(variant.getValue(), work.arguments(1), "verify-lookup-" + variant.getKey());
                requireSuccess(actual);
                if (!actual.text().strip().split(",")[5].equals(checksum))
                    throw new IOException("JVM checksum mismatch: lookup " + operation + " " + variant.getKey());
            }
        }
        if (executables.containsKey("lookup"))
            verification.add("Selected isolated lookup operations: every implementation's checksums match the JVM.");
        for (String operation : GENERAL) {
            if (selected.stream().noneMatch(work -> work.suite().equals("general") && work.operation().equals(operation))) continue;
            Case work = new Case("general", operation, 17, 10);
            Result expected = jvm("GeneralPerformanceBenchmark", work.arguments(1));
            requireSuccess(expected);
            String checksum = expected.text().strip().split(",")[5];
            for (String variant : variants(work)) {
                Result actual = execute(executables.get("general").get(variant), work.arguments(1), "verify-" + variant);
                requireSuccess(actual);
                if (!actual.text().strip().split(",")[5].equals(checksum)) throw new IOException("JVM checksum mismatch: " + operation);
            }
        }
        if (executables.containsKey("general"))
            verification.add("Selected general operations: every implementation's small input checksums match the JVM.");
        for (String suite : List.of("identity")) {
            if (!executables.containsKey(suite)) continue;
            String main = "IdentityHashBenchmark";
            Result expected = jvm(main, List.of("verify"));
            requireSuccess(expected);
            for (var variant : executables.get(suite).entrySet()) {
                Result actual = execute(variant.getValue(), List.of("verify"), "verify-" + suite + "-" + variant.getKey());
                boolean same = actual.exitCode() == 0 && actual.error().isBlank() && actual.text().equals(expected.text());
                verification.add(suite + " " + variant.getKey() + ": " + (same ? "PASS" : "FAIL, exit=" + actual.exitCode()));
                if (!same)
                    throw new IOException("Verification mismatch: " + suite + " " + variant.getKey());
            }
        }
        for (Case work : selected) verifyExactCase(work);
        verification.add("Every measured size and round count has a JVM reference checksum before timing.");
        Files.write(output.resolve("verification.txt"), verification, StandardCharsets.UTF_8);
        verification.forEach(System.out::println);
    }

    private void verifyExactCase(Case work) throws Exception {
        String fixture = switch(work.suite()) {
            case "general" -> "GeneralPerformanceBenchmark";
            case "identity" -> "IdentityHashBenchmark";
            case "lookup" -> "LookupIsolation";
            case "map-probe" -> "MapProbeIsolation";
            case "allocation" -> "AllocationIsolation";
            case "map-growth" -> "MapGrowthIsolation";
            case "map-iteration" -> "MapIterationIsolation";
            case "nested-array" -> "NestedArrayIsolation";
            case "runtime-cost" -> "RuntimeCostIsolation";
            case "numeric" -> "NumericPipelineIsolation";
            case "transfer" -> "TransferIsolation";
            default -> throw new IllegalArgumentException("Unknown suite: " + work.suite());
        };
        Result expected = jvm(fixture, work.arguments(1));
        requireSuccess(expected);
        String checksum = parseOutput(expected.text(), work.operation(), work.size(), work.rounds(), 1).getFirst().checksum();
        String previous = expectedChecksums.putIfAbsent(work, checksum);
        if(previous != null && !previous.equals(checksum)) throw new IOException("Unstable JVM checksum: " + work);
    }

    static List<Reading> parseOutput(String text, String operation, int size, int rounds, int expectedCount) throws IOException {
        var readings = new ArrayList<Reading>();
        try {
            for(String line : text.lines().toList()) {
                String[] fields = line.split(",");
                if(fields.length != 6 || !fields[0].equals("BENCH") || !fields[1].equals(operation)
                        || Integer.parseInt(fields[2]) != size || Integer.parseInt(fields[3]) != rounds)
                    throw new IOException("Invalid benchmark output: " + line);
                long nanos = Long.parseLong(fields[4]);
                if(nanos <= 0) throw new IOException("Nonpositive timing");
                readings.add(new Reading(nanos, fields[5]));
            }
        } catch(NumberFormatException failure) {
            throw new IOException("Invalid numeric benchmark output", failure);
        }
        if(readings.size() != expectedCount) throw new IOException("Unexpected sample count: " + readings.size());
        return readings;
    }

    private List<Reading> checkedReadings(Result result, Case work, int count) throws IOException {
        requireSuccess(result);
        var readings = parseOutput(result.text(), work.operation(), work.size(), work.rounds(), count);
        for(Reading reading : readings) checkChecksum(work, reading.checksum());
        return readings;
    }

    private Case boundedInitialCase(Case work) {
        if(!calibration.enabled()) return work;
        int rounds = Math.min(work.rounds(), calibration.limit(work.suite(), work.operation(), work.size()).rounds());
        return new Case(work.suite(), work.operation(), work.size(), rounds);
    }

    static boolean supportsListCandidate(Case work, String variant) {
        return !List.of("CppStack", "CppSmall", "CppSmall32").contains(variant)
                || work.suite().equals("general") && work.operation().equals("list-grow");
    }

    private List<String> variants(Case work) {
        var variants = new ArrayList<>(executables.get(work.suite()).keySet());
        variants.removeIf(variant -> !supportsListCandidate(work, variant));
        if(work.suite().equals("general") && !generalCppSupports(work.operation())) variants.remove("Cpp");
        variants.removeIf(variant -> unavailable(work, variant));
        return variants;
    }

    private List<Case> calibrate(List<Case> selected) throws Exception {
        var resolved = new ArrayList<Case>();
        for(Case initial : selected) {
            Case work = initial;
            String reason = "attempt-limit";
            for(int attempt = 1; attempt <= calibration.maximumAttempts(); attempt++) {
                if(!expectedChecksums.containsKey(work)) verifyExactCase(work);
                double fastest = Double.POSITIVE_INFINITY, slowest = 0;
                var order = new ArrayList<>(variants(work));
                if(attempt % 2 == 0) Collections.reverse(order);
                for(String variant : order) {
                    Result result = execute(executables.get(work.suite()).get(variant), work.arguments(PILOT_SAMPLES),
                            "calibrate-" + work.suite() + "-" + work.operation() + "-" + work.size() + "-" + attempt + "-" + variant);
                    double elapsed = median(checkedReadings(result, work, PILOT_SAMPLES).stream()
                            .mapToDouble(Reading::nanos).toArray());
                    fastest = Math.min(fastest, elapsed);
                    slowest = Math.max(slowest, elapsed);
                    calibrationRows.append(csv(work.suite(), work.operation(), work.size(), attempt, work.rounds(), variant, elapsed));
                }
                Files.writeString(output.resolve("calibration.csv"), calibrationRows);
                var decision = calibration.next(work.rounds(), fastest, slowest,
                        calibration.limit(work.suite(), work.operation(), work.size()));
                if(!decision.repeat()) {
                    reason = decision.reason();
                    break;
                }
                if(attempt == calibration.maximumAttempts()) break;
                work = new Case(work.suite(), work.operation(), work.size(), decision.rounds());
            }
            resolved.add(work);
            calibrationReasons.put(work, reason);
            System.out.println("Calibrated " + work.key() + ": " + work.rounds() + " shared rounds (" + reason + ")");
        }
        verification.add("Calibration pilots and final round counts match JVM checksums; pilots are excluded from reported samples.");
        Files.write(output.resolve("verification.txt"), verification, StandardCharsets.UTF_8);
        return resolved;
    }

    private Result jvm(String main, List<String> arguments) throws Exception {
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-cp", classes.toString(), main));
        command.addAll(arguments);
        return process(command, "jvm-verify");
    }

    private Result execute(Path executable, List<String> arguments, String label) throws Exception {
        return execute(executable, arguments, label, false);
    }

    private Result execute(Path executable, List<String> arguments, String label, boolean profile) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows"))
            throw new IllegalStateException("This runner currently uses Windows processor affinity.");
        String path = executable.toString();
        if (path.matches(".*[&|<>^%\"!\r\n].*")) throw new IllegalArgumentException("Unsupported executable path");
        for (String arg : arguments)
            if (!arg.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid benchmark argument");
        String launch = "start \"\" /b /wait /affinity 1 \"" + path + "\" " + String.join(" ", arguments);
        return process(List.of("cmd.exe", "/d", "/s", "/c", launch), label, profile);
    }

    private Result process(List<String> command, String label) throws Exception {
        return process(command, label, false);
    }

    private Result process(List<String> command, String label, boolean profile) throws Exception {
        String prefix = String.format(Locale.ROOT, "%04d-%s", sequence++, label);
        Path stdout = output.resolve(prefix + ".log"), stderr = output.resolve(prefix + ".stderr.log");
        ProcessBuilder builder = new ProcessBuilder(command).directory(output.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        builder.environment().remove("JNATIVE_GC_INTERVAL");
        builder.environment().remove("JNATIVE_STATS");
        if (profile) builder.environment().put("JNATIVE_STATS", "1");
        Process process = builder.start();
        process.getOutputStream().close();
        try {
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new IOException("Benchmark timed out: " + prefix);
            return new Result(process.exitValue(), Files.readString(stdout).replace("\r\n", "\n"), Files.readString(stderr));
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void requireSuccess(Result result) throws IOException {
        if (result.exitCode() != 0 || !result.error().isBlank())
            throw new IOException("Benchmark process failed: " + result.exitCode() + " " + result.error());
    }

    private void measure(Case work) throws Exception {
        var variants = variants(work);
        for (int pair = 1; pair <= pairs; pair++) {
            var order = new ArrayList<>(variants);
            if (pair % 2 == 0) Collections.reverse(order);
            for (String variant : order) {
                Result result = execute(executables.get(work.suite()).get(variant), work.arguments(SAMPLES),
                        work.suite() + "-" + work.operation() + "-" + work.size() + "-" + pair + "-" + variant);
                for(Reading reading : checkedReadings(result, work, SAMPLES))
                    samples.add(new Sample(work, variant, pair, reading.nanos(), reading.checksum()));
            }
        }
        for (String variant : variants) {
            var medians = new ArrayList<Double>();
            for (int pair = 1; pair <= pairs; pair++) {
                int trial = pair;
                double[] values = samples.stream().filter(s -> s.work().equals(work) && s.variant().equals(variant) && s.pair() == trial)
                        .mapToDouble(Sample::nanos).toArray();
                medians.add(median(values));
            }
            double[] values = medians.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            summaries.add(new Summary(work, variant, median(values), values[0], values[values.length - 1]));
        }
        writeResults();
        System.out.println("Measured " + work.key() + "; checksums match; " + durationStatus(work));
    }

    private void checkChecksum(Case work, String checksum) throws IOException {
        String expected = expectedChecksums.get(work);
        if(expected == null) throw new IOException("No JVM checksum for exact work count: " + work);
        if(!expected.equals(checksum)) throw new IOException("Checksum mismatch: " + work);
    }

    private boolean generalCppSupports(String operation) {
        return operation.matches(config.getProperty("general.cppCases", String.join("|", CPP)));
    }

    // Failures are fatal by default. Explicitly documented unsupported inputs
    // can be excluded for a comparator while preserving the other raw timings.
    private boolean unavailable(Case work, String variant) {
        return List.of("GraalVM", "Cpp").contains(variant)
                && work.key().matches(config.getProperty("unavailable." + variant, ""));
    }

    private void writeResults() throws IOException {
        var raw = new StringBuilder("Suite,Operation,Size,Rounds,Variant,Pair,Nanoseconds,Checksum\n");
        for (Sample sample : samples) {
            Case work = sample.work();
            raw.append(csv(work.suite(), work.operation(), work.size(), work.rounds(), sample.variant(), sample.pair(), sample.nanos(), sample.checksum()));
        }
        Files.writeString(output.resolve("samples.csv"), raw);
        var table = new StringBuilder("Suite,Operation,Size,Rounds,Variant,MedianNs,MinimumNs,MaximumNs\n");
        for (Summary row : summaries) {
            Case work = row.work();
            table.append(csv(work.suite(), work.operation(), work.size(), work.rounds(), row.variant(), row.median(),
                    row.minimum(), row.maximum()));
        }
        Files.writeString(output.resolve("summary.csv"), table);
    }

    private Summary summary(Case work, String variant) {
        return summaries.stream().filter(s -> s.work().equals(work) && s.variant().equals(variant)).findFirst().orElseThrow();
    }

    private void writeComparisons() throws IOException {
        var timings = new StringBuilder("Suite,Operation,Size,Rounds,jNativeMs,PreviousJNativeMs,GraalVMMs,CppMs,CppStackMs,CppSmallMs,CppSmall32Ms,jNativeTimeChangePercent,TargetSampleMs,DurationStatus\n");
        for (Summary current : summaries) {
            if (!current.variant().equals("jNative")) continue;
            Case work = current.work();
            var columns = new ArrayList<Object>(List.of(work.suite(), work.operation(), work.size(), work.rounds(), current.median() / 1e6));
            for (String variant : List.of("Baseline", "GraalVM", "Cpp", "CppStack", "CppSmall", "CppSmall32")) {
                Summary compared = summaries.stream().filter(s -> s.work().equals(work) && s.variant().equals(variant)).findFirst().orElse(null);
                columns.add(compared == null ? "" : compared.median() / 1e6);
            }
            boolean hasBaseline = summaries.stream().anyMatch(s -> s.work().equals(work) && s.variant().equals("Baseline"));
            columns.add(hasBaseline ? 100 * (current.median() / summary(work, "Baseline").median() - 1) : "");
            columns.add(calibration.targetNanos() / 1e6);
            columns.add(durationStatus(work));
            timings.append(csv(columns.toArray()));
        }
        Files.writeString(output.resolve("timings.csv"), timings);
    }

    private String durationStatus(Case work) {
        var observed = summaries.stream().filter(row -> row.work().equals(work)).toList();
        boolean shortSample = observed.stream().anyMatch(row -> row.minimum() < calibration.targetNanos());
        boolean longSample = observed.stream().anyMatch(row -> row.maximum() > calibration.maximumNanos());
        String status = (shortSample ? "below-target" : "target-met") + (longSample ? ";above-budget" : "");
        return status + " (" + calibrationReasons.getOrDefault(work, "fixed-rounds") + ")";
    }

    // Separate diagnostic processes after timing: statistics cover three warmups
    // plus one measured iteration and are never mixed with benchmark timing.
    private void profileGc() throws Exception {
        var fields = List.of("allocations", "live_objects", "peak_objects", "collections", "gc_total_ns",
                "gc_p50_ns", "gc_p99_ns", "gc_max_ns", "pooled_bytes", "allocated_bytes", "managed_bytes", "peak_managed_bytes");
        var optionalFields = List.of("pooled_bytes", "allocated_bytes", "managed_bytes", "peak_managed_bytes");
        var rows = new StringBuilder("Operation,Size,Rounds,Variant,Pair," + String.join(",", fields) + "\n");
        var operations = List.of("reference-array", "typed-array", "list-grow", "map-grow");
        // Lookup timing excludes setup; these diagnostics also count map construction.
        var cases = summaries.stream().filter(s -> s.variant().equals("jNative")
                && (s.work().suite().equals("runtime-cost") || s.work().suite().equals("allocation") || s.work().suite().equals("identity") || s.work().suite().equals("lookup") || s.work().suite().equals("map-probe") || s.work().suite().equals("map-growth") || s.work().suite().equals("map-iteration")
                    || s.work().suite().equals("general") && operations.contains(s.work().operation())))
                .map(Summary::work)
                .filter(work -> work.key().matches(config.getProperty("profileGcCases", ".*"))).toList();
        for (Case work : cases) {
            for (int pair = 1; pair <= pairs; ++pair) {
                var variants = new ArrayList<>(List.of("jNative", "Baseline"));
                if (pair % 2 == 0) Collections.reverse(variants);
                for (String variant : variants) {
                    Path binary = executables.get(work.suite()).get(variant);
                    if (binary == null) continue;
                    Result result = execute(binary, work.arguments(1),
                            "gc-" + work.operation() + "-" + work.size() + "-" + pair + "-" + variant, true);
                    String statistics = result.error().strip();
                    if (result.exitCode() != 0 || !statistics.startsWith("JNATIVE_STATS ") || statistics.contains("\n"))
                        throw new IOException("Invalid GC diagnostic output: " + statistics);
                    String[] sample = result.text().strip().split(",");
                    if (sample.length != 6 || !sample[0].equals("BENCH"))
                        throw new IOException("Invalid diagnostic sample: " + result.text());
                    checkChecksum(work, sample[5]);
                    var values = new LinkedHashMap<String, String>();
                    for (String item : statistics.substring("JNATIVE_STATS ".length()).split(" ")) {
                        String[] entry = item.split("=");
                        if (entry.length != 2 || Long.parseLong(entry[1]) < 0) throw new IOException("Invalid statistic: " + item);
                        values.put(entry[0], entry[1]);
                    }
                    var columns = new ArrayList<Object>(List.of(work.operation(), work.size(), work.rounds(), variant, pair));
                    for (String field : fields) {
                        if (!optionalFields.contains(field) && !values.containsKey(field))
                            throw new IOException("Missing statistic: " + field);
                        columns.add(values.getOrDefault(field, ""));
                    }
                    rows.append(csv(columns.toArray()));
                }
            }
        }
        Files.writeString(output.resolve("gc.csv"), rows);
    }

    private static double median(double[] input) {
        double[] values = input.clone();
        Arrays.sort(values);
        int middle = values.length / 2;
        return values.length % 2 == 0 ? (values[middle - 1] + values[middle]) / 2 : values[middle];
    }
    private static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
    private static String csv(Object... values) {
        return String.join(",", Arrays.stream(values).map(value -> "\"" + value.toString().replace("\"", "\"\"") + "\"").toList()) + "\n";
    }
}
