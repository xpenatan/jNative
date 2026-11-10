package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.CrashReportMode;
import com.github.xpenatan.jnative.NativeBuilder;
import com.github.xpenatan.jnative.NativeSymbols;
import com.github.xpenatan.jnative.StackTraceMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in provenance check against an existing full comparison's compiled inputs. */
class FullMatrixSourceStabilityTest {
    @TempDir
    Path temporary;

    @Test
    void identifyChangedCompilationInputsWithoutRemeasuringUnchangedSuites() throws Exception {
        String baseline = System.getProperty("jnative.comparison.sourceBaseline", "");
        assumeTrue(!baseline.isBlank(), "Opt-in full-matrix source baseline");
        Path root = Path.of(baseline);
        var suites = ProjectComparison.selectedSuites("all");
        var affected = new ArrayList<String>();
        for(int index = 0; index < suites.size(); index++) {
            String suite = suites.get(index);
            Path oldRoot = root.resolve(String.format("%02d", index));
            var configuration = new Properties();
            try(var reader = Files.newBufferedReader(oldRoot.resolve("benchmark.properties"))) {
                configuration.load(reader);
            }
            String reference = configuration.stringPropertyNames().stream()
                    .filter(key -> key.startsWith("reference.")).findFirst().orElseThrow();
            Path source;
            try(var files = Files.list(oldRoot.resolve("j/b"))) {
                source = files.filter(file -> file.toString().endsWith(".java")).findFirst().orElseThrow();
            }
            String main = source.getFileName().toString().replace(".java", "");
            var generation = NativeBuilder.create().classpath(Path.of(configuration.getProperty(reference)))
                    .mainClass(main).buildRoot(temporary.resolve("s" + index)).buildType(BuildType.RELEASE)
                    .generator(configuration.getProperty("generator")).cmakeDefine("JNATIVE_IPO", "ON")
                    .nativeSymbols(NativeSymbols.NONE).stackTraces(StackTraceMode.NONE)
                    .crashReports(CrashReportMode.OFF).generate();
            Path oldProject = oldRoot.resolve("j/b/jnative/native");
            Path newProject = generation.request().buildRoot().resolve("native");
            var changed = new ArrayList<String>();
            var inputs = new TreeSet<Path>();
            for(String directory : List.of("src", "runtime")) {
                for(Path project : List.of(oldProject, newProject)) {
                    try(var files = Files.walk(project.resolve(directory))) {
                        for(Path file : files.filter(Files::isRegularFile).toList()) {
                            String name = file.getFileName().toString();
                            if(List.of(".c", ".cc", ".cpp", ".cxx", ".h", ".hh", ".hpp", ".hxx", ".inc", ".inl", ".s", ".asm")
                                    .stream().anyMatch(name::endsWith)) inputs.add(project.relativize(file));
                        }
                    }
                }
            }
            for(Path relative : inputs) {
                Path previous = oldProject.resolve(relative), current = newProject.resolve(relative);
                if(!Files.isRegularFile(previous) || !Files.isRegularFile(current)
                        || !Files.readString(previous).equals(Files.readString(current)))
                    changed.add(relative.toString().replace('\\', '/'));
            }
            System.out.println("Compilation input delta " + suite + ": " + changed);
            if(!changed.isEmpty()) affected.add(suite);
        }
        assertFalse(affected.isEmpty(), "The failing nested-array source must change");
        assertEquals(List.of("nested-array"), affected,
                "Any additional affected suite needs its own rerun before reusing baseline timings");
    }
}
