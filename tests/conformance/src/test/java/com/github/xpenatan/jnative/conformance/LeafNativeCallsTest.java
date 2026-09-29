package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.CompilerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class LeafNativeCallsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void primitiveLeavesRestoreFloatingStateAndPreserveGeneralCallbacks(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("LeavesNative.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.*;
                        @com.github.xpenatan.jnative.interop.NativeInclude("jnative_imports.h")
                        public class LeavesNative {
                            @NativeImport(value = "leaf_add", leaf = true)
                            static int add(int a, int b) { return a + b; }
                            @NativeImport(value = "leaf_rounding", leaf = true)
                            static void rounding(boolean fail) {
                                if (fail) throw new RuntimeException("leaf failure");
                            }
                            @NativeImport("regular_rounding")
                            static boolean correctRounding() { return true; }
                            @NativeImport("regular_callback")
                            static int regular(int number) { return callback(number); }
                            @NativeExport("leaf_test_callback")
                            static int callback(int number) { System.gc(); return number + 9; }
                            public static void main(String[] args) throws Exception {
                                System.out.println(add(17, 25));
                                rounding(false);
                                System.out.println(correctRounding());
                                try { rounding(true); }
                                catch (RuntimeException e) { System.gc(); System.out.println(e.getMessage()); }
                                System.out.println(correctRounding());
                                System.out.println(regular(6));
                                Thread collector = new Thread(() -> {
                                    for (int i = 0; i < 60; i++) System.gc();
                                });
                                collector.start();
                                int sum = 0;
                                for (int i = 0; i < 10000; i++) sum = add(sum, 3);
                                collector.join();
                                System.out.println(sum);
                            }
                        }
                        """);
        Path nativeFile = temporary.resolve("leaves.cpp");
        Files.writeString(
                nativeFile,
                """
                        #include "jnative_exports.h"
                        #include "jn_runtime.hpp"
                        #include <cfenv>
                        #include <stdexcept>
                        #include <cstdlib>
                        extern "C" int32_t leaf_add(int32_t a, int32_t b) {
                            if (jnative::current_thread()->state != jnative::ThreadState::running_managed) std::abort();
                            return a + b;
                        }
                        extern "C" void leaf_rounding(int32_t fail) {
                            std::fesetround(FE_DOWNWARD);
                            if (fail) throw std::runtime_error("leaf failure");
                        }
                        extern "C" int32_t regular_rounding() {
                            return std::fegetround() == FE_TONEAREST
                                && jnative::current_thread()->state == jnative::ThreadState::in_native;
                        }
                        extern "C" int32_t regular_callback(int32_t number) {
                            int32_t result = 0;
                            if (leaf_test_callback(number, &result) != JN_OK) std::abort();
                            return result;
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "LeavesNative"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("LeavesNative")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType)
                        .nativeFile(nativeFile);
        var generation = builder.generate();
        String code =
                Files.readString(
                        generation
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/LeavesNative.cpp"));
        assertTrue(code.contains("LeafNativeRegion"), code);
        assertTrue(code.contains("::jnative::JavaNativeRegion native_call;"), code);
        var compiled = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    @Test
    void rejectsReferenceSignaturesForLeafImports() throws Exception {
        List<String> declarations =
                List.of(
                        "static native int bad(Object value);",
                        "static native Object bad();",
                        "static native void bad(int[] array);");
        List<String> calls = List.of("bad(null);", "bad();", "bad(null);");
        for(int i = 0; i < declarations.size(); i++) {
            String name = "InvalidLeaf" + i;
            Path source = temporary.resolve(name + ".java");
            Path classes = temporary.resolve("classes" + i);
            Files.writeString(
                    source,
                    "import com.github.xpenatan.jnative.interop.NativeImport;\n"
                            + "@com.github.xpenatan.jnative.interop.NativeInclude(\"jnative_imports.h\")\npublic class "
                            + name
                            + " {\n"
                            + "@NativeImport(value=\"invalid_leaf\", leaf=true) "
                            + declarations.get(i)
                            + "\npublic static void main(String[] args) { "
                            + calls.get(i)
                            + " }\n}");
            ProcessHarness.javac(source, classes, 25);
            var builder =
                    ProcessHarness.behavioralBuilder()
                            .classpath(classes)
                            .mainClass(name)
                            .buildRoot(temporary.resolve("out" + i));
            CompilerException error = assertThrows(CompilerException.class, builder::generate);
            assertTrue(
                    error.getMessage().contains("primitive parameters and return type"),
                    error.getMessage());
        }
    }
}
