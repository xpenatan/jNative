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

class RootScopesTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void siblingScopesRetainOuterReferencesDuringCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("RootScopes.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class RootScopes {
                            static class Pair {
                                final String first, second;
                                Pair(String first, String second) {
                                    System.gc(); this.first = first; System.gc(); this.second = second;
                                }
                                String text() { System.gc(); return first + ":" + second; }
                            }
                            static String choose(int value, Pair outer) {
                                String selected;
                                if (value < 0) {
                                    if (value == -1) selected = new Pair(new String("negative"), new String("one")).text();
                                    else selected = new Pair(new String("negative"), new String("other")).text();
                                } else {
                                    if (value == 0) selected = new Pair(new String("zero"), new String("value")).text();
                                    else selected = new Pair(new String("positive"), new String("value")).text();
                                }
                                System.gc();
                                return new Pair(selected, outer.text()).text();
                            }
                            public static void main(String[] args) {
                                Pair outer = new Pair(new String("outer"), new String("alive"));
                                for (int round = 0; round < 3; round++) {
                                    for (int value = -2; value <= 2; value++) System.out.println(choose(value, outer));
                                }
                                try { choose(1, null); }
                                catch (NullPointerException expected) { System.gc(); System.out.println(outer.text()); }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "RootScopes"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("RootScopes")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        Path cpp =
                generated.request().generatedSourcesDirectory().resolve("classes/RootScopes.cpp");
        String generatedText = Files.readString(cpp);
        int start = generatedText.indexOf("\n::jnative::Object* RootScopes::choose(");
        int end = generatedText.indexOf("\n}\n", start);
        assertTrue(start >= 0 && end > start, generatedText);
        String method = generatedText.substring(start, end);
        var slots =
                java.util.regex.Pattern.compile("gc_roots\\.slot\\((\\d+)\\)")
                        .matcher(method)
                        .results()
                        .map(match -> match.group(1))
                        .toList();
        assertTrue(slots.size() > slots.stream().distinct().count(), method);
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
