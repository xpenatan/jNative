package com.github.xpenatan.jnative;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class SubstitutionOptionsTest {
    @Test
    void normalizesAndSnapshotsProviderConfigurationWithoutReadingArtifacts() {
        var paths = new ArrayList<>(List.of(Path.of("providers/../provider.jar"), Path.of("provider.jar")));
        var preferences = new LinkedHashMap<String, String>();
        preferences.put("vendor/Counter", "example.provider");
        var classes = new ArrayList<>(List.of(new ClassSubstitution("vendor/Counter", "nativeimpl.Counter")));
        var provider = new SubstitutionProvider("example.provider", Path.of("provider.jar"), classes, List.of());
        var options = new SubstitutionOptions(false, paths, paths, List.of(provider), preferences, Map.of());
        paths.clear();
        classes.clear();
        preferences.clear();
        assertEquals(List.of(Path.of("provider.jar").toAbsolutePath()), options.providerPaths());
        assertEquals("example.provider", options.classPreferences().get("vendor.Counter"));
        assertEquals(1, provider.classes().size());
        assertFalse(options.useBuiltinSubstitutions());
        assertThrows(UnsupportedOperationException.class, () -> options.providerPaths().clear());
        assertThrows(UnsupportedOperationException.class, () -> options.classPreferences().clear());
        assertThrows(UnsupportedOperationException.class, () -> provider.classes().clear());
    }

    @Test
    void builderAndCompatibilityRequestCaptureEquivalentDefaultsAndExplicitRules() {
        var target = new MethodReference("vendor/Counter", "add", "(I)I");
        var implementation = new StaticMethodReference("nativeimpl.CounterMethods", "add", "(Lvendor/Counter;I)I");
        var provider = new SubstitutionProvider("example.provider", Path.of("provider.jar"), List.of(),
                List.of(new MethodSubstitution(target, implementation)));
        var builder = NativeBuilder.create().classpath(Path.of("classes")).mainClass("example.Main").buildRoot(Path.of("output"));
        assertEquals(SubstitutionOptions.defaults(), builder.request().substitutions());
        var request = builder.substitutionPath(List.of(Path.of("provider.jar")))
                .substitutionDependencies(Path.of("helper.jar"))
                .substitutions(provider).preferClass("vendor.Counter", provider.id())
                .preferMethod(target, provider.id()).useBuiltinSubstitutions(false).request();
        builder.substitutionPath(Path.of("later.jar")).useBuiltinSubstitutions(true);
        assertEquals(1, request.substitutions().providerPaths().size());
        assertEquals(List.of(provider), request.substitutions().providers());
        assertEquals(Map.of(target, provider.id()), request.substitutions().methodPreferences());
        assertFalse(request.substitutions().useBuiltinSubstitutions());
        var compatibility = new NativeBuildRequest(request.classpath(), request.mainClass(), request.buildRoot(),
                null, null, "app", BuildType.DEBUG, false, NativeOptions.defaults(), List.of(), List.of(),
                ConsoleMode.NORMAL, SourceLayout.PACKAGE_FILENAME, DiagnosticsOptions.legacy());
        assertEquals(SubstitutionOptions.defaults(), compatibility.substitutions());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "()", "(V)V", "([V)V", "()Vextra", "(Ljava.lang.String;)V", "(Lbad//Name;)V", "(I)Q", "(I)V;"})
    void rejectsMalformedExactDescriptors(String descriptor) {
        assertThrows(IllegalArgumentException.class, () -> new MethodReference("example.Target", "method", descriptor));
        assertThrows(IllegalArgumentException.class, () -> new StaticMethodReference("example.Helper", "method", descriptor));
    }

    @Test
    void rejectsMalformedNamesAndConstructorPatches() {
        assertThrows(IllegalArgumentException.class, () -> new ClassSubstitution("bad..Name", "example.Donor"));
        assertThrows(IllegalArgumentException.class, () -> new ClassSubstitution("example.Same", "example/Same"));
        assertThrows(IllegalArgumentException.class, () -> new MethodReference("example.Target", "<init>", "()V"));
        assertThrows(IllegalArgumentException.class, () -> new MethodReference("example.Target", "<clinit>", "()V"));
        assertThrows(IllegalArgumentException.class, () -> new StaticMethodReference("example.Helper", "<init>", "()V"));
        assertThrows(IllegalArgumentException.class, () -> new ClassSubstitution("[I", "example.Donor"));
        assertThrows(IllegalArgumentException.class, () -> new SubstitutionProvider("../bad", Path.of("provider.jar"), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> NativeBuilder.create().preferClass("vendor.Counter", "one").preferClass("vendor/Counter", "two"));
    }
}
