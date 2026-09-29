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

class TypeChecksTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void precomputedHierarchyPreservesCastsArraysAndConcurrentChecks(BuildType buildType)
            throws Exception {
        StringBuilder markers = new StringBuilder();
        StringBuilder implemented = new StringBuilder();
        StringBuilder cases = new StringBuilder();
        StringBuilder reflected = new StringBuilder();
        for(int i = 0; i < 70; i++) {
            markers.append("interface Marker").append(i).append(" {}\n");
            if(i != 0) implemented.append(", ");
            implemented.append("Marker").append(i);
            reflected.append("Marker").append(i).append(".class, ");
            cases.append("case ")
                    .append(i)
                    .append(": return value instanceof Marker")
                    .append(i)
                    .append(";\n");
        }
        Path source = temporary.resolve("TypeChecks.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.io.Serializable;
                        import java.nio.ByteBuffer;

                        public class TypeChecks {
                            %s
                            interface Root {}
                            interface Left extends Root {}
                            interface Right extends Root {}
                            interface Diamond extends Left, Right {}
                            static class Base { int value = 17; }
                            static final class Child extends Base implements Diamond, %s {}
                            static class I extends Base {}
                            static class Failure extends IllegalArgumentException implements Left {}
                            static class ConstructorBase {
                                int observed;
                                ConstructorBase() {
                                    if (!(this instanceof ConstructorChild) || !(this instanceof Root)
                                            || getClass() != ConstructorChild.class
                                            || !ConstructorChild.class.isInstance(this))
                                        throw new IllegalStateException("base constructor lost actual class");
                                    observed = answer();
                                    System.gc();
                                }
                                int answer() { return -1; }
                            }
                            static final class ConstructorChild extends ConstructorBase implements Root {
                                int number = 31;
                                int answer() { return number; }
                                void set_type_id(int value) {
                                    throw new IllegalStateException("Java method called during C++ shell construction");
                                }
                            }
                            static class Uninitialized {
                                static { if (true) throw new IllegalStateException("type check initialized a class"); }
                            }
                            static boolean marker(Object value, int marker) {
                                switch (marker) { %s default: return false; }
                            }
                            static boolean reflectedInstance(Class<?> type, Object value) {
                                return type.isInstance(value);
                            }
                            static Object castOrKeep(Class<?> type, Object value, Object kept) {
                                try { return type.cast(value); }
                                catch (ClassCastException expected) { System.gc(); return kept; }
                            }
                            static int inspect(Object value) {
                                int bits = 0;
                                if (value instanceof Base) bits |= 1;
                                if (value instanceof Child) bits |= 2;
                                if (value instanceof Root) bits |= 4;
                                if (value instanceof Left) bits |= 8;
                                if (value instanceof Right) bits |= 16;
                                if (value instanceof Diamond) bits |= 32;
                                if (value instanceof RuntimeException) bits |= 64;
                                if (value instanceof CharSequence) bits |= 128;
                                if (value instanceof Object[]) bits |= 256;
                                if (value instanceof Base[]) bits |= 512;
                                if (value instanceof int[]) bits |= 1024;
                                if (value instanceof Cloneable) bits |= 2048;
                                try {
                                    Base base = (Base) value;
                                    if (base != null) bits += base.value * 4096;
                                } catch (ClassCastException expected) {
                                    System.gc(); bits += 8192;
                                }
                                return bits;
                            }
                            public static void main(String[] args) throws Exception {
                                ConstructorBase constructed = new ConstructorChild();
                                System.out.println("constructed:" + constructed.observed + ":"
                                    + constructed.answer() + ":" + (constructed instanceof Root));
                                try { ((ConstructorChild) constructed).set_type_id(9); }
                                catch (IllegalStateException expected) { System.out.println("member collision"); }
                                Object[] values = {null, new Child(), new Base(), new I(), new Failure(),
                                    new String("text"), new int[1], new Child[1], new I[1], new int[1][1],
                                    new Object(), ByteBuffer.allocateDirect(4), Child.class};
                                long checksum = 0;
                                for (Object value : values) {
                                    System.out.println(inspect(value));
                                    for (int i = 0; i < 70; i++) if (marker(value, i)) checksum += i + 1;
                                }
                                Object child = values[1];
                                long[] workerResult = new long[1];
                                Thread worker = new Thread(() -> {
                                    long result = 0;
                                    for (int round = 0; round < 100; round++) {
                                        for (int i = 0; i < 70; i++) if (marker(child, i)) result += i + 1;
                                        if (((Left) child) instanceof Right) result++;
                                    }
                                    workerResult[0] = result;
                                });
                                worker.start(); worker.join();
                                System.out.println("hierarchy:" + checksum + ":" + workerResult[0]);
                                Class<?>[] types = {%s Base.class, Child.class, I.class, Root.class,
                                    Left.class, Right.class, Diamond.class, Failure.class, RuntimeException.class,
                                    Object.class, String.class, CharSequence.class, Cloneable.class,
                                    int.class, void.class, boolean.class, int[].class, Object[].class,
                                    Base[].class, Child[].class, I[].class, int[][].class,
                                    ByteBuffer.class, Uninitialized.class};
                                long reflectedResult = 0;
                                for (Class<?> type : types) {
                                    for (Object value : values) {
                                        boolean matches = reflectedInstance(type, value);
                                        reflectedResult = reflectedResult * 31 + (matches ? 1 : 0);
                                        try {
                                            Object cast = type.cast(value);
                                            if (cast != value || (value != null && !matches))
                                                throw new IllegalStateException("reflection cast mismatch");
                                            reflectedResult += 3;
                                        } catch (ClassCastException expected) {
                                            if (matches || value == null) throw expected;
                                            reflectedResult += 7;
                                        }
                                    }
                                    for (Class<?> actual : types)
                                        reflectedResult = reflectedResult * 31 + (type.isAssignableFrom(actual) ? 1 : 0);
                                }
                                System.out.println("reflection:" + reflectedResult);
                                Object kept = new String("survived cast failure");
                                System.out.println(castOrKeep(Child.class, new Base(), kept));
                                Class<?> missing = null;
                                try { missing.cast(null); }
                                catch (NullPointerException expected) { System.gc(); System.out.println(kept); }
                                try { Child.class.isAssignableFrom(null); }
                                catch (NullPointerException expected) { System.gc(); System.out.println(kept); }
                                Thread reflectedWorker = new Thread(() -> {
                                    long result = 0;
                                    for (int round = 0; round < 100; round++) {
                                        for (Class<?> type : types) if (type.isInstance(child)) result++;
                                        System.gc();
                                    }
                                    workerResult[0] = result;
                                });
                                reflectedWorker.start(); reflectedWorker.join();
                                System.out.println("reflected worker:" + workerResult[0]);
                                Object array = new I[1];
                                ((Base[]) array)[0] = new I();
                                try { ((Base[]) array)[0] = new Child(); }
                                catch (ArrayStoreException expected) { System.out.println("array-store"); }
                                Object[][] destinations = {new Object[2], new Base[2], new Child[2],
                                    new I[2], new Left[2], new Root[2], new CharSequence[2], new String[2],
                                    new Cloneable[2], new Serializable[2], new Object[2][],
                                    new String[2][], new int[2][]};
                                long stores = 0;
                                for (Object[] destination : destinations) {
                                    for (Object value : values) {
                                        Object before = destination[0];
                                        try {
                                            destination[0] = value;
                                            System.gc();
                                            if (destination[0] != value) throw new AssertionError();
                                            stores = stores * 31 + 1;
                                        } catch (ArrayStoreException expected) {
                                            System.gc();
                                            if (destination[0] != before) throw new AssertionError();
                                            stores = stores * 31 + 2;
                                        }
                                    }
                                }
                                System.out.println("stores:" + stores);
                                Base[] partial = new Base[3];
                                try { System.arraycopy(new Object[]{child, new I(), "bad"}, 0, partial, 0, 3); }
                                catch (ArrayStoreException expected) {
                                    System.gc();
                                    System.out.println("partial:" + (partial[0] == child) + ":"
                                        + (partial[1] instanceof I) + ":" + (partial[2] == null));
                                }
                                try { ((Object[]) null)[-1] = new Object(); }
                                catch (NullPointerException expected) { System.out.println("null first"); }
                                try { ((Object[]) new Base[0])[-1] = "bad"; }
                                catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds first"); }
                                Object[] shared = new Left[4];
                                Thread storingWorker = new Thread(() -> {
                                    for (int i = 0; i < 100; i++) {
                                        shared[0] = child;
                                        shared[1] = new Failure();
                                        try { shared[2] = new Base(); }
                                        catch (ArrayStoreException expected) { System.gc(); }
                                    }
                                });
                                storingWorker.start();
                                for (int i = 0; i < 30; i++) System.gc();
                                storingWorker.join();
                                System.out.println("shared stores:" + (shared[0] == child) + ":"
                                    + (shared[1] instanceof Failure) + ":" + (shared[2] == null));
                                try { throw new Failure(); }
                                catch (Failure expected) { System.out.println("generated-catch"); }
                            }
                        }
                        """
                        .formatted(markers, implemented, cases, reflected));
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "TypeChecks"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("TypeChecks")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        Path output = generated.request().generatedSourcesDirectory();
        assertTrue(
                Files.readString(output.resolve("type_checks.cpp"))
                        .contains("register_type_checks"));
        assertTrue(
                Files.readString(output.resolve("classes/TypeChecks.cpp")).contains("::class_id"));
        String effects = Files.readString(output.resolve("method-effects.tsv"));
        assertTrue(
                effects.lines()
                        .anyMatch(
                                line ->
                                        line.contains("TypeChecks.reflectedInstance(")
                                                && line.contains("bounded")),
                effects);
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
