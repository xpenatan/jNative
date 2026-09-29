package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.cli.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class ReadableFallbackTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    @ResourceLock(Resources.SYSTEM_ERR)
    void irreducibleBytecodeFallsBackAutomaticallyAndMatchesJvm(BuildType buildType)
            throws Exception {
        Path classes = Files.createDirectories(temporary.resolve("classes"));
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC, "Irreducible", null, "java/lang/Object", null);
        var sum = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "sum", "(I)I", null, null);
        Label first = new Label(), second = new Label(), end = new Label();
        sum.visitCode();
        sum.visitInsn(ICONST_0);
        sum.visitVarInsn(ISTORE, 1);
        sum.visitVarInsn(ILOAD, 0);
        sum.visitJumpInsn(IFLE, end);
        // Both blocks can be entered from outside their cycle: no single loop header.
        sum.visitVarInsn(ILOAD, 0);
        sum.visitInsn(ICONST_1);
        sum.visitInsn(IAND);
        sum.visitJumpInsn(IFNE, second);
        sum.visitLabel(first);
        sum.visitIincInsn(1, 1);
        sum.visitIincInsn(0, -1);
        sum.visitVarInsn(ILOAD, 0);
        sum.visitJumpInsn(IFLE, end);
        sum.visitLabel(second);
        sum.visitIincInsn(1, 2);
        sum.visitIincInsn(0, -1);
        sum.visitVarInsn(ILOAD, 0);
        sum.visitJumpInsn(IFGT, first);
        sum.visitLabel(end);
        sum.visitVarInsn(ILOAD, 1);
        sum.visitInsn(IRETURN);
        sum.visitMaxs(0, 0);
        sum.visitEnd();

        var main =
                writer.visitMethod(
                        ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        main.visitCode();
        for(int count : List.of(0, 1, 2, 3, 4, 11)) {
            main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            main.visitLdcInsn(count);
            main.visitMethodInsn(INVOKESTATIC, "Irreducible", "sum", "(I)I", false);
            main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        }
        main.visitInsn(RETURN);
        main.visitMaxs(0, 0);
        main.visitEnd();
        writer.visitEnd();
        Files.write(classes.resolve("Irreducible.class"), writer.toByteArray());

        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Irreducible"),
                        Duration.ofSeconds(20));
        assertEquals(new ProcessHarness.Output(0, "0\n2\n3\n5\n6\n17\n"), expected);
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Irreducible")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType);
        var warnings = new ByteArrayOutputStream();
        NativeGenerationResult generated;
        PrintStream previousError = System.err;
        try(var warningLog = new PrintStream(warnings, true, StandardCharsets.UTF_8)) {
            System.setErr(warningLog);
            try {
                generated = builder.generate();
            } finally {
                System.setErr(previousError);
            }
        }
        String report =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("source-readability.tsv"));
        assertTrue(
                report.lines()
                        .anyMatch(
                                line ->
                                        line.startsWith("Irreducible.sum(I)I\tlow-level\t")
                                                && !line.endsWith("\t")),
                report);
        assertTrue(report.contains("Irreducible.main([Ljava/lang/String;)V\tstructured\t"), report);
        assertEquals(1, generated.readabilityFallbacks().size());
        var fallback = generated.readabilityFallbacks().getFirst();
        assertEquals("Irreducible.sum(I)I", fallback.javaMethod());
        assertEquals(
                generated.request().generatedSourcesDirectory().resolve("classes/Irreducible.cpp"),
                fallback.cppFile());
        assertTrue(Files.isRegularFile(fallback.cppFile()));
        assertTrue(
                report.contains(fallback.javaMethod() + "\tlow-level\t" + fallback.reason()),
                report);
        String warning = warnings.toString(StandardCharsets.UTF_8);
        assertTrue(
                warning.contains("JN3001 Low-level C++ fallback in " + fallback.javaMethod()),
                warning);
        assertTrue(
                warning.contains(fallback.reason())
                        && warning.contains(fallback.cppFile().toString()),
                warning);
        assertTrue(
                warning.contains(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("source-readability.tsv")
                                .toString()),
                warning);

        var cliOutput = new ByteArrayOutputStream();
        var cliErrors = new ByteArrayOutputStream();
        Path cliRoot = temporary.resolve("cli-output");
        var cliArguments =
                new ArrayList<>(
                        List.of(
                                "generate",
                                "--classpath",
                                classes.toString(),
                                "--main",
                                "Irreducible",
                                "--output",
                                cliRoot.toString()));
        if(buildType == BuildType.RELEASE) cliArguments.add("--release");
        try(var outputLog = new PrintStream(cliOutput, true, StandardCharsets.UTF_8);
            var errorLog = new PrintStream(cliErrors, true, StandardCharsets.UTF_8)) {
            assertEquals(
                    0,
                    Main.execute(
                            cliArguments.toArray(String[]::new), outputLog, errorLog));
        }
        String cliWarning = cliErrors.toString(StandardCharsets.UTF_8);
        assertTrue(
                cliWarning.contains(fallback.javaMethod())
                        && cliWarning.contains(fallback.reason()),
                cliWarning);
        assertTrue(
                cliWarning.contains(
                        cliRoot.resolve("native/src/classes/Irreducible.cpp").toString()),
                cliWarning);
        assertFalse(cliOutput.toString(StandardCharsets.UTF_8).contains("JN3001"));
        var result = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(20),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
