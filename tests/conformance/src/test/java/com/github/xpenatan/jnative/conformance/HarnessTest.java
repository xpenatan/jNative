package com.github.xpenatan.jnative.conformance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HarnessTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {17, 25})
    void javaReferenceIsCompiledAndExecuted(int release) throws Exception {
        Path source = temporary.resolve("Reference.java");
        Files.writeString(
                source,
                """
                        public class Reference {
                            public static void main(String[] args) {
                                int value = Integer.MAX_VALUE;
                                System.out.println(value + 1);
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, release);
        var output =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Reference"),
                        Duration.ofSeconds(15));
        assertEquals(0, output.exitCode());
        assertEquals("-2147483648\n", output.text());
    }
}
