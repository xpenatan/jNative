package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.compiler.PlatformBindings;
import com.github.xpenatan.jnative.compiler.Program;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NativeLibraryKernelsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void textCollectionsAndCodecsMatchJvmUnderCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("NativeLibraryKernels.java");
        try(var input = getClass().getResourceAsStream("/fixtures/NativeLibraryKernels.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "NativeLibraryKernels"),
                Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("NativeLibraryKernels").buildRoot(temporary.resolve("out"))
                .buildType(buildType).timeout(Duration.ofMinutes(5));
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        StringBuilder emitted = new StringBuilder();
        try(var paths = Files.walk(generated)) {
            for(Path path : paths.filter(p -> p.toString().endsWith(".cpp")).toList())
                emitted.append(Files.readString(path));
        }
        for(String helper : List.of("string_to_char_array", "string_get_chars", "string_index_of_code_point",
                "string_index_of_text", "string_last_index_of_code_point", "string_starts_with",
                "string_code_point_count", "string_offset_by_code_points", "base64_encode",
                "base64_encode_string", "base64_decode", "base64_decode_string", "crc32_update", "checksum_update"))
            assertTrue(emitted.toString().contains("::jnative::" + helper + "("), helper);
        String deque = Files.readString(generated.resolve("classes/java.util.ArrayDeque.cpp"));
        String builderCpp = Files.readString(generated.resolve("classes/java.lang.StringBuilder.cpp"));
        var copy = PlatformBindings.find(new Program.MethodId("java/lang/System", "arraycopy",
                "(Ljava/lang/Object;ILjava/lang/Object;II)V"));
        assertNotNull(copy);
        assertTrue(deque.contains(copy.helper().name()), "Deque uses the annotated copy helper");
        assertTrue(emitted.toString().contains("::" + copy.binding().symbol() + "("));
        assertTrue(emitted.toString().contains("::jnative::string_get_chars("));
        assertTrue(emitted.toString().contains("::jnative::arrays_fill_c("));
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
        assertEquals(new ProcessHarness.Output(0, "callbacks-ok\n"),
                ProcessHarness.run(temporary, List.of(result.executable().toString(), "callbacks"),
                        Duration.ofSeconds(30), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
