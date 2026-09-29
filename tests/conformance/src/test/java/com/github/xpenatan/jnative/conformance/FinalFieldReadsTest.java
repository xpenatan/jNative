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

class FinalFieldReadsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void repeatedFieldsKeepArrayWritesReceiverChangesAndExceptionOrder(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("FieldReads.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class FieldReads {
                            static class Parent {
                                final float[] values;
                                Parent(float[] values) { this.values = values; }
                            }
                            static class Box extends Parent {
                                volatile int barrier;
                                Box(float[] values) { super(values); }
                                float update(Box other) {
                                    float first = values[0] + values[1];
                                    values[1] = first;
                                    float second = values[0] + values[1];
                                    int observed = barrier;
                                    return second + values[1] + observed + other.values[0];
                                }
                            }
                            static float dot(Box left, Box right) {
                                return left.values[0] * right.values[0]
                                     + left.values[1] * right.values[1]
                                     + left.values[2] * right.values[2]
                                     + left.values[3] * right.values[3];
                            }
                            static float changeReceiver(Box first, Box second) {
                                float value = first.values[0];
                                first = second;
                                return value + first.values[0];
                            }
                            static void replaceElement(Box value) {
                                System.gc(); value.values[0] = 100;
                            }
                            static float acrossCall(Box value) {
                                float before = value.values[0];
                                replaceElement(value);
                                return before + value.values[0];
                            }
                            static void exceptional(Box left, Box right) {
                                try { System.out.println(dot(left, right)); }
                                catch (NullPointerException e) { System.gc(); System.out.println("null"); }
                                catch (ArrayIndexOutOfBoundsException e) { System.gc(); System.out.println("range"); }
                            }
                            public static void main(String[] args) throws Exception {
                                Box left = new Box(new float[]{1,2,3,4});
                                Box right = new Box(new float[]{5,6,7,8});
                                System.out.println(dot(left, right));
                                System.out.println(dot(left, left));
                                System.out.println(left.update(left));
                                System.out.println(left.update(right));
                                System.out.println(changeReceiver(left, right));
                                System.out.println(acrossCall(left));
                                exceptional(null, right);
                                exceptional(new Box(null), new Box(new float[0]));
                                exceptional(new Box(new float[0]), null);
                                exceptional(new Box(new float[]{1}), right);
                                Thread worker = new Thread(() -> {
                                    right.values[0] = 25;
                                    right.barrier = 1;
                                });
                                worker.start();
                                while (right.barrier == 0) Thread.yield();
                                System.out.println(right.update(left));
                                worker.join();
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "FieldReads"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("FieldReads")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/FieldReads.cpp"));
        int begin = cpp.indexOf("\nfloat FieldReads::dot(");
        int end = cpp.indexOf("\n}\n", begin);
        assertTrue(begin >= 0 && end > begin, cpp);
        assertEquals(
                2,
                cpp.substring(begin, end).split("->values.get\\(\\)", -1).length - 1,
                cpp.substring(begin, end));
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
