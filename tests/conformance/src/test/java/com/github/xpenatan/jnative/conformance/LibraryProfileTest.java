package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class LibraryProfileTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @org.junit.jupiter.api.Test
    void unsupportedLibraryAndAlternateBootstrapFailBeforeOutput() throws Exception {
        for(String body :
                List.of(
                        "new java.util.ArrayList<String>().subList(0, 0);",
                        "Runnable work = (Runnable & java.io.Serializable)() -> {}; work.run();")) {
            Path source = temporary.resolve("OutsideProfile.java"),
                    classes = temporary.resolve("classes");
            Files.writeString(
                    source,
                    "public class OutsideProfile { public static void main(String[] args) { "
                            + body
                            + " } }");
            ProcessHarness.javac(source, classes, 25);
            Path output = temporary.resolve("unsupported-output");
            var error =
                    assertThrows(
                            CompilerException.class,
                            () ->
                                    NativeBuilder.create()
                                            .classpath(classes)
                                            .mainClass("OutsideProfile")
                                            .buildRoot(output)
                                            .generate());
            assertTrue(error.getMessage().contains("OutsideProfile.main"), error.getMessage());
            assertTrue(error.getMessage().contains("Unsupported"), error.getMessage());
            assertFalse(Files.exists(output));
        }
        assertTrue(NativeCompatibility.libraryInventory().contains("java.util.ArrayList"));
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 25})
    void utf8FilesAndCollectionReportMatchJvm(int release) throws Exception {
        Path source = temporary.resolve("Report.java");
        Files.writeString(
                source,
                """
                        import java.util.*;
                        import java.util.function.*;
                        import java.nio.file.*;
                        import java.io.IOException;
                        public class Report {
                            public static void main(String[] args) throws IOException {
                                Path file = Path.of("report-Ω.txt");
                                StringBuilder text = new StringBuilder("red\\nblue\\nred\\n🌎\\n");
                                CharSequence sequence = text;
                                System.out.println(sequence.charAt(0));
                                System.out.println(sequence.subSequence(0, 3));
                                CharSequence string = "hello";
                                System.out.println(string.length());
                                Files.writeString(file, text);
                                System.out.println(Files.exists(file));
                                String read = Files.readString(file);
                                Map<String,Integer> counts = new HashMap<>();
                                int start = 0;
                                for (int i = 0; i < read.length(); ++i) if (read.charAt(i) == '\\n') {
                                    String word = read.substring(start, i);
                                    counts.put(word, counts.getOrDefault(word, 0) + 1);
                                    start = i + 1; System.gc();
                                }
                                Function<String,String> format = word -> word + ":" + counts.get(word);
                                System.out.println(format.apply("red"));
                                System.out.println(format.apply("blue"));
                                System.out.println(format.apply("🌎"));
                                System.out.println(file.toAbsolutePath().getFileName().toString());
                                System.out.println(Paths.get("unused", "..", "report-Ω.txt").normalize().equals(file));
                                Files.writeString(file, "A\\0Ω🌎");
                                System.out.println(Files.readString(file).length());
                                System.out.println(Files.readAllBytes(file).length);
                                Files.write(file, new byte[]{(byte)0xc0, (byte)0x80});
                                try { Files.readString(file); }
                                catch (java.nio.charset.MalformedInputException expected) { System.out.println("bad UTF-8"); }
                                System.out.println(Files.deleteIfExists(file));
                                System.out.println(Files.deleteIfExists(file));
                                try { Files.readAllBytes(file); }
                                catch (NoSuchFileException expected) { System.out.println("missing"); }
                                try { Path.of("bad\\0path"); }
                                catch (InvalidPathException expected) { System.out.println("invalid path"); }
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, release);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Report"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Report")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(release == 17 ? BuildType.DEBUG : BuildType.RELEASE)
                        .timeout(Duration.ofMinutes(3))
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(release == 25 && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, result, expected, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 25})
    void lambdasCaptureReferencesAndAdaptMethodReferences(int release) throws Exception {
        Path source = temporary.resolve("Functions.java");
        Files.writeString(
                source,
                """
                        import java.util.function.*;
                        import java.util.concurrent.atomic.AtomicInteger;
                        public class Functions {
                            static class Box {
                                int value;
                                Box(int value) { this.value = value; }
                                int read() { System.gc(); return value; }
                            }
                            static long twice(int value) { return (long)value * 2; }
                            static Function<String,String> prefix(String text) { return value -> text + ":" + value; }
                            public static void main(String[] args) throws InterruptedException {
                                int offset = 7;
                                IntUnaryOperator arithmetic = value -> value * 3 + offset;
                                Function<String,String> text = prefix(new String("captured"));
                                System.gc();
                                System.out.println(arithmetic.applyAsInt(11));
                                System.out.println(text.apply("value"));
                                Function<String,Integer> length = String::length;
                                Function<Integer,Long> widen = Functions::twice;
                                IntFunction<Box> create = Box::new;
                                Function<Box,Integer> read = Box::read;
                                Function<String,String> concat = new String("bound")::concat;
                                System.out.println(length.apply("🌎"));
                                System.out.println(widen.apply(Integer.MAX_VALUE));
                                System.out.println(read.apply(create.apply(42)));
                                System.out.println(concat.apply("-method"));
                                StringBuilder builder = new StringBuilder();
                                Consumer<String> sink = builder::append;
                                sink.accept("ignored return"); System.gc();
                                System.out.println(builder);
                                try { length.apply(null); }
                                catch (NullPointerException expected) { System.out.println("null receiver"); }
                                AtomicInteger total = new AtomicInteger();
                                Runnable task = () -> { for (int i = 0; i < 30; ++i) total.addAndGet(arithmetic.applyAsInt(i)); };
                                Thread first = new Thread(task), second = new Thread(task);
                                first.start(); second.start(); first.join(); second.join();
                                System.out.println(total.get());
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, release);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Functions"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Functions")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(release == 17 ? BuildType.DEBUG : BuildType.RELEASE)
                        .timeout(Duration.ofMinutes(3))
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(release == 25 && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, result, expected, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 25})
    void collectionsAndBoxingMatchJvm(int release) throws Exception {
        Path source = temporary.resolve("Inventory.java");
        Files.writeString(
                source,
                """
                        import java.util.*;
                        public class Inventory {
                            static class Key {
                                int value;
                                Key(int value) { this.value = value; }
                                public int hashCode() { return 7; }
                                public boolean equals(Object other) { return other instanceof Key && ((Key)other).value == value; }
                            }
                            public static void main(String[] args) {
                                ArrayList<Integer> empty = new ArrayList<>();
                                Iterator<Integer> unchanged = empty.iterator();
                                empty.ensureCapacity(1);
                                try { unchanged.next(); }
                                catch (NoSuchElementException expected) { System.out.println("empty iterator"); }
                                List<Integer> values = new ArrayList<>(1);
                                for (int i = 0; i < 35; ++i) values.add(i);
                                values.add(2, 90);
                                values.set(3, 91);
                                Iterator<Integer> iterator = values.iterator();
                                int sum = 0;
                                while (iterator.hasNext()) {
                                    int value = iterator.next();
                                    if (value % 3 == 0) iterator.remove(); else sum += value;
                                    System.gc();
                                }
                                System.out.println(sum);
                                System.out.println(values.size());
                                System.out.println(values.equals(new ArrayList<Integer>(values)));
                                System.out.println(values.hashCode());
                                Object[] snapshot = values.toArray();
                                System.out.println(snapshot.length == values.size());
                                iterator = values.iterator(); values.add(7);
                                try { iterator.next(); }
                                catch (ConcurrentModificationException error) { System.out.println("modified"); }
                                Map<Key,Integer> counts = new HashMap<>(1);
                                for (int i = 0; i < 40; ++i) counts.put(new Key(i % 9), i);
                                counts.put(null, null);
                                System.out.println(counts.getOrDefault(null, 99) == null);
                                System.out.println(counts.putIfAbsent(null, 5));
                                System.out.println(counts.get(new Key(8)));
                                int total = 0;
                                for (Map.Entry<Key,Integer> entry : counts.entrySet()) {
                                    total += entry.getValue();
                                    if (entry.getKey() != null && entry.getKey().value == 3) entry.setValue(100);
                                    System.gc();
                                }
                                System.out.println(total);
                                System.out.println(counts.remove(new Key(3), 100));
                                System.out.println(counts.keySet().remove(new Key(4)));
                                System.out.println(counts.values().contains(5));
                                System.out.println(counts.size());
                                Set<String> set = new HashSet<>();
                                set.add("one"); set.add(new String("one")); set.add(null); set.add("two");
                                System.out.println(set.size());
                                System.out.println(set.equals(new HashSet<String>(set)));
                                System.out.println(set.hashCode());
                                Iterator<String> keys = set.iterator();
                                while (keys.hasNext()) { keys.next(); keys.remove(); }
                                System.out.println(set.isEmpty());
                                Number number = Integer.valueOf(-129);
                                System.out.println(number.longValue());
                                System.out.println(number.byteValue());
                                System.out.println(Integer.valueOf(127) == Integer.valueOf(127));
                                System.out.println(Long.valueOf(-128) == Long.valueOf(-128));
                                Object wide = Long.valueOf(Long.MIN_VALUE);
                                System.out.println(wide);
                                System.out.println(wide.hashCode());
                                System.out.println(Boolean.valueOf(true) == Boolean.TRUE);
                                System.out.println(new StringBuilder("inventory").append(':').append(sum)
                                    .append(':').append((Object)number).append(':').append(true).toString());
                                values.clear(); counts.clear();
                                System.gc();
                                System.out.println(values.isEmpty() && counts.isEmpty());
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, release);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Inventory"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Inventory")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(release == 17 ? BuildType.DEBUG : BuildType.RELEASE)
                        .timeout(Duration.ofMinutes(3))
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(release == 25 && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, result, expected, 1);
    }
}
