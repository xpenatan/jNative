package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.cli.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class NativeExportTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @Test
    void removedGenerationOptionIsRejectedBeforeWritingProject() throws Exception {
        var captured = new ByteArrayOutputStream();
        var log = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Path output = temporary.resolve("output");
        assertEquals(
                2,
                Main.execute(
                        new String[]{
                                "generate",
                                "--classpath",
                                temporary.toString(),
                                "--main",
                                "Example",
                                "--output",
                                output.toString(),
                                "--optimization",
                                "BALANCED"
                        },
                        log,
                        log));
        assertTrue(
                captured.toString().contains("Unknown option or missing value: --optimization"),
                captured.toString());
        assertFalse(Files.exists(output));
    }

    @Test
    void exportsEditedSourcesAndUserExtensionsWithoutJavaInputs() throws Exception {
        Path source = temporary.resolve("Editable.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.*;
                        public class Editable {
                            int value;
                            Editable(int value) { this.value = value; }
                            int getValue() { return value; }
                            @NativeExport("retained_answer") static int answer() { return new Editable(42).getValue(); }
                            public static void main(String[] args) { System.out.println(answer()); }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Editable")
                        .buildRoot(temporary.resolve("generated"))
                        .generatedSourcesDirectory(temporary.resolve("custom sources Ω"));
        var generation = builder.generate();
        Path project = generation.request().buildRoot().resolve("native");
        String firstManifest = Files.readString(project.resolve("jnative.manifest"));
        builder.generate();
        assertEquals(
                firstManifest,
                Files.readString(project.resolve("jnative.manifest")),
                "Repeatable source generation");
        Path unit =
                generation.request().generatedSourcesDirectory().resolve("classes/Editable.cpp");
        String original = Files.readString(unit);
        assertTrue(original.contains("42"));
        Files.writeString(unit, original.replace("initialize(42)", "initialize(99)"));
        assertNotEquals(original, Files.readString(unit), "Real C++ application edit");
        var refused = assertThrows(CompilerException.class, builder::generate);
        assertTrue(refused.getMessage().contains("JN4002"));
        assertEquals(
                firstManifest,
                Files.readString(project.resolve("jnative.manifest")),
                "Failure must not publish a manifest");

        Path user = project.resolve("user");
        Files.createDirectories(user);
        Files.writeString(
                user.resolve("extra.cpp"),
                """
                        #include "application.hpp"
                        extern "C" int handwritten_value() {
                            jnative::LocalRoot<generated::Editable> object(generated::Editable::create(7));
                            jnative::Heap::instance().collect();
                            return object->getValue();
                        }
                        """);
        Files.writeString(
                user.resolve("CMakeLists.txt"),
                "target_sources(jnative_app PRIVATE \""
                        + "$"
                        + "{CMAKE_CURRENT_LIST_DIR}/extra.cpp\")\n");
        Path application = generation.request().generatedSourcesDirectory().resolve("launcher.cpp");
        Files.writeString(
                application,
                "extern \"C\" int handwritten_value();\n"
                        + Files.readString(application)
                        .replace(
                                "int status = 0;",
                                "int status = 0; std::cout << handwritten_value() << '\\n';"));
        Path destination = temporary.resolve("export build root/native");
        NativeProject snapshot = builder.exportProject(generation, destination);
        assertTrue(snapshot.editable());
        assertThrows(CompilerException.class, () -> builder.exportProject(generation, destination));
        assertFalse(
                Files.readString(destination.resolve("CMakeLists.txt")).contains("custom sources"));
        assertFalse(
                Files.readString(destination.resolve("jnative-project.properties"))
                        .contains(temporary.toString()));
        Files.move(classes, temporary.resolve("input classes retired"));
        Files.move(source, temporary.resolve("input source retired.txt"));
        var nativeResult =
                NativeBuilder.create().buildType(BuildType.RELEASE).compileProject(destination);
        assertEquals(
                new ProcessHarness.Output(0, "7\n99\n"),
                ProcessHarness.run(
                        temporary,
                        List.of(nativeResult.executable().toString()),
                        Duration.ofSeconds(20)));
        var overwriteExport =
                NativeBuilder.create()
                        .classpath(temporary.resolve("input classes retired"))
                        .mainClass("Editable")
                        .buildRoot(destination.getParent());
        assertThrows(CompilerException.class, overwriteExport::generate);
        assertTrue(
                Files.readString(destination.resolve("src/classes").resolve(unit.getFileName()))
                        .contains("99"));
    }

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void cliExportsAnExistingProjectAndStandaloneCmakeBuildsIt(BuildType buildType)
            throws Exception {
        Path source = temporary.resolve("Command.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                "public class Command { public static void main(String[] args) { System.out.println(123); } }");
        ProcessHarness.javac(source, classes, 17);
        var captured = new ByteArrayOutputStream();
        var log = new PrintStream(captured, true, StandardCharsets.UTF_8);
        Path output = temporary.resolve("generated"),
                snapshot = temporary.resolve("command export");
        assertEquals(
                0,
                Main.execute(
                        new String[]{
                                "generate",
                                "--classpath",
                                classes.toString(),
                                "--main",
                                "Command",
                                "--output",
                                output.toString(),
                                "--console-mode",
                                "PAUSE_ON_EXIT"
                        },
                        log,
                        log),
                captured.toString());
        assertEquals(
                0,
                Main.execute(
                        new String[]{
                                "export",
                                "--project",
                                output.resolve("native").toString(),
                                "--destination",
                                snapshot.toString()
                        },
                        log,
                        log),
                captured.toString());
        assertEquals(
                2,
                Main.execute(
                        new String[]{
                                "compile", "--project", snapshot.toString(), "--main", "ignored"
                        },
                        log,
                        log),
                "Reject ignored/inapplicable options");
        Files.move(classes, temporary.resolve("retired classes"));
        boolean windows =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
        Path build = snapshot.resolve("direct-build");
        var configured =
                ProcessHarness.run(
                        temporary,
                        List.of(
                                "cmake",
                                "-S",
                                snapshot.toString(),
                                "-B",
                                build.toString(),
                                "-G",
                                windows ? "MinGW Makefiles" : "Unix Makefiles",
                                "-DCMAKE_BUILD_TYPE="
                                        + (buildType == BuildType.DEBUG ? "" : "Release")),
                        Duration.ofSeconds(60),
                        Map.of("JAVA_HOME", temporary.resolve("no-jdk").toString()));
        assertEquals(0, configured.exitCode(), configured.text());
        var compiled =
                ProcessHarness.run(
                        temporary,
                        List.of("cmake", "--build", build.toString(), "--parallel", "2"),
                        Duration.ofSeconds(90),
                        Map.of("JAVA_HOME", temporary.resolve("no-jdk").toString()));
        assertEquals(0, compiled.exitCode(), compiled.text());
        assertEquals(
                new ProcessHarness.Output(0, "123\n"),
                ProcessHarness.run(
                        temporary,
                        List.of(
                                snapshot.resolve(buildType.name().toLowerCase(Locale.ROOT))
                                        .resolve(windows ? "app.exe" : "app")
                                        .toString()),
                        Duration.ofSeconds(20)));
    }

    @Test
    void regenerationRemovesOnlyUneditedObsoleteOwnedFiles() throws Exception {
        Path source = temporary.resolve("Changing.java"), classes = temporary.resolve("classes");
        String before =
                """
                        public class Changing {
                            static class Old { int value() { return 3; } }
                            public static void main(String[] args) { System.out.println(new Old().value()); }
                        }
                        """;
        Files.writeString(source, before);
        ProcessHarness.javac(source, classes, 17);
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Changing")
                        .buildRoot(temporary.resolve("output"));
        var generation = builder.generate();
        Path old =
                generation.generatedFiles().stream()
                        .filter(
                                p ->
                                        p.getFileName().toString().startsWith("Changing")
                                                && p.getFileName().toString().contains("Old")
                                                && p.toString().endsWith(".cpp"))
                        .findFirst()
                        .orElseThrow();
        Path handwritten = old.resolveSibling("my_extension.cpp");
        Files.writeString(handwritten, "// user-owned file\n");
        String original = Files.readString(old);
        Files.writeString(old, original + "// local edit\n");
        Files.writeString(
                source,
                "public class Changing { public static void main(String[] args) { System.out.println(4); } }");
        ProcessHarness.javac(source, classes, 17);
        assertThrows(CompilerException.class, builder::generate);
        assertTrue(Files.exists(old));
        Files.writeString(old, original);
        builder.generate();
        assertFalse(Files.exists(old));
        assertEquals("// user-owned file\n", Files.readString(handwritten));
    }
}
