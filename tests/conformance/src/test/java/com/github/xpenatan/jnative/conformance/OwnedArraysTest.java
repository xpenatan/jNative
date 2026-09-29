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

class OwnedArraysTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void privateArraysPreserveAliasesThreadsAndNativeExposure(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("Ownership.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.*;
                        import com.github.xpenatan.jnative.interop.NativeInclude;
                        @NativeInclude("jnative_imports.h")
                        public class Ownership {
                            static class Owned {
                                private final float[] values;
                                Owned(int size) { values = new float[size]; }
                                void add(float amount) { for (int i = 0; i < values.length; i++) values[i] += amount + i; }
                                void writeRange(int start, int end) { for (int i = start; i < end; i++) values[i] = i + 100; }
                                float read(int index) { return values[index]; }
                                int size() { return values.length; }
                            }
                            static class Escaped {
                                private final float[] values = new float[2];
                                float[] expose() { return values; }
                            }
                            static class Internal {
                                final int[] values = new int[2];
                                void fill() { for (int i = 0; i < values.length; i++) values[i] = i + 1; }
                            }
                            static class Derived extends Internal { int read() { return values[1]; } }
                            static class AliasParent { protected final int[] values = new int[1]; }
                            static class AliasChild extends AliasParent { int[] expose() { return values; } }
                            static class Shared {
                                private final int[] values = new int[1];
                                void add() { values[0]++; }
                                int read() { return values[0]; }
                            }
                            static class Aliased {
                                private final int[] first, second;
                                Aliased() { int[] array = new int[1]; first = array; second = array; }
                                int run() { first[0] = 19; return second[0]; }
                            }
                            static class Exposed {
                                private final int[] values = new int[1];
                                int read() { return values[0]; }
                            }
                            @NativeImport("inspect_owned_argument") static void inspect(Exposed object) {}
                            static class Exported {
                                private final int[] values = new int[1];
                                int read() { return values[0]; }
                            }
                            @NativeExport("ownership_read") static int exported() { return new Exported().read(); }
                            static class WorkerOwned {
                                private final int[] values = new int[1];
                                int read() { return values[0]; }
                            }
                            static class Local extends ThreadLocal<Integer> {
                                protected Integer initialValue() { return new WorkerOwned().read(); }
                            }
                            public static void main(String[] args) throws Exception {
                                Owned owned = new Owned(7);
                                for (int i = 0; i < 30; i++) { owned.add(.25f); System.gc(); }
                                for (int i = 0; i < owned.size(); i++) System.out.println(owned.read(i));
                                try { owned.read(7); } catch (ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
                                try { owned.writeRange(5, 9); } catch (ArrayIndexOutOfBoundsException expected) { System.out.println("range"); }
                                System.out.println(owned.read(5)); System.out.println(owned.read(6));
                                owned.writeRange(Integer.MAX_VALUE, Integer.MIN_VALUE);
                                owned.writeRange(-1, -2);
                                try { owned.writeRange(-1, 0); } catch (ArrayIndexOutOfBoundsException expected) { System.out.println("negative range"); }
                                try { new Owned(-1); } catch (NegativeArraySizeException expected) { System.out.println("negative"); }
                                System.out.println(new Owned(0).size());
                                Escaped escaped = new Escaped(); escaped.expose()[1] = -0.0f;
                                Derived internal = new Derived(); internal.fill(); System.out.println(internal.read());
                                AliasChild inherited = new AliasChild(); inherited.expose()[0] = 123; System.out.println(inherited.values[0]);
                                System.out.println(Float.floatToRawIntBits(escaped.expose()[1]));
                                System.out.println(new Aliased().run());
                                Shared shared = new Shared(); shared.add();
                                Thread worker = new Thread(() -> {
                                    for (int i = 0; i < 100; i++) { shared.add(); System.gc(); }
                                    if (new Local().get() != 0) throw new IllegalStateException("worker callback");
                                });
                                worker.start(); worker.join(); System.out.println(shared.read());
                                Exposed exposed = new Exposed(); inspect(exposed); System.out.println(exposed.read());
                                System.out.println(exported());
                                System.out.println(new Local().get());
                            }
                        }
                        """);
        Path nativeSource = temporary.resolve("inspect.cpp");
        Files.writeString(
                nativeSource,
                """
                        #include "jn_abi.h"
                        extern "C" void inspect_owned_argument(jn_handle argument) {}
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Ownership"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Ownership")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType)
                        .nativeFile(nativeSource);
        var generated = builder.generate();
        String report =
                Files.readString(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("array-ownership.tsv"));
        assertTrue(report.contains("Ownership$Owned.values[F\tplain"), report);
        assertFalse(report.contains("Ownership$Escaped."), report);
        assertTrue(report.contains("Ownership$Internal.values[I\tplain"), report);
        assertFalse(report.contains("Ownership$AliasParent."), report);
        assertFalse(report.contains("Ownership$Shared."), report);
        assertFalse(report.contains("Ownership$Aliased."), report);
        assertFalse(report.contains("Ownership$Exposed."), report);
        assertFalse(report.contains("Ownership$Exported."), report);
        assertFalse(report.contains("Ownership$WorkerOwned."), report);
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
