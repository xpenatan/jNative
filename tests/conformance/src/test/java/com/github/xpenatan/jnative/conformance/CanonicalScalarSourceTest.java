package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.StackTraceMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CanonicalScalarSourceTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void structuresCountedLoopsAndDeclaresScalarsAtTheirFirstAssignment(BuildType buildType) throws Exception {
        Path source = temporary.resolve("CanonicalScalars.java");
        Files.writeString(source, """
                public class CanonicalScalars {
                    static int limitReads;

                    static int up(int from, int to, int seed) {
                        int weight = seed * 3;
                        int sum = 0;
                        for (int i = from; i < to; ++i) {
                            if ((i & 1) == 0) continue;
                            sum += i ^ weight;
                        }
                        return sum;
                    }

                    static int down(int from, int to) {
                        int count = 0;
                        for (int i = from; to < i; --i) count++;
                        return count;
                    }

                    static int nested(int width, int height) {
                        int count = 0;
                        for (int row = 0; row < height; ++row)
                            for (int column = 0; column < width; ++column)
                                count += row ^ column;
                        return count;
                    }

                    static int wrapUp(int i) {
                        for (; i <= Integer.MAX_VALUE; ++i) {
                            if (i < 0) return i;
                        }
                        return 0;
                    }

                    static int wrapDown(int i) {
                        for (; i >= Integer.MIN_VALUE; --i) {
                            if (i > 0) return i;
                        }
                        return 0;
                    }

                    static int skipUpdate() {
                        int i = 0, visits = 0;
                        while (i < 3) {
                            if (visits++ == 0) continue;
                            ++i;
                        }
                        return visits;
                    }

                    static int limit(int n) {
                        limitReads++;
                        return n;
                    }

                    static int changingHead(int n) {
                        int count = 0;
                        for (int i = 0; i < limit(n); ++i) count++;
                        return count;
                    }

                    static int guardedArray(int[] values, int start) {
                        int sum = 0;
                        for (int i = start; i < values.length; ++i) sum += values[i];
                        return sum;
                    }

                    public static void main(String[] args) {
                        System.out.println(up(-3, 7, 4));
                        System.out.println(up(4, 4, -1));
                        System.out.println(up(Integer.MAX_VALUE - 2, Integer.MAX_VALUE, 2));
                        System.out.println(down(Integer.MIN_VALUE + 2, Integer.MIN_VALUE));
                        System.out.println(nested(3, 4));
                        System.out.println(wrapUp(Integer.MAX_VALUE));
                        System.out.println(wrapDown(Integer.MIN_VALUE));
                        System.out.println(skipUpdate());
                        System.out.println(changingHead(4));
                        System.out.println(changingHead(0));
                        System.out.println(limitReads);
                        System.out.println(guardedArray(new int[] {2, 3, 5, 7}, 1));
                        try {
                            guardedArray(new int[] {2, 3}, -1);
                        } catch (ArrayIndexOutOfBoundsException expected) {
                            System.out.println("checked");
                        }
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "CanonicalScalars"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());

        boolean sourceLocations = buildType == BuildType.DEBUG;
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("CanonicalScalars")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .stackTraces(StackTraceMode.JAVA).javaSourceLocations(sourceLocations);
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(),
                "CanonicalScalars");
        String ascending = body(cpp, "up");
        assertTrue(ascending.contains("const std::int32_t weight = "), ascending);
        assertTrue(ascending.contains("std::int32_t sum = 0;"), ascending);
        assertTrue(ascending.contains("++i"), ascending);
        assertTrue(body(cpp, "down").contains("--i"), cpp);
        assertTrue(body(cpp, "wrapUp").contains("::jnative::add(i, 1)"), cpp);
        assertTrue(body(cpp, "wrapDown").contains("::jnative::add(i, -1)"), cpp);
        assertFalse(body(cpp, "skipUpdate").contains("for ("), cpp);
        if (sourceLocations) {
            assertTrue(ascending.contains("java_frame.line = "), ascending);
            assertFalse(ascending.contains("for ("), ascending);
        } else {
            assertTrue(body(cpp, "nested").contains("for (; column < width; ++column)"), cpp);
            assertTrue(body(cpp, "guardedArray").contains("for (; i < chunk_end; ++i)"), cpp);
        }

        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* CanonicalScalars::" + name + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }
}
