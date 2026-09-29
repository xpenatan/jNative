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

class StaticFieldEffectsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void ownStaticFieldsPreserveInitializationAndRooting(BuildType buildType) throws Exception {
        Path source = temporary.resolve("StaticFields.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class StaticFields {
                            static volatile boolean running = true;
                            static class Values {
                                static final int[] TABLE = table();
                                static volatile int published;
                                static Object reference;
                                static int[] table() {
                                    System.gc(); System.out.println("table initialized");
                                    return new int[] {3, 5, 7, 11};
                                }
                                static int lookup(int index) { return TABLE[index]; }
                                static int nested(int index) { return lookup(index) + lookup(3 - index); }
                                static int publish(int value) { published = value; return published; }
                                static Object exchange(Object value) {
                                    Object previous = reference; reference = value; return previous;
                                }
                            }
                            static class Reentrant {
                                static int value = initialize();
                                static int count;
                                static int helper(int x) { return x + 1; }
                                static int nested(int x) { return helper(x) + helper(x); }
                                static int initialize() {
                                    int sum = 0;
                                    for (int i = 0; i < 10000; i++) sum += nested(i);
                                    return sum;
                                }
                                static int read() { return value; }
                            }
                            static class Parent {
                                static Object shared = initialize();
                                static Object initialize() {
                                    System.gc(); System.out.println("parent initialized"); return new String("parent");
                                }
                            }
                            static class Child extends Parent {
                                static Object inherited() { return shared; }
                            }
                            static class Lazy {
                                static int value = initialize();
                                static int initialize() { System.out.println("lazy initialized"); return 13; }
                            }
                            static class Publication {
                                static volatile Published value;
                                static volatile boolean readerStarted;
                                static int result;
                            }
                            static class Published {
                                static int initialized = initialize();
                                static int initialize() {
                                    Publication.value = new Published();
                                    while (!Publication.readerStarted) Thread.yield();
                                    try { Thread.sleep(50); }
                                    catch (InterruptedException failure) { throw new RuntimeException(failure); }
                                    return 37;
                                }
                                int read() { return initialized; }
                            }
                            static int conditional(boolean touch) { return touch ? Lazy.value : 19; }
                            public static void main(String[] args) throws Exception {
                                System.out.println(conditional(false));
                                System.out.println(Values.exchange(new String("first")));
                                System.gc();
                                System.out.println(Values.exchange(new String("second")));
                                System.out.println(Values.exchange(null));
                                System.out.println(Values.publish(23));
                                for (int i = 0; i < 4; i++) System.out.println(Values.nested(i));
                                String survivor = new String("survivor");
                                try { Values.lookup(-1); }
                                catch (ArrayIndexOutOfBoundsException expected) {
                                    System.gc(); System.out.println(survivor);
                                }
                                Thread collector = new Thread(() -> {
                                    while (running) { System.gc(); Thread.yield(); }
                                });
                                collector.start();
                                System.out.println(Reentrant.read());
                                running = false;
                                collector.join();
                                System.out.println(Child.inherited());
                                System.out.println(conditional(true));
                                Thread reader = new Thread(() -> {
                                    while (Publication.value == null) Thread.yield();
                                    Publication.readerStarted = true;
                                    Publication.result = Publication.value.read();
                                });
                                reader.start();
                                System.out.println(Published.initialized);
                                reader.join();
                                System.out.println(Publication.result);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "StaticFields"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("StaticFields")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        String cpp =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/StaticFields_Values.cpp"));
        for(String method : List.of("lookup", "nested", "publish", "exchange")) {
            String returnType = method.equals("exchange") ? "::jnative::Object*" : "std::int32_t";
            int start = cpp.indexOf("\n" + returnType + " StaticFields_Values::" + method + "(");
            int end = cpp.indexOf("\n}", start);
            assertTrue(start >= 0 && end > start, cpp);
            String body = cpp.substring(start, end);
            assertFalse(body.contains("gc_roots"), body);
            assertFalse(body.contains("safepoint"), body);
            assertTrue(body.contains("ensure_initialized"), body);
            if(method.equals("exchange")) assertTrue(body.contains("initialization_roots"), body);
        }
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
