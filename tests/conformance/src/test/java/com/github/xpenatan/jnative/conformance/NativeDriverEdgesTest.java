package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class NativeDriverEdgesTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void charsetAndDriverEdgesPreserveContracts(BuildType buildType) throws Exception {
        Path source = temporary.resolve("NativeDriverEdges.java");
        try (var input = getClass().getResourceAsStream("/fixtures/NativeDriverEdges.java")) {
            assertNotNull(input);
            Files.copy(input, source);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "NativeDriverEdges"), Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        // DataOutputStream.writeInt is final on the JDK, but virtual in this
        // existing classlib profile. Rename the dormant hook only after JVM run.
        Path override = classes.resolve("NativeDriverEdges$IntOverride.class");
        ClassReader reader = new ClassReader(Files.readAllBytes(override));
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return super.visitMethod(access, name.equals("recordInt") ? "writeInt" : name,
                        descriptor, signature, exceptions);
            }
        }, 0);
        Files.write(override, writer.toByteArray());
        var result = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("NativeDriverEdges")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeDefine("CMAKE_CXX_STANDARD", buildType == BuildType.DEBUG ? "11" : "17")
                .cmakeDefine("CMAKE_CXX_STANDARD_REQUIRED", "ON")
                .cmakeDefine("CMAKE_CXX_EXTENSIONS", "OFF")
                .cmakeDefine("JNATIVE_FEATURES", buildType == BuildType.DEBUG ? "PORTABLE" : "AUTO").timeout(Duration.ofMinutes(5)).build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
        assertEquals(new ProcessHarness.Output(0,
                "crc-after-write:ok\nwrite-long-dispatch:ok\nfile-bulk-dispatch:ok\n"
                        + "numeric-utf16-message:ok\ndecimal-utf16-message:ok\n"),
                ProcessHarness.run(temporary, List.of(result.executable().toString(), "policies"),
                        Duration.ofSeconds(30), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
