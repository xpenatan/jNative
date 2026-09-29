package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.cli.Main;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

class NativePortabilityTest {
    @TempDir
    Path temporary;

    private NativeBuilder program(String directory) throws Exception {
        Path source = temporary.resolve("Portable.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.NativeExport;
                        public class Portable {
                            public static int total;
                            @NativeExport("portable_add") public static int add(int value) { return value + 7; }
                            public static void main(String[] args) throws Exception {
                                Thread worker = new Thread(() -> total = add(35));
                                worker.start(); worker.join();
                                if (total != 42) throw new IllegalStateException();
                                Object monitor = new Object();
                                synchronized (monitor) {
                                    synchronized (monitor) {
                                        if (!Thread.holdsLock(monitor)) throw new IllegalStateException();
                                    }
                                }
                                if (Thread.holdsLock(monitor)) throw new IllegalStateException();
                                System.out.println("portable output");
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 17);
        return NativeBuilder.create()
                .classpath(classes)
                .mainClass("Portable")
                .exportClass("Portable")
                .buildRoot(temporary.resolve(directory))
                .nativeSymbols(NativeSymbols.NONE)
                .stackTraces(StackTraceMode.NONE)
                .crashReports(CrashReportMode.OFF);
    }

    @Test
    void releaseCanDisableInterproceduralOptimization() throws Exception {
        var result =
                program("without-ipo")
                        .buildType(BuildType.RELEASE)
                        .cmakeDefine("JNATIVE_IPO", "OFF")
                        .build();
        assertEquals("portable output\n", run(result.executable()).text());
        String optimization =
                Files.readString(
                        result.artifact()
                                .buildDirectory()
                                .resolve("jnative-optimization.properties"));
        assertTrue(optimization.contains("ipo.requested=OFF"), optimization);
        assertTrue(optimization.contains("ipo.enabled=FALSE"), optimization);
    }

    @Test
    void argumentsCapabilitiesAndRetainedSourcesUseTheActualTargetConfiguration() throws Exception {
        Path extension = temporary.resolve("CMakeLists.txt");
        Files.writeString(
                extension,
                """
                        if(NOT APP_TEXT STREQUAL "SDK path with spaces")
                            message(FATAL_ERROR "Configure argument order/value was lost: ${APP_TEXT}")
                        endif()
                        file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/observed.txt" "${APP_TEXT}\n${CMAKE_PREFIX_PATH}\n")
                        set_target_properties(jnative_app PROPERTIES SUFFIX ".custom${CMAKE_EXECUTABLE_SUFFIX}")
                        file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/sdk.cpp" [=[
                        extern "C" int portable_sdk_value() { return 42; }
                        ]=])
                        add_library(portable_sdk SHARED "${CMAKE_CURRENT_BINARY_DIR}/sdk.cpp")
                        set_target_properties(portable_sdk PROPERTIES WINDOWS_EXPORT_ALL_SYMBOLS ON
                            RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/sdk"
                            RUNTIME_OUTPUT_DIRECTORY_DEBUG "${CMAKE_CURRENT_BINARY_DIR}/sdk"
                            RUNTIME_OUTPUT_DIRECTORY_RELEASE "${CMAKE_CURRENT_BINARY_DIR}/sdk"
                            LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/sdk"
                            LIBRARY_OUTPUT_DIRECTORY_DEBUG "${CMAKE_CURRENT_BINARY_DIR}/sdk"
                            LIBRARY_OUTPUT_DIRECTORY_RELEASE "${CMAKE_CURRENT_BINARY_DIR}/sdk")
                        set(JNATIVE_RUNTIME_LIBRARY_DIRECTORIES "${CMAKE_CURRENT_BINARY_DIR}/sdk")
                        target_link_libraries(jnative_app PRIVATE portable_sdk)
                        """);
        Path nativeProbe = temporary.resolve("sdk_probe.cpp");
        Files.writeString(
                nativeProbe,
                """
                        #include <cstdlib>
                        extern "C" int portable_sdk_value();
                        namespace {
                        struct SdkCheck { SdkCheck() { if (portable_sdk_value() != 42) std::abort(); } };
                        SdkCheck sdk_check;
                        }
                        """);
        Path sdk = temporary.resolve("SDK with spaces");
        Files.createDirectories(sdk);
        Path toolchain = sdk.resolve("toolchain.cmake");
        Path includedSettings = sdk.resolve("settings.cmake");
        Files.writeString(
                includedSettings, "set(PORTABILITY_TOOLCHAIN_LOADED ON CACHE BOOL \"\" FORCE)\n");
        Files.writeString(toolchain, "include(\"${CMAKE_CURRENT_LIST_DIR}/settings.cmake\")\n");
        var messages = new ArrayList<String>();
        var builder =
                program("arguments")
                        .nativeFile(extension)
                        .nativeFile(nativeProbe)
                        .cmakeToolchain(toolchain)
                        .cmakeArgs("-DAPP_TEXT=first", "-Wdev")
                        .cmakeDefine("APP_TEXT", "SDK path with spaces")
                        .cmakeDefine("CMAKE_PREFIX_PATH", sdk.toString())
                        .cmakeDefine("CMAKE_CXX_STANDARD", "11")
                        .cmakeDefine("CMAKE_CXX_STANDARD_REQUIRED", "ON")
                        .cmakeDefine("CMAKE_CXX_EXTENSIONS", "OFF")
                        .cmakeDefine("JNATIVE_FEATURES", "PORTABLE")
                        .cmakeBuildArgs("--parallel", "1", "--verbose")
                        .buildToolArgs("VERBOSE=1")
                        .log((level, message) -> messages.add(message));
        var generated = builder.generate();
        builder.cmakeDefine("APP_TEXT", "changed after generation");
        var compiled = builder.compile(generated);
        assertEquals(NativeArtifactKind.EXECUTABLE, compiled.artifact().kind());
        assertTrue(compiled.executable().getFileName().toString().contains(".custom"));
        assertEquals("portable output\n", run(compiled.executable()).text());
        Path build = compiled.artifact().buildDirectory();
        assertEquals(
                "SDK path with spaces",
                Files.readAllLines(build.resolve("observed.txt")).getFirst());
        String capabilities =
                Files.readString(build.resolve("jnative-capabilities-Debug.properties"));
        assertTrue(
                capabilities.contains("make_unique=0") && capabilities.contains("charconv=0"),
                capabilities);
        assertTrue(
                Files.readString(build.resolve("CMakeCache.txt"))
                        .contains("PORTABILITY_TOOLCHAIN_LOADED:BOOL=ON"));
        assertTrue(
                messages.stream()
                        .anyMatch(
                                message ->
                                        message.startsWith("CMake compile argv:")
                                                && message.contains("\"--parallel\", \"1\"")
                                                && message.contains("\"--\", \"VERBOSE=1\"")));
        assertNull(compiled.diagnostics());
        assertFalse(Files.exists(build.resolve("helper")));
        assertTrue(
                compiled.outputFiles().stream()
                        .anyMatch(
                                path ->
                                        path.getParent().equals(compiled.executable().getParent())
                                                && path.getFileName()
                                                .toString()
                                                .matches(".*portable_sdk\\.(dll|so)")),
                compiled.outputFiles().toString());
        Path oldArtifact = compiled.executable().getParent().resolve("unrelated-old-build.dll");
        Files.writeString(oldArtifact, "an unrelated retained artifact");
        Files.writeString(
                includedSettings,
                "set(PORTABILITY_TOOLCHAIN_LOADED UPDATED CACHE STRING \"\" FORCE)\n");
        var updatedSdk = builder.compile(generated);
        assertNotEquals(
                build,
                updatedSdk.artifact().buildDirectory(),
                "An included SDK setting changed in place");
        assertTrue(
                Files.readString(updatedSdk.artifact().buildDirectory().resolve("CMakeCache.txt"))
                        .contains("PORTABILITY_TOOLCHAIN_LOADED:STRING=UPDATED"));
        assertFalse(updatedSdk.outputFiles().contains(oldArtifact));
        assertTrue(Files.exists(oldArtifact));
        var skippedBuild =
                assertThrows(
                        CompilerException.class,
                        () ->
                                NativeBuilder.create()
                                        .cmakeDefine("APP_TEXT", "SDK path with spaces")
                                        .cmakeBuildArgs("--target", "help")
                                        .compileProject(
                                                generated.request().buildRoot().resolve("native")));
        assertTrue(
                skippedBuild.getMessage().contains("did not compile jnative_app"),
                skippedBuild.getMessage());

        var exported = builder.exportProject(generated, temporary.resolve("retained project"));
        Path cpp = exported.directory().resolve("src/string_literals.cpp");
        String original = Files.readString(cpp);
        assertTrue(original.contains("portable output"), "Expected the retained literal initializer");
        String edited = original.replace("portable output", "edited output");
        Files.writeString(cpp, edited);
        Files.move(temporary.resolve("classes"), temporary.resolve("retired Java classes"));
        var rebuilt =
                NativeBuilder.create()
                        .cmakeDefine("APP_TEXT", "SDK path with spaces")
                        .cmakeDefine("CMAKE_CXX_STANDARD", "20")
                        .buildType(BuildType.RELEASE)
                        .compileProject(exported.directory());
        assertEquals("edited output\n", run(rebuilt.executable()).text());
        assertEquals(edited, Files.readString(cpp));
        String modern =
                Files.readString(
                        rebuilt.artifact()
                                .buildDirectory()
                                .resolve("jnative-capabilities-Release.properties"));
        assertTrue(modern.contains("make_unique=1") && modern.contains("charconv=1"), modern);
        assertNotEquals(build, rebuilt.artifact().buildDirectory());

        // Retained projects predating the artifact manifest still report their
        // custom target suffix through CMake's File API, without host guessing.
        Path projectCmake = exported.directory().resolve("runtime/jn_project.cmake");
        String cmake = Files.readString(projectCmake);
        Files.writeString(
                projectCmake,
                cmake.substring(
                        0,
                        cmake.indexOf(
                                "file(GENERATE OUTPUT"
                                        + " \"${CMAKE_CURRENT_BINARY_DIR}/jnative-artifact-")));
        var output = new ByteArrayOutputStream();
        int status =
                Main.execute(
                        new String[]{
                                "compile",
                                "--project",
                                exported.directory().toString(),
                                "--cmake-arg=-DAPP_TEXT=old",
                                "--cmake-arg=-DAPP_TEXT=SDK path with spaces",
                                "--cmake-arg",
                                "-DCMAKE_CXX_STANDARD=20",
                                "--cmake-build-arg=--parallel",
                                "--cmake-build-arg=1",
                                "--build-tool-arg=VERBOSE=1"
                        },
                        new PrintStream(output),
                        new PrintStream(output));
        assertEquals(0, status, output.toString());
        assertTrue(output.toString().contains(".custom"), output.toString());
        assertEquals(edited, Files.readString(cpp));
    }

    @ParameterizedTest
    @EnumSource(
            value = NativeArtifactKind.class,
            names = {"STATIC_LIBRARY", "SHARED_LIBRARY"})
    void librariesExposeLifecycleAndCallbacksWithoutOwningTheHostProcess(NativeArtifactKind kind)
            throws Exception {
        var builder = program(kind.name()).outputKind(kind);
        var result = builder.build();
        assertEquals(kind, result.artifact().kind());
        assertTrue(Files.isRegularFile(result.artifact().path()));
        if(kind == NativeArtifactKind.STATIC_LIBRARY) {
            assertTrue(
                    result.outputFiles().stream()
                            .anyMatch(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .contains("jnative_runtime")));
            assertTrue(
                    result.outputFiles().stream()
                            .anyMatch(
                                    path ->
                                            path.getFileName()
                                                    .toString()
                                                    .contains("jnative_platform")));
        }
        assertThrows(IllegalStateException.class, result::executable);
        var exported =
                builder.exportProject(result.generation(), temporary.resolve("export " + kind));
        Path parent = temporary.resolve("parent " + kind);
        Files.createDirectories(parent);
        Files.writeString(
                parent.resolve("CMakeLists.txt"),
                """
                        cmake_minimum_required(VERSION 3.20)
                        project(native_parent LANGUAGES C CXX)
                        set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/bin")
                        add_subdirectory("${NATIVE_PROJECT}" application)
                        add_executable(host host.cpp)
                        target_link_libraries(host PRIVATE jNative::Application)
                        """);
        Files.writeString(
                parent.resolve("host.cpp"),
                """
                        #include "jnative_exports.h"
                        #include <cstdio>
                        int main() {
                            if (jn_app_initialize() != JN_OK) return 1;
                            if (jn_attach_thread() != JN_OK || jn_app_shutdown() != JN_BUSY) return 2;
                            if (jn_detach_thread() != JN_OK) return 3;
                            if (jn_app_main(0, nullptr) != JN_OK) return 4;
                            int32_t value = 0;
                            if (portable_add(9, &value) != JN_OK || value != 16) return 5;
                            std::puts("native host continues");
                            if (jn_app_shutdown() != JN_OK) return 6;
                            if (jn_app_initialize() != JN_SHUTTING_DOWN) return 7;
                            return 0;
                        }
                        """);
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path build = parent.resolve("build");
        var configure =
                ProcessHarness.run(
                        parent,
                        List.of(
                                "cmake",
                                "-S",
                                parent.toString(),
                                "-B",
                                build.toString(),
                                "-G",
                                windows ? "MinGW Makefiles" : "Unix Makefiles",
                                "-DCMAKE_BUILD_TYPE=Release",
                                "-DJNATIVE_OUTPUT_KIND=" + kind,
                                "-DNATIVE_PROJECT=" + exported.directory()),
                        Duration.ofSeconds(60));
        assertEquals(0, configure.exitCode(), configure.text());
        var compile =
                ProcessHarness.run(
                        parent,
                        List.of("cmake", "--build", build.toString(), "--parallel", "2"),
                        Duration.ofSeconds(120));
        assertEquals(0, compile.exitCode(), compile.text());
        assertEquals(
                new ProcessHarness.Output(0, "portable output\nnative host continues\n"),
                run(build.resolve("bin/host" + (windows ? ".exe" : ""))));
    }

    @Test
    void externalProvidersMustDeclareRequestedServices() throws Exception {
        var generated = program("services").generate();
        Path project = generated.request().buildRoot().resolve("native");
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        String desktop =
                Files.readString(
                        project.resolve(
                                "runtime/jn_platform_" + (windows ? "windows" : "linux") + ".cpp"));
        Path provider = project.resolve("user/without_console.cpp");
        Files.createDirectories(provider.getParent());
        Files.writeString(
                provider,
                desktop.substring(0, desktop.indexOf("bool interactive_console()"))
                        + desktop.substring(desktop.indexOf("void exit_process(")));
        var base =
                List.of(
                        "cmake",
                        "-S",
                        project.toString(),
                        "-B",
                        project.resolve("b/services").toString(),
                        "-G",
                        windows ? "MinGW Makefiles" : "Unix Makefiles",
                        "-DCMAKE_BUILD_TYPE=Debug",
                        "-DJNATIVE_FEATURES=PORTABLE",
                        "-DJNATIVE_PLATFORM_SOURCES=" + provider,
                        "-DJNATIVE_PLATFORM_LIBRARIES=" + (windows ? "" : "pthread"),
                        "-DJNATIVE_PLATFORM_SERVICES=threads;synchronization;tls;allocation;floating;files;clocks;output;process-exit");
        var pause = new ArrayList<>(base);
        pause.add("-DJNATIVE_PAUSE_ON_EXIT=ON");
        var refusedConsole = ProcessHarness.run(project, pause, Duration.ofSeconds(60));
        assertNotEquals(0, refusedConsole.exitCode());
        assertTrue(
                refusedConsole.text().contains("unavailable platform service: console"),
                refusedConsole.text());
        var capture = new ArrayList<>(base);
        capture.addAll(List.of("-DJNATIVE_PAUSE_ON_EXIT=OFF", "-DJNATIVE_NATIVE_TRACES=ON"));
        var refusedCapture = ProcessHarness.run(project, capture, Duration.ofSeconds(60));
        assertNotEquals(0, refusedCapture.exitCode());
        assertTrue(
                refusedCapture.text().contains("unavailable platform service: native-traces"),
                refusedCapture.text());
        var disabled = new ArrayList<>(base);
        disabled.addAll(List.of("-DJNATIVE_PAUSE_ON_EXIT=OFF", "-DJNATIVE_NATIVE_TRACES=OFF"));
        var accepted = ProcessHarness.run(project, disabled, Duration.ofSeconds(60));
        assertEquals(0, accepted.exitCode(), accepted.text());
        assertFalse(
                Files.readString(project.resolve("b/services/jnative-platform.properties"))
                        .lines()
                        .filter(line -> line.startsWith("available="))
                        .findFirst()
                        .orElseThrow()
                        .contains("console"));
        assertFalse(
                Files.readString(project.resolve("b/services/jnative-platform.properties"))
                        .lines()
                        .filter(line -> line.startsWith("available="))
                        .findFirst()
                        .orElseThrow()
                        .contains("try-lock"));
        var built =
                ProcessHarness.run(
                        project,
                        List.of(
                                "cmake",
                                "--build",
                                project.resolve("b/services").toString(),
                                "--parallel",
                                "2"),
                        Duration.ofSeconds(120));
        assertEquals(0, built.exitCode(), built.text());
        assertEquals(
                "portable output\n",
                run(project.resolve(
                        "debug/"
                                + generated.request().targetFileName()
                                + (windows ? ".exe" : "")))
                        .text());
        var unavailableRtti = new ArrayList<>(disabled);
        unavailableRtti.add("-DCMAKE_CXX_FLAGS=-fno-rtti");
        var refusedRtti = ProcessHarness.run(project, unavailableRtti, Duration.ofSeconds(60));
        assertNotEquals(0, refusedRtti.exitCode());
        assertTrue(
                refusedRtti.text().contains("lacks required runtime facilities"),
                refusedRtti.text());
    }

    private ProcessHarness.Output run(Path executable) throws Exception {
        return ProcessHarness.run(
                temporary, List.of(executable.toString()), Duration.ofSeconds(30));
    }
}
