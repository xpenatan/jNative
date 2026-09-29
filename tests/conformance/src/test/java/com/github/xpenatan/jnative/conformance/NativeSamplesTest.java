package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.samples.nativeinterop.BuildNativeInterop;
import com.github.xpenatan.jnative.samples.nativeinterop.NativeInterop;
import com.github.xpenatan.jnative.samples.nativecallbacks.BuildNativeCallbacks;
import com.github.xpenatan.jnative.samples.nativecallbacks.NativeCallbacks;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the examples themselves, keeping their documented output reproducible.
 */
class NativeSamplesTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"native-interop", "native-callbacks"})
    void samplesUseRealNativeBindingsAndMatchTheirJvmReferences(String sample) throws Exception {
        Path repository = Path.of(System.getProperty("jnative.root"));
        Path module = repository.resolve("samples").resolve(sample);
        Class<?> entry =
                sample.equals("native-interop") ? NativeInterop.class : NativeCallbacks.class;
        Path classes = Path.of(entry.getProtectionDomain().getCodeSource().getLocation().toURI());
        String expected =
                Files.readString(module.resolve("expected-output.txt")).replace("\r\n", "\n");
        var reference =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), entry.getName()),
                        Duration.ofSeconds(30));
        assertEquals(0, reference.exitCode(), reference.text());
        assertEquals(
                expected.replace("Execution: native C/C++", "Execution: JVM reference"),
                reference.text());

        NativeBuilder builder =
                sample.equals("native-interop")
                        ? BuildNativeInterop.builder(module, temporary)
                        : BuildNativeCallbacks.builder(module, temporary);
        // Gradle may isolate test classes from the worker JVM's java.class.path.
        var compiled =
                builder.classpath(classes)
                        .buildType(BuildType.RELEASE)
                        .log(BuildLog.silent())
                        .build();
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(45),
                        Map.of("JNATIVE_GC_INTERVAL", "1"));
        assertEquals(new ProcessHarness.Output(0, expected), actual);
        if(Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, compiled, actual, 1);
    }
}
