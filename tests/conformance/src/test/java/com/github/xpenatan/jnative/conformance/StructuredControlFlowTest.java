package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

class StructuredControlFlowTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void mergesSwitchesAndHandlersMatchJava(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Regions.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Regions {
                            static int order;
                            static int tick(int value) { order = order * 10 + value; System.gc(); return value; }
                            static String text(int value) { tick(value); return new String("text" + value); }
                            static int merge(int key) { return tick(1) + (key < 0 ? tick(2) : tick(3)) + tick(4); }
                            static String references(boolean choose) { return text(1) + (choose ? text(2) : text(3)) + text(4); }
                            static long wide(boolean choose) { return 1234567890123L + (choose ? Long.MIN_VALUE : Long.MAX_VALUE); }
                            static boolean shortCircuit(int value) { return value > 0 && tick(1) == 1 || value < 0 && tick(2) == 2; }
                            static int dense(int key) {
                                int result = 0;
                                switch (key) {
                                    case 0: result += tick(1);
                                    case 1: result += tick(2); break;
                                    case 2: case 3: result = tick(3); break;
                                    default: result = tick(4);
                                }
                                return result;
                            }
                            static int sparse(int key) {
                                switch (key) {
                                    case -1000: return tick(1);
                                    case 42: return tick(2);
                                    case 90000: return tick(3);
                                    default: return tick(4);
                                }
                            }
                            static String expression(int key) {
                                return text(1) + switch (key) {
                                    case 0 -> text(2);
                                    case 1, 2 -> { System.gc(); yield text(3); }
                                    default -> text(4);
                                };
                            }
                            static int switchingLoop(int count) {
                                int result = 0;
                                while (count > 0) {
                                    --count;
                                    switch (count) {
                                        case 2: continue;
                                        case 3: result += 30; break;
                                        default: result += count;
                                    }
                                }
                                return result;
                            }
                            static int loopWithTry(int count) {
                                int result = 0;
                                while (count > 0) {
                                    --count;
                                    try { maybeThrow(count); result += count; }
                                    catch (RuntimeException error) { System.gc(); result += error.getMessage().length(); }
                                }
                                return result;
                            }
                            static void maybeThrow(int key) {
                                if (key == 0) throw new IllegalArgumentException("argument");
                                if (key == 1) throw new IllegalStateException("state");
                                tick(1);
                            }
                            static int handlers(int key) {
                                int result;
                                try { maybeThrow(key); result = tick(2); }
                                catch (IllegalArgumentException error) { System.gc(); result = error.getMessage().length(); }
                                catch (RuntimeException error) { result = error.getMessage().length() + tick(3); }
                                return result;
                            }
                            static int multiCatch(int key) {
                                try { maybeThrow(key); return tick(2); }
                                catch (IllegalArgumentException | IllegalStateException error) { System.gc(); return error.getMessage().length(); }
                            }
                            static int nested(int key) {
                                try {
                                    try { maybeThrow(key); }
                                    catch (IllegalArgumentException error) { tick(2); throw new IllegalStateException(error.getMessage()); }
                                    return tick(3);
                                } catch (RuntimeException error) { System.gc(); return error.getMessage().length(); }
                            }
                            static int cleanup(int key) {
                                try { maybeThrow(key); return tick(2); }
                                finally { tick(3); }
                            }
                            static int catchAndFinally(int key) {
                                int result = 0;
                                try { maybeThrow(key); result = tick(2); }
                                catch (IllegalArgumentException error) { System.gc(); result = error.getMessage().length(); }
                                finally { tick(3); }
                                return result;
                            }
                            static int cleanupThrows(int key) {
                                try {
                                    try { return tick(1); }
                                    finally { if (key == 0) throw new IllegalStateException("cleanup"); tick(2); }
                                } catch (IllegalStateException error) { System.gc(); return error.getMessage().length(); }
                            }
                            static void run(int key) {
                                order = 0;
                                System.out.println(merge(key) + ":" + order);
                                order = 0;
                                System.out.println(references(key < 0) + ":" + order);
                                System.out.println(wide(key < 0));
                                order = 0;
                                System.out.println(shortCircuit(key) + ":" + order);
                                System.out.println(dense(key));
                                System.out.println(sparse(key));
                                System.out.println(expression(key));
                                System.out.println(handlers(key));
                                System.out.println(multiCatch(key));
                                System.out.println(nested(key));
                                try { System.out.println(cleanup(key)); }
                                catch (RuntimeException error) { System.gc(); System.out.println(error.getMessage() + ":" + order); }
                                try { System.out.println(catchAndFinally(key)); }
                                catch (RuntimeException error) { System.gc(); System.out.println(error.getMessage() + ":" + order); }
                                System.out.println(cleanupThrows(key));
                            }
                            public static void main(String[] args) {
                                for (int i = -1; i < 5; ++i) run(i);
                                System.out.println(sparse(-1000) + sparse(42) + sparse(90000));
                                System.out.println(switchingLoop(6));
                                System.out.println(loopWithTry(6));
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, buildType == BuildType.DEBUG ? 17 : 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Regions"),
                        Duration.ofSeconds(30));
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Regions")
                        .buildType(buildType)
                        .debugInformation(true)
                        .buildRoot(temporary.resolve("native"));
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String report = Files.readString(generated.resolve("source-readability.tsv"));
        assertFalse(report.contains("low-level"), report);
        String cpp = Files.readString(generated.resolve("classes/Regions.cpp"));
        assertTrue(cpp.contains("switch ("), cpp);
        assertTrue(cpp.contains("try {") && cpp.contains("catch (const ::jnative::Thrown&"), cpp);
        assertFalse(
                cpp.contains("goto ") || cpp.contains("stack0") || cpp.contains("exception_pc"),
                cpp);
        var result = builder.compile(generation);
        assertEquals(0, expected.exitCode(), expected.text());
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(buildType == BuildType.RELEASE && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, result, expected, 1);
    }

    @Test
    void stackMergesPreserveSwapsAndSwitchEntryValuesWithoutDebugTables() throws Exception {
        Path classes = temporary.resolve("classes");
        Files.createDirectories(classes);
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC, "Rotate", null, "java/lang/Object", null);
        var rotate = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "rotate", "(I)I", null, null);
        Label head = new Label(), end = new Label();
        rotate.visitCode();
        rotate.visitInsn(ICONST_1);
        rotate.visitInsn(ICONST_2);
        rotate.visitLabel(head);
        rotate.visitVarInsn(ILOAD, 0);
        rotate.visitJumpInsn(IFLE, end);
        rotate.visitInsn(SWAP);
        rotate.visitIincInsn(0, -1);
        rotate.visitJumpInsn(GOTO, head);
        rotate.visitLabel(end);
        rotate.visitInsn(ISUB);
        rotate.visitInsn(IRETURN);
        rotate.visitMaxs(0, 0);
        rotate.visitEnd();
        var references =
                writer.visitMethod(
                        ACC_PUBLIC | ACC_STATIC,
                        "rotateReferences",
                        "(I)Ljava/lang/String;",
                        null,
                        null);
        Label referenceHead = new Label(), referenceEnd = new Label();
        references.visitCode();
        references.visitLdcInsn("first");
        references.visitLdcInsn("second");
        references.visitLabel(referenceHead);
        references.visitVarInsn(ILOAD, 0);
        references.visitJumpInsn(IFLE, referenceEnd);
        references.visitInsn(SWAP);
        references.visitMethodInsn(INVOKESTATIC, "java/lang/System", "gc", "()V", false);
        references.visitIincInsn(0, -1);
        references.visitJumpInsn(GOTO, referenceHead);
        references.visitLabel(referenceEnd);
        references.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/String",
                "concat",
                "(Ljava/lang/String;)Ljava/lang/String;",
                false);
        references.visitInsn(ARETURN);
        references.visitMaxs(0, 0);
        references.visitEnd();
        var selection =
                writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "selection", "(I)I", null, null);
        Label firstCase = new Label(),
                secondCase = new Label(),
                defaultCase = new Label(),
                triple = new Label();
        selection.visitCode();
        selection.visitIntInsn(BIPUSH, 10);
        selection.visitVarInsn(ILOAD, 0);
        selection.visitTableSwitchInsn(0, 1, defaultCase, firstCase, secondCase);
        selection.visitLabel(firstCase);
        selection.visitInsn(ICONST_1);
        selection.visitInsn(IADD);
        selection.visitLabel(secondCase); // Direct entry has 10; fall-through has 11.
        selection.visitVarInsn(ILOAD, 0);
        selection.visitJumpInsn(IFNE, triple);
        selection.visitInsn(ICONST_2);
        selection.visitInsn(IMUL);
        selection.visitInsn(IRETURN);
        selection.visitLabel(triple);
        selection.visitInsn(ICONST_3);
        selection.visitInsn(IMUL);
        selection.visitInsn(IRETURN);
        selection.visitLabel(defaultCase);
        selection.visitIntInsn(BIPUSH, 99);
        selection.visitInsn(IADD);
        selection.visitInsn(IRETURN);
        selection.visitMaxs(0, 0);
        selection.visitEnd();
        var main =
                writer.visitMethod(
                        ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
        main.visitCode();
        for(int count : List.of(0, 1, 2, 11)) {
            main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            main.visitLdcInsn(count);
            main.visitMethodInsn(INVOKESTATIC, "Rotate", "rotate", "(I)I", false);
            main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
            main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            main.visitLdcInsn(count);
            main.visitMethodInsn(
                    INVOKESTATIC, "Rotate", "rotateReferences", "(I)Ljava/lang/String;", false);
            main.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/io/PrintStream",
                    "println",
                    "(Ljava/lang/String;)V",
                    false);
            main.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
            main.visitLdcInsn(count);
            main.visitMethodInsn(INVOKESTATIC, "Rotate", "selection", "(I)I", false);
            main.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        }
        main.visitInsn(RETURN);
        main.visitMaxs(0, 0);
        main.visitEnd();
        writer.visitEnd();
        Files.write(classes.resolve("Rotate.class"), writer.toByteArray());
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Rotate"),
                        Duration.ofSeconds(20));
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Rotate")
                        .buildRoot(temporary.resolve("native"))
                        .build();
        assertEquals(
                new ProcessHarness.Output(
                        0,
                        "-1\nfirstsecond\n22\n1\nsecondfirst\n30\n-1\nfirstsecond\n109\n1\nsecondfirst\n109\n"),
                expected);
        String report =
                Files.readString(
                        result.generation()
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("source-readability.tsv"));
        assertFalse(report.contains("low-level"), report);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(20),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
