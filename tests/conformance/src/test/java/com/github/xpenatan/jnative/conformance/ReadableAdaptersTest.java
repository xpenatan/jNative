package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ReadableAdaptersTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void namedCallsRemainEditableWithoutJava(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Api.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        package demo;
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        @com.github.xpenatan.jnative.interop.NativeInclude("jnative_imports.h")
                        public class Api {
                            interface Operation {
                                int apply(int value);
                                int apply(boolean value);
                                default int twice(int value) { return apply(value) * 2; }
                            }
                            static class Worker implements Operation {
                                volatile int total;
                                public int apply(int value) { return value + 10; }
                                public int apply(boolean value) { return value ? 5 : 6; }
                                int viaDefault() { return Operation.super.twice(2); }
                                int add(int value) { total += value; return total; }
                                public String toString() { System.gc(); return "worker"; }
                            }
                            static class Local extends ThreadLocal<String> {
                                protected String initialValue() { System.gc(); return "local"; }
                            }
                            @NativeImport("adapter_double")
                            static int nativeDouble(int value) { return value * 2; }
                            public static void main(String[] args) {
                                Worker worker = new Worker();
                                Operation operation = worker;
                                System.out.println(operation.apply(3));
                                System.out.println(operation.apply(true));
                                System.out.println(worker.viaDefault());
                                System.out.println(worker.add(4));
                                System.out.println(worker.add(5));
                                Object object = worker;
                                System.out.println(object);
                                System.out.println("value:" + object);
                                System.out.println(object.getClass().getName());
                                System.out.println(new Local().get());
                                System.out.println(String.valueOf((Object)null));
                                System.out.println(nativeDouble(6));
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, buildType == BuildType.DEBUG ? 17 : 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "demo.Api"),
                        Duration.ofSeconds(20));
        assertEquals(0, expected.exitCode(), expected.text());
        Path nativeSource = temporary.resolve("adapter.cpp");
        Files.writeString(
                nativeSource,
                "#include <cstdint>\nextern \"C\" std::int32_t adapter_double(std::int32_t value) { return value * 2; }\n");
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("demo.Api")
                        .nativeFile(nativeSource)
                        .sourceLayout(SourceLayout.PACKAGE_DIRECTORIES)
                        .buildRoot(temporary.resolve("output"))
                        .buildType(buildType)
                        .debugInformation(true);
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String report = Files.readString(generated.resolve("source-readability.tsv"));
        assertFalse(report.contains("low-level"), report);
        assertTrue(
                report.contains("demo.Api.nativeDouble(I)I\tnative-adapter\tC ABI import"), report);
        String api = Files.readString(generated.resolve("java_api.hpp"));
        assertTrue(
                api.contains(
                        "namespace jnative { namespace java_api { namespace demo { namespace Api_Operation"),
                api);
        assertTrue(api.contains("apply(") && api.contains("apply_2("), api);
        try(var units = Files.walk(generated.resolve("classes"))) {
            for(Path unit : units.filter(p -> p.toString().endsWith(".cpp")).toList()) {
                String cpp = Files.readString(unit);
                assertFalse(
                        cpp.contains("dispatch_j_")
                                || cpp.matches("(?s).*\\bj_[A-Za-z0-9_]+_[0-9a-f]{12}\\b.*"),
                        cpp);
            }
        }
        String worker = Files.readString(generated.resolve("classes/demo/Api_Worker.cpp"));
        assertTrue(
                worker.contains("this->total.set(::jnative::add(this->total.get(), value))"),
                worker);
        var compiled = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(buildType == BuildType.RELEASE) {
            Path destination = temporary.resolve("native export");
            var exported = builder.exportProject(generation, destination);
            Path edited = destination.resolve("src/classes/demo/Api_Worker.cpp");
            Files.writeString(
                    edited,
                    Files.readString(edited)
                            .replace("::jnative::add(value, 10)", "::jnative::add(value, 20)"));
            assertNotEquals(worker, Files.readString(edited));
            Path main = destination.resolve("src/launcher.cpp");
            Files.writeString(
                    main,
                    Files.readString(main)
                            .replace(
                                    "int status = 0;",
                                    """
                                            int status = 0;
                                                generated::initialize_program();
                                                jnative::LocalRoot<generated::demo::Api_Worker> handwritten(generated::demo::Api_Worker::create());
                                                std::cout << jnative::java_api::demo::Api_Operation::apply(handwritten.get(), 7) << '\\n';
                                            """));
            Files.move(classes, temporary.resolve("retired classes"));
            Files.move(source, temporary.resolve("retired source.txt"));
            boolean windows =
                    System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
            Path build = destination.resolve("direct-build");
            Map<String, String> environment =
                    Map.of("JAVA_HOME", temporary.resolve("no-jdk").toString());
            var configured =
                    ProcessHarness.run(
                            temporary,
                            List.of(
                                    "cmake",
                                    "-S",
                                    destination.toString(),
                                    "-B",
                                    build.toString(),
                                    "-G",
                                    windows ? "MinGW Makefiles" : "Unix Makefiles",
                                    "-DCMAKE_BUILD_TYPE=Release"),
                            Duration.ofSeconds(60),
                            environment);
            assertEquals(0, configured.exitCode(), configured.text());
            var built =
                    ProcessHarness.run(
                            temporary,
                            List.of("cmake", "--build", build.toString(), "--parallel", "2"),
                            Duration.ofSeconds(90),
                            environment);
            assertEquals(0, built.exitCode(), built.text());
            var editedExpected =
                    new ProcessHarness.Output(
                            0, "27\n" + expected.text().replaceFirst("13\n5\n24\n", "23\n5\n44\n"));
            assertEquals(
                    editedExpected,
                    ProcessHarness.run(
                            temporary,
                            List.of(
                                    destination
                                            .resolve("release")
                                            .resolve(windows ? "app.exe" : "app")
                                            .toString()),
                            Duration.ofSeconds(30),
                            Map.of("JNATIVE_GC_INTERVAL", "1")));
            if(Boolean.getBoolean("jnative.linux"))
                ProcessHarness.compareLinuxProject(
                        temporary, destination, exported.targetFileName(), editedExpected, 1);
        }
    }
}
