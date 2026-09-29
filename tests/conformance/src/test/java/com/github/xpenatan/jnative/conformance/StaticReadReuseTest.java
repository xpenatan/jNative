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

class StaticReadReuseTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void ordinaryStaticsReuseReadsWithoutCrossingEffects(BuildType buildType) throws Exception {
        Path source = temporary.resolve("StaticReuse.java"), classes = temporary.resolve("classes");
        Files.writeString(source, """
                public class StaticReuse {
                    static class Box { int number = 3; }
                    static Box box = new Box();
                    static int scalar = 3;
                    static int[][] table = {{5, 7}, {11, 13}};
                    static volatile int published;
                    static volatile int signal;
                    static class Other { static int number = 17; }
                    static class Parent {
                        static int number = initialize();
                        static int initialize() { System.gc(); return 107; }
                    }
                    static class Child extends Parent {}
                    static class Lazy {
                        static int trigger = initialize();
                        static int initialize() {
                            box.number = 31;
                            table[0][0] = 37;
                            table[0] = new int[]{41, 43};
                            System.gc();
                            return 47;
                        }
                    }
                    static class Reentrant {
                        static int scalar = 3;
                        static int value = initialize();
                        static int repeated() { return scalar + scalar; }
                        static int initialize() { System.gc(); return repeated() + repeated(); }
                    }
                    static class Failing {
                        static int trigger = initialize();
                        static int initialize() {
                            box.number = 53;
                            table[0] = new int[]{59};
                            System.gc();
                            throw new IllegalStateException("failed");
                        }
                    }
                    static int repeated() { return scalar * scalar + scalar; }
                    static int acrossOwn(Box value) { return value.number + scalar + value.number; }
                    static int tableReads() {
                        return table[0][0] + scalar + table[0][1] + table[0][0];
                    }
                    static int other() {
                        return Other.number + box.number + Other.number + box.number;
                    }
                    static int inherited() { return Child.number + Parent.number + Child.number; }
                    static int initializingRead() {
                        return box.number + table[0][0] + Lazy.trigger + box.number + table[0][0];
                    }
                    static int failing() {
                        return box.number + table[0][0] + Failing.trigger + box.number + table[0][0];
                    }
                    static int volatileReads() { return published + published; }
                    static int unusedAcquire() { return 0; }
                    static int acquire() {
                        return box.number + table[0][0] + published + box.number + table[0][0];
                    }
                    static int writeScalar() {
                        int before = scalar;
                        scalar = 19;
                        return before + scalar + scalar;
                    }
                    static int writeTable(int[][] replacement) {
                        int before = table[0][0];
                        table = replacement;
                        return before + table[0][0];
                    }
                    static void mutate() {
                        box.number = 61;
                        table[0] = new int[]{67};
                        System.gc();
                    }
                    static int calling() {
                        int before = box.number + table[0][0];
                        mutate();
                        return before + box.number + table[0][0];
                    }
                    static int synchronizedRead() {
                        int before = box.number + table[0][0];
                        synchronized(box) { box.number = 71; table[0] = new int[]{73}; }
                        return before + box.number + table[0][0];
                    }
                    static int aliasStore(int[] output) {
                        output[0] = table[0][0] + 1;
                        return table[0][0] + scalar + table[0][0];
                    }
                    static void copy(int[] output) {
                        output[0] = table[0][0] + scalar;
                        output[1] = table[1][0] + scalar;
                    }
                    static void attempt(int[][] input, int[] output) {
                        table = input;
                        try { copy(output); System.out.println("copied"); }
                        catch (NullPointerException expected) { System.out.println("null"); }
                        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
                        System.gc();
                        if (output != null) for (int value : output) System.out.println(value);
                    }
                    public static void main(String[] args) throws Exception {
                        System.out.println(repeated());
                        System.out.println(acrossOwn(box));
                        System.out.println(tableReads());
                        System.out.println(other());
                        System.out.println(other());
                        System.out.println(inherited());
                        System.out.println(initializingRead());
                        System.out.println(Reentrant.value);
                        for (int i = 0; i < 2; i++) {
                            try { System.out.println(failing()); }
                            catch (ExceptionInInitializerError expected) { System.out.println("initializer"); }
                            catch (NoClassDefFoundError expected) { System.out.println("failed class"); }
                            System.out.println(box.number + table[0][0]);
                        }
                        System.out.println(writeScalar());
                        System.out.println(writeTable(new int[][]{{79}, {83}}));
                        System.out.println(calling());
                        System.out.println(synchronizedRead());
                        System.out.println(aliasStore(table[0]));
                        System.out.println(aliasStore(new int[]{-1}));
                        System.out.println(table[0][0]);
                        Thread worker = new Thread(() -> {
                            while (signal == 0) Thread.yield();
                            box.number = 89;
                            table[0] = new int[]{97};
                            System.gc();
                            published = 1;
                        });
                        worker.start();
                        signal = 1;
                        while (published == 0) Thread.yield();
                        System.out.println(volatileReads());
                        System.out.println(unusedAcquire());
                        System.out.println(acquire());
                        worker.join();
                        attempt(new int[][]{{101}, {103}}, new int[]{-1, -1});
                        attempt(new int[][]{{101}, null}, new int[]{-1, -1});
                        attempt(new int[][]{{101}, {}}, new int[]{-1, -1});
                        attempt(new int[][]{{101}}, new int[]{-1, -1});
                        attempt(new int[][]{null, {103}}, new int[]{-1, -1});
                        attempt(new int[][]{}, new int[]{-1, -1});
                        attempt(null, new int[]{-1, -1});
                        attempt(new int[][]{{101}, {103}}, new int[]{-1});
                        attempt(new int[][]{{101}, null}, new int[]{-1});
                        attempt(new int[][]{{101}, {103}}, null);
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 25);
        replaceUnusedAcquire(classes.resolve("StaticReuse.class"));
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "StaticReuse"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("StaticReuse")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generated = builder.generate();
        Path generatedSources = generated.request().generatedSourcesDirectory();
        String cpp = ProcessHarness.generatedClassSource(generatedSources, "StaticReuse");
        assertOccurrences(cpp, "repeated", "::scalar.get()", 1);
        assertOccurrences(cpp, "acrossOwn", "->number.get()", 1);
        assertOccurrences(cpp, "tableReads", "::table.get()", 1);
        assertOccurrences(cpp, "tableReads", "::jnative::reference_get(", 1);
        assertOccurrences(cpp, "other", "::number.get()", 1);
        assertOccurrences(cpp, "other", "->number.get()", 1);
        assertOccurrences(cpp, "other", "StaticReuse::ensure_initialized()", 1);
        assertOccurrences(cpp, "other", "StaticReuse_Other::ensure_initialized()", 1);
        assertOccurrences(cpp, "inherited", "::number.get()", 1);
        assertOccurrences(cpp, "inherited", "StaticReuse::ensure_initialized()", 1);
        assertOccurrences(cpp, "inherited", "StaticReuse_Parent::ensure_initialized()", 1);
        assertOccurrences(cpp, "inherited", "StaticReuse_Child::ensure_initialized()", 0);
        for (String method : List.of("initializingRead", "failing", "acquire", "calling", "synchronizedRead")) {
            assertOccurrences(cpp, method, "->number.get()", 2);
            assertOccurrences(cpp, method, "::jnative::reference_get(", 2);
        }
        assertOccurrences(cpp, "volatileReads", "::published.get()", 2);
        assertOccurrences(cpp, "unusedAcquire", "::published.get()", 1);
        assertOccurrences(cpp, "unusedAcquire", "::table.get()", 2);
        assertOccurrences(cpp, "unusedAcquire", "::jnative::reference_get(", 2);
        assertOccurrences(cpp, "writeScalar", "::scalar.get()", 2);
        assertOccurrences(cpp, "writeTable", "::table.get()", 2);
        assertOccurrences(cpp, "aliasStore", "::table.get()", 1);
        assertOccurrences(cpp, "aliasStore", "::jnative::reference_get(", 1);
        assertOccurrences(cpp, "copy", "::table.get()", 1);
        String reentrant = ProcessHarness.generatedClassSource(generatedSources, "StaticReuse$Reentrant");
        assertOccurrences(reentrant, "repeated", "::scalar.get()", 1);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String method) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n;]*::" + Pattern.quote(method)
                + "\\([^\\n;]*\\) \\{").matcher(cpp);
        assertTrue(declaration.find(), method);
        int end = cpp.indexOf("\n}", declaration.start());
        assertTrue(end > declaration.start(), method);
        return cpp.substring(declaration.start(), end);
    }

    private static int occurrences(String text, String value) {
        return text.split(Pattern.quote(value), -1).length - 1;
    }

    private static void assertOccurrences(String cpp, String method, String value, int expected) {
        String body = body(cpp, method);
        assertEquals(expected, occurrences(body, value), method + ": " + value + "\n" + body);
    }

    private static void replaceUnusedAcquire(Path file) throws Exception {
        var node = new ClassNode();
        new ClassReader(Files.readAllBytes(file)).accept(node, 0);
        var method = node.methods.stream().filter(m -> m.name.equals("unusedAcquire"))
                .findFirst().orElseThrow();
        method.instructions.clear();
        method.localVariables = null;
        method.tryCatchBlocks.clear();
        method.visitFieldInsn(Opcodes.GETSTATIC, "StaticReuse", "table", "[[I");
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.AALOAD);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IALOAD);
        method.visitFieldInsn(Opcodes.GETSTATIC, "StaticReuse", "published", "I");
        method.visitInsn(Opcodes.POP);
        method.visitFieldInsn(Opcodes.GETSTATIC, "StaticReuse", "table", "[[I");
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.AALOAD);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IALOAD);
        method.visitInsn(Opcodes.IADD);
        method.visitInsn(Opcodes.IRETURN);
        method.maxStack = 3;
        method.maxLocals = 0;
        var writer = new ClassWriter(0);
        node.accept(writer);
        Files.write(file, writer.toByteArray());
    }
}
