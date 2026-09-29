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

class FieldReadReuseTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void reuseStopsAtWritesCallsVolatilesAndReceiverChanges(BuildType buildType) throws Exception {
        Path source = temporary.resolve("FieldReuse.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class FieldReuse {
                            static class Box {
                                int number = 3;
                                float scalar = 2.5f;
                                Box child;
                                volatile int barrier;
                            }
                            static int repeated(Box value) { return value.number * value.number + value.number; }
                            static int stacked(Box first, Box second) { return 0; }
                            static float floating(Box value) { return value.scalar * value.scalar + value.scalar; }
                            static int references(Box value) { return value.child.number + value.child.number; }
                            static int aliasWrite(Box first, Box second) {
                                int old = first.number;
                                second.number = 11;
                                return old + first.number + first.number;
                            }
                            static int reassign(Box first, Box second) {
                                int old = first.number;
                                first = second;
                                return old + first.number;
                            }
                            static void mutate(Box value) { System.gc(); value.number = 23; }
                            static int calling(Box value) {
                                int before = value.number;
                                mutate(value);
                                return before + value.number;
                            }
                            static int volatileReads(Box value) { return value.barrier + value.barrier; }
                            static int acrossVolatile(Box value) {
                                int before = value.number;
                                int observed = value.barrier;
                                return before + observed + value.number;
                            }
                            static int acrossMonitor(Box value) {
                                int before = value.number;
                                synchronized(value) { value.number = 29; }
                                return before + value.number;
                            }
                            static int joined(Box first, Box second, boolean choose) {
                                int old = first.number;
                                if (choose) first = second;
                                return old + first.number;
                            }
                            static int caught(Box value) {
                                Box keep = new Box();
                                try { return references(value); }
                                catch (NullPointerException expected) { System.gc(); return keep.number; }
                            }
                            public static void main(String[] args) throws Exception {
                                Box first = new Box(), second = new Box();
                                second.number = 5;
                                first.child = second;
                                System.out.println(stacked(first, second));
                                System.out.println(repeated(first));
                                System.out.println(floating(first));
                                System.out.println(references(first));
                                System.out.println(aliasWrite(first, first));
                                System.out.println(aliasWrite(first, second));
                                System.out.println(reassign(first, second));
                                System.out.println(calling(first));
                                System.out.println(volatileReads(first));
                                System.out.println(acrossVolatile(first));
                                System.out.println(acrossMonitor(first));
                                System.out.println(joined(first, second, true));
                                System.out.println(joined(first, second, false));
                                System.out.println(caught(null));
                                System.out.println(caught(new Box()));
                                Thread worker = new Thread(() -> {
                                    first.number = 37;
                                    first.child = new Box();
                                    first.child.number = 41;
                                    first.barrier = 1;
                                });
                                worker.start();
                                while (first.barrier == 0) {}
                                System.out.println(repeated(first) + ":" + references(first));
                                worker.join();
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        replaceStackedReceiver(classes.resolve("FieldReuse.class"));
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "FieldReuse"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("FieldReuse")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/FieldReuse.cpp"));
        assertEquals(1, occurrences(body(cpp, "repeated"), "->number.get()"));
        assertEquals(1, occurrences(body(cpp, "floating"), "->scalar.get()"));
        assertEquals(1, occurrences(body(cpp, "references"), "->child.get()"));
        assertEquals(2, occurrences(body(cpp, "calling"), "->number.get()"));
        assertEquals(2, occurrences(body(cpp, "volatileReads"), "->barrier.get()"));
        assertEquals(2, occurrences(body(cpp, "acrossVolatile"), "->number.get()"));
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
                                "(?m)^\\S[^\\n;]*\\bFieldReuse::"
                                        + Pattern.quote(name)
                                        + "\\([^\\n;]*\\) \\{")
                        .matcher(cpp);
        assertTrue(declaration.find(), name);
        int start = declaration.start();
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, name);
        return cpp.substring(start, end);
    }

    private static int occurrences(String text, String value) {
        return text.split(Pattern.quote(value), -1).length - 1;
    }

    private static void replaceStackedReceiver(Path file) throws Exception {
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
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitVarInsn(Opcodes.ASTORE, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, "FieldReuse$Box", "number", "I");
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, "FieldReuse$Box", "number", "I");
        method.visitInsn(Opcodes.IADD);
        method.visitInsn(Opcodes.IRETURN);
        method.maxStack = 2;
        method.maxLocals = 2;
        var writer = new ClassWriter(0);
        node.accept(writer);
        Files.write(file, writer.toByteArray());
    }
}
