package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class HardeningTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void numericEdgesUnicodeArgumentsAndNativeFloatingState(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Edges.java");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.*;
                        import com.github.xpenatan.jnative.interop.NativeInclude;
                        @NativeInclude("jnative_imports.h")
                        public class Edges {
                            @NativeImport("change_rounding") static void changeRounding() {}
                            @NativeImport("foreign_rounding") static boolean foreignRounding() { return true; }
                            @NativeExport("edge_sum") static double add(double a, double b) { return a + b; }
                            static double separate(double a, double b, double c) { return a * b + c; }
                            public static void main(String[] args) {
                                if (args.length == 0) args = new String[]{"OlÃ¡ Î© ðŸŒŽ æ—¥æœ¬èªž"};
                                System.out.println(args[0]); System.out.println(args[0].length());
                                double[] doubles = {Double.MIN_VALUE, Double.MIN_VALUE * 2, Double.MIN_NORMAL,
                                    Double.MAX_VALUE, 0.0, -0.0, 1e-7, 0.001, 1e7, 1e23, 1.0 / 3,
                                    Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
                                float[] floats = {Float.MIN_VALUE, Float.MIN_VALUE * 2, Float.MIN_NORMAL,
                                    Float.MAX_VALUE, 0.0f, -0.0f, 1e-7f, 0.001f, 1e7f, 1e23f, 1.0f / 3};
                                for (double value : doubles) System.out.println(value);
                                for (float value : floats) System.out.println(value);
                                System.out.println(separate(1.0000000000000002, 0.9999999999999998, -1.0));
                                System.out.println(1.0 / -0.0);
                                System.out.println(-5.5 % 2.0);
                                changeRounding();
                                System.out.println(add(1.0, 0x1.0p-53));
                                System.out.println(add(Double.MIN_VALUE, Double.MIN_VALUE));
                                System.out.println(foreignRounding());
                                System.out.println(add(1.0, 0x1.0p-53));
                            }
                        }
                        """);
        Path cpp = temporary.resolve("floating.cpp");
        Files.writeString(
                cpp,
                """
                        #include "jnative_exports.h"
                        #include <cfenv>
                        #if defined(__SSE2__) || defined(_M_X64)
                        #include <xmmintrin.h>
                        #endif
                        extern "C" void change_rounding() {
                            std::fesetround(FE_UPWARD);
                        #if defined(__SSE2__) || defined(_M_X64)
                            _mm_setcsr(_mm_getcsr() | (1u << 15) | (1u << 6));
                        #endif
                        }
                        extern "C" int32_t foreign_rounding() {
                            change_rounding();
                            double value = 0;
                            if (edge_sum(1.0, 0x1.0p-53, &value) != JN_OK || value != 1.0) return 0;
                            if (std::fegetround() != FE_UPWARD) return 0;
                        #if defined(__SSE2__) || defined(_M_X64)
                            if ((_mm_getcsr() & ((1u << 15) | (1u << 6))) != ((1u << 15) | (1u << 6))) return 0;
                        #endif
                            return 1;
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        String argument = "Olá Ω 🌎 日本語";
        // The Windows JVM launcher narrows command-line arguments through the host codepage.
        // Use the same UTF-16 literal for the reference; test native argv independently.
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Edges"),
                        Duration.ofSeconds(20));
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Edges")
                        .nativeFile(cpp)
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType)
                        .build();
        assertEquals(0, expected.exitCode(), expected.text());
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString(), argument),
                        Duration.ofSeconds(20)));
        if(Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinuxProject(
                    temporary,
                    result.generation().request().buildRoot().resolve("native"),
                    result.generation().request().targetFileName(),
                    expected,
                    1,
                    List.of(argument));
    }

    @Test
    void defaultInterfaceInitializationAndJavaStackTrace() throws Exception {
        Path source = temporary.resolve("Initialize.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class Initialize {
                            static int mark(String name) { System.out.println(name); return 1; }
                            interface Root { int R = mark("root"); default int root() { return 1; } }
                            interface Middle extends Root { int M = mark("middle"); default int middle() { return 2; } }
                            interface Quiet { int Q = mark("should not initialize"); }
                            static class Parent { static int P = mark("parent"); }
                            static class Child extends Parent implements Middle, Quiet { static int C = mark("child"); }
                            static class Custom extends RuntimeException { public String toString() { return "custom cause"; } }
                            static void leaf() { throw new RuntimeException("failure"); }
                            static void nested() { leaf(); }
                            public static void main(String[] args) {
                                System.out.println(new Child().middle());
                                System.out.println(new RuntimeException(new Custom()).getMessage());
                                System.out.println(new RuntimeException((Throwable)null).getMessage());
                                try { nested(); } catch (RuntimeException error) { error.printStackTrace(); }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Initialize"),
                        Duration.ofSeconds(20));
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Initialize")
                        .buildRoot(temporary.resolve("output"))
                        .buildType(BuildType.RELEASE)
                        .stackTraces(StackTraceMode.JAVA)
                        .javaSourceLocations(true)
                        .build();
        assertEquals(0, expected.exitCode(), expected.text());
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(20)));
        if(Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, result, expected, 1);
    }

    @Test
    void malformedInputProducesDiagnosticsBeforeWriting() throws Exception {
        Path classes = Files.createDirectories(temporary.resolve("classes"));
        Path output = temporary.resolve("output");
        var builder =
                NativeBuilder.create().classpath(classes).mainClass("Broken").buildRoot(output);
        Files.write(classes.resolve("Broken.class"), new byte[]{0, 1, 2, 3});
        assertTrue(
                assertThrows(CompilerException.class, builder::generate)
                        .getMessage()
                        .contains("JN1004"));
        var writer = new ClassWriter(0);
        writer.visit(61, Opcodes.ACC_PUBLIC, "Broken", null, "Broken", null);
        writer.visitEnd();
        Files.write(classes.resolve("Broken.class"), writer.toByteArray());
        assertTrue(
                assertThrows(CompilerException.class, builder::generate)
                        .getMessage()
                        .contains("Cyclic class hierarchy"));
        assertFalse(
                Files.exists(output.resolve("native")),
                "Invalid bytecode must not leave a generated project");
        for(String invalid :
                List.of("CON", "nul.txt", "com1", "LPT2.data", "../escape", "trailing."))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            NativeBuilder.create()
                                    .classpath(classes)
                                    .mainClass("Broken")
                                    .buildRoot(output)
                                    .targetFileName(invalid)
                                    .request());
    }

    @Test
    void failedNativeBuildCannotReturnAnEarlierExecutable() throws Exception {
        Path source = temporary.resolve("Failure.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                "public class Failure { public static void main(String[] args) { System.out.println(1); } }");
        ProcessHarness.javac(source, classes, 17);
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Failure")
                        .buildRoot(temporary.resolve("output"));
        var result = builder.build();
        assertTrue(Files.exists(result.executable()));
        Path application =
                result.generation()
                        .request()
                        .generatedSourcesDirectory()
                        .resolve("application.cpp");
        Files.writeString(
                application,
                Files.readString(application) + "\n#error deliberate native build failure\n");
        var failure =
                assertThrows(CompilerException.class, () -> builder.compile(result.generation()));
        assertTrue(failure.getMessage().contains("JN3002"), failure.getMessage());
        assertTrue(
                failure.getMessage().contains("deliberate native build failure"),
                failure.getMessage());
        assertTrue(
                Files.exists(result.executable()),
                "Old files may remain, but failure never returns them as a successful result");
    }
}
