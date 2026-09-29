package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuilder;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class FinalDispatchTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void finalClassesAndOpenHierarchiesPreserveDispatch(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Dispatch.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Dispatch {
                            interface Value { int value(); }
                            static class Base implements Value { public int value() { return 1; } }
                            static final class Leaf extends Base { public int value() { return 2; } }
                            static class Open extends Base { public int value() { return 3; } }
                            static class Derived extends Open { public int value() { return super.value() + 1; } }
                            enum Choice implements Value {
                                FIRST { public int value() { return 5; } },
                                SECOND { public int value() { return 6; } }
                            }
                            static int read(Value value) { System.gc(); return value.value(); }
                            public static void main(String[] args) {
                                Value[] values = {new Leaf(), new Derived(), Choice.FIRST, Choice.SECOND};
                                for (Value value : values) System.out.println(read(value));
                                Base base = new Derived();
                                System.out.println(base.value());
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Dispatch"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Dispatch")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        Path headers = generated.request().generatedSourcesDirectory().resolve("classes");
        assertTrue(
                Files.readString(headers.resolve("Dispatch_Leaf.hpp"))
                        .contains("struct Dispatch_Leaf final :"));
        assertFalse(
                Files.readString(headers.resolve("Dispatch_Open.hpp"))
                        .contains("struct Dispatch_Open final :"));
        assertFalse(
                Files.readString(headers.resolve("Dispatch_Choice.hpp"))
                        .contains("struct Dispatch_Choice final :"));
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "2")));
    }
}
