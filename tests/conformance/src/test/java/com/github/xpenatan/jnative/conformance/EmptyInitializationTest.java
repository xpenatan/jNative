package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import static org.junit.jupiter.api.Assertions.*;

class EmptyInitializationTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void preservesInheritedInitializationRootsAndConstantStrings(BuildType buildType) throws Exception {
        Path source = temporary.resolve("EmptyInitialization.java");
        Files.writeString(source, """
                public class EmptyInitialization {
                    static int mark(String text) { System.gc(); System.out.println(text); return 7; }
                    static class Plain { static Object pass(Object value) { return value; } }
                    static class Parent { static int value = mark("parent"); }
                    static class Child extends Parent { static String take(String value) { return value; } }
                    interface DefaultParent {
                        int VALUE = mark("default");
                        default int value() { return VALUE; }
                    }
                    interface Indirect extends DefaultParent { }
                    static class DefaultChild implements Indirect {
                        static String take(String value) { return value; }
                    }
                    interface Marker { int VALUE = mark("marker"); }
                    static class MarkerChild implements Marker { }
                    static class Text {
                        static final String TEXT = "constant string";
                        static String read() { return TEXT; }
                    }
                    public static void main(String[] args) {
                        System.out.println(Plain.pass(new String("plain")));
                        System.out.println(Child.take(new String("kept through parent")));
                        System.out.println(DefaultChild.take(new String("kept through interface")));
                        new MarkerChild();
                        System.out.println("marker still uninitialized");
                        System.out.println(Text.read());
                        System.out.println(Marker.VALUE);
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        // Keep a legal ConstantValue GETSTATIC instead of javac's literal folding.
        Path textClass = classes.resolve("EmptyInitialization$Text.class");
        var text = new ClassNode();
        new ClassReader(Files.readAllBytes(textClass)).accept(text, 0);
        for (var method : text.methods) if (method.name.equals("read")) {
            for (var instruction : method.instructions.toArray()) if (instruction instanceof LdcInsnNode)
                method.instructions.set(instruction, new FieldInsnNode(Opcodes.GETSTATIC,
                        text.name, "TEXT", "Ljava/lang/String;"));
        }
        var writer = new ClassWriter(0);
        text.accept(writer);
        Files.write(textClass, writer.toByteArray());
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "EmptyInitialization"), Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("EmptyInitialization")
                .buildRoot(temporary.resolve("out")).buildType(buildType).timeout(Duration.ofMinutes(5));
        var generated = builder.generate();
        String plain = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(), "EmptyInitialization$Plain");
        assertFalse(plain.contains("initialization_roots"), plain);
        var result = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
