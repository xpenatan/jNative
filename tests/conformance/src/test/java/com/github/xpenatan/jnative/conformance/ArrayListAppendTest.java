package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ArrayListAppendTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void appendPreservesGrowthIterationAndSubclassDispatch(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Append.java");
        Files.writeString(source, """
                import java.util.ArrayList;
                import java.util.ConcurrentModificationException;
                import java.util.List;
                public class Append {
                    static class Indexed extends ArrayList<String> {
                        int indexed;
                        @Override public void add(int index, String value) {
                            indexed++;
                            super.add(index, value);
                        }
                    }
                    static void check(ArrayList<String> list) {
                        List<String> view = list;
                        for (int i = 0; i < 80; i++) {
                            if (!view.add(i % 7 == 0 ? null : "value" + i)) throw new AssertionError();
                            if (i % 13 == 0) System.gc();
                        }
                        long sum = 0;
                        for (String value : view) sum += value == null ? 1 : value.length();
                        var iterator = view.iterator();
                        view.add("last");
                        try { iterator.next(); throw new AssertionError(); }
                        catch (ConcurrentModificationException expected) { sum++; }
                        list.trimToSize();
                        list.ensureCapacity(150);
                        view.add(null);
                        view.add(0, "first");
                        System.gc();
                        System.out.println(sum + ":" + view.size() + ":" + view.get(0) + ":" + view.get(81));
                        view.clear();
                        view.add("again");
                        System.gc();
                        System.out.println(view.get(0));
                    }
                    public static void main(String[] args) {
                        check(new ArrayList<>());
                        check(new ArrayList<>(0));
                        check(new ArrayList<>(1));
                        Indexed list = new Indexed();
                        check(list);
                        System.out.println(list.indexed);
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "Append"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("Append")
                .buildRoot(temporary.resolve("out")).buildType(buildType).build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
