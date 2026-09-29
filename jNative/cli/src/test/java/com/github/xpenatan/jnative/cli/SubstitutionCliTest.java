package com.github.xpenatan.jnative.cli;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.spi.NativeBackend;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubstitutionCliTest {
    @Test
    void cliCapturesTheSameExplicitSelectionAsTheBuilder() {
        var backend = new CapturingBackend();
        var errors = new ByteArrayOutputStream();
        int status = Main.execute(new String[] {"generate", "--classpath", "classes", "--main", "example.Main",
                "--output", "output", "--substitution-path", "first.jar", "--substitution-path=second.jar",
                "--substitution-dependency", "helpers.jar", "--no-builtin-substitutions",
                "--prefer-class-substitution", "vendor.Counter=example.provider",
                "--prefer-method-substitution", "vendor.Counter#add(I)I=example.provider"},
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(errors), NativeBuilder.create().backend(backend));
        assertEquals(0, status, errors.toString());
        var expected = NativeBuilder.create().classpath(Path.of("classes")).mainClass("example.Main").buildRoot(Path.of("output"))
                .substitutionPath(Path.of("first.jar")).substitutionPath(Path.of("second.jar"))
                .substitutionDependencies(Path.of("helpers.jar")).useBuiltinSubstitutions(false)
                .preferClass("vendor.Counter", "example.provider")
                .preferMethod(new MethodReference("vendor.Counter", "add", "(I)I"), "example.provider").request();
        assertEquals(expected.substitutions(), backend.request.substitutions());
    }

    @Test
    void invalidOrConflictingPreferencesFailBeforeGeneration() {
        for(String value : new String[] {"vendor.Counter.add(I)I=provider", "vendor.Counter#add(V)I=provider", "vendor.Counter#<init>()V=provider"}) {
            var backend = new CapturingBackend();
            int status = Main.execute(new String[] {"generate", "--classpath", "classes", "--main", "example.Main",
                    "--output", "output", "--prefer-method-substitution", value},
                    new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()), NativeBuilder.create().backend(backend));
            assertEquals(2, status);
            assertNull(backend.request);
        }
    }

    private static final class CapturingBackend implements NativeBackend {
        NativeBuildRequest request;
        public NativeGenerationResult generate(NativeBuildRequest request, BuildLog log) {
            this.request = request;
            return new NativeGenerationResult(request, Set.of(), Set.of());
        }
        public NativeCompilationResult compile(NativeGenerationResult generation, BuildLog log) {
            throw new AssertionError("Unexpected native compilation");
        }
    }
}
