package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BoundedCallLoopPollingTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void boundedCallsPreserveChecksInitializationVolatileReadsAndCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("BoundedCallLoopPolling.java");
        try (var input = getClass().getResourceAsStream("/fixtures/BoundedCallLoopPolling.java")) {
            assertNotNull(input);
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "BoundedCallLoopPolling"),
                Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("BoundedCallLoopPolling").buildRoot(temporary.resolve("out"))
                .buildType(buildType).cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5));
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                "BoundedCallLoopPolling");
        for(String name : List.of("staticLoop", "finalLoop", "virtualLoop", "aliasMutation", "finalMutation", "rowReplacement", "rowRead", "maskedIndex", "guardEffects")) {
            String body = body(cpp, name);
            assertTrue(body.contains("++ & 63u"), body);
            assertTrue(body.contains("get_unchecked(") || body.contains("set_unchecked("), body);
            assertTrue(body.contains("chunk_end"), body);
        }
        for(String name : List.of("effectLoop", "dividingLoop", "volatileLoop", "mutableReplacement", "unknownIndex", "changedLimit")) {
            String body = body(cpp, name);
            assertTrue(body.contains("++ & 63u"), body);
            assertFalse(body.contains("get_unchecked("), body);
            assertFalse(body.contains("chunk_end"), body);
        }
        assertFalse(body(cpp, "foreignLoop").contains("poll_budget"));
        String volatileLoop = body(cpp, "volatileLoop");
        // Lazy loop roots can lower the initial test separately and use do/while.
        // In either spelling, the first volatile load must remain inside the loop.
        int loopStart = volatileLoop.indexOf("do {");
        if (loopStart < 0) loopStart = volatileLoop.indexOf("while (");
        assertTrue(loopStart >= 0, volatileLoop);
        assertTrue(volatileLoop.indexOf("changing.get()") > loopStart, volatileLoop);
        String rows = body(cpp, "rowReplacement");
        assertTrue(rows.contains("rows_elements.get_unchecked("), rows);
        assertTrue(rows.contains("::jnative::array_get<"), rows);
        String mutate = body(cpp, "mutate");
        assertFalse(mutate.contains("set_unchecked("), mutate);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(120), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* BoundedCallLoopPolling::"
                + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }
}
