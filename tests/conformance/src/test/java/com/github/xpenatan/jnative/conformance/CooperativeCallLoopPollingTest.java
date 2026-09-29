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

class CooperativeCallLoopPollingTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void collectingCallsKeepCallerValuesRowsAndCheckedFailures(BuildType buildType) throws Exception {
        Path source = temporary.resolve("CooperativeCallLoopPolling.java");
        try(var input = getClass().getResourceAsStream("/fixtures/CooperativeCallLoopPolling.java")) {
            assertNotNull(input);
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "CooperativeCallLoopPolling"),
                Duration.ofSeconds(90));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("CooperativeCallLoopPolling").buildRoot(temporary.resolve("out"))
                .buildType(buildType).cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5));
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                "CooperativeCallLoopPolling");
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* CooperativeCallLoopPolling::stable\\(").matcher(cpp);
        assertTrue(declaration.find(), cpp);
        int start = declaration.start();
        String stable = cpp.substring(start, cpp.indexOf("\n}", start));
        assertTrue(stable.contains("++ & 63u"), stable);
        assertTrue(stable.contains("chunk_end"), stable);
        assertTrue(stable.contains("rows_elements.get_unchecked("), stable);
        assertTrue(stable.contains("::jnative::array_get<"), stable);
        assertTrue(stable.contains("RootFrame<"), stable);
        // This fixture allocates callerOnly before its loop, so the bounded
        // unpublished-prefix proof must retain the eager frame.
        assertFalse(stable.contains("RootAddressFrame<"), stable);
        assertTrue(stable.contains("callerOnly"), stable);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(120), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
