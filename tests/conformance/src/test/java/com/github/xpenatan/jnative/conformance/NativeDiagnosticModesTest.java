package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class NativeDiagnosticModesTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @ParameterizedTest
    @EnumSource(StackTraceMode.class)
    void constructionRethrowCausesAndNativeBoundaries(StackTraceMode mode) throws Exception {
        Path source = temporary.resolve("Player.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        package game;
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        import com.github.xpenatan.jnative.interop.NativeInclude;
                        @NativeInclude("jnative_imports.h")
                        public class Player {
                            @NativeImport("native_throw") static int nativeThrow(int kind) { return 0; }
                            static RuntimeException makeFailure() { return new RuntimeException("original"); }
                            static void throwLater(RuntimeException error) { throw error; }
                            public static void main(String[] args) {
                                if (args.length > 0) {
                                    try { nativeThrow(args[0].equals("owned") ? 1 : 0); }
                                    catch (Exception error) { error.printStackTrace(); }
                                    return;
                                }
                                RuntimeException original = makeFailure();
                                try { throwLater(original); }
                                catch (Exception error) { new RuntimeException("outer", error).printStackTrace(); }
                                System.out.println("continued");
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        Path nativeFile = temporary.resolve("native_throw.cpp");
        Files.writeString(
                nativeFile,
                """
                        #include "jn_diagnostics.hpp"
                        #include <stdexcept>
                        extern "C" std::int32_t native_throw(std::int32_t kind) {
                            if(kind) throw jnative::NativeException("owned native failure");
                            throw std::runtime_error("foreign native failure");
                        }
                        """);
        var built =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("game.Player")
                        .nativeFile(nativeFile)
                        .buildRoot(temporary.resolve("build"))
                        .buildType(BuildType.RELEASE)
                        .sourceLayout(SourceLayout.PACKAGE_DIRECTORIES)
                        .stackTraces(mode)
                        .javaSourceLocations(true)
                        .nativeSymbols(
                                mode == StackTraceMode.BOTH
                                        ? NativeSymbols.NONE
                                        : NativeSymbols.SEPARATE)
                        .build();
        String cpp =
                Files.readString(
                        built.generation()
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/game/Player.cpp"));
        assertEquals(mode.javaFrames(), cpp.contains("JavaFrame"));
        assertEquals(mode.javaFrames(), cpp.contains("java_frame.line"));
        Path reports = temporary.resolve("reports");
        var result =
                ProcessHarness.run(
                        temporary,
                        List.of(built.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of(
                                "JNATIVE_REPORT_DIR",
                                reports.toString(),
                                "JNATIVE_GC_INTERVAL",
                                "1"));
        assertEquals(0, result.exitCode(), result.text());
        assertTrue(result.text().contains("continued"), result.text());
        assertEquals(mode.nativeFrames(), result.text().contains("Native stack"));
        assertEquals(mode.javaFrames(), result.text().contains("at game.Player.makeFailure("));
        if(mode.nativeFrames()) {
            Path report = NativeDiagnostics.pendingReports(reports).getFirst();
            var decoded = NativeDiagnostics.decode(report, built.diagnostics().directory());
            var json = Json.object(Json.read(decoded.json()));
            assertEquals(1, Json.array(json.get("causes")).size());
            assertTrue(decoded.text().contains("Caused by:"), decoded.text());
            if(mode == StackTraceMode.NATIVE) {
                assertTrue(decoded.text().contains("makeFailure"), decoded.text());
                var cause = Json.object(Json.array(json.get("causes")).getFirst());
                assertFalse(Json.write(cause).contains("throwLater"), Json.write(cause));
                for(String kind : List.of("owned", "foreign")) {
                    Path directory = temporary.resolve(kind);
                    var nativeResult =
                            ProcessHarness.run(
                                    temporary,
                                    List.of(built.executable().toString(), kind),
                                    Duration.ofSeconds(30),
                                    Map.of("JNATIVE_REPORT_DIR", directory.toString()));
                    assertEquals(0, nativeResult.exitCode(), nativeResult.text());
                    var nativeReport =
                            NativeDiagnostics.decode(
                                    NativeDiagnostics.pendingReports(directory).getFirst(),
                                    built.diagnostics().directory());
                    assertTrue(
                            nativeReport
                                    .json()
                                    .contains(
                                            kind.equals("owned")
                                                    ? "native-construction"
                                                    : "native-boundary-catch"),
                            nativeReport.json());
                    if(kind.equals("owned"))
                        assertTrue(
                                nativeReport.text().contains("native_throw.cpp"),
                                nativeReport.text());
                }
            }
            else assertTrue(decoded.text().contains("symbols unavailable"), decoded.text());
        }
        else assertTrue(NativeDiagnostics.pendingReports(reports).isEmpty());
    }
}
