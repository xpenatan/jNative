package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DesktopLibraryTest {
    @TempDir
    Path temporary;

    @Test
    void enumsRecordsArraysAndCleanupMatchTheJvmUnderCollection() throws Exception {
        Path source = temporary.resolve("Library.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.util.*;
                        public class Library {
                            enum Mode { FIRST { public String toString() { return "first"; } }, SECOND { public String toString() { return "second"; } } }
                            record Point(int x, String label, float scale, boolean visible) {}
                            interface Feature {
                                private int check(int value) { return value + 1; }
                                default int evaluate(int value) { return check(value) * 2; }
                            }
                            static final class Example implements Feature {}
                            static Object service = new Object();
                            static int width(int value) { return value; }
                            static int frames() {
                                int total = 0;
                                for (int i = 0; i < 4; ++i) {
                                    try {
                                        boolean changed = width(i) != 2 || width(i + 1) != 3;
                                        if (changed && service != null) total++;
                                        if (service == null || width(i) > 0) {
                                            try { total += i; }
                                            finally { if (service != null) total++; }
                                        }
                                    } catch (Throwable error) {
                                        throw error instanceof RuntimeException ? (RuntimeException) error : new RuntimeException(error);
                                    }
                                }
                                return total;
                            }
                            public static void main(String[] args) throws Exception {
                                System.out.println(frames());
                                System.out.println(new Example().evaluate(4));
                                Mode[] modes = Mode.values();
                                modes[0] = Mode.SECOND;
                                System.out.println(Mode.values()[0].name() + ":" + Mode.SECOND.ordinal());
                                System.out.println(Mode.FIRST.compareTo(Mode.SECOND));
                                System.out.println(Mode.FIRST.getDeclaringClass() == Mode.class);
                                Point first = new Point(7, "point", Float.NaN, true);
                                Point same = new Point(7, new String("point"), Float.NaN, true);
                                System.out.println(first);
                                System.out.println(first.equals(same) + ":" + (first.hashCode() == same.hashCode()));
                                System.out.println(new Point(7, null, -0.0f, false).equals(new Point(7, null, 0.0f, false)));
                                System.out.println(first.equals(null));
                                int[] values = {9, 2, 7, 1, 4};
                                System.arraycopy(values, 0, values, 1, 4);
                                for (int value : values) System.out.print(value + ",");
                                System.out.println();
                                System.arraycopy(values, 1, values, 0, 4);
                                int[] grown = Arrays.copyOf(values, 8);
                                Arrays.sort(grown);
                                for (int value : grown) System.out.print(value + ",");
                                System.out.println();
                                String[] words = {"b", "a", "c"};
                                Arrays.sort(words);
                                System.out.println(words[0] + words[1] + words[2]);
                                String[] copied = Arrays.copyOf(words, 5);
                                System.out.println(copied.getClass() == String[].class);
                                Object[] sourceValues = {"first", 3, "last"};
                                try { System.arraycopy(sourceValues, 0, copied, 0, 3); }
                                catch (ArrayStoreException expected) { System.out.println(copied[0] + ":" + copied[1]); }
                                try { System.arraycopy(new int[0], 0, new long[0], 0, 0); }
                                catch (ArrayStoreException expected) { System.out.println("primitive mismatch"); }
                                boolean[] flags = {true, false};
                                System.out.println(flags.clone()[0]);
                                ArrayDeque<String> queue = new ArrayDeque<>(1);
                                queue.addLast("b"); queue.addFirst("a"); queue.addLast("c");
                                System.gc();
                                Iterator<String> iterator = queue.iterator();
                                System.out.println(iterator.next()); iterator.remove();
                                System.out.println(queue.removeFirst() + queue.removeLast() + queue.isEmpty());
                                RuntimeException error = new RuntimeException("primary");
                                error.addSuppressed(new Exception("secondary"));
                                System.gc();
                                System.out.println(error.getSuppressed()[0].getMessage());
                                error.getSuppressed()[0] = null;
                                System.out.println(error.getSuppressed().length);
                                try { error.addSuppressed(error); }
                                catch (IllegalArgumentException expected) { System.out.println("self"); }
                                try { error.addSuppressed(null); }
                                catch (NullPointerException expected) { System.out.println("null"); }
                                System.out.println(Integer.parseInt("-2147483648"));
                                System.out.println(Integer.parseInt("+7fffffff", 16));
                                System.out.println(Integer.parseInt("Ù¡Ù¢Ù£"));
                                System.out.println(Integer.parseInt("ï¼¦ï¼¦", 16));
                                System.out.println(Character.digit(0x1D7D7, 10) + ":" + Character.digit('9', 8));
                                Object identity = new Object();
                                System.out.println(System.identityHashCode(null));
                                System.out.println(System.identityHashCode(identity) == identity.hashCode());
                                try { Integer.parseInt("2147483648"); }
                                catch (NumberFormatException expected) { System.out.println("overflow"); }
                                System.out.println(Float.floatToIntBits(Math.min(0f, -0f)));
                                System.out.println(Float.floatToIntBits(Math.max(0f, -0f)));
                                System.out.println(Double.isFinite(1d) + ":" + Double.isFinite(Double.NaN));
                                System.out.println(Math.sqrt(25) + ":" + Math.ceil(-1.5));
                                System.out.println(Double.doubleToRawLongBits(Math.sqrt(-0d)));
                                System.out.println(Double.doubleToRawLongBits(Math.ceil(-0.5d)));
                                System.out.println(Double.isNaN(Math.sqrt(-1)) + ":" + Math.sqrt(Double.POSITIVE_INFINITY));
                                System.out.println("  trimmed ".trim());
                                System.out.flush();
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Library"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Library")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(BuildType.RELEASE)
                        .nativeSymbols(NativeSymbols.NONE)
                        .stackTraces(StackTraceMode.NONE)
                        .crashReports(CrashReportMode.OFF);
        var result = builder.build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
