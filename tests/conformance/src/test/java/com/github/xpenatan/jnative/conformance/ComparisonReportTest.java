package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ComparisonReportTest {
    @TempDir
    Path temporary;

    @Test
    void everySelectedCaseIsVisibleIncludingFailedSuitesAndWarnings() throws Exception {
        Path root = completedRun();
        ComparisonReport.write(root);
        var rows = ComparisonReport.readCsv(Files.readString(root.resolve("all-cases.csv")));
        assertEquals(45, rows.size());
        assertEquals(45, rows.stream().map(row -> row.get("CaseId")).distinct().count());
        assertEquals("general:reference-array:16", rows.getFirst().get("CaseId"));
        assertTrue(rows.stream().allMatch(row -> row.get("LookupCount").isEmpty()));
        assertEquals(27, rows.stream().filter(row -> row.get("SuiteStatus").equals("PASS")).count());
        assertEquals(18, rows.stream().filter(row -> row.get("SuiteStatus").equals("FAIL")).count());
        assertEquals(3, rows.stream().filter(row -> row.get("DurationStatus").startsWith("below-target")).count());
        assertTrue(rows.stream().filter(row -> row.get("SuiteStatus").equals("FAIL"))
                .allMatch(row -> row.get("jNativeMs").isEmpty() && row.get("Rounds").isEmpty()));
        String html = Files.readString(root.resolve("all-cases.html"));
        assertEquals(45, html.split("<tr data-warning=", -1).length - 1);
        assertTrue(html.contains("45 selected cases · 27 verified · 24 duration-qualified · 21 warnings"));
        assertTrue(html.contains("run&amp;name"));
        assertTrue(html.contains("data-failed=\"true\""));
        assertTrue(html.contains("id=\"search\""));
        assertTrue(html.contains("C++ Small32 ms"));
    }

    @Test
    void stringLookupCostsUseMeasuredRoundsForEachSize() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("string-lookups"));
        Files.writeString(root.resolve("metadata.txt"), "Suites=1\nCases=2\nCasesInCompletedSuites=2\n");
        Files.writeString(root.resolve("suite-status.csv"), """
                Suite,Cases,Status,ElapsedMillis,TimingsFile,ErrorFile
                map-probe,2,PASS,1000,00/measurements/timings.csv,
                """);
        Path suite = Files.createDirectories(root.resolve("00/measurements"));
        Files.writeString(root.resolve("00/benchmark.properties"), "cases=map-probe:get-string:(256|4096)\n");
        Files.writeString(suite.resolve("timings.csv"), """
                Suite,Operation,Size,Rounds,jNativeMs,GraalVMMs,CppMs,DurationStatus
                map-probe,get-string,256,232480,169.8869,153.8575,113.1731,target-met (target-reached)
                map-probe,get-string,4096,7808,119.9453,97.0844,115.6289,below-target (target-reached)
                """);

        ComparisonReport.write(root);

        var rows = ComparisonReport.readCsv(Files.readString(root.resolve("all-cases.csv")));
        assertEquals("map-probe:get-string:256", rows.getFirst().get("CaseId"));
        assertEquals("59514880", rows.getFirst().get("LookupCount"));
        assertEquals("2.8545", rows.getFirst().get("jNativeNsPerLookup"));
        assertEquals("map-probe:get-string:4096", rows.getLast().get("CaseId"));
        assertEquals("31981568", rows.getLast().get("LookupCount"));
        assertEquals("3.7505", rows.getLast().get("jNativeNsPerLookup"));
        assertEquals("3.0356", rows.getLast().get("GraalVMNsPerLookup"));
        assertEquals("3.6155", rows.getLast().get("CppNsPerLookup"));
    }

    @Test
    void completedMetadataMustAccountForEveryReportedSuiteAndCase() throws Exception {
        Path root = completedRun();
        Path metadata = root.resolve("metadata.txt");
        Files.writeString(metadata, Files.readString(metadata).replace("Cases=45", "Cases=46"));
        var error = assertThrows(IOException.class, () -> ComparisonReport.write(root));
        assertTrue(error.getMessage().contains("totals differ"));
    }

    @Test
    void incompleteSuccessfulSuiteCannotBecomeAnApparentlyCompleteReport() throws Exception {
        Path root = completedRun();
        Path source = root.resolve("00/measurements/timings.csv");
        var lines = new ArrayList<>(Files.readAllLines(source));
        lines.removeLast();
        Files.write(source, lines);
        var error = assertThrows(IOException.class, () -> ComparisonReport.write(root));
        assertTrue(error.getMessage().contains("Incomplete timings"));
        assertFalse(Files.exists(root.resolve("all-cases.html")));
    }

    @Test
    void duplicateCasesCannotHideAnUnmeasuredCase() throws Exception {
        Path root = completedRun();
        Path source = root.resolve("00/measurements/timings.csv");
        var lines = new ArrayList<>(Files.readAllLines(source));
        lines.set(1, lines.get(2));
        Files.write(source, lines);
        var error = assertThrows(IOException.class, () -> ComparisonReport.write(root));
        assertTrue(error.getMessage().contains("Duplicate timing case"));
    }

    @Test
    void invalidNumericTimingsCannotBePresentedAsBenchmarkResults() throws Exception {
        Path root = completedRun();
        Path source = root.resolve("00/measurements/timings.csv");
        Files.writeString(source, Files.readString(source).replace("10.25", "NaN"));
        var error = assertThrows(IOException.class, () -> ComparisonReport.write(root));
        assertTrue(error.getMessage().contains("Invalid timing data"));
    }

    @Test
    void anUnfinishedRunCannotProduceACompleteReport() throws Exception {
        Path root = Files.createDirectory(temporary.resolve("unfinished"));
        assertThrows(IOException.class, () -> ComparisonReport.write(root));
        assertFalse(Files.exists(root.resolve("all-cases.csv")));
    }

    @Test
    void csvPreservesQuotedCommasQuotesNewlinesAndEmptyCells() throws Exception {
        var rows = ComparisonReport.readCsv("Name,Note,Empty\r\n\"a,b\",\"say \"\"yes\"\"\nnext\",\"\"\r\n");
        assertEquals(1, rows.size());
        assertEquals("a,b", rows.getFirst().get("Name"));
        assertEquals("say \"yes\"\nnext", rows.getFirst().get("Note"));
        assertEquals("", rows.getFirst().get("Empty"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"A,A\n1,2\n", "A,B\n1\n", "A\n\"unclosed", "A\n\"closed\"text"})
    void malformedCsvFailsExplicitly(String source) {
        assertThrows(IOException.class, () -> ComparisonReport.readCsv(source));
    }

    private Path completedRun() throws IOException {
        Path root = Files.createDirectory(temporary.resolve("run&name"));
        Files.writeString(root.resolve("metadata.txt"), "Suites=2\nCases=45\nCasesInCompletedSuites=27\nFailedSuites=lookup\n");
        Files.writeString(root.resolve("suite-status.csv"), """
                Suite,Cases,Status,ElapsedMillis,TimingsFile,ErrorFile
                general,27,PASS,1000,00/measurements/timings.csv,
                lookup,18,FAIL,100,,01-lookup-failure.log
                """);
        Path suite = Files.createDirectories(root.resolve("00/measurements"));
        Files.writeString(root.resolve("00/benchmark.properties"), "cases=general:.*\n");
        var selection = new Properties();
        selection.setProperty("cases", "general:.*");
        StringBuilder timings = new StringBuilder("Suite,Operation,Size,Rounds,jNativeMs,GraalVMMs,CppMs,DurationStatus\n");
        for(var work : BenchmarkScores.cases(selection)) {
            timings.append("general,").append(work.operation()).append(',').append(work.size()).append(',')
                    .append(work.rounds()).append(",10.25,11.5,12.75,")
                    .append(work.operation().equals("hash-cold") ? "below-target (setup-memory-limit)" : "target-met (target-reached)")
                    .append('\n');
        }
        Files.writeString(suite.resolve("timings.csv"), timings);
        return root;
    }
}
