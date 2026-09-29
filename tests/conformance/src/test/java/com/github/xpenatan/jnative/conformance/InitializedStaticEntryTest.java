package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.StackTraceMode;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InitializedStaticEntryTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void internalEntriesPreserveInitializationRootsCyclesFailureAndJavaFrames(BuildType buildType) throws Exception {
        Path source = temporary.resolve("InitializedStaticEntries.java");
        try(var input = getClass().getResourceAsStream("/fixtures/InitializedStaticEntries.java")) {
            assertNotNull(input);
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "InitializedStaticEntries"),
                Duration.ofSeconds(90));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("InitializedStaticEntries").buildRoot(temporary.resolve("out"))
                .stackTraces(StackTraceMode.JAVA).javaSourceLocations(true).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5));
        var generated = builder.generate();
        Map<String, Object> map = Json.object(Json.read(Files.readString(
                generated.request().generatedSourcesDirectory().resolve("source-map.json"))));
        int variants = 0;
        for(Object value : Json.array(map.get("classes"))) {
            Map<String, Object> owner = Json.object(value);
            List<String> lines = Files.readAllLines(generated.request().generatedSourcesDirectory().resolve((String)owner.get("cppFile")));
            for(Object entry : Json.array(owner.get("methods"))) {
                Map<String, Object> method = Json.object(entry);
                if(!"initialized".equals(method.get("entry"))) continue;
                int start = ((Number)method.get("cppLineStart")).intValue();
                int end = ((Number)method.get("cppLineEnd")).intValue();
                String body = String.join("\n", lines.subList(start - 1, end));
                assertTrue(body.contains("::" + method.get("cppMethod") + "("), body);
                if(((String)owner.get("javaInternalName")).startsWith("InitializedStaticEntries"))
                    assertTrue(body.lines().anyMatch(line -> line.startsWith("JNATIVE_INLINE_ENTRY ")
                            && line.contains("::" + method.get("cppMethod") + "(")), body);
                assertEquals(1, body.lines().filter(line -> line.contains("JavaFrame java_frame(")).count(), body);
                variants++;
            }
        }
        assertTrue(variants >= 6, "Expected independent reentrant, cyclic, concurrent and frame entries");
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(120), Map.of("JNATIVE_GC_INTERVAL", "1")));
        var frames = ProcessHarness.run(temporary, List.of(compiled.executable().toString(), "frames"),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1"));
        assertEquals(0, frames.exitCode(), frames.text());
        for(String method : List.of("divide", "callDivide", "main"))
            assertEquals(1, frames.text().lines().filter(line -> line.contains("at InitializedStaticEntries." + method + "(")).count(), frames.text());
        assertFalse(frames.text().contains("initialized_divide") || frames.text().contains("initialized_callDivide"), frames.text());
    }
}
