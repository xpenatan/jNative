package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class LargeProgramGenerationTest {
    @TempDir
    Path temporary;

    @Test
    void repeatedBranchJoinsGenerateWithoutEnumeratingEveryPath() throws Exception {
        StringBuilder source =
                new StringBuilder(
                        "public class Diamonds { static int value(int n) { int result=0; if(n<0) {");
        for(int i = 0; i < 45; i++)
            source.append("if((n & ")
                    .append(1 << i % 20)
                    .append(") == 0) result += ")
                    .append(i)
                    .append("; else result -= ")
                    .append(i)
                    .append(';');
        source.append(
                "return result; } return n; } public static void main(String[] args) { System.out.println(value(-1)); } }");
        Path input = temporary.resolve("Diamonds.java"), classes = temporary.resolve("classes");
        Files.writeString(input, source);
        ProcessHarness.javac(input, classes, 25);
        assertTimeoutPreemptively(
                Duration.ofSeconds(15),
                () ->
                        NativeBuilder.create()
                                .classpath(classes)
                                .mainClass("Diamonds")
                                .buildRoot(temporary.resolve("out"))
                                .generate());
    }

    @Test
    void unsupportedApisAreReportedTogetherAndConstructorsAreNotInherited() throws Exception {
        Path input = temporary.resolve("Missing.java"), classes = temporary.resolve("classes");
        Files.writeString(
                input,
                """
                        import java.net.URI;
                        import java.time.Instant;
                        import java.util.StringJoiner;

                        public class Missing {
                            public static void main(String[] args) {
                                System.out.println(Instant.now());
                                System.out.println(URI.create("https://example.invalid"));
                                System.out.println(new StringJoiner(","));
                            }
                        }
                        """);
        ProcessHarness.javac(input, classes, 25);
        CompilerException failure =
                assertThrows(
                        CompilerException.class,
                        () ->
                                NativeBuilder.create()
                                        .classpath(classes)
                                        .mainClass("Missing")
                                        .buildRoot(temporary.resolve("out"))
                                        .generate());
        assertTrue(failure.getMessage().contains("java.time.Instant.now"), failure.getMessage());
        assertTrue(failure.getMessage().contains("java.net.URI.create"), failure.getMessage());
        assertTrue(failure.getMessage().contains("java.util.StringJoiner.<init>"), failure.getMessage());
        assertFalse(Files.exists(temporary.resolve("out")));
    }
}
