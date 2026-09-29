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

class LiteralCacheTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void literalsKeepIdentityAndUtf16AcrossConcurrentFirstUseAndCollection(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("Literals.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Literals {
                            static volatile boolean start;
                            static boolean initialized;
                            static class Uninitialized {
                                static { initialized = true; }
                            }
                            static Class<?> type() { return Uninitialized.class; }
                            static Class<?> primitiveType() { return int.class; }
                            static class First {
                                static final String constant = "shared identity";
                                static String text() { return "shared identity"; }
                                static String cold() { return "concurrent first use"; }
                                static String zero() { return "A\\0B\\0C"; }
                                static String surrogate() { return "\\uD800x\\uDC00"; }
                            }
                            static class Second {
                                static String text() { return "shared identity"; }
                                static String cold() { return "concurrent first use"; }
                                static String zero() { return "A\\0B\\0C"; }
                                static String surrogate() { return "\\uD800x\\uDC00"; }
                            }
                            public static void main(String[] args) throws Exception {
                                Class<?> retainedClass = type();
                                Class<?>[] classResults = new Class<?>[8];
                                String[] results = new String[8];
                                Thread[] workers = new Thread[results.length];
                                for (int i = 0; i < workers.length; i++) {
                                    final int index = i;
                                    workers[i] = new Thread(() -> {
                                        while (!start) Thread.yield();
                                        for (int j = 0; j < 100; j++) {
                                            String value = (index & 1) == 0 ? First.cold() : Second.cold();
                                            if (results[index] != null && results[index] != value) throw new AssertionError();
                                            results[index] = value;
                                            Class<?> type = type();
                                            if (classResults[index] != null && classResults[index] != type)
                                                throw new AssertionError();
                                            classResults[index] = type;
                                            if (j % 8 == 0) System.gc();
                                        }
                                    });
                                    workers[i].start();
                                }
                                start = true;
                                for (Thread worker : workers) worker.join();
                                for (Class<?> result : classResults) System.out.println(result == retainedClass);
                                System.out.println(initialized);
                                System.out.println(retainedClass.getName());
                                System.out.println(primitiveType() == Integer.TYPE);
                                System.out.println(void.class == Void.TYPE);
                                System.out.println(new int[0].getClass() == int[].class);
                                System.out.println(new String[0][0].getClass() == String[][].class);
                                System.gc();
                                System.out.println(retainedClass == type());
                                for (String result : results) System.out.println(result == First.cold());
                                System.out.println(First.text() == Second.text());
                                System.out.println(First.constant == Second.text());
                                System.out.println(First.zero() == Second.zero());
                                System.out.println(First.surrogate() == Second.surrogate());
                                for (String value : new String[]{First.zero(), First.surrogate(), "ðŸŒŽ"}) {
                                    System.out.println(value.length());
                                    for (int i = 0; i < value.length(); i++) System.out.println((int) value.charAt(i));
                                }
                                String retained = First.text();
                                for (int i = 0; i < 80; i++) {
                                    byte[] pressure = new byte[8192];
                                    pressure[0] = (byte) i;
                                    System.gc();
                                    if (retained != Second.text()) throw new AssertionError();
                                }
                                System.out.println(retained);
                                System.out.println(String.valueOf(true) == "true");
                                System.out.println(String.valueOf(false) == "false");
                                System.out.println(String.valueOf((Object) null) == "null");
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Literals"),
                        Duration.ofSeconds(45));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Literals")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        Path output = generated.request().generatedSourcesDirectory();
        assertTrue(Files.isRegularFile(output.resolve("class_literals.cpp")));
        String effects = Files.readString(output.resolve("method-effects.tsv"));
        assertTrue(effects.contains("Literals.type()Ljava/lang/Class;\ttrue\t"), effects);
        assertTrue(effects.contains("Literals.primitiveType()Ljava/lang/Class;\ttrue\t"), effects);
        assertTrue(
                Files.isRegularFile(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("string_literals.cpp")));
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(90),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
