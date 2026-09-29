package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class RandomTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @CsvSource({"17, DEBUG", "17, RELEASE", "25, DEBUG", "25, RELEASE"})
    void uniformApiMatchesJvm(int release, BuildType buildType) throws Exception {
        Path source = temporary.resolve("RandomUniform.java");
        try(var input = getClass().getResourceAsStream("/fixtures/RandomUniform.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, release);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "RandomUniform"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        assertTrue(expected.text().contains("concurrent=true"), expected.text());
        var result =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("RandomUniform")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType)
                        .timeout(Duration.ofMinutes(3))
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        assertTrue(NativeCompatibility.libraryInventory().contains("java.util.Random"));
    }

    @Test
    void unsupportedRandomApisFailBeforeOutput() throws Exception {
        String[] bodies = {
            "new java.util.Random(1).nextGaussian();",
            "new java.util.Random(1).ints();",
            "new java.util.Random(1).nextInt(1, 3);",
            "java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(new java.io.ByteArrayOutputStream()); out.writeObject(new java.util.Random(1));"
        };
        for(int i = 0; i < bodies.length; ++i) {
            Path directory = temporary.resolve("case-" + i);
            Files.createDirectories(directory);
            Path source = directory.resolve("OutsideRandomProfile.java");
            Files.writeString(
                    source,
                    "public class OutsideRandomProfile { public static void main(String[] args) throws Exception { "
                            + bodies[i]
                            + " } }");
            Path classes = directory.resolve("classes");
            ProcessHarness.javac(source, classes, 25);
            Path output = directory.resolve("output");
            var error =
                    assertThrows(
                            CompilerException.class,
                            () ->
                                    ProcessHarness.behavioralBuilder()
                                            .classpath(classes)
                                            .mainClass("OutsideRandomProfile")
                                            .buildRoot(output)
                                            .generate());
            assertTrue(error.getMessage().contains("JN1003"), error.getMessage());
            assertTrue(error.getMessage().contains("OutsideRandomProfile.main"), error.getMessage());
            assertFalse(Files.exists(output));
        }
    }
}
