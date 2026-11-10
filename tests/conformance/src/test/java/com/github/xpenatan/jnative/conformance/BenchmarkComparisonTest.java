package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BenchmarkComparisonTest {
    @TempDir
    Path temporary;

    @Test
    void fullSelectionContainsAllTenOwnedSuitesAnd166Cases() {
        var suites = ProjectComparison.selectedSuites("all");
        assertEquals(10, suites.size());
        assertEquals(10, suites.stream().distinct().count());
        int cases = 0;
        int operations = 0;
        for(String suite : suites) {
            var selection = new Properties();
            selection.setProperty("cases", suite + ":.*");
            var workloads = BenchmarkScores.cases(selection);
            assertFalse(workloads.isEmpty(), suite);
            cases += workloads.size();
            operations += (int)workloads.stream().map(BenchmarkScores.Case::operation).distinct().count();
            assertTrue(workloads.stream().allMatch(work -> work.suite().equals(suite)));
        }
        assertEquals(59, operations);
        assertEquals(166, cases);
        assertEquals(1, ProjectComparison.selectedSuites("general").size());
    }

    @Test
    void optionalNativeListCandidatesCannotParticipateInOtherWorkloads() {
        var growingList = new BenchmarkScores.Case("general", "list-grow", 256, 1000);
        var arithmetic = new BenchmarkScores.Case("general", "arithmetic", 256, 1000);
        var map = new BenchmarkScores.Case("map-growth", "preboxed-grow", 256, 1000);
        for(String candidate : new String[]{"CppStack", "CppSmall", "CppSmall32"}) {
            assertTrue(BenchmarkScores.supportsListCandidate(growingList, candidate));
            assertFalse(BenchmarkScores.supportsListCandidate(arithmetic, candidate));
            assertFalse(BenchmarkScores.supportsListCandidate(map, candidate));
        }
        assertTrue(BenchmarkScores.supportsListCandidate(arithmetic, "Cpp"));
        assertTrue(BenchmarkScores.supportsListCandidate(map, "GraalVM"));
    }

    @Test
    void alteredReferenceBytecodeStopsBeforeNativeExecutionOrTiming() throws Exception {
        Path reference = compileReference(true);
        Path configuration = configuration(reference);
        var error = assertThrows(IOException.class,
                () -> BenchmarkScores.main(new String[]{configuration.toString(), temporary.resolve("results").toString()}));
        assertTrue(error.getMessage().contains("Fixture differs from competitor build input"), error.getMessage());
        assertFalse(Files.exists(temporary.resolve("results/validation/timings.csv")));
    }

    @Test
    void obsoleteComparatorDoesNotReplaceRequiredNativeImageAndDefaultUsesOnlyGeneralFixture() throws Exception {
        Path reference = compileReference(false);
        Path configuration = configuration(reference);
        var error = assertThrows(IllegalArgumentException.class,
                () -> BenchmarkScores.main(new String[]{configuration.toString(), temporary.resolve("results").toString()}));
        assertTrue(error.getMessage().contains("Missing comparison implementations for general"), error.getMessage());
        assertTrue(Files.isRegularFile(temporary.resolve("results/validation/classes/GeneralPerformanceBenchmark.class")));
        assertFalse(Files.exists(temporary.resolve("results/validation/classes/SpriteOptimizationKernels.class")));
        assertFalse(Files.exists(temporary.resolve("results/validation/classes/IdentityHashBenchmark.class")));
        assertFalse(Files.exists(temporary.resolve("results/validation/timings.csv")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sprite", "libfdx", "libgdx", "teavm"})
    void applicationAndExcludedComparatorNamesCannotSelectAProjectSuite(String suite) {
        Path output = temporary.resolve("comparison");
        var error = assertThrows(IllegalArgumentException.class,
                () -> ProjectComparison.main(new String[]{"unused-tools", output.toString(), "validation",
                        suite, "unused-cpp", ".*", "3", ""}));
        assertTrue(error.getMessage().contains("Unknown project comparison suite"), error.getMessage());
        assertFalse(Files.exists(output));
    }

    private Path compileReference(boolean altered) throws Exception {
        String source;
        try(var input = getClass().getResourceAsStream("/fixtures/GeneralPerformanceBenchmark.java")) {
            if(input == null) throw new IOException("Missing standalone fixture");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        if(altered) source = source.replace("return value + 17;", "return value + 18;");
        Path file = temporary.resolve("GeneralPerformanceBenchmark.java");
        Files.writeString(file, source);
        Path classes = temporary.resolve("reference");
        ProcessHarness.javac(file, classes, 17);
        return classes;
    }

    private Path configuration(Path reference) throws IOException {
        Path placeholder = Files.writeString(temporary.resolve("unexecuted.exe"), "Not an executable");
        var config = new Properties();
        config.setProperty("runName", "validation");
        config.setProperty("reference.classes", reference.toString());
        config.setProperty("general.jNative", placeholder.toString());
        config.setProperty("general.Cpp", placeholder.toString());
        config.setProperty("general.TeaVM", placeholder.toString());
        Path path = temporary.resolve("comparison.properties");
        try(var writer = Files.newBufferedWriter(path)) {
            config.store(writer, "No native process may execute in these failure-path tests");
        }
        return path;
    }
}
