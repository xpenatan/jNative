package com.github.xpenatan.jnative.conformance;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/** One repository-owned build, verification and timing workflow. */
public final class ProjectComparison {
    private static final List<String> ALL_SUITES = List.of("general", "lookup", "map-probe", "allocation",
            "map-growth", "map-iteration", "nested-array", "runtime-cost", "numeric", "transfer");
    private static final Map<String, String> CPP_SOURCES = Map.of(
            "general", "general_performance.cpp",
            "lookup", "lookup_isolation.cpp",
            "map-probe", "map_probe_isolation.cpp",
            "allocation", "allocation_isolation.cpp",
            "map-growth", "map_growth_isolation.cpp",
            "map-iteration", "map_iteration_isolation.cpp",
            "nested-array", "nested_array_isolation.cpp",
            "runtime-cost", "runtime_cost_isolation.cpp",
            "numeric", "numeric_pipeline_isolation.cpp",
            "transfer", "transfer_isolation.cpp");

    private ProjectComparison() {
    }

    public static void main(String[] args) throws Exception {
        if(args.length == 1 && args[0].equals("--list")) {
            listCases();
            return;
        }
        if(args.length != 8 || args[0].isBlank() || !args[2].matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException(
                    "Use compareImplementations -PcomparisonTools=<properties> -PcomparisonRun=<new-name>");
        String suite = args[3];
        selectedSuites(suite);
        if(!System.getProperty("os.name").startsWith("Windows"))
            throw new IllegalArgumentException("The comparison runner currently requires Windows/MSVC");
        if(suite.equals("all")) {
            runAll(args);
            return;
        }
        String sourceName = CPP_SOURCES.get(suite);
        if(sourceName == null) throw new IllegalArgumentException("Unknown project comparison suite: " + suite);
        Pattern.compile(args[5]);
        int processes = Integer.parseInt(args[6]);
        if(processes < 1 || processes > 32)
            throw new IllegalArgumentException("comparisonProcesses must be between 1 and 32");
        var calibrationOptions = new Properties();
        calibrationOptions.setProperty("calibration.enabled", System.getProperty("jnative.comparison.calibrate", "true"));
        calibrationOptions.setProperty("calibration.targetMillis", System.getProperty("jnative.comparison.targetMillis", "100"));
        calibrationOptions.setProperty("calibration.maxSampleMillis", System.getProperty("jnative.comparison.maxSampleMillis", "2000"));
        calibrationOptions.setProperty("calibration.maxSetupMiB", System.getProperty("jnative.comparison.maxSetupMiB", "64"));
        BenchmarkCalibration.from(calibrationOptions);
        Path cppSource = Path.of(args[4]).resolve(sourceName).toAbsolutePath().normalize();
        if(!Files.isRegularFile(cppSource)) throw new IllegalArgumentException("Missing C++ workload: " + cppSource);
        Path baseline = args[7].isBlank() ? null : Path.of(args[7]).toAbsolutePath().normalize();
        if(baseline != null && !Files.isRegularFile(baseline))
            throw new IllegalArgumentException("Missing prior jNative executable: " + baseline);
        Path toolsFile = Path.of(args[0]).toAbsolutePath().normalize();
        var tools = new Properties();
        try(var reader = Files.newBufferedReader(toolsFile)) {
            tools.load(reader);
        }
        if(tools.containsKey("priorRuntime"))
            throw new IllegalArgumentException("Use comparisonBaseline for a prior build; omit priorRuntime");
        for(String key : new String[]{"vcvars", "nativeImage"}) {
            String value = tools.getProperty(key, "");
            if(value.isBlank() || !Files.isRegularFile(Path.of(value)))
                throw new IllegalArgumentException("Missing installed tool: " + key);
        }
        Instant started = Instant.now();
        Path root = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path output = Files.createDirectory(root.resolve(args[2]));
        System.out.println("Building project-owned " + suite + " comparison: " + output);
        BuildLookupComparisons.main(new String[]{toolsFile.toString(), output.resolve("j").toString(), "b", suite});
        Path configuration = output.resolve("j/b/benchmark.properties");
        var config = new Properties();
        try(var reader = Files.newBufferedReader(configuration)) {
            config.load(reader);
        }
        String generator = config.getProperty("generator");
        BuildMapCppComparison.main(new String[]{cppSource.toString(), output.resolve("cpp").toString(), generator, "build", suite});
        String target = suite.equals("general") ? "general_cpp" : suite.replace('-', '_') + "_cpp";
        Path cppExecutable = output.resolve("cpp/build/native/Release/" + target + ".exe");
        if(!Files.isRegularFile(cppExecutable)) throw new IllegalStateException("Missing C++ executable: " + cppExecutable);
        config.setProperty(suite + ".Cpp", cppExecutable.toString());
        if(suite.equals("general") && Boolean.getBoolean("jnative.comparison.cppTuning")) {
            config.setProperty("general.CppStack", output.resolve("cpp/build/native/Release/general_stack_cpp.exe").toString());
            config.setProperty("general.CppSmall", output.resolve("cpp/build/native/Release/general_small_cpp.exe").toString());
            config.setProperty("general.CppSmall32", output.resolve("cpp/build/native/Release/general_small32_cpp.exe").toString());
        }
        config.setProperty("general.cppCases", ".*");
        config.setProperty("cases", suite + ":(?:" + args[5] + ")");
        config.setProperty("pairs", Integer.toString(processes));
        config.setProperty("profileGc", "false");
        config.setProperty("runName", "measurements");
        config.putAll(calibrationOptions);
        if(baseline != null) config.setProperty(suite + ".Baseline", baseline.toString());
        Path combined = output.resolve("benchmark.properties");
        try(var writer = Files.newBufferedWriter(combined)) {
            config.store(writer, "Project-owned jNative / GraalVM Native Image / C++ comparison");
        }
        System.out.println("All builds finished. Verifying JVM checksums before timing.");
        BenchmarkScores.main(new String[]{combined.toString(), output.toString()});
        System.out.println("Total build, verification and measurement time: "
                + Duration.between(started, Instant.now()).toMillis() + " ms");
    }

    static List<String> selectedSuites(String selection) {
        if(selection.equals("all")) return ALL_SUITES;
        if(!CPP_SOURCES.containsKey(selection))
            throw new IllegalArgumentException("Unknown project comparison suite: " + selection);
        return List.of(selection);
    }

    private static List<BenchmarkScores.Case> cases(String suite, String filter) {
        var selection = new Properties();
        selection.setProperty("cases", suite + ":(?:" + filter + ")");
        return BenchmarkScores.cases(selection);
    }

    private static void listCases() {
        System.out.println("Suite,Operation,Size,DefaultRounds,CppSource");
        int count = 0;
        for(String suite : ALL_SUITES) {
            for(var work : cases(suite, ".*")) {
                System.out.println(suite + "," + work.operation() + "," + work.size() + "," + work.rounds()
                        + ",src/test/cpp/" + CPP_SOURCES.get(suite));
                count++;
            }
        }
        System.out.println("Total: " + ALL_SUITES.size() + " suites, " + count + " cases; calibrated rounds may differ.");
    }

    private static void runAll(String[] args) throws Exception {
        Pattern.compile(args[5]);
        var selected = ALL_SUITES.stream().filter(suite -> !cases(suite, args[5]).isEmpty()).toList();
        if(selected.isEmpty()) throw new IllegalArgumentException("The case filter selected no workloads");
        if(!args[7].isBlank() && selected.size() > 1)
            throw new IllegalArgumentException("comparisonBaseline requires a single suite; select comparisonSuite explicitly");
        Path parent = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(parent);
        Path output = Files.createDirectory(parent.resolve(args[2]));
        var status = new StringBuilder("Suite,Cases,Status,ElapsedMillis,TimingsFile,ErrorFile\n");
        var failures = new ArrayList<String>();
        int totalCases = selected.stream().mapToInt(suite -> cases(suite, args[5]).size()).sum();
        int completedCases = 0;
        Instant started = Instant.now();
        System.out.println("Full comparison: " + selected.size() + " suites, " + totalCases + " cases. "
                + "Native builds and measured processes run sequentially.");
        for(int index = 0; index < selected.size(); index++) {
            String suite = selected.get(index);
            String directory = String.format("%02d", index);
            int count = cases(suite, args[5]).size();
            String[] single = args.clone();
            single[1] = output.toString();
            single[2] = directory;
            single[3] = suite;
            Instant suiteStarted = Instant.now();
            System.out.println("Starting suite " + (index + 1) + "/" + selected.size() + ": " + suite + " (" + count + " cases)");
            try {
                main(single);
                completedCases += count;
                status.append(suite).append(',').append(count).append(",PASS,")
                        .append(Duration.between(suiteStarted, Instant.now()).toMillis()).append(',')
                        .append(directory).append("/measurements/timings.csv,\n");
            } catch(Exception failure) {
                String errorName = directory + "-" + suite + "-failure.log";
                var error = new StringWriter();
                failure.printStackTrace(new PrintWriter(error));
                Files.writeString(output.resolve(errorName), error.toString());
                status.append(suite).append(',').append(count).append(",FAIL,")
                        .append(Duration.between(suiteStarted, Instant.now()).toMillis()).append(",,")
                        .append(errorName).append('\n');
                failures.add(suite);
                System.out.println("Suite failed: " + suite + "; " + failure.getMessage());
            }
            Files.writeString(output.resolve("suite-status.csv"), status);
            System.out.println("Coverage so far: " + completedCases + "/" + totalCases + " cases in completed suites; "
                    + failures.size() + " failed suites.");
        }
        long elapsed = Duration.between(started, Instant.now()).toMillis();
        Files.writeString(output.resolve("metadata.txt"), "Suites=" + selected.size() + "\nCases=" + totalCases
                + "\nCasesInCompletedSuites=" + completedCases + "\nFailedSuites=" + String.join(",", failures)
                + "\nTotalElapsedMillis=" + elapsed + "\n");
        System.out.println("Full comparison completed in " + elapsed + " ms: " + output.resolve("suite-status.csv"));
        ComparisonReport.write(output);
        if(!failures.isEmpty())
            throw new IOException("Comparison incomplete; failed suites: " + failures + ". Successful results remain in " + output);
    }
}
