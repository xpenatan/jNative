package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;
import com.github.xpenatan.jnative.BuildType;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NativeMigrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void allDriversMatchJvmUnderCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("NativeMigration.java");
        try(var input = getClass().getResourceAsStream("/fixtures/NativeMigration.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "NativeMigration"),
                Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("NativeMigration").buildRoot(temporary.resolve("out"))
                .buildType(buildType)
                .cmakeDefine("CMAKE_CXX_STANDARD", buildType == BuildType.DEBUG ? "11" : "17")
                .cmakeDefine("CMAKE_CXX_STANDARD_REQUIRED", "ON")
                .cmakeDefine("CMAKE_CXX_EXTENSIONS", "OFF")
                .cmakeDefine("JNATIVE_FEATURES", buildType == BuildType.DEBUG ? "PORTABLE" : "AUTO").timeout(Duration.ofMinutes(5)).build();
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(result.executable().toString()), Duration.ofSeconds(90),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
