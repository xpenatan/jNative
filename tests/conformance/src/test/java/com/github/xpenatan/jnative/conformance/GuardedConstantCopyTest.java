package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class GuardedConstantCopyTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void preservesShortCopiesOverlapAndExceptions(BuildType buildType) throws Exception {
        Path source = temporary.resolve("GuardedCopies.java");
        Files.writeString(source, """
                import java.util.Arrays;

                public class GuardedCopies {
                    static final class Batch {
                        final float[] target = new float[80];
                        int cursor;

                        void draw(float[] source, int remaining, int from, int count) {
                            int length = Math.min(remaining, count);
                            System.arraycopy(source, from, target, cursor, length);
                            cursor += length;
                        }

                        void conflicting(float[] source, int count) {
                            System.arraycopy(source, 0, target, 0, Math.min(target.length, count));
                        }
                    }

                    static void run(int remaining, int count, int from, int to, int sourceMode) {
                        Batch batch = new Batch();
                        float[] input = new float[80];
                        for (int i = 0; i < input.length; i++) {
                            input[i] = i + 1;
                            batch.target[i] = -i - 1;
                        }
                        if (sourceMode == 1) input = batch.target;
                        if (sourceMode == 2) input = null;
                        if (sourceMode == 3) input = new float[3];
                        batch.cursor = to;
                        try {
                            if (count == 20 && from == 0) batch.draw(input, remaining, 0, 20);
                            else if (count == 20) batch.draw(input, remaining, from, 20);
                            else batch.draw(input, remaining, from, count);
                            System.out.println("copied");
                        } catch (RuntimeException error) {
                            System.out.println(error.getClass().getName());
                        }
                        System.out.println(batch.cursor + ":" + Arrays.toString(batch.target));
                    }

                    public static void main(String[] args) {
                        run(80, 20, 3, 4, 0);
                        run(7, 20, 3, 73, 0);
                        run(80, 20, 3, 4, 1);
                        run(80, 20, 4, 3, 1);
                        run(80, 20, 0, 1, 1);
                        run(80, 20, 0, 20, 1);
                        run(80, 20, 0, 20, 0);
                        run(80, 20, 0, 0, 2);
                        run(80, 20, 0, 0, 3);
                        run(80, 20, -1, 0, 0);
                        run(80, 20, 0, 61, 0);
                        run(80, 20, 0, Integer.MIN_VALUE, 0);
                        run(80, 20, 0, Integer.MAX_VALUE, 0);
                        run(80, 66, 0, 0, 0);
                        run(80, -1, 0, 0, 0);
                        run(0, 20, 80, 80, 0);
                        Batch batch = new Batch();
                        batch.conflicting(batch.target, 12);
                        batch.conflicting(batch.target, 20);
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "GuardedCopies"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());

        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("GuardedCopies")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generation = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generation.request().generatedSourcesDirectory(),
                "GuardedCopies$Batch");
        int draw = cpp.indexOf("::draw(");
        int end = cpp.indexOf("\n}", draw);
        assertTrue(draw >= 0 && end > draw, cpp);
        String body = cpp.substring(draw, end);
        assertTrue(body.contains(" == 20) {") && body.contains("bounded_primitive_array_copy<float, 20, 0>"), body);
        assertTrue(body.contains("bounded_primitive_array_copy<float>(") && body.contains("platform_System_arraycopy_"), body);
        int conflicting = cpp.indexOf("::conflicting(");
        int conflictingEnd = cpp.indexOf("\n}", conflicting);
        assertTrue(conflicting >= 0 && conflictingEnd > conflicting, cpp);
        assertFalse(cpp.substring(conflicting, conflictingEnd).contains("bounded_primitive_array_copy<float, "), cpp);

        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(result.executable().toString()), Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
