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

class InitializedDependencyEntryTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void guardedScalarEntriesPreserveFirstUseAndCheckedLoads(BuildType buildType) throws Exception {
        Path source = temporary.resolve("WarmCalls.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                import java.util.Arrays;

                public class WarmCalls {
                    static int initializations;
                    static class Table {
                        static final float[] values = initialize();
                        static float[] initialize() {
                            initializations++;
                            System.gc();
                            return new float[] {1, 2, 3, 4};
                        }
                    }
                    static class Lookup {
                        static float first(int index) { return Table.values[index]; }
                        static float second(int index) { return Table.values[index + 1]; }
                    }
                    static final class Worker {
                        float readFirst(int index) { return Lookup.first(index); }
                        float readSecond(int index) { return Lookup.second(index); }
                    }
                    static class Kernel {
                        static void write(Worker worker, float[] output, int index, boolean update) {
                            if (update) {
                                output[0] = worker.readFirst(index);
                                output[1] = worker.readSecond(index);
                            }
                        }
                        static float combine(Worker input, int index) {
                            Worker worker = input;
                            return worker.readFirst(index) + worker.readSecond(index);
                        }
                    }
                    static void run(float[] output, int index, boolean update) {
                        try { Kernel.write(new Worker(), output, index, update); }
                        catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
                        System.out.println(initializations + ":" + Arrays.toString(output));
                    }
                    public static void main(String[] args) {
                        float[] output = new float[2];
                        run(output, 0, false);
                        run(output, 0, true);
                        run(output, 1, true);
                        run(output, 3, true);
                        try { Kernel.write(null, output, 0, true); }
                        catch (NullPointerException expected) { System.out.println("null"); }
                        System.out.println(Kernel.combine(new Worker(), 1));
                        System.out.println(Lookup.first(2));
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "WarmCalls"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());

        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("WarmCalls")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String kernel = ProcessHarness.generatedClassSource(generated, "WarmCalls$Kernel");
        String worker = ProcessHarness.generatedClassSource(generated, "WarmCalls$Worker");
        String lookup = ProcessHarness.generatedClassSource(generated, "WarmCalls$Lookup");
        int guard = kernel.indexOf("::initialization_complete.load(");
        int fast = kernel.indexOf("::initialized_readFirst(");
        int second = kernel.indexOf("::initialized_readSecond(");
        int cold = kernel.indexOf("::readFirst(");
        assertTrue(guard >= 0 && fast > guard && cold > fast, kernel);
        assertTrue(second > fast && cold > second && kernel.contains("::readSecond("), kernel);
        assertTrue(kernel.substring(guard, fast).contains("::jnative::require_non_null("), kernel);
        String pair = kernel.substring(kernel.indexOf("::combine("));
        int firstCall = pair.indexOf("::readFirst(");
        int secondCall = pair.indexOf("::readSecond(");
        assertTrue(firstCall >= 0 && secondCall > firstCall, pair);
        assertFalse(pair.substring(firstCall, secondCall).contains("::jnative::require_non_null("), pair);
        assertTrue(worker.contains("::initialized_first(") && worker.contains("::initialized_second("), worker);
        for (String name : List.of("first", "second")) {
            int start = lookup.indexOf("::initialized_" + name + "(");
            int end = lookup.indexOf("\n}", start);
            assertTrue(start >= 0 && end > start, lookup);
            assertFalse(lookup.substring(start, end).contains("::ensure_initialized()"), lookup);
        }
        assertTrue(lookup.contains("::ensure_initialized()"), lookup);

        var compiled = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
