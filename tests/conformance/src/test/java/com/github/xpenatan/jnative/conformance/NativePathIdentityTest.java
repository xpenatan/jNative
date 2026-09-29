package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NativePathIdentityTest {
    @TempDir
    Path temporary;

    @Test
    void portableNamesKeepUnambiguousJavaIdentityInBothLayouts() throws Exception {
        var owners =
                List.of(
                        "game/Hero",
                        "game/hero",
                        "other/Hero",
                        "game/Player$State",
                        "game/Player$jNativeLambda$0",
                        "game/Player_State",
                        "game/CON",
                        "game/Ω",
                        "game/Space name");
        Path jar = temporary.resolve("input classes.jar");
        try(var output = new JarOutputStream(Files.newOutputStream(jar))) {
            for(String owner : owners) {
                output.putNextEntry(new JarEntry(owner + ".class"));
                output.write(classBytes(owner, false, owners));
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("game/Main.class"));
            output.write(classBytes("game/Main", true, owners));
        }
        Map<String, String> logical = null;
        for(SourceLayout layout : SourceLayout.values()) {
            var generated =
                    NativeBuilder.create()
                            .classpath(jar)
                            .mainClass("game.Main")
                            .buildRoot(temporary.resolve(layout.name()))
                            .sourceLayout(layout)
                            .generate();
            var map =
                    Json.object(
                            Json.read(
                                    Files.readString(
                                            generated
                                                    .request()
                                                    .generatedSourcesDirectory()
                                                    .resolve("source-map.json"))));
            Set<String> physical = new HashSet<>();
            Map<String, String> names = new TreeMap<>();
            for(Object raw : Json.array(map.get("classes"))) {
                var entry = Json.object(raw);
                String owner = entry.get("javaInternalName").toString(),
                        file = entry.get("cppFile").toString();
                assertTrue(physical.add(file.toLowerCase(Locale.ROOT)), file);
                assertTrue(
                        Files.isRegularFile(
                                generated.request().generatedSourcesDirectory().resolve(file)));
                names.put(owner, entry.get("cppClass").toString());
                if(owners.contains(owner)) {
                    var input = Json.object(entry.get("input"));
                    assertEquals(owner + ".class", input.get("member"));
                    if(owner.equals("game/Player$jNativeLambda$0")) {
                        assertEquals("", input.get("generatedFrom"));
                        assertEquals("::generated::game::Player_jNativeLambda_0", entry.get("cppClass"));
                    }
                    assertTrue(input.get("classSha256").toString().matches("[0-9a-f]{64}"));
                    assertEquals(
                            "game/Player.java",
                            owner.equals("game/Player$State")
                                    ? entry.get("javaSource")
                                    : "game/Player.java");
                }
            }
            assertTrue(physical.stream().anyMatch(name -> name.contains("con_type")));
            if(logical != null) assertEquals(logical, names);
            logical = names;
        }
    }

    @Test
    void invalidAndOverlongBytecodeNamesFailBeforePublishing() throws Exception {
        for(String owner : List.of("bad/../Escape", "bad/" + "LongClass".repeat(36))) {
            Path jar = temporary.resolve(UUID.randomUUID() + ".jar");
            try(var output = new JarOutputStream(Files.newOutputStream(jar))) {
                output.putNextEntry(new JarEntry(owner + ".class"));
                output.write(classBytes(owner, true, List.of()));
            }
            Path root = temporary.resolve("output");
            var builder =
                    NativeBuilder.create()
                            .classpath(jar)
                            .mainClass(owner.replace('/', '.'))
                            .buildRoot(root);
            assertThrows(RuntimeException.class, builder::generate);
            assertFalse(Files.exists(root));
        }
    }

    private static byte[] classBytes(String owner, boolean main, List<String> calls) {
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        writer.visitSource(owner.contains("Player") ? "Player.java" : "Input.java", null);
        var method =
                writer.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        main ? "main" : "value",
                        main ? "([Ljava/lang/String;)V" : "()I",
                        null,
                        null);
        method.visitCode();
        if(main) {
            for(String called : calls) {
                method.visitMethodInsn(Opcodes.INVOKESTATIC, called, "value", "()I", false);
                method.visitInsn(Opcodes.POP);
            }
            method.visitInsn(Opcodes.RETURN);
        }
        else {
            method.visitInsn(Opcodes.ICONST_1);
            method.visitInsn(Opcodes.IRETURN);
        }
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
