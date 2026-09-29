package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.compiler.PlatformBindings;
import com.github.xpenatan.jnative.compiler.Program;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class NativeArrayKernelsTest {
    @TempDir
    Path temporary;

    private Path compileFixture(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path source = directory.resolve("NativeArrayKernels.java");
        try(var input = getClass().getResourceAsStream("/fixtures/NativeArrayKernels.java")) {
            assertNotNull(input);
            Files.copy(input, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = directory.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        return classes;
    }

    @Test
    void buildBenchmark() throws Exception {
        String label = System.getenv("JNATIVE_ARRAY_BENCHMARK");
        assumeTrue(label != null && !label.isBlank(), "Opt-in retained benchmark build");
        assertTrue(label.matches("[A-Za-z0-9_-]+"), "Benchmark label must be a directory name");
        Path benchmarkRoot = Path.of(System.getProperty("jnative.root"))
                .resolve("tests/conformance/build/native-array-benchmark");
        Path directory = benchmarkRoot.resolve(label);
        Path baselineSource = benchmarkRoot.resolve("baseline/NativeArrayKernels.java");
        Path classes;
        if (!label.equals("baseline") && Files.exists(baselineSource)) {
            Files.createDirectories(directory);
            Path source = directory.resolve("NativeArrayKernels.java");
            Files.copy(baselineSource, source, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            classes = directory.resolve("classes");
            ProcessHarness.javac(source, classes, 25);
        } else {
            classes = compileFixture(directory);
        }
        var result = ProcessHarness.behavioralBuilder()
                .classpath(classes)
                .mainClass("NativeArrayKernels")
                .buildRoot(directory.resolve("out"))
                .buildType(BuildType.RELEASE)
                .timeout(Duration.ofMinutes(5))
                .build();
        Files.writeString(directory.resolve("executable.txt"), result.executable().toString() + "\n");
        System.out.println("JNATIVE_ARRAY_BENCHMARK_EXECUTABLE=" + result.executable());
    }

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void kernelsAndCollectionsMatchJvmUnderCollection(BuildType buildType) throws Exception {
        Path classes = compileFixture(temporary);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "NativeArrayKernels"),
                Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder()
                .classpath(classes)
                .mainClass("NativeArrayKernels")
                .buildRoot(temporary.resolve("out"))
                .buildType(buildType)
                .timeout(Duration.ofMinutes(5));
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String fixture = Files.readString(generated.resolve("classes/NativeArrayKernels.cpp"))
                + Files.readString(generated.resolve("classes/java.util.Arrays.cpp"));
        for (String helper : List.of("arrays_fill_z", "arrays_fill_b", "arrays_fill_c",
                "arrays_fill_s", "arrays_fill_i", "arrays_fill_j", "arrays_fill_f",
                "arrays_fill_d", "arrays_fill_reference", "arrays_equals_i", "arrays_hash_i")) {
            assertTrue(fixture.contains("::jnative::" + helper + "("), helper);
        }
        String arrayList = Files.readString(generated.resolve("classes/java.util.ArrayList.cpp"));
        var copy = PlatformBindings.find(new Program.MethodId("java/lang/System", "arraycopy",
                "(Ljava/lang/Object;ILjava/lang/Object;II)V"));
        assertNotNull(copy);
        assertTrue(arrayList.contains(copy.helper().name()), "ArrayList uses the annotated copy helper");
        String platform = Files.readString(generated.resolve("classes/java.lang.NativePlatform.cpp"));
        assertTrue(platform.contains("::" + copy.binding().symbol() + "("), "Copy helper calls its bound native entry");
        assertTrue(fixture.contains("::jnative::arrays_fill_reference("), "ArrayList uses bound reference fill");
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(result.executable().toString()), Duration.ofSeconds(90),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
