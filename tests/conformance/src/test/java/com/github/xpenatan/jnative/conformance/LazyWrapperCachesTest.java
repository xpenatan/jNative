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
import static org.junit.jupiter.api.Assertions.*;

class LazyWrapperCachesTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void firstBoxingPublishesOneCacheAcrossThreadsAndCollection(BuildType buildType) throws Exception {
        Path source = temporary.resolve("LazyWrapperCaches.java");
        Files.writeString(source, """
                public class LazyWrapperCaches {
                    static final Object[] first = new Object[5], second = new Object[5];
                    static volatile boolean start;
                    static void box(Object[] values) {
                        while (!start) Thread.yield();
                        values[0] = Integer.valueOf(42);
                        values[1] = Long.valueOf(42);
                        values[2] = Byte.valueOf((byte)42);
                        values[3] = Short.valueOf((short)42);
                        values[4] = Character.valueOf('*');
                        System.gc();
                    }
                    public static void main(String[] args) throws Exception {
                        if (Integer.parseInt("42") != 42 || Long.parseLong("42") != 42
                                || Byte.hashCode((byte)42) != 42 || Short.hashCode((short)42) != 42
                                || Character.toUpperCase('a') != 'A') throw new AssertionError();
                        Thread left = new Thread(() -> box(first));
                        Thread right = new Thread(() -> box(second));
                        left.start(); right.start(); start = true;
                        left.join(); right.join();
                        System.gc();
                        for (int i = 0; i < first.length; i++)
                            if (first[i] == null || first[i] != second[i] || first[i].hashCode() != 42)
                                throw new AssertionError();
                        for (int i = -128; i < 128; i++) {
                            if (Integer.valueOf(i) != Integer.valueOf(i) || Long.valueOf(i) != Long.valueOf(i)
                                    || Byte.valueOf((byte)i) != Byte.valueOf((byte)i)
                                    || Short.valueOf((short)i) != Short.valueOf((short)i)) throw new AssertionError();
                        }
                        for (char i = 0; i < 128; i++)
                            if (Character.valueOf(i) != Character.valueOf(i)) throw new AssertionError();
                        if (Integer.valueOf(4096).intValue() != 4096 || Long.valueOf(4096).longValue() != 4096
                                || Short.valueOf((short)4096).shortValue() != 4096
                                || Character.valueOf((char)4096).charValue() != 4096) throw new AssertionError();
                        System.out.println("caches-ok");
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "LazyWrapperCaches"), Duration.ofSeconds(60));
        assertEquals(new ProcessHarness.Output(0, "caches-ok\n"), expected);
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("LazyWrapperCaches")
                .buildRoot(temporary.resolve("out")).buildType(buildType).timeout(Duration.ofMinutes(5));
        var result = builder.build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
