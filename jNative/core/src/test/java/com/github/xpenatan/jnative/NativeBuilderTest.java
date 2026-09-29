package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.fixtures.RecordingBackend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NativeBuilderTest {
    @TempDir
    Path temporary;

    @Test
    void nativeArgumentsKeepOrderTokensAndGenerationOwnership() {
        var backend = new RecordingBackend();
        String[] raw = {"-DMY_FEATURE=first", "-Wdev"};
        var builder =
                configured()
                        .backend(backend)
                        .cmakeArgs(raw)
                        .cmakeDefine("MY_FEATURE", "second value with spaces")
                        .cmakeToolchain(temporary.resolve("SDK files/toolchain.cmake"))
                        .cmakeBuildArgs("--parallel", "8", "--verbose")
                        .buildToolArgs("VERBOSE=1");
        var generated = builder.generate();
        raw[0] = "mutated";
        builder.cmakeDefine("MY_FEATURE", "later").cmakeBuildArgs("--clean-first");
        builder.compile(generated);
        var settings = generated.request().nativeOptions();
        assertEquals(
                List.of(
                        "-DMY_FEATURE=first",
                        "-Wdev",
                        "-DMY_FEATURE=second value with spaces",
                        "--toolchain",
                        temporary.resolve("SDK files/toolchain.cmake").toString()),
                settings.cmakeArguments());
        assertEquals(List.of("--parallel", "8", "--verbose"), settings.cmakeBuildArguments());
        assertEquals(List.of("VERBOSE=1"), settings.buildToolArguments());
        assertThrows(UnsupportedOperationException.class, () -> settings.cmakeArguments().clear());
        assertThrows(
                IllegalArgumentException.class, () -> builder.cmakeDefine("bad=name", "value"));
    }

    @Test
    void managedNativeSettingsRejectConflictingRawArgumentsBeforeInvokingTools() {
        for(String argument :
                List.of(
                        "-Selsewhere",
                        "-Belsewhere",
                        "--preset=other",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=elsewhere")) {
            var request = configured().cmakeArgs(argument).request();
            var generated = new NativeGenerationResult(request, Set.of(), Set.of());
            var error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> NativeBuilder.create().compile(generated));
            assertTrue(error.getMessage().contains("JN3020"), error.getMessage());
        }
    }

    @Test
    void reflectionRegistrationsAreExplicitDeduplicatedSnapshots() {
        NativeBuilder builder =
                configured()
                        .reflectClass("example.Model")
                        .reflectClass("example.Model")
                        .reflectMetadata("example.Info")
                        .reflectMethod("example.Model", "work", "(I)I")
                        .reflectConstructor("example.Model", "()V")
                        .reflectField("example.Model", "value");
        var request = builder.request();
        assertEquals(5, request.reflection().size());
        builder.reflectClass("example.Later");
        assertEquals(5, request.reflection().size());
        assertThrows(UnsupportedOperationException.class, () -> request.reflection().clear());
        assertThrows(IllegalArgumentException.class, () -> configured().reflectClass(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> configured().reflectMethod("example.Model", "", "(I)I"));
    }

    @Test
    void buildGeneratesBeforeCompilingAndForwardsDiagnostics() {
        var backend = new RecordingBackend();
        var messages = new ArrayList<String>();
        BuildLog log = (level, message) -> messages.add(message);

        NativeCompilationResult result = configured().backend(backend).log(log).build();

        assertEquals(List.of("generate", "compile"), backend.operations);
        assertEquals(List.of("generation", "compilation"), messages);
        assertSame(log, backend.generationLog);
        assertSame(log, backend.compilationLog);
        assertSame(backend.request, result.generation().request());
        assertSame(backend.generation, result.generation());
        assertTrue(result.outputFiles().contains(result.executable()));
    }

    @Test
    void releaseRetentionRequiresAnArchiveFromTheCurrentBuild() {
        var error =
                assertThrows(
                        CompilerException.class,
                        () ->
                                configured()
                                        .backend(new RecordingBackend())
                                        .buildRelease(temporary.resolve("private")));
        assertTrue(error.getMessage().contains("JN4102"), error.getMessage());
        assertTrue(error.getMessage().contains("from this build"), error.getMessage());
        assertFalse(Files.exists(temporary.resolve("private")));
    }

    @Test
    void compileUsesGenerationSnapshotAfterBuilderChanges() {
        var backend = new RecordingBackend();
        NativeBuilder builder = configured().backend(backend);
        NativeGenerationResult generated = builder.generate();
        assertEquals(List.of("generate"), backend.operations);

        builder.mainClass("example.OtherApplication")
                .buildRoot(temporary.resolve("other"))
                .targetFileName("other")
                .buildType(BuildType.RELEASE);
        NativeCompilationResult result = builder.compile(generated);

        assertEquals(List.of("generate", "compile"), backend.operations);
        assertSame(generated, result.generation());
        assertEquals("example.Main", backend.generation.request().mainClass());
        assertEquals(BuildType.DEBUG, backend.generation.request().buildType());
        assertEquals(temporary.resolve("output/native/debug/app.bin"), result.executable());
    }

    @Test
    void freshBuilderCanCompileAnExistingGenerationWithoutJavaInputConfiguration() {
        var backend = new RecordingBackend();
        var generated = configured().backend(backend).generate();

        var compiled = NativeBuilder.create().backend(backend).compile(generated);

        assertSame(generated, compiled.generation());
        assertEquals(List.of("generate", "compile"), backend.operations);
    }

    @Test
    void failedGenerationPreventsCompilationAndPreservesCause() {
        var backend = new RecordingBackend();
        var cause = new IllegalStateException("missing class");
        backend.generationFailure = new CompilerException("Cannot resolve application", cause);

        var error =
                assertThrows(CompilerException.class, () -> configured().backend(backend).build());

        assertSame(backend.generationFailure, error);
        assertSame(cause, error.getCause());
        assertEquals(List.of("generate"), backend.operations);
    }

    @Test
    void failedCompilationDoesNotReturnSuccess() {
        var backend = new RecordingBackend();
        backend.compilationFailure = new CompilerException("C compiler exited with code 1");

        var error =
                assertThrows(CompilerException.class, () -> configured().backend(backend).build());

        assertSame(backend.compilationFailure, error);
        assertEquals(List.of("generate", "compile"), backend.operations);
    }

    @Test
    void missingInputFailsBeforeCallingBackend() {
        var backend = new RecordingBackend();
        var builder =
                NativeBuilder.create().backend(backend).buildRoot(temporary.resolve("output"));

        var error = assertThrows(IllegalArgumentException.class, builder::generate);

        assertTrue(error.getMessage().contains("Classpath is empty"));
        assertTrue(backend.operations.isEmpty());
        assertFalse(Files.exists(temporary.resolve("output")));
    }

    @Test
    void defaultBackendRejectsMissingInputWithoutCreatingArtifacts() {
        NativeBuilder builder = configured();
        var generationFailure = assertThrows(CompilerException.class, builder::generate);
        var buildFailure = assertThrows(CompilerException.class, builder::build);
        var generated = new NativeGenerationResult(builder.request(), Set.of(), Set.of());
        var compilationFailure =
                assertThrows(CompilerException.class, () -> builder.compile(generated));

        assertTrue(generationFailure.getMessage().contains("Classpath entry does not exist"));
        assertEquals(generationFailure.getMessage(), buildFailure.getMessage());
        assertTrue(compilationFailure.getMessage().contains("CMakeLists.txt is missing"));
        assertFalse(Files.exists(builder.request().buildRoot()));
    }

    @Test
    void generationCannotReturnAResultForADifferentRequest() {
        var wrongRequest = configured().mainClass("example.Wrong").request();
        var backend =
                new RecordingBackend() {
                    @Override
                    public NativeGenerationResult generate(
                            NativeBuildRequest request, BuildLog log) {
                        return new NativeGenerationResult(wrongRequest, Set.of(), Set.of());
                    }
                };

        assertThrows(CompilerException.class, () -> configured().backend(backend).build());
        assertTrue(
                backend.operations.isEmpty(),
                "Compilation must not follow an invalid generation result");
    }

    @Test
    void compilationCannotReturnAResultForAnotherGeneration() {
        var wrong =
                new NativeGenerationResult(
                        configured().mainClass("example.Wrong").request(), Set.of(), Set.of());
        var backend =
                new RecordingBackend() {
                    @Override
                    public NativeCompilationResult compile(
                            NativeGenerationResult generation, BuildLog log) {
                        Path executable = temporary.resolve("wrong.bin");
                        return new NativeCompilationResult(wrong, executable, Set.of(executable));
                    }
                };

        assertThrows(CompilerException.class, () -> configured().backend(backend).build());
    }

    @Test
    void nullBackendResultsAreFailures() {
        var backend =
                new RecordingBackend() {
                    @Override
                    public NativeGenerationResult generate(
                            NativeBuildRequest request, BuildLog log) {
                        return null;
                    }

                    @Override
                    public NativeCompilationResult compile(
                            NativeGenerationResult generation, BuildLog log) {
                        return null;
                    }
                };
        NativeBuilder builder = configured().backend(backend);

        assertThrows(CompilerException.class, builder::generate);
        var generation = new NativeGenerationResult(builder.request(), Set.of(), Set.of());
        assertThrows(CompilerException.class, () -> builder.compile(generation));
    }

    private NativeBuilder configured() {
        return NativeBuilder.create()
                .classpath(temporary.resolve("classes"))
                .mainClass("example.Main")
                .buildRoot(temporary.resolve("output"));
    }
}
