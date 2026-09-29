package com.github.xpenatan.jnative;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NativeBuildRequestTest {
    @TempDir
    Path temporary;

    @Test
    void snapshotsCollectionsAndNormalizesPathsWithoutCreatingOutput() {
        Path first = temporary.resolve("classes");
        Path second = temporary.resolve("dependency.jar");
        var entries = new ArrayList<>(List.of(first.resolve("../classes"), second, first));
        NativeBuilder builder =
                NativeBuilder.create()
                        .classpath(entries)
                        .mainClass("example.Main")
                        .buildRoot(temporary.resolve("unused/../output"));
        entries.clear();

        NativeBuildRequest request = builder.request();
        builder.classpath(temporary.resolve("later.jar"));

        assertEquals(List.of(first, second), request.classpath());
        assertThrows(UnsupportedOperationException.class, () -> request.classpath().clear());
        assertEquals(temporary.resolve("output/native/src"), request.generatedSourcesDirectory());
        assertEquals(temporary.resolve("output/native/debug"), request.releaseDirectory());
        assertEquals(ConsoleMode.NORMAL, request.consoleMode());
        assertFalse(Files.exists(request.buildRoot()));
    }

    @Test
    void outputDirectoryFollowsTheSnapshottedBuildType() {
        var builder = configured();
        var debug = builder.request();
        var release = builder.buildType(BuildType.RELEASE).request();
        var debugAgain = builder.buildType(BuildType.DEBUG).request();

        assertEquals(temporary.resolve("output/native/debug"), debug.releaseDirectory());
        assertEquals(temporary.resolve("output/native/release"), release.releaseDirectory());
        assertEquals(debug.releaseDirectory(), debugAgain.releaseDirectory());
        assertFalse(Files.exists(debug.buildRoot()));
    }

    @Test
    void explicitOutputPathsAndOptionsReachTheRequest() {
        NativeBuildRequest request =
                configured()
                        .generatedSourcesDirectory(Path.of("build/custom-c/../sources"))
                        .releaseDirectory(Path.of("build/native-output"))
                        .targetFileName("hello-world")
                        .buildType(BuildType.RELEASE)
                        .consoleMode(ConsoleMode.PAUSE_ON_EXIT)
                        .debugInformation(true)
                        .request();

        assertEquals(
                Path.of("build/sources").toAbsolutePath().normalize(),
                request.generatedSourcesDirectory());
        assertEquals(
                Path.of("build/native-output").toAbsolutePath().normalize(),
                request.releaseDirectory());
        assertEquals("hello-world", request.targetFileName());
        assertEquals(BuildType.RELEASE, request.buildType());
        assertEquals(ConsoleMode.PAUSE_ON_EXIT, request.consoleMode());
        assertTrue(request.debugInformation());
    }

    @Test
    void publicRequestConstructorAlsoMakesADefensiveCopy() {
        var entries = new ArrayList<>(List.of(temporary.resolve("classes")));
        var request =
                new NativeBuildRequest(
                        entries,
                        "example.Main",
                        temporary.resolve("output"),
                        null,
                        null,
                        "app",
                        BuildType.DEBUG,
                        false);
        entries.clear();

        assertEquals(List.of(temporary.resolve("classes")), request.classpath());
        assertEquals(temporary.resolve("output/native/debug"), request.releaseDirectory());
        assertEquals(ConsoleMode.NORMAL, request.consoleMode());
    }

    @Test
    void consoleModeIsSnapshottedAndRejectsNull() {
        var builder = configured().consoleMode(ConsoleMode.PAUSE_ON_EXIT);
        var request = builder.request();
        builder.consoleMode(ConsoleMode.NORMAL);
        assertEquals(ConsoleMode.PAUSE_ON_EXIT, request.consoleMode());
        assertEquals(ConsoleMode.NORMAL, builder.request().consoleMode());
        assertThrows(NullPointerException.class, () -> builder.consoleMode(null));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "",
                    " ",
                    ".",
                    "..",
                    "../app",
                    "dir/app",
                    "dir\\app",
                    "C:app",
                    "bad?name",
                    "app."
            })
    void rejectsInvalidTargetNames(String name) {
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> configured().targetFileName(name).request());
        assertTrue(error.getMessage().contains("targetFileName"));
    }

    @Test
    void requiresMainClassAndBuildRoot() {
        var withoutMain =
                NativeBuilder.create().classpath(temporary).buildRoot(temporary.resolve("output"));
        var withoutRoot = NativeBuilder.create().classpath(temporary).mainClass("example.Main");

        assertTrue(
                assertThrows(IllegalArgumentException.class, withoutMain::request)
                        .getMessage()
                        .contains("mainClass"));
        assertTrue(
                assertThrows(IllegalArgumentException.class, withoutRoot::request)
                        .getMessage()
                        .contains("buildRoot"));
        assertThrows(IllegalArgumentException.class, () -> configured().mainClass("  ").request());
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void currentJvmClasspathUsesHostSeparatorAndTreatsEmptyEntriesAsCurrentDirectory() {
        String previous = System.getProperty("java.class.path");
        Path classes = temporary.resolve("classes with spaces");
        try {
            System.setProperty(
                    "java.class.path", classes + File.pathSeparator + File.pathSeparator + classes);
            var request =
                    NativeBuilder.create()
                            .classpathFromCurrentJvm()
                            .mainClass("example.Main")
                            .buildRoot(temporary.resolve("output"))
                            .request();

            assertEquals(
                    List.of(classes, Path.of("").toAbsolutePath().normalize()),
                    request.classpath());
        } finally {
            if(previous == null) {
                System.clearProperty("java.class.path");
            }
            else {
                System.setProperty("java.class.path", previous);
            }
        }
    }

    private NativeBuilder configured() {
        return NativeBuilder.create()
                .classpath(temporary.resolve("classes"))
                .mainClass("example.Main")
                .buildRoot(temporary.resolve("output"));
    }
}
