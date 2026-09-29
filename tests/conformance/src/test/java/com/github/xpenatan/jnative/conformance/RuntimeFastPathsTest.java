package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class RuntimeFastPathsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void dispatchLazyMonitorsAndOverlappingBuffersMatchJvm(BuildType buildType) throws Exception {
        Path source = temporary.resolve("FastPaths.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.nio.*;
                        import java.util.*;
                        public class FastPaths {
                            interface Value { default int value() { return 3; } }
                            interface More extends Value { default int value() { return 7; } }
                            static class Base implements Value {
                                public int hashCode() { return 42; }
                                public boolean equals(Object other) { return other instanceof Base; }
                                public String toString() { return "base"; }
                            }
                            static class Child extends Base implements More {
                                public int hashCode() { return 81; }
                                public String toString() { return "child"; }
                            }
                            static class Leaf extends Child { public int value() { return 11; } }
                            static volatile int phase, doneA, doneB;
                            static Object lock;
                            static int count;
                            static void contend(boolean first) {
                                for (int round = 1; round <= 200; round++) {
                                    while (phase != round) {}
                                    for (int i = 0; i < 20; i++) {
                                        synchronized (lock) {
                                            if (!Thread.holdsLock(lock)) throw new IllegalStateException("owner");
                                            synchronized (lock) { count++; }
                                        }
                                    }
                                    if (first) doneA = round; else doneB = round;
                                }
                            }
                            public static void main(String[] args) throws Exception {
                                for (Object item : new Object[] {new Base(), new Child(), new Leaf(), "abc", Integer.valueOf(17)}) {
                                    System.out.println(item.toString() + ":" + item.hashCode() + ":" + item.equals(item));
                                    if (item instanceof Value) System.out.println(((Value)item).value());
                                }
                                Thread a = new Thread(() -> contend(true));
                                Thread b = new Thread(() -> contend(false));
                                a.start(); b.start();
                                for (int round = 1; round <= 200; round++) {
                                    lock = new Object(); count = 0; phase = round;
                                    while (doneA != round || doneB != round) {}
                                    if (count != 40) throw new IllegalStateException("lost mutual exclusion");
                                    System.gc();
                                }
                                a.join(); b.join();
                                System.out.println("contended first-use monitors");
                                ByteBuffer storage = ByteBuffer.allocateDirect(16);
                                for (int i = 0; i < 16; i++) storage.put((byte)i);
                                ByteBuffer from = storage.duplicate().position(0).limit(10);
                                ByteBuffer to = storage.duplicate().position(3).limit(13);
                                to.put(from);
                                byte[] bytes = new byte[16];
                                storage.clear().get(bytes);
                                System.out.println(Arrays.toString(bytes) + ":" + from.position() + ":" + to.position());
                                ByteBuffer sliced = storage.duplicate().position(4).limit(12).slice();
                                ByteBuffer heap = ByteBuffer.wrap(new byte[] {21, 22, 23, 24});
                                sliced.put(heap);
                                ByteBuffer back = ByteBuffer.allocate(4);
                                back.put(sliced.flip());
                                System.out.println(Arrays.toString(back.array()));
                                from.clear().limit(2);
                                try { storage.asReadOnlyBuffer().put(from); }
                                catch (ReadOnlyBufferException expected) { System.out.println("read only:" + from.position()); }
                                try { ByteBuffer.allocateDirect(1).put(from); }
                                catch (BufferOverflowException expected) { System.out.println("overflow:" + from.position()); }
                                try { storage.put(storage); }
                                catch (IllegalArgumentException expected) { System.out.println("self copy"); }
                                ByteBuffer.allocateDirect(0).put(ByteBuffer.allocateDirect(0));
                                FloatBuffer floats = ByteBuffer.allocateDirect(16).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
                                floats.put(new float[] {1.25f, -2, 3.5f, 4}).flip();
                                FloatBuffer swapped = ByteBuffer.allocateDirect(16).order(ByteOrder.BIG_ENDIAN).asFloatBuffer();
                                swapped.put(floats).flip();
                                float[] values = new float[4]; swapped.get(values);
                                System.out.println(Arrays.toString(values) + ":" + floats.position());
                                String[] same = {"a", "b", "c", "d"};
                                System.arraycopy(same, 0, same, 1, 3);
                                System.out.println(Arrays.toString(same.clone()));
                                String[] target = new String[3];
                                try { System.arraycopy(new Object[] {"ok", new Object(), "late"}, 0, target, 0, 3); }
                                catch (ArrayStoreException expected) { System.out.println(Arrays.toString(target)); }
                                I[] letters = new J[2];
                                letters[0] = new J();
                                System.out.println(letters instanceof Value[]);
                                System.out.println(((Object)letters) instanceof I[]);
                                System.out.println(((Object)letters) instanceof int[]);
                                System.out.println(((Object)new int[2]) instanceof I[]);
                                System.out.println(((Object)new I[2]) instanceof Object[]);
                                System.out.println(((Object)new I[2][3]) instanceof Object[][]);
                                System.out.println(((Object)new int[2][3]) instanceof Object[]);
                                System.out.println(((Object)new int[2][3]) instanceof Object[][]);
                                System.out.println(((Object)new int[2]) instanceof Cloneable);
                                System.out.println(((Object)new int[2]) instanceof java.io.Serializable);
                                System.out.println(((Object)new int[2]) instanceof Object[]);
                                try { letters[1] = new I(); }
                                catch (ArrayStoreException expected) { System.out.println(letters[1] == null); }
                                Value[] interfaces = new Value[2];
                                interfaces[0] = new J(); interfaces[1] = new Leaf();
                                System.out.println(interfaces[0].value() + interfaces[1].value());
                                I[] prefix = new I[3];
                                try { System.arraycopy(new Object[] {new J(), new Base(), new I()}, 0, prefix, 0, 3); }
                                catch (ArrayStoreException expected) {
                                    System.out.println(prefix[0] instanceof J && prefix[1] == null && prefix[2] == null);
                                }
                                try { I[] invalid = (I[])(Object)new int[2]; System.out.println(invalid.length); }
                                catch (ClassCastException expected) { System.out.println("primitive cast rejected"); }
                            }
                        }
                        class I implements FastPaths.Value {}
                        class J extends I {}
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "FastPaths"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("FastPaths")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType)
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "2")));
    }
}
