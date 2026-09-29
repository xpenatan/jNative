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

class OwnedFieldsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void primitiveStoragePreservesPublicationReflectionAndNativeExposure(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("FieldOwnership.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.*;
                        import com.github.xpenatan.jnative.interop.NativeInclude;
                        @NativeInclude("jnative_imports.h")
                        public class FieldOwnership {
                            static class Values {
                                public boolean flag;
                                public byte small;
                                public char letter;
                                public short word;
                                public int count;
                                public long wide;
                                public float fraction;
                                public double precise;
                                public volatile int published;
                                public static int global;
                                Object reference;
                                void update() {
                                    flag = !flag; small--; letter++; word--; count++;
                                    wide += 12345678901L; fraction -= .25f; precise += .125;
                                }
                            }
                            static class Derived extends Values {
                                int read() { return count; }
                            }
                            static class SharedParent { int count; long wide; double precise; }
                            static class SharedChild extends SharedParent {
                                volatile boolean ready;
                                void publish() { count = 31; wide = 987654321234L; precise = -0.0; ready = true; }
                            }
                            public static class Reflected { public int count; }
                            static class NativePayload { int count; }
                            static class NativeEnvelope { final NativePayload payload = new NativePayload(); }
                            @NativeImport("inspect_field_envelope") static void inspect(NativeEnvelope value) {}
                            static class Returned { int count; }
                            @NativeExport("field_ownership_return") static Returned exported() { return new Returned(); }
                            static class Callback { int count; }
                            static class Local extends ThreadLocal<Integer> {
                                protected Integer initialValue() { Callback value = new Callback(); return ++value.count; }
                            }
                            public static void main(String[] args) throws Exception {
                                Derived value = new Derived();
                                value.count = Integer.MAX_VALUE - 3; value.wide = Long.MAX_VALUE - 2;
                                value.fraction = -0.0f; value.precise = -0.0;
                                for (int i = 0; i < 13; i++) { value.update(); System.gc(); }
                                System.out.println(value.flag); System.out.println(value.small);
                                System.out.println((int)value.letter); System.out.println(value.word);
                                System.out.println(value.read()); System.out.println(value.wide);
                                System.out.println(Float.floatToRawIntBits(value.fraction));
                                System.out.println(Double.doubleToRawLongBits(value.precise));
                                value.reference = new Object(); value.published = 9; Values.global = 17;
                                System.out.println(value.published + Values.global);
                                SharedChild shared = new SharedChild();
                                Thread worker = new Thread(() -> { shared.publish(); new Local().get(); System.gc(); });
                                worker.start(); while (!shared.ready) Thread.yield();
                                System.out.println(shared.count); System.out.println(shared.wide);
                                System.out.println(Double.doubleToRawLongBits(shared.precise)); worker.join();
                                Reflected reflected = new Reflected();
                                var field = Reflected.class.getField("count"); field.setInt(reflected, 73);
                                System.out.println(reflected.count); System.out.println(field.getInt(reflected));
                                NativeEnvelope envelope = new NativeEnvelope(); inspect(envelope);
                                System.out.println(envelope.payload.count); System.out.println(exported().count);
                                System.out.println(new Local().get());
                            }
                        }
                        """);
        Path nativeSource = temporary.resolve("inspect.cpp");
        Files.writeString(
                nativeSource,
                """
                        #include "jn_abi.h"
                        extern "C" void inspect_field_envelope(jn_handle argument) {}
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "FieldOwnership"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("FieldOwnership")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType)
                        .nativeFile(nativeSource)
                        .reflectClass("FieldOwnership$Reflected");
        var generated = builder.generate();
        String report =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("field-storage.tsv"));
        for(String field :
                List.of(
                        "flagZ",
                        "smallB",
                        "letterC",
                        "wordS",
                        "countI",
                        "wideJ",
                        "fractionF",
                        "preciseD")) {
            assertTrue(report.contains("FieldOwnership$Values." + field + "\tplain"), report);
        }
        assertFalse(report.contains("Values.published"), report);
        assertFalse(report.contains("Values.global"), report);
        assertFalse(report.contains("Values.reference"), report);
        for(String owner :
                List.of(
                        "SharedParent",
                        "SharedChild",
                        "Reflected",
                        "NativePayload",
                        "Returned",
                        "Callback")) {
            assertFalse(report.contains("FieldOwnership$" + owner + "."), report);
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
