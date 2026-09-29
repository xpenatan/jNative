package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NativeLayoutTest {
    @TempDir
    Path temporary;

    private NativeBuilder builder(Path root) throws Exception {
        Path source = temporary.resolve("Player.java");
        Files.writeString(
                source,
                "package game.characters; public class Player { public static void main(String[] args) { System.out.println(7); } }");
        Path classes = Files.createDirectories(temporary.resolve("classes"));
        assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler()
                        .run(null, null, null, "-g", "-d", classes.toString(), source.toString()));
        return NativeBuilder.create()
                .classpath(classes)
                .mainClass("game.characters.Player")
                .buildRoot(root);
    }

    @Test
    void layoutsPreserveNamesAndExportsProtectSelection() throws Exception {
        var a = builder(temporary.resolve("a")).sourceLayout(SourceLayout.PACKAGE_DIRECTORIES);
        var first = a.generate();
        assertTrue(first.readabilityFallbacks().isEmpty());
        var b = builder(temporary.resolve("b")).sourceLayout(SourceLayout.PACKAGE_FILENAME);
        var second = b.generate();
        Path nested =
                first.request()
                        .generatedSourcesDirectory()
                        .resolve("classes/game/characters/Player.cpp");
        Path flat =
                second.request()
                        .generatedSourcesDirectory()
                        .resolve("classes/game.characters.Player.cpp");
        assertEquals(Files.readString(nested), Files.readString(flat));
        var map =
                Json.object(
                        Json.read(
                                Files.readString(
                                        second.request()
                                                .generatedSourcesDirectory()
                                                .resolve("source-map.json"))));
        assertEquals("PACKAGE_FILENAME", map.get("layout"));
        assertTrue(
                Files.readString(
                                second.request()
                                        .generatedSourcesDirectory()
                                        .resolve("java-symbols.tsv"))
                        .contains("classes/game.characters.Player.cpp"));
        var mismatch =
                assertThrows(
                        CompilerException.class,
                        () -> a.sourceLayout(SourceLayout.PACKAGE_FILENAME).generate());
        assertTrue(
                mismatch.getMessage()
                        .contains("recorded PACKAGE_DIRECTORIES, requested PACKAGE_FILENAME"));
        assertTrue(
                mismatch.getMessage()
                        .contains(
                                temporary
                                        .resolve("a/native/jnative-project.properties")
                                        .toString()));
        // Repeating the selected layout, including with a fresh builder, is valid.
        b.generate();
        builder(temporary.resolve("b")).sourceLayout(SourceLayout.PACKAGE_FILENAME).generate();
        var exported = b.exportProject(second, temporary.resolve("export"));
        assertEquals(exported, NativeProjects.open(exported.directory()));
    }

    @Test
    void pathFailuresDoNotCreatePartialProjects() throws Exception {
        var b = builder(temporary.resolve("x".repeat(180)));
        assertTrue(
                assertThrows(CompilerException.class, b::generate).getMessage().contains("JN4010"));
        assertFalse(Files.exists(b.request().buildRoot()));
    }

    @Test
    void nativeDefaultsAreIndependentAndLegacyConstructorRemainsUsable() throws Exception {
        var modern = builder(temporary.resolve("config")).buildType(BuildType.RELEASE).request();
        assertEquals(StackTraceMode.NATIVE, modern.stackTraces());
        assertEquals(NativeSymbols.SEPARATE, modern.nativeSymbols().resolve(modern.buildType()));
        var legacy =
                new NativeBuildRequest(
                        modern.classpath(),
                        modern.mainClass(),
                        modern.buildRoot(),
                        null,
                        null,
                        "game",
                        BuildType.RELEASE,
                        true);
        assertEquals(StackTraceMode.JAVA, legacy.stackTraces());
        assertTrue(legacy.javaSourceLocations());
        assertEquals(CrashReportMode.OFF, legacy.crashReports());
    }
}
