package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeapGrowthTest {
    @TempDir
    Path temporary;

    @Test
    void automaticCollectionScalesWithRetainedObjects() throws Exception {
        Path source = temporary.resolve("Growing.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Growing {
                            static class Node {
                                int value;
                                Node(int value) { this.value = value; }
                            }
                            public static void main(String[] args) {
                                Node[] retained = new Node[50000];
                                for (int i = 0; i < retained.length; i++) retained[i] = new Node(i);
                                long sum = 0;
                                for (int i = 0; i < 300000; i++) {
                                    Node temporary = new Node(i);
                                    sum += temporary.value + retained[i % retained.length].value;
                                }
                                System.gc();
                                System.out.println(sum + ":" + retained[49999].value);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Growing"),
                        Duration.ofSeconds(30));
        var compiled =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Growing")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(BuildType.RELEASE)
                        .nativeSymbols(NativeSymbols.NONE)
                        .stackTraces(StackTraceMode.NONE)
                        .crashReports(CrashReportMode.OFF)
                        .build();
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_STATS", "1"));
        assertEquals(0, expected.exitCode(), expected.text());
        assertEquals(0, actual.exitCode(), actual.text());
        assertTrue(actual.text().startsWith(expected.text()), actual.text());
        var collections = Pattern.compile(" collections=(\\d+)").matcher(actual.text());
        assertTrue(collections.find(), actual.text());
        assertTrue(
                Integer.parseInt(collections.group(1)) < 50,
                "A large live heap must not be scanned every few hundred allocations: "
                        + actual.text());
    }
}
