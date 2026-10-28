package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.*;

class ReferenceArrayAllocationTest {
    @TempDir Path directory;

    @Test
    void readableAndFallbackAllocationsUseKnownComponentMetadata() throws Exception {
        Path source = directory.resolve("ArrayMetadata.java");
        Files.writeString(source, """
                public class ArrayMetadata {
                    static class Element {}
                    static Object create(int size, int kind) {
                        if (kind == 0) return new Element[size];
                        if (kind == 1) return new String[size];
                        if (kind == 2) return new Object[size];
                        return new Element[size][];
                    }
                    static Object fallback(int size) { return null; }
                    public static void main(String[] args) {
                        System.out.println(create(args.length, 0));
                        System.out.println(fallback(args.length));
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        Path bytecode = directory.resolve("ArrayMetadata.class");
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(bytecode)).accept(node, 0);
        node.methods.removeIf(method -> method.name.equals("fallback"));
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        var fallback = writer.visitMethod(ACC_STATIC, "fallback", "(I)Ljava/lang/Object;", null, null);
        Label first = new Label(), second = new Label(), end = new Label();
        fallback.visitCode();
        fallback.visitVarInsn(ILOAD, 0);
        fallback.visitJumpInsn(IFLE, end);
        fallback.visitVarInsn(ILOAD, 0);
        fallback.visitInsn(ICONST_1);
        fallback.visitInsn(IAND);
        fallback.visitJumpInsn(IFNE, second);
        fallback.visitLabel(first);
        fallback.visitIincInsn(0, -1);
        fallback.visitLabel(second);
        fallback.visitIincInsn(0, -1);
        fallback.visitVarInsn(ILOAD, 0);
        fallback.visitJumpInsn(IFGT, first);
        fallback.visitLabel(end);
        fallback.visitInsn(ICONST_1);
        fallback.visitTypeInsn(ANEWARRAY, "ArrayMetadata$Element");
        fallback.visitInsn(ARETURN);
        fallback.visitMaxs(0, 0);
        fallback.visitEnd();
        writer.visitEnd();
        Files.write(bytecode, writer.toByteArray());

        var request = new NativeBuildRequest(List.of(directory), "ArrayMetadata", directory.resolve("out"),
                null, null, "arrays", BuildType.DEBUG, false);
        var emitter = new CppEmitter();
        String generated = emitter.emit(new BytecodeCompiler().compile(request), request).get("classes/ArrayMetadata.cpp");
        assertTrue(generated.contains("::jnative::ReferenceArray::create(size, \"[LArrayMetadata$Element;\","), generated);
        assertTrue(generated.contains("::jnative::ReferenceArray::create(size, \"[Ljava/lang/String;\", 0)"), generated);
        assertTrue(generated.contains("::jnative::ReferenceArray::create(size, \"[Ljava/lang/Object;\", 0)"), generated);
        assertTrue(generated.contains("::jnative::ReferenceArray::create(size, \"[[LArrayMetadata$Element;\", 0)"), generated);
        assertFalse(generated.contains("::jnative::new_array("), generated);
        assertTrue(emitter.readabilityFallbacks().stream().anyMatch(failure -> failure.toString().contains("fallback")));
        assertTrue(generated.matches("(?s).*ReferenceArray::create\\(stack[^;]+\\\"\\[LArrayMetadata\\$Element;\\\", [^;]+::class_id\\).*"), generated);
    }
}
