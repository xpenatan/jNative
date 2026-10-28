package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BorrowedArraysTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void borrowedCallsPreserveRepresentationsAliasingFailuresAndSharedFallbacks(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Borrowed.java");
        Files.writeString(source, """
                import com.github.xpenatan.jnative.interop.NativeImport;
                import com.github.xpenatan.jnative.interop.NativeImport.Invocation;
                import com.github.xpenatan.jnative.interop.NativeInclude;
                import java.nio.ByteBuffer;
                import java.nio.ByteOrder;
                import java.nio.FloatBuffer;
                @NativeInclude("borrowed_bridge.hpp")
                public class Borrowed {
                    static Object published;
                    @NativeImport(value="borrowed::sum", managed=true, borrowedArrays={0})
                    static long nativeSum(float[] values) { return sum(values); }
                    @NativeImport(value="borrowed::sum", managed=true)
                    static long unknown(float[] values) { return sum(values); }
                    static long sum(float[] values) {
                        long sum = 0;
                        for(int i = 0; i < values.length; i++) sum += Float.floatToRawIntBits(values[i]);
                        return sum;
                    }
                    static void transfer(float[] values, FloatBuffer buffer) {
                        buffer.clear().put(values);
                        System.gc();
                        for(int i = 0; i < values.length; i++)
                            if(Float.floatToRawIntBits(buffer.get(i)) != Float.floatToRawIntBits(values[i]))
                                throw new IllegalStateException("nio bits");
                        buffer.flip().get(values);
                    }
                    static void overlap(float[] values) {
                        System.arraycopy(values, 0, values, 1, values.length - 1);
                        System.arraycopy(values, 1, values, 0, values.length - 1);
                    }
                    static void failed(float[] values) {
                        long before = sum(values);
                        try { System.arraycopy(values, -1, values, 0, 100); throw new IllegalStateException("bounds"); }
                        catch(IndexOutOfBoundsException expected) { }
                        try { System.arraycopy(values, 0, values, 1, values.length); throw new IllegalStateException("end"); }
                        catch(IndexOutOfBoundsException expected) { }
                        if(sum(values) != before) throw new IllegalStateException("partial failed copy");
                    }
                    static class Owner {
                        private final float[] values = new float[1025];
                        long run() {
                            for(int i = 0; i < values.length; i++) values[i] = Float.intBitsToFloat(
                                    i == 0 ? 0x80000000 : i == 1 ? 0x7fc12345 : 0x3f800000 + i);
                            for(boolean direct : new boolean[]{false, true})
                                for(ByteOrder order : new ByteOrder[]{ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN}) {
                                    ByteBuffer bytes = direct ? ByteBuffer.allocateDirect(values.length * 4 + 1)
                                            : ByteBuffer.allocate(values.length * 4 + 1);
                                    FloatBuffer buffer = bytes.position(1).slice().order(order).asFloatBuffer();
                                    transfer(values, buffer);
                                }
                            failed(values); overlap(values);
                            if(nativeSum(values) != sum(values)) throw new IllegalStateException("native bits");
                            return sum(values);
                        }
                    }
                    static class Unknown {
                        private final float[] values = new float[]{7};
                        long run() { return unknown(values); }
                    }
                    static class Returned {
                        private final float[] values = new float[]{9};
                        float[] escape() { return identity(values); }
                    }
                    static class AccessorOwner {
                        private final float[] values = new float[]{17};
                        float[] values() { return values; }
                    }
                    static class EscapedGetter {
                        private final float[] values = new float[]{19, 20, 21, 22};
                        float[] values() { return values; }
                        void touch() {
                            float[] alias = values;
                            alias[0] = 23; alias[1] = 24; alias[2] = 25; alias[3] = 26;
                        }
                    }
                    static class CallbackOwner {
                        private final float[] values = new float[]{37};
                        float[] values() { return values; }
                    }
                    @NativeImport(value="borrowed::callbackValue", managed=true, runtimeOnly=true,
                            callbacksSynchronous=true, callbacks={"Borrowed$CallbackOwner.values()[F"})
                    static float[] callbackValue(CallbackOwner owner) { return owner.values(); }
                    static long staticReader(float[] values) {
                        long result = 0;
                        for(int i = 0; i < values.length; i++) result += Float.floatToRawIntBits(values[i]);
                        return result;
                    }
                    @NativeImport(value="borrowed::callbackRead", managed=true, runtimeOnly=true,
                            callbacksSynchronous=true, callbacks={"Borrowed.staticReader([F)J"},
                            callbackKinds={Invocation.STATIC})
                    static long callbackRead(float[] values) { return staticReader(values); }
                    static long directReader(float[] values) {
                        long result = 0;
                        for(int i = 0; i < values.length; i++) result += Float.floatToRawIntBits(values[i]);
                        return result;
                    }
                    static long direct() {
                        float[] values = new float[]{41};
                        return directReader(values);
                    }
                    interface Getter { float[] values(); }
                    static class FirstGetter implements Getter {
                        private final float[] values = new float[]{23};
                        public float[] values() { return values; }
                    }
                    static class SecondGetter implements Getter {
                        private final float[] values = new float[]{29};
                        public float[] values() { return values; }
                    }
                    static void publishGetter(Getter getter) { published = getter.values(); }
                    static long merged(boolean choose, float[] candidate) {
                        float[] ordinary = new float[]{31};
                        published = ordinary;
                        float[] values = choose ? candidate : ordinary;
                        long result = 0;
                        for(int i = 0; i < values.length; i++) result += Float.floatToRawIntBits(values[i]);
                        return result;
                    }
                    static float[] identity(float[] array) { return array; }
                    interface Consumer { void accept(float[] value); }
                    static class Reader implements Consumer { public void accept(float[] value) { sum(value); } }
                    static class Publisher implements Consumer { public void accept(float[] value) { published = value; } }
                    static class Dispatched {
                        private final float[] values = new float[]{11};
                        void run(Consumer consumer) { consumer.accept(values); }
                    }
                    static class Worker extends Thread {
                        private final float[] values = new float[]{13};
                        public void run() { transfer(values, ByteBuffer.allocateDirect(4).asFloatBuffer()); }
                        long read() { return sum(values); }
                    }
                    static long mixed() {
                        float[] plain = new float[1025], shared = new float[1025];
                        published = shared;
                        for(int i = 0; i < plain.length; i++) plain[i] = i;
                        System.arraycopy(plain, 0, shared, 0, plain.length);
                        System.arraycopy(shared, 0, plain, 0, plain.length);
                        if(sum(plain) != sum(shared)) throw new IllegalStateException("mixed");
                        failed(plain);
                        return sum(plain);
                    }
                    public static void main(String[] args) throws Exception {
                        System.out.println(new Owner().run());
                        System.out.println(new Unknown().run());
                        System.out.println(sum(new Returned().escape()));
                        Dispatched dispatched = new Dispatched();
                        dispatched.run(new Reader()); dispatched.run(new Publisher());
                        System.out.println(sum((float[])published));
                        System.out.println(staticReader(new float[]{43}));
                        float[] callbackInput = new float[]{47}; published = callbackInput;
                        System.out.println(callbackRead(callbackInput));
                        System.out.println(direct());
                        Worker worker = new Worker(); worker.start(); worker.join(); System.out.println(worker.read());
                        System.out.println(mixed());
                        System.out.println(sum(new AccessorOwner().values()));
                        EscapedGetter escaped = new EscapedGetter(); escaped.touch();
                        published = escaped.values();
                        System.out.println(sum((float[])published));
                        publishGetter(new FirstGetter()); publishGetter(new SecondGetter());
                        System.out.println(sum((float[])published));
                        System.out.println(merged(true, new AccessorOwner().values()));
                        System.out.println(merged(false, new AccessorOwner().values()));
                        published = callbackValue(new CallbackOwner());
                        System.out.println(sum((float[])published));
                    }
                }
                """);
        Path header = temporary.resolve("borrowed_bridge.hpp");
        Files.writeString(header, """
                #pragma once
                #include "jn_runtime.hpp"
                namespace borrowed {
                template<class Callback> std::int64_t callbackRead(jnative::Object* values, Callback callback) {
                    return callback(values);
                }
                template<class Callback> jnative::Object* callbackValue(jnative::Object* owner, Callback callback) {
                    return callback(owner);
                }
                inline std::int64_t sum(jnative::Object* object) {
                    jnative::LocalRoot<> root(object);
                    jnative::PrimitiveArrayView<float, true> values(object);
                    jnative::Heap::instance().collect();
                    std::int64_t result = 0;
                    for (int i = 0; i < values.length(); ++i) {
                        float value = values.get(i);
                        std::int32_t bits;
                        std::memcpy(&bits, &value, sizeof(bits));
                        result += bits;
                    }
                    return result;
                }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary, List.of(ProcessHarness.java(), "-cp", classes.toString(), "Borrowed"),
                Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("Borrowed")
                .nativeFile(header).buildRoot(temporary.resolve("out")).buildType(buildType);
        var generated = builder.generate();
        Path generatedSources = generated.request().generatedSourcesDirectory();
        String ownership = Files.readString(generatedSources.resolve("array-ownership.tsv"));
        assertTrue(ownership.contains("Borrowed$Owner.values[F\tplain"), ownership);
        assertTrue(ownership.contains("Borrowed$AccessorOwner.values[F\tplain"), ownership);
        for(String type : List.of("Unknown", "Returned", "Dispatched", "Worker", "EscapedGetter", "FirstGetter", "SecondGetter", "CallbackOwner"))
            assertFalse(ownership.contains("Borrowed$" + type + ".values"), ownership);
        String code = ProcessHarness.generatedClassSource(generatedSources, "Borrowed");
        assertTrue(code.contains("new_confined_array<float>"), code);
        int directReaderStart = code.indexOf("std::int64_t Borrowed::directReader(");
        String directReader = code.substring(directReaderStart, code.indexOf("\n}\n", directReaderStart));
        assertTrue(directReader.contains("ConfinedArrayView<float>"), directReader);
        int callbackStart = code.indexOf("std::int64_t Borrowed::staticReader(");
        String callbackReader = code.substring(callbackStart, code.indexOf("\n}\n", callbackStart));
        assertFalse(callbackReader.contains("ConfinedArrayView<float>"), callbackReader);
        String ordinary = ProcessHarness.generatedClassSource(generatedSources, "Borrowed$EscapedGetter");
        assertTrue(ordinary.contains("PrimitiveArrayView<float, false>"), ordinary);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
