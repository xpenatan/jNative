package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class NativeLayoutExecutionTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(SourceLayout.class)
    void bothLayoutsCompileAndRun(SourceLayout layout) throws Exception {
        Path source = temporary.resolve("Player.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                "package game.characters; public class Player { public static void main(String[] args) { System.out.println(7); } }");
        ProcessHarness.javac(source, classes, 17);
        var built =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("game.characters.Player")
                        .buildRoot(temporary.resolve("out"))
                        .sourceLayout(layout)
                        .buildType(BuildType.RELEASE)
                        .build();
        var output =
                ProcessHarness.run(
                        temporary, List.of(built.executable().toString()), Duration.ofSeconds(30));
        assertEquals(new ProcessHarness.Output(0, "7\n"), output);
        if(Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, built, output, 1);
    }
}
