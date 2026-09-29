package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class ReadableSourceTest {
    @TempDir
    Path temporary;

    @Test
    void binaryNamesRemainDistinctAndPortableWithoutDebugTables() throws Exception {
        Path jar = temporary.resolve("names.jar");
        List<String> owners =
                List.of(
                        "CON",
                        "same",
                        "Same",
                        "data/one$Two",
                        "data/one_Two",
                        "namespace/template",
                        "EOF",
                        "NULL");
        try(var output = new JarOutputStream(Files.newOutputStream(jar))) {
            for(int i = 0; i < owners.size(); ++i) {
                var writer =
                        new ClassWriter(
                                ClassWriter.COMPUTE_MAXS);
                writer.visit(
                        61,
                        Opcodes.ACC_PUBLIC,
                        owners.get(i),
                        null,
                        "java/lang/Object",
                        null);
                var value =
                        writer.visitMethod(
                                Opcodes.ACC_PUBLIC
                                        | Opcodes.ACC_STATIC,
                                "template",
                                "()I",
                                null,
                                null);
                value.visitCode();
                value.visitLdcInsn(i + 1);
                value.visitInsn(Opcodes.IRETURN);
                value.visitMaxs(0, 0);
                value.visitEnd();
                writer.visitEnd();
                output.putNextEntry(new JarEntry(owners.get(i) + ".class"));
                output.write(writer.toByteArray());
                output.closeEntry();
            }
            var writer =
                    new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(
                    61,
                    Opcodes.ACC_PUBLIC,
                    "Names",
                    null,
                    "java/lang/Object",
                    null);
            var main =
                    writer.visitMethod(
                            Opcodes.ACC_PUBLIC
                                    | Opcodes.ACC_STATIC,
                            "main",
                            "([Ljava/lang/String;)V",
                            null,
                            null);
            main.visitCode();
            for(String owner : owners) {
                main.visitFieldInsn(
                        Opcodes.GETSTATIC,
                        "java/lang/System",
                        "out",
                        "Ljava/io/PrintStream;");
                main.visitMethodInsn(
                        Opcodes.INVOKESTATIC, owner, "template", "()I", false);
                main.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/io/PrintStream",
                        "println",
                        "(I)V",
                        false);
            }
            main.visitInsn(Opcodes.RETURN);
            main.visitMaxs(0, 0);
            main.visitEnd();
            writer.visitEnd();
            output.putNextEntry(new JarEntry("Names.class"));
            output.write(writer.toByteArray());
            output.closeEntry();
        }
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", jar.toString(), "Names"),
                        Duration.ofSeconds(20));
        var result =
                NativeBuilder.create()
                        .classpath(jar)
                        .mainClass("Names")
                        .buildRoot(temporary.resolve("output"))
                        .build();
        assertEquals(new ProcessHarness.Output(0, "1\n2\n3\n4\n5\n6\n7\n8\n"), expected);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(20)));
        String report =
                Files.readString(
                        result.generation()
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("source-readability.tsv"));
        assertFalse(report.contains("low-level"), report);
    }

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void editableMethodsPreserveEvaluationOrderRootsAndControlFlow(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("Readable.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        package example;
                        public class Readable {
                            static int order;
                            static class Player {
                                String name;
                                volatile int health;
                                Player(String name, int health) { this.name = name; this.health = health; }
                                synchronized int damage(int amount) {
                                    if (amount < 0) throw new IllegalArgumentException("damage cannot be negative");
                                    System.gc();
                                    health -= amount;
                                    return health;
                                }
                                String describe() { return name + ": " + health; }
                            }
                            static int tick(int digit) {
                                order = order * 10 + digit;
                                System.gc();
                                return digit;
                            }
                            static int combine(int a, int b) { return a * 10 + b; }
                            static int arguments() { return combine(tick(1), tick(2)); }
                            static int locals() { int value = 3; return value++ + value; }
                            static int choose(int amount) {
                                int result;
                                if (amount < 0) result = 7;
                                else result = amount + 9;
                                return result;
                            }
                            static int loops(int count) {
                                int result = 0;
                                for (int i = 0; i < count; ++i) {
                                    int j = 0;
                                    while (j < 3) { result += i + j; ++j; }
                                }
                                return result;
                            }
                            static int countdown(int count) {
                                int result = 0;
                                while (count > 0) {
                                    --count;
                                    if (count == 2) continue;
                                    if (count == 1) break;
                                    result += count;
                                }
                                return result;
                            }
                            static void nullStore() { Player missing = null; missing.health = tick(3); }
                            static int references(Player first, Player second) {
                                return compare(first, first = second);
                            }
                            static int compare(Player first, Player second) {
                                System.gc();
                                return first.health * 10 + second.health;
                            }
                            static int overload(Object value) { return 1; }
                            static int overload(String value) { return 2; }
                            static int overload(byte value) { return 3; }
                            static int overload(int value) { return 4; }
                            static String unicode() { return "a\\u0000b\\ud800\\n\\\"\\\\"; }
                            public static void main(String[] args) {
                                Player player = new Player("Ada", 100);
                                System.out.println(player.damage(25));
                                System.out.println(player.describe());
                                try { player.damage(-1); } catch (IllegalArgumentException e) { System.out.println(e.getMessage()); }
                                System.out.println(arguments());
                                System.out.println(order);
                                System.out.println(locals());
                                System.out.println(choose(-1));
                                System.out.println(choose(2));
                                System.out.println(loops(5));
                                System.out.println(countdown(6));
                                try { nullStore(); } catch (NullPointerException e) { System.out.println(order); }
                                System.out.println(references(new Player("one", 1), new Player("two", 2)));
                                System.out.println(overload((Object)"x") + overload("x") + overload((byte)1) + overload(1));
                                String text = unicode();
                                System.out.println(text.length());
                                System.out.println((int)text.charAt(3));
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, buildType == BuildType.DEBUG ? 17 : 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(
                                ProcessHarness.java(),
                                "-cp",
                                classes.toString(),
                                "example.Readable"),
                        Duration.ofSeconds(20));
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("example.Readable")
                        .sourceLayout(SourceLayout.PACKAGE_DIRECTORIES)
                        .buildRoot(temporary.resolve("generated"))
                        .buildType(buildType)
                        .debugInformation(true);
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        Path playerFile =
                generation.generatedFiles().stream()
                        .filter(p -> p.toString().endsWith("Player.cpp"))
                        .findFirst()
                        .orElseThrow();
        String player = Files.readString(playerFile);
        assertTrue(player.contains("::damage(std::int32_t amount)"), player);
        assertTrue(player.contains("if (amount < 0)"), player);
        assertTrue(player.contains("this->health.set("), player);
        assertTrue(player.contains("\"damage cannot be negative\""), player);
        assertFalse(
                player.contains("stack0") || player.contains("goto ") || player.contains("block_"),
                player);
        assertFalse(player.contains("Invalid fallthrough"), player);
        assertTrue(player.lines().count() < 80, player);
        String application = Files.readString(generated.resolve("classes/example/Readable.cpp"));
        assertTrue(application.contains("while ("), application);
        assertTrue(application.contains("} else {"), application);
        String report = Files.readString(generated.resolve("source-readability.tsv"));
        assertTrue(report.contains("Readable.main([Ljava/lang/String;)V\tstructured"), report);
        assertTrue(report.contains("Readable.loops(I)I\tstructured"), report);
        assertTrue(report.contains("Readable.countdown(I)I\tstructured"), report);
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
}
