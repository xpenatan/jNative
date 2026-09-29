package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.CrashReportMode;
import com.github.xpenatan.jnative.NativeSymbols;
import com.github.xpenatan.jnative.StackTraceMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SpriteOptimizationKernelsTest {
    @TempDir
    Path temporary;

    private Path compileFixture(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path source = directory.resolve("SpriteOptimizationKernels.java");
        try (var input = getClass().getResourceAsStream("/fixtures/SpriteOptimizationKernels.java")) {
            assertNotNull(input);
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = directory.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        return classes;
    }

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void verificationMatchesJvmUnderCollection(BuildType buildType) throws Exception {
        Path classes = compileFixture(temporary);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "SpriteOptimizationKernels", "verify"),
                Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder()
                .classpath(classes)
                .mainClass("SpriteOptimizationKernels")
                .buildRoot(temporary.resolve("out"))
                .buildType(buildType)
                .cmakeBuildArgs("--parallel", "2")
                .timeout(Duration.ofMinutes(5))
                .build();
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(result.executable().toString(), "verify"), Duration.ofSeconds(90),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    @Test
    void buildBenchmark() throws Exception {
        String label = System.getenv("JNATIVE_SPRITE_BENCHMARK");
        assumeTrue(label != null && !label.isBlank(), "Opt-in retained benchmark build");
        assertTrue(label.matches("[A-Za-z0-9_-]+"), "Benchmark label must be a directory name");
        Path directory = Path.of(System.getProperty("jnative.root"))
                .resolve("tests/conformance/build/sprite-optimization-benchmark").resolve(label);
        Path classes = compileFixture(directory);
        var result = ProcessHarness.behavioralBuilder()
                .classpath(classes)
                .mainClass("SpriteOptimizationKernels")
                .buildRoot(directory.resolve("out"))
                .buildType(BuildType.RELEASE)
                .nativeSymbols(NativeSymbols.NONE)
                .stackTraces(StackTraceMode.NONE)
                .crashReports(CrashReportMode.OFF)
                .cmakeDefine("JNATIVE_IPO", "ON")
                .cmakeBuildArgs("--parallel", "2")
                .timeout(Duration.ofMinutes(5))
                .build();
        Files.writeString(directory.resolve("executable.txt"), result.executable() + "\n");
        Files.writeString(directory.resolve("manifest.txt"),
                "mainClass=SpriteOptimizationKernels\nclasses=" + classes + "\nexecutable=" + result.executable()
                + "\nbuildType=RELEASE\nnativeSymbols=NONE\nstackTraces=NONE\ncrashReports=OFF"
                + "\nJNATIVE_IPO=ON\ncmakeParallel=2\nfixture=current-resource\n");
        System.out.println("JNATIVE_SPRITE_BENCHMARK_EXECUTABLE=" + result.executable());
    }
}
