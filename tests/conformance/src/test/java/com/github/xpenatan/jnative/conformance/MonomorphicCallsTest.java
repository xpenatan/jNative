package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class MonomorphicCallsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void uniqueTargetsIncludeInheritedDefaultsAndReflectiveConstruction(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("Calls.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Calls {
                            interface Single { int value(int argument); }
                            static class Parent implements Single {
                                public int value(int argument) { System.gc(); return argument + 3; }
                            }
                            static class Inherited extends Parent { }
                            interface Default {
                                default int number() { System.gc(); return 7; }
                            }
                            static class FirstDefault implements Default { }
                            static class SecondDefault implements Default { }
                            static class Open {
                                public int value() { return 11; }
                                public final int constantValue() { return 17; }
                            }
                            public static class Reflected extends Open {
                                public Reflected() { }
                                public int value() { System.gc(); return 13; }
                            }
                            interface Generic<T> { T value(); }
                            static class Text implements Generic<String> {
                                public String value() { System.gc(); return new String("text"); }
                            }
                            static int single(Single value) { return value.value(5); }
                            static int inherited(Parent value) { return value.value(6); }
                            static int defaultValue(Default value) { return value.number(); }
                            static int polymorphic(Open value) { return value.value(); }
                            static int finalValue(Open value) { return value.constantValue(); }
                            static Object bridge(Generic<?> value) { return value.value(); }
                            static int argument() { System.out.println("argument"); return 9; }
                            static void absent(Single value) {
                                String kept = new String("retained");
                                try { System.out.println(value.value(argument())); }
                                catch (NullPointerException expected) { System.gc(); System.out.println(kept); }
                            }
                            public static void main(String[] args) throws Exception {
                                Parent parent = new Parent();
                                Parent inherited = new Inherited();
                                System.out.println(single(parent) + ":" + single(inherited));
                                System.out.println(inherited(parent) + ":" + inherited(inherited));
                                System.out.println(defaultValue(new FirstDefault()) + ":" + defaultValue(new SecondDefault()));
                                Open open = new Open();
                                Open reflected = (Open)Class.forName("Calls$Reflected").getConstructor().newInstance();
                                System.out.println(polymorphic(open) + ":" + polymorphic(reflected));
                                System.out.println(finalValue(open) + ":" + finalValue(reflected));
                                System.out.println(bridge(new Text()));
                                absent(null);
                                String[] result = new String[1];
                                Thread worker = new Thread(() -> result[0] = single(inherited) + ":" + bridge(new Text()));
                                worker.start(); worker.join(); System.out.println(result[0]);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Calls"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Calls")
                        .reflectClass("Calls$Reflected")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/Calls.cpp"));
        assertTrue(body(cpp, "single").contains("->Calls_Parent::"), body(cpp, "single"));
        assertTrue(body(cpp, "inherited").contains("->Calls_Parent::"), body(cpp, "inherited"));
        assertTrue(
                body(cpp, "defaultValue").contains("Calls_Default::"), body(cpp, "defaultValue"));
        assertFalse(body(cpp, "polymorphic").contains("->Calls_Open::"), body(cpp, "polymorphic"));
        assertTrue(body(cpp, "finalValue").contains("->Calls_Open::"), body(cpp, "finalValue"));
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
                java.util.regex.Pattern.compile(
                                "(?m)^\\S[^\\n;]*\\bCalls::"
                                        + java.util.regex.Pattern.quote(name)
                                        + "\\([^\\n;]*\\) \\{")
                        .matcher(cpp);
        assertTrue(declaration.find(), name);
        int start = declaration.start();
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, name);
        return cpp.substring(start, end);
    }
}
