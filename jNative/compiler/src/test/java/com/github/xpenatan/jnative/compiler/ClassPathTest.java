package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import java.nio.file.*;
import java.util.List;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassPathTest {
    @TempDir
    Path directory;

    @Test
    void multiReleaseJarSelectsJava25AndOrderedClasspathCanShadowIt() throws Exception {
        Path jar = directory.resolve("versions.jar");
        var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        try(var out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            member(out, "demo/Version.class", type(52, 8));
            member(out, "META-INF/versions/17/demo/Version.class", type(61, 17));
            member(out, "META-INF/versions/25/demo/Version.class", type(69, 25));
            member(out, "META-INF/versions/26/demo/Version.class", type(70, 26));
        }
        assertEquals(25, value(new ClassPath(List.of(jar)).read("demo/Version")));
        Path overlay = Files.createDirectories(directory.resolve("overlay/demo"));
        Files.write(overlay.resolve("Version.class"), type(61, 7));
        assertEquals(
                7, value(new ClassPath(List.of(overlay.getParent(), jar)).read("demo/Version")));
        assertEquals(
                25, value(new ClassPath(List.of(jar, overlay.getParent())).read("demo/Version")));
    }

    @Test
    void plainJarDoesNotActivateVersionedEntriesAndBadJarReportsItsPath() throws Exception {
        Path jar = directory.resolve("plain.jar");
        try(var out = new JarOutputStream(Files.newOutputStream(jar))) {
            member(out, "demo/Version.class", type(52, 8));
            member(out, "META-INF/versions/25/demo/Version.class", type(69, 25));
        }
        assertEquals(8, value(new ClassPath(List.of(jar)).read("demo/Version")));
        Files.write(jar, new byte[]{0, 1, 2});
        var failure =
                assertThrows(
                        CompilerException.class,
                        () -> new ClassPath(List.of(jar)).read("demo/Version"));
        assertTrue(failure.getMessage().contains("JN1001"));
        assertTrue(failure.getMessage().contains("plain.jar"));
    }

    @Test
    void readingDoesNotDefineClassesOrRunInitializers() throws Exception {
        Path root = Files.createDirectories(directory.resolve("demo"));
        Files.write(root.resolve("Version.class"), type(69, 25));
        // This class contains an unconditional throwing <clinit>; metadata reading must succeed.
        assertEquals(25, value(new ClassPath(List.of(directory)).read("demo/Version")));
    }

    private static byte[] type(int version, int value) {
        var writer = new ClassWriter(0);
        writer.visit(version, Opcodes.ACC_PUBLIC, "demo/Version", null, "java/lang/Object", null);
        var method =
                writer.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "()I", null, null);
        method.visitCode();
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        var initializer = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitInsn(Opcodes.ACONST_NULL);
        initializer.visitInsn(Opcodes.ATHROW);
        initializer.visitMaxs(1, 0);
        initializer.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void member(JarOutputStream out, String name, byte[] bytes) throws Exception {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }

    private static int value(ClassNode node) {
        return (Integer)
                ((LdcInsnNode)
                        node.methods.stream()
                                .filter(m -> m.name.equals("value"))
                                .findFirst()
                                .orElseThrow()
                                .instructions
                                .getFirst())
                        .cst;
    }
}
