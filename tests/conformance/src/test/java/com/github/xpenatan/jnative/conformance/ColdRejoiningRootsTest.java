package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class ColdRejoiningRootsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void collectingArmsRejoinWithLiveArgumentsLocalsAndTemporaries(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ColdRejoiningRoots.java");
        try(var input = getClass().getResourceAsStream("/fixtures/ColdRejoiningRoots.java")) {
            assertNotNull(input);
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ColdRejoiningRoots"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder()
                .classpath(classes)
                .mainClass("ColdRejoiningRoots")
                .buildRoot(temporary.resolve("out"))
                .buildType(buildType);
        var generated = builder.generate();
        String cpp = Files.readString(generated.request().generatedSourcesDirectory()
                .resolve("classes/ColdRejoiningRoots.cpp"));
        for(String name : List.of("rejoin", "choose", "multiple", "failing", "recursive",
                "consumedBeforeCollection")) {
            String body = body(cpp, name);
            assertTrue(body.contains("RootAddressFrame<"), body);
            assertTrue(body.contains("RootValue<>"), body);
            assertFalse(body.contains("RootFrame<"), body);
            assertFalse(body.contains("poll_if_requested"), body);
            String afterInitialization = body.substring(body.indexOf("::ensure_initialized();")
                    + "::ensure_initialized();".length());
            assertFalse(afterInitialization.substring(0, afterInitialization.indexOf("if ("))
                    .contains("safepoint"), body);
            assertTrue(afterInitialization.indexOf("::jnative::safepoint();")
                    > afterInitialization.indexOf("RootAddressFrame<"), body);
        }
        for(String name : List.of("caught", "locked")) {
            String body = body(cpp, name);
            assertTrue(body.contains("RootFrame<"), body);
            assertFalse(body.contains("RootAddressFrame<"), body);
        }
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(compiled.executable().toString()), Duration.ofSeconds(60),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* ColdRejoiningRoots::"
                + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        int start = declaration.start();
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > start, name);
        return cpp.substring(start, end);
    }
}
