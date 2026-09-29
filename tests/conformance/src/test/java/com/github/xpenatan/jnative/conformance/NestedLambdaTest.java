package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class NestedLambdaTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(SourceLayout.class)
    void lambdasShareEnclosingFilesAndRetainNativeBehavior(SourceLayout layout) throws Exception {
        String owner = "Enclosing" + "LongName".repeat(3);
        Path source = temporary.resolve(owner + ".java"), classes = temporary.resolve("classes");
        Files.writeString(source, """
                package game;
                import java.util.function.*;
                public class OWNER {
                    static int Lambda0 = 10;
                    static int Lambda1() { return 20; }
                    static class Lambda2 { static int value() { return 30; } }
                    interface Collision { void Lambda7(); }
                    interface IntValue { int value(); }
                    static class Box {
                        int value;
                        Box(int value) { this.value = value; }
                        int read() { System.gc(); return value; }
                        IntValue deferred() { return () -> value; }
                    }
                    static long twice(int value) { return (long)value * 2; }
                    public static void main(String[] args) {
                        int offset = Lambda0;
                        IntUnaryOperator capturing = value -> value + offset;
                        IntValue plain = () -> Lambda1();
                        IntFunction<Box> constructor = Box::new;
                        Function<Box,Integer> unbound = Box::read;
                        IntValue bound = new Box(Lambda2.value())::read;
                        Function<Integer,Long> widen = OWNER::twice;
                        String text = new String("retained");
                        Supplier<String> reference = () -> text;
                        Collision collision = () -> { Lambda0++; };
                        collision.Lambda7();
                        System.gc();
                        System.out.println(capturing.applyAsInt(7));
                        System.out.println(plain.value());
                        System.out.println(unbound.apply(constructor.apply(42)));
                        System.out.println(bound.value());
                        System.out.println(widen.apply(9));
                        System.out.println(reference.get());
                        System.out.println(new Box(51).deferred().value());
                    }
                }
                """.replace("OWNER", owner));
        ProcessHarness.javac(source, classes, 17);
        var built = ProcessHarness.behavioralBuilder()
                .classpath(classes).mainClass("game." + owner)
                .buildRoot(temporary.resolve("output")).sourceLayout(layout)
                .buildType(BuildType.RELEASE).timeout(Duration.ofMinutes(3)).build();
        Path generated = built.generation().request().generatedSourcesDirectory();
        var map = Json.object(Json.read(Files.readString(generated.resolve("source-map.json"))));
        Map<String, Map<String, Object>> entries = new HashMap<>();
        for(Object raw : Json.array(map.get("classes"))) {
            var entry = Json.object(raw);
            entries.put(entry.get("javaInternalName").toString(), entry);
        }
        int lambdas = 0;
        Set<String> symbols = new HashSet<>();
        Set<String> files = new HashSet<>();
        for(var entry : entries.values()) {
            assertTrue(symbols.add(entry.get("cppClass").toString()));
            String cppFile = entry.get("cppFile").toString();
            files.add(cppFile);
            var input = Json.object(entry.get("input"));
            String parent = input.get("generatedFrom").toString();
            if(parent.isEmpty()) continue;
            lambdas++;
            var enclosing = entries.get(parent);
            assertEquals(enclosing.get("cppFile"), entry.get("cppFile"));
            assertEquals(enclosing.get("headerFile"), entry.get("headerFile"));
            String qualified = entry.get("cppClass").toString();
            assertTrue(qualified.startsWith(enclosing.get("cppClass") + "::Lambda"));
            if(parent.equals("game/" + owner))
                assertFalse(Set.of("Lambda0", "Lambda1", "Lambda2", "Lambda7")
                        .contains(qualified.substring(qualified.lastIndexOf("::") + 2)));
            String header = Files.readString(generated.resolve(entry.get("headerFile").toString()));
            assertTrue(header.contains("    struct " + qualified.substring(qualified.lastIndexOf("::") + 2) + ";"));
            List<String> lines = Files.readAllLines(generated.resolve(cppFile));
            for(Object raw : Json.array(entry.get("methods"))) {
                var method = Json.object(raw);
                int start = ((Number)method.get("cppLineStart")).intValue();
                int end = ((Number)method.get("cppLineEnd")).intValue();
                assertTrue(start <= end && end <= lines.size());
                assertTrue(String.join("\n", lines.subList(start - 1, end))
                        .contains("::" + method.get("cppMethod") + "("));
            }
        }
        assertEquals(9, lambdas);
        try(var paths = Files.walk(generated.resolve("classes"))) {
            assertEquals(files.size(), paths.filter(path -> path.toString().endsWith(".cpp")).count());
        }
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "game." + owner),
                Duration.ofSeconds(30));
        assertEquals(new ProcessHarness.Output(0, "17\n20\n42\n30\n18\nretained\n51\n"), expected);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(built.executable().toString()),
                Duration.ofSeconds(30), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
