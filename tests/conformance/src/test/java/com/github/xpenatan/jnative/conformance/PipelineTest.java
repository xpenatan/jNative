package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {17, 25})
    void bytecodeBuildsAndMatchesJvmIncludingCAndCppImports(int release) throws Exception {
        Path source = temporary.resolve("Arithmetic.java");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.*;
                        @NativeInclude("arithmetic.h")
                        public class Arithmetic {
                            @NativeImport("c_add")
                            static int add(int a, int b) { return a + b; }
                            @NativeImport("cpp_multiply")
                            static long multiply(long a, long b) { return a * b; }
                            static int sum(int count) {
                                int result = 0;
                                for (int i = 0; i < count; ++i) result += i;
                                return result;
                            }
                            static int select(int key) {
                                switch (key) { case -5: return 10; case 1: return 20; case 25: return 30; default: return 40; }
                            }
                            public static void main(String[] args) {
                                System.out.println("Hello native ðŸŒŽ");
                                System.out.println(sum(100));
                                System.out.println(add(Integer.MAX_VALUE, 1));
                                System.out.println(multiply(Long.MAX_VALUE, 2));
                                System.out.println(select(-5) + select(1) + select(25) + select(7));
                                int min = Integer.MIN_VALUE;
                                System.out.println(min / -1);
                                System.out.println(min % -1);
                                System.out.println(-1 >>> 33);
                                System.out.println(-32 >> 2);
                                System.out.println(1L << 63);
                                System.out.println((int)Double.NaN);
                                System.out.println((long)Double.POSITIVE_INFINITY);
                                System.out.println((byte)255);
                                System.out.println((char)0x03a9);
                                System.out.println(sum(5) == 10);
                            }
                        }
                        """);
        Path header = temporary.resolve("arithmetic.h");
        Files.writeString(
                header,
                """
                        #include <stdint.h>
                        #ifdef __cplusplus
                        extern "C" {
                        #endif
                        int32_t c_add(int32_t a, int32_t b);
                        int64_t cpp_multiply(int64_t a, int64_t b);
                        #ifdef __cplusplus
                        }
                        #endif
                        """);
        Path c = temporary.resolve("arithmetic.c");
        Files.writeString(
                c,
                """
                        #include "arithmetic.h"
                        int32_t c_add(int32_t a, int32_t b) {
                            uint32_t v = (uint32_t)a + (uint32_t)b;
                            return v <= INT32_MAX ? (int32_t)v : -1 - (int32_t)~v;
                        }
                        """);
        Path cpp = temporary.resolve("multiply.cpp");
        Files.writeString(
                cpp,
                """
                        #include "arithmetic.h"
                        int64_t cpp_multiply(int64_t a, int64_t b) {
                            uint64_t v = (uint64_t)a * (uint64_t)b;
                            return v <= INT64_MAX ? (int64_t)v : -1 - (int64_t)~v;
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, release);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Arithmetic"),
                        Duration.ofSeconds(15));
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Arithmetic")
                        .buildRoot(temporary.resolve("native project with spaces"))
                        .buildType(release == 17 ? BuildType.DEBUG : BuildType.RELEASE)
                        .nativeFile(header)
                        .nativeFile(c)
                        .nativeFile(cpp)
                        .debugInformation(true);
        var generated = builder.generate();
        assertTrue(
                Files.isRegularFile(
                        generated
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("application.cpp")));
        boolean structuredLoop = false;
        for(Path file : generated.generatedFiles())
            if(file.toString().endsWith(".cpp") && Files.readString(file).contains("while ("))
                structuredLoop = true;
        assertTrue(structuredLoop);
        assertFalse(Files.exists(generated.request().releaseDirectory()));
        // A fresh builder compiles the captured generation with no Java input settings.
        var compiled = NativeBuilder.create().compile(generated);
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(15));
        assertEquals(0, expected.exitCode(), expected.text());
        assertEquals(expected, actual);
    }

    @Test
    void debugAndReleaseExecutablesAndHelpersCoexist() throws Exception {
        Path source = temporary.resolve("Configuration.java"),
                classes = temporary.resolve("classes");
        Files.writeString(
                source,
                "public class Configuration { public static void main(String[] args) { System.out.println(42); } }");
        ProcessHarness.javac(source, classes, 17);
        Path root = temporary.resolve("output"), project = root.resolve("native");
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Configuration")
                        .buildRoot(root)
                        .buildType(BuildType.RELEASE);
        var release = builder.build();
        assertEquals(project.resolve("release"), release.executable().getParent());
        String helperName =
                System.getProperty("os.name").startsWith("Windows")
                        ? "jnative-diagnostics.exe"
                        : "jnative-diagnostics";
        Path releaseHelper = release.executable().resolveSibling(helperName);
        byte[] releaseImage = Files.readAllBytes(release.executable());
        byte[] releaseHelperImage = Files.readAllBytes(releaseHelper);

        var debug = builder.buildType(BuildType.DEBUG).compileProject(project);
        assertEquals(project.resolve("debug"), debug.executable().getParent());
        assertArrayEquals(releaseImage, Files.readAllBytes(release.executable()));
        assertArrayEquals(releaseHelperImage, Files.readAllBytes(releaseHelper));
        Path debugHelper = debug.executable().resolveSibling(helperName);
        byte[] debugImage = Files.readAllBytes(debug.executable());
        byte[] debugHelperImage = Files.readAllBytes(debugHelper);

        var rebuiltRelease = builder.buildType(BuildType.RELEASE).build();
        assertEquals(release.executable(), rebuiltRelease.executable());
        assertArrayEquals(debugImage, Files.readAllBytes(debug.executable()));
        assertArrayEquals(debugHelperImage, Files.readAllBytes(debugHelper));
        var expected = new ProcessHarness.Output(0, "42\n");
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary, List.of(debug.executable().toString()), Duration.ofSeconds(15)));
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(rebuiltRelease.executable().toString()),
                        Duration.ofSeconds(15)));
    }

    @Test
    void unsupportedReachablePlatformMethodFailsBeforeWritingProject() throws Exception {
        Path source = temporary.resolve("Unsupported.java");
        Files.writeString(
                source,
                """
                        public class Unsupported {
                            public static void main(String[] args) { Runtime.getRuntime().halt(0); }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        Path output = temporary.resolve("output");
        var error =
                assertThrows(
                        CompilerException.class,
                        () ->
                                NativeBuilder.create()
                                        .classpath(classes)
                                        .mainClass("Unsupported")
                                        .buildRoot(output)
                                        .generate());
        assertTrue(error.getMessage().contains("java.lang.Runtime.getRuntime"));
        assertTrue(error.getMessage().contains("Unsupported.main"));
        assertFalse(Files.exists(output));
    }
}
