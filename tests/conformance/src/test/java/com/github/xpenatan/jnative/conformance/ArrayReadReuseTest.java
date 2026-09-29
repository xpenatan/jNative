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

class ArrayReadReuseTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void reusePreservesAliasingIndexChangesPublicationAndExceptions(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("ArrayReads.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class ArrayReads {
                            static volatile int barrier;
                            static int stacked(int[] first, int[] second) { return 0; }
                            static int stackedIndex(int[] values, int index) { return 0; }
                            static float fixed(float[] values) { return values[0] * values[0] + values[0]; }
                            static long indexed(long[] values, int index) { return values[index] + values[index] + values[index]; }
                            static Object reference(Object[] values) { return values[0] == values[0] ? values[0] : null; }
                            static int alias(int[] first, int[] second) {
                                int before = first[0];
                                second[0] = 13;
                                return before + first[0] + first[0];
                            }
                            static int indexChange(int[] values, int index) {
                                int first = values[index];
                                index++;
                                return first + values[index];
                            }
                            static int reassign(int[] first, int[] second) {
                                int old = first[0]; first = second;
                                return old + first[0];
                            }
                            static int acrossVolatile(int[] values) {
                                int before = values[0]; int observed = barrier;
                                return before + observed + values[0];
                            }
                            static void change(int[] values) { System.gc(); values[0] = 17; }
                            static int acrossCall(int[] values) {
                                int before = values[0]; change(values);
                                return before + values[0];
                            }
                            static int loop(int[] values) {
                                int sum = 0;
                                for (int i = 0; i < values.length; ++i) sum += values[i] * values[i] + values[i];
                                return sum;
                            }
                            static int caught(float[] values) {
                                String[] keep = {new String("retained")};
                                try { return (int)fixed(values); }
                                catch (NullPointerException expected) { System.gc(); return keep[0].length(); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.gc(); return -keep[0].length(); }
                            }
                            public static void main(String[] args) throws Exception {
                                System.out.println(fixed(new float[]{2.5f}));
                                System.out.println(indexed(new long[]{Long.MAX_VALUE, 7}, 0));
                                System.out.println(reference(new Object[]{new String("item")}));
                                System.out.println(reference(new Object[]{null}));
                                int[] first = {3, 5, 7}, second = {11, 13, 17};
                                System.out.println(stacked(first, second));
                                System.out.println(stackedIndex(first, 0));
                                System.out.println(alias(first, first));
                                System.out.println(alias(first, second));
                                System.out.println(indexChange(first, 0));
                                System.out.println(reassign(first, second));
                                System.out.println(acrossVolatile(first));
                                System.out.println(acrossCall(first));
                                System.out.println(loop(first));
                                System.out.println(caught(null));
                                System.out.println(caught(new float[0]));
                                Thread worker = new Thread(() -> { first[0] = 23; first[1] = 29; barrier = 1; });
                                worker.start();
                                while (barrier == 0) {}
                                System.out.println(loop(first));
                                worker.join();
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        replaceStackedLoads(classes.resolve("ArrayReads.class"));
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "ArrayReads"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("ArrayReads")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/ArrayReads.cpp"));
        assertEquals(1, scalarLoads(body(cpp, "fixed")), body(cpp, "fixed"));
        assertEquals(1, scalarLoads(body(cpp, "indexed")), body(cpp, "indexed"));
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
                                "(?m)^\\S[^\\n;]*\\bArrayReads::"
                                        + Pattern.quote(name)
                                        + "\\([^\\n;]*\\) \\{")
                        .matcher(cpp);
        assertTrue(declaration.find(), name);
        int start = declaration.start();
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, name);
        return cpp.substring(start, end);
    }

    private static long scalarLoads(String text) {
        return Pattern.compile("::jnative::array_get<|values_elements\\.get\\(")
                .matcher(text)
                .results()
                .count();
    }

    private static void replaceStackedLoads(Path file) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(node, 0);
        for(String name : List.of("stacked", "stackedIndex")) {
            var method =
                    node.methods.stream()
                            .filter(m -> m.name.equals(name))
                            .findFirst()
                            .orElseThrow();
            method.instructions.clear();
            method.localVariables = null;
            method.tryCatchBlocks.clear();
            method.visitVarInsn(Opcodes.ALOAD, 0);
            if(name.equals("stacked")) {
                method.visitVarInsn(Opcodes.ALOAD, 1);
                method.visitVarInsn(Opcodes.ASTORE, 0);
                method.visitInsn(Opcodes.ICONST_0);
            }
            else {
                method.visitVarInsn(Opcodes.ILOAD, 1);
                method.visitInsn(Opcodes.ICONST_1);
                method.visitVarInsn(Opcodes.ISTORE, 1);
            }
            method.visitInsn(Opcodes.IALOAD);
            method.visitVarInsn(Opcodes.ALOAD, 0);
            if(name.equals("stacked")) method.visitInsn(Opcodes.ICONST_0);
            else method.visitVarInsn(Opcodes.ILOAD, 1);
            method.visitInsn(Opcodes.IALOAD);
            method.visitInsn(Opcodes.IADD);
            method.visitInsn(Opcodes.IRETURN);
            method.maxStack = 3;
            method.maxLocals = 2;
        }
        var writer = new ClassWriter(0);
        node.accept(writer);
        Files.write(file, writer.toByteArray());
    }
}
