package com.github.xpenatan.jnative.substitution.processor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SubstitutionProcessorTest {
    @TempDir Path temporary;

    @Test
    void emitsSortedBinaryNamesIncludingNestedAndRepeatableDeclarations() throws IOException {
        String source = """
                package example;
                import com.github.xpenatan.jnative.substitution.*;
                @SubstituteClass("vendor.Z") class Z {
                    @SubstituteClass("vendor.Nested") static class Nested {}
                }
                class A {
                    @SubstituteMethod(owner="vendor.X", name="first", descriptor="()I")
                    @SubstituteMethod(owner="vendor.X", name="second", descriptor="()I")
                    static int helper() { return 1; }
                    @TargetField(owner="vendor.X",name="value",descriptor="I",access=FieldAccess.GET)
                    static native int value(Object receiver);
                    @OriginalMethod(owner="vendor.X",name="first",descriptor="()I")
                    static native int original(Object receiver);
                }
                """;
        var result = compile(source, true);
        assertTrue(result.success, result.messages);
        assertEquals("""
                {
                  "schemaVersion": 1,
                  "providerId": "example.provider",
                  "declarations": [
                    "example.A",
                    "example.Z",
                    "example.Z$Nested"
                  ]
                }
                """, Files.readString(temporary.resolve(SubstitutionProcessor.INDEX)));
    }

    @Test
    void rejectsMissingProviderIdAndInvalidAliasesAndDuplicateRules() throws IOException {
        var missing = compile("package example; import com.github.xpenatan.jnative.substitution.SubstituteClass; @SubstituteClass(\"vendor.X\") class X {}", false);
        assertFalse(missing.success);
        assertTrue(missing.messages.contains("Required processor option"));
        var invalid = compile("""
                package example;
                import com.github.xpenatan.jnative.substitution.*;
                class Invalid {
                    @SubstituteMethod(owner="vendor.X",name="method",descriptor="()I")
                    @SubstituteMethod(owner="vendor.X",name="method",descriptor="()I")
                    int helper() { return 1; }
                    @OriginalMethod(owner="vendor.X",name="<init>",descriptor="()V")
                    static int original() { return 0; }
                }
                """, true);
        assertFalse(invalid.success);
        assertTrue(invalid.messages.contains("Duplicate substitution definition"));
        assertTrue(invalid.messages.contains("must be static"));
        assertTrue(invalid.messages.contains("static native declarations"));
        assertTrue(invalid.messages.contains("complete class replacement"));
        assertFalse(Files.exists(temporary.resolve(SubstitutionProcessor.INDEX)));
    }

    @Test
    void incrementalRecompilationReplacesTheIndexDeterministically() throws IOException {
        String source = "package example; import com.github.xpenatan.jnative.substitution.SubstituteClass; @SubstituteClass(\"vendor.X\") class X {}";
        assertTrue(compile(source, true).success);
        String first = Files.readString(temporary.resolve(SubstitutionProcessor.INDEX));
        assertTrue(compile(source, true).success);
        assertEquals(first, Files.readString(temporary.resolve(SubstitutionProcessor.INDEX)));
    }

    private Result compile(String source, boolean providerId) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try(var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var options = new ArrayList<>(List.of("--release", "17", "-classpath", System.getProperty("java.class.path"), "-d", temporary.toString()));
            if(providerId) options.add("-Ajnative.substitutionProvider=example.provider");
            var unit = new SimpleJavaFileObject(URI.create("string:///example/Fixture.java"), JavaFileObject.Kind.SOURCE) {
                public CharSequence getCharContent(boolean ignoreEncodingErrors) { return source; }
            };
            var task = compiler.getTask(null, files, diagnostics, options, null, List.of(unit));
            task.setProcessors(List.of(new SubstitutionProcessor()));
            boolean success = task.call();
            return new Result(success, diagnostics.getDiagnostics().toString());
        }
    }

    private record Result(boolean success, String messages) {}
}
