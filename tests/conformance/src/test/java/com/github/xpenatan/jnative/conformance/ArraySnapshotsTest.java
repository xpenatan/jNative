package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.*;

class ArraySnapshotsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void metadataBelongsToTheEvaluatedReferenceAcrossWritesAndCollection(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ArraySnapshots.java"),
                classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.util.Arrays;
                        public class ArraySnapshots {
                            static class Holder {
                                float[] values;
                                Holder(float[] values) { this.values = values; }
                                float sum() { return values[0] + values[1] + values[2]; }
                            }
                            static volatile boolean ready;
                            static float sum(Holder holder) {
                                return holder.values[0] + holder.values[1] + holder.values[2];
                            }
                            static float optional(Holder holder, boolean take) {
                                if (!take) return -1;
                                return holder.values[0] + holder.values[1] + holder.values[2];
                            }
                            static float stacked(Holder holder) { return 0; }
                            static float initializedLocal(float first, float second) {
                                float[] values = {first, second, 7};
                                return values[0] + values[1] + values[2];
                            }
                            static void change(Holder holder) {
                                holder.values = new float[]{17, 19, 23};
                                System.gc();
                            }
                            static float acrossCall(Holder holder) {
                                float before = holder.values[0] + holder.values[1] + holder.values[2];
                                change(holder);
                                return before + holder.values[0] + holder.values[1] + holder.values[2];
                            }
                            static void write(Holder holder) {
                                holder.values[0] = 5;
                                holder.values[1] = 7;
                                holder.values[2] = 11;
                            }
                            static float aliases(Holder holder) {
                                holder.values[0] = holder.values[1];
                                holder.values[1] = holder.values[2];
                                return holder.values[0] + holder.values[1] + holder.values[2];
                            }
                            static void checked(Holder holder) {
                                try { write(holder); System.out.println("ok"); }
                                catch (NullPointerException expected) { System.gc(); System.out.println("null"); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.gc(); System.out.println("bounds"); }
                                if (holder != null) System.out.println(Arrays.toString(holder.values));
                            }
                            public static void main(String[] args) throws Exception {
                                Holder holder = new Holder(new float[]{2, 3, 5});
                                System.out.println(holder.sum() + ":" + sum(holder));
                                System.out.println(initializedLocal(2, 3));
                                System.out.println(optional(null, false));
                                System.out.println(optional(new Holder(null), false));
                                System.out.println(stacked(holder));
                                System.out.println(sum(holder));
                                System.out.println(acrossCall(holder));
                                System.out.println(aliases(holder));
                                checked(null);
                                checked(new Holder(null));
                                for (int i = 0; i <= 3; ++i) checked(new Holder(new float[i]));
                                Thread worker = new Thread(() -> {
                                    holder.values = new float[]{29, 31, 37}; ready = true;
                                });
                                worker.start();
                                while (!ready) { System.gc(); }
                                System.out.println(holder.sum() + ":" + sum(holder));
                                worker.join();
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        replaceStackedLoads(classes.resolve("ArraySnapshots.class"));
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "ArraySnapshots"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("ArraySnapshots")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/ArraySnapshots.cpp"));
        String sum = body(cpp, "sum");
        assertTrue(sum.contains("::jnative::PrimitiveArrayView<float>"), sum);
        assertFalse(sum.contains("::jnative::array_get<float>"), sum);
        String stacked = body(cpp, "stacked");
        assertTrue(stacked.contains("::jnative::PrimitiveArrayView<float>"), stacked);
        assertFalse(stacked.contains("::jnative::array_get<float>"), stacked);
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(buildType == BuildType.RELEASE && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, compiled, expected, 1);
    }

    private static String body(String cpp, String name) {
        var declaration =
                Pattern.compile(
                                "(?m)^\\S[^\\n;]*\\bArraySnapshots::"
                                        + Pattern.quote(name)
                                        + "\\([^\\n;]*\\) \\{")
                        .matcher(cpp);
        assertTrue(declaration.find(), name);
        int end = cpp.indexOf("\n}", declaration.start());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }

    private static void replaceStackedLoads(Path file) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(node, 0);
        var method =
                node.methods.stream()
                        .filter(m -> m.name.equals("stacked"))
                        .findFirst()
                        .orElseThrow();
        method.instructions.clear();
        method.localVariables = null;
        method.tryCatchBlocks.clear();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(
                Opcodes.GETFIELD, "ArraySnapshots$Holder", "values", "[F");
        method.visitInsn(Opcodes.DUP);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.FALOAD);
        method.visitInsn(Opcodes.SWAP);
        method.visitInsn(Opcodes.DUP);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.FALOAD);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "ArraySnapshots",
                "change",
                "(LArraySnapshots$Holder;)V",
                false);
        method.visitInsn(Opcodes.SWAP);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.FALOAD);
        method.visitInsn(Opcodes.FADD);
        method.visitInsn(Opcodes.FADD);
        method.visitInsn(Opcodes.FRETURN);
        method.maxStack = 4;
        method.maxLocals = 1;
        var writer = new ClassWriter(0);
        node.accept(writer);
        Files.write(file, writer.toByteArray());
    }
}
