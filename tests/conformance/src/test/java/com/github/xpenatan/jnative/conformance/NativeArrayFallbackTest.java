package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.compiler.PlatformBindings;
import com.github.xpenatan.jnative.compiler.Program;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class NativeArrayFallbackTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void fallbackKernelsPreserveValuesAndExceptions(BuildType buildType) throws Exception {
        Path classes = createFixture(temporary);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ArrayFallback"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        assertTrue(expected.text().contains("reference-incompatible:java.lang.ArrayStoreException"));
        assertTrue(expected.text().contains("primitive-null:java.lang.NullPointerException"));
        var builder = ProcessHarness.behavioralBuilder()
                .classpath(classes)
                .mainClass("ArrayFallback")
                .buildRoot(temporary.resolve("out"))
                .buildType(buildType);
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String report = Files.readString(generated.resolve("source-readability.tsv"));
        for (String signature : List.of("primitive(I[I[III)I",
                "references(I[Ljava/lang/Object;Ljava/lang/Object;II)I")) {
            assertTrue(report.contains("ArrayFallback." + signature + "\tlow-level\t"), report);
            assertTrue(generation.readabilityFallbacks().stream()
                    .anyMatch(fallback -> fallback.javaMethod().equals("ArrayFallback." + signature)));
        }
        String cpp = Files.readString(generated.resolve("classes/ArrayFallback.cpp"));
        String primitive = body(cpp, "primitive");
        for (String helper : List.of("fill", "hashCode", "equals"))
            assertTrue(primitive.contains("j_java_util_Arrays_" + helper + "_"), helper);
        String arrays = Files.readString(generated.resolve("classes/java.util.Arrays.cpp"));
        for (String helper : List.of("arrays_fill_i", "arrays_hash_i", "arrays_equals_i", "arrays_fill_reference"))
            assertTrue(arrays.contains("::jnative::" + helper + "("), helper);
        var copy = PlatformBindings.find(new Program.MethodId("java/lang/System", "arraycopy",
                "(Ljava/lang/Object;ILjava/lang/Object;II)V"));
        assertNotNull(copy);
        assertTrue(primitive.contains(copy.helper().name()));
        String references = body(cpp, "references");
        assertTrue(references.contains("j_java_util_Arrays_fill_"));
        assertTrue(references.contains(copy.helper().name()));
        String platform = ProcessHarness.generatedClassSource(generated, copy.helper().owner());
        assertTrue(platform.contains("::" + copy.binding().symbol() + "("));
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(result.executable().toString()), Duration.ofSeconds(60),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String method) {
        int start = cpp.indexOf("\nstd::int32_t ArrayFallback::" + method + "(");
        assertTrue(start >= 0, method);
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, method);
        return cpp.substring(start, end);
    }

    static Path createFixture(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path source = directory.resolve("ArrayFallback.java");
        Files.writeString(source, """
                import java.util.Arrays;
                public class ArrayFallback {
                    static int primitive(int rounds, int[] first, int[] second, int from, int to) {
                        throw new UnsupportedOperationException();
                    }
                    static int references(int rounds, Object[] array, Object value, int from, int to) {
                        throw new UnsupportedOperationException();
                    }
                    static void attempt(String label, Runnable action) {
                        try { action.run(); System.out.println(label + ":ok"); }
                        catch (RuntimeException error) {
                            System.gc();
                            System.out.println(label + ":" + error.getClass().getName());
                        }
                    }
                    public static void main(String[] args) {
                        int[] first = new int[2051], second = new int[2051];
                        for (int rounds : new int[] {0, 1, 2, 3, 4, 11}) {
                            System.out.println(primitive(rounds, first, second, 1, 2050));
                            System.gc();
                            System.out.println(Arrays.hashCode(first) + ":" + Arrays.hashCode(second));
                        }
                        attempt("primitive-negative", () -> primitive(2, first, second, -1, 0));
                        attempt("primitive-reversed", () -> primitive(2, first, second, 2, 1));
                        attempt("primitive-null", () -> primitive(2, null, second, 2, 1));
                        attempt("copy-bounds", () -> primitive(2, first, new int[1], 0, 2051));
                        System.out.println(Arrays.hashCode(first));
                        Object[] narrow = new String[2051];
                        Object retained = new String(new char[] {'k', 'e', 'p', 't'});
                        System.out.println(references(4, narrow, retained, 1, 2050));
                        System.gc();
                        System.out.println(narrow[0] + ":" + narrow[1] + ":" + narrow[2050]);
                        Object wrong = new Object();
                        attempt("reference-incompatible", () -> references(2, narrow, wrong, 0, 2051));
                        attempt("reference-empty", () -> references(2, narrow, wrong, 5, 5));
                        attempt("reference-null", () -> references(2, null, retained, 2, 1));
                        System.out.println(references(2, narrow, null, 0, 2051));
                        System.gc();
                        System.out.println(Arrays.hashCode(narrow));
                    }
                }
                """);
        Path classes = directory.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        Path classFile = classes.resolve("ArrayFallback.class");
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        new ClassReader(Files.readAllBytes(classFile)).accept(new ClassVisitor(ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (name.equals("primitive") || name.equals("references")) return null;
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                kernel(writer, false);
                kernel(writer, true);
                super.visitEnd();
            }
        }, 0);
        Files.write(classFile, writer.toByteArray());
        return classes;
    }

    private static void kernel(ClassWriter writer, boolean reference) {
        String descriptor = reference ? "(I[Ljava/lang/Object;Ljava/lang/Object;II)I" : "(I[I[III)I";
        MethodVisitor method = writer.visitMethod(ACC_STATIC, reference ? "references" : "primitive",
                descriptor, null, null);
        Label first = new Label(), second = new Label(), end = new Label();
        method.visitCode();
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ISTORE, 5);
        method.visitVarInsn(ILOAD, 0);
        method.visitJumpInsn(IFLE, end);
        method.visitVarInsn(ILOAD, 0);
        method.visitInsn(ICONST_1);
        method.visitInsn(IAND);
        method.visitJumpInsn(IFNE, second);
        // The cycle has two external entries, forcing the low-level emitter.
        method.visitLabel(first);
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ILOAD, 3);
        method.visitVarInsn(ILOAD, 4);
        method.visitVarInsn(reference ? ALOAD : ILOAD, reference ? 2 : 0);
        method.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "fill",
                reference ? "([Ljava/lang/Object;IILjava/lang/Object;)V" : "([IIII)V", false);
        method.visitVarInsn(ILOAD, 5);
        if (reference) {
            method.visitVarInsn(ILOAD, 0);
        } else {
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "hashCode", "([I)I", false);
        }
        method.visitInsn(IADD);
        method.visitVarInsn(ISTORE, 5);
        method.visitIincInsn(0, -1);
        method.visitVarInsn(ILOAD, 0);
        method.visitJumpInsn(IFLE, end);

        method.visitLabel(second);
        method.visitVarInsn(ALOAD, 1);
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ALOAD, reference ? 1 : 2);
        method.visitInsn(reference ? ICONST_1 : ICONST_0);
        method.visitVarInsn(ALOAD, 1);
        method.visitInsn(ARRAYLENGTH);
        if (reference) {
            method.visitInsn(ICONST_1);
            method.visitInsn(ISUB);
        }
        method.visitMethodInsn(INVOKESTATIC, "java/lang/System", "arraycopy",
                "(Ljava/lang/Object;ILjava/lang/Object;II)V", false);
        method.visitVarInsn(ILOAD, 5);
        if (reference) {
            method.visitVarInsn(ALOAD, 1);
            method.visitInsn(ARRAYLENGTH);
        } else {
            method.visitVarInsn(ALOAD, 1);
            method.visitVarInsn(ALOAD, 2);
            method.visitMethodInsn(INVOKESTATIC, "java/util/Arrays", "equals", "([I[I)Z", false);
        }
        method.visitInsn(IADD);
        method.visitVarInsn(ISTORE, 5);
        method.visitIincInsn(0, -1);
        method.visitVarInsn(ILOAD, 0);
        method.visitJumpInsn(IFGT, first);
        method.visitLabel(end);
        method.visitVarInsn(ILOAD, 5);
        method.visitInsn(IRETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
