package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.jupiter.api.Assertions.*;

class SubstitutionRegistryTest {
    @TempDir Path temporary;

    @Test
    void wholeClassUsesArtifactScopedBytesAndPreservesLiteralAndPrivateCompanionIdentity() throws Exception {
        Path original = compile("original", Map.of("vendor/Counter.java", "package vendor; public class Counter { public int add(int n) { return n; } }"));
        Path donor = compile("donor", Map.of("replacement/Portable.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("vendor.Counter")
                public class Portable {
                    private int value;
                    public int add(int n) { value += n * 2; return value; }
                    public Portable[] copies() { return new Portable[] {this}; }
                    public String literal() { return "replacement.Portable"; }
                    public static class Companion { public Portable owner; }
                    static { if(System.nanoTime() == 0) throw new AssertionError("must not execute on compiler JVM"); }
                }
                """));
        index(donor, "example.provider", "replacement.Portable");
        Path jar = jar(donor, "donor.jar");
        var path = new ClassPath(List.of(original), options(List.of(jar), List.of(), Map.of(), Map.of()));
        ClassNode effective = path.read("vendor/Counter");
        assertEquals("vendor/Counter", effective.name);
        assertEquals("()[Lvendor/Counter;", method(effective, "copies").desc);
        assertEquals("replacement.Portable", Arrays.stream(method(effective, "literal").instructions.toArray())
                .filter(LdcInsnNode.class::isInstance).map(LdcInsnNode.class::cast).findFirst().orElseThrow().cst);
        assertEquals("Lvendor/Counter;", path.read("replacement/Portable$Companion").fields.getFirst().desc);
        assertEquals("replacement/Portable$Companion", path.read("replacement/Portable$Companion").name);
        assertEquals("replacement/Portable", path.origins().get("vendor/Counter").substitutionDonor());
        assertEquals("", path.origins().get("vendor/Counter").generatedFrom());
        assertEquals("vendor/Counter", path.raw("vendor/Counter").name);
        assertEquals(1, path.raw("vendor/Counter").methods.stream().filter(m -> m.name.equals("add")).count());
    }

    @Test
    void methodPatchPreservesStateAndPreviousBodyAndLowersExactAliases() throws Exception {
        Path original = counter();
        Path donor = compile("methods", Map.of("replacement/Methods.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.*;
                import vendor.Counter;
                public class Methods {
                    @SubstituteMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static int add(Counter self, int amount) { return previous(self, amount * 2); }
                    @OriginalMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static native int previous(Counter self, int amount);
                    @TargetField(owner="vendor.Counter",name="value",descriptor="I",access=FieldAccess.GET)
                    public static native int value(Counter self);
                }
                """), original);
        index(donor, "example.methods", "replacement.Methods");
        var path = new ClassPath(List.of(original), options(List.of(donor), List.of(), Map.of(), Map.of()));
        ClassNode target = path.read("vendor/Counter");
        assertEquals(1, target.fields.size());
        assertEquals("value", target.fields.getFirst().name);
        MethodNode patch = method(target, "add");
        assertTrue(patch.instructions.get(2) instanceof MethodInsnNode call && call.owner.equals("replacement/Methods"));
        assertEquals(1, target.methods.stream().filter(m -> m.name.startsWith("$jnative$previous$")).count());
        ClassNode helpers = path.read("replacement/Methods");
        MethodNode alias = method(helpers, "previous");
        assertEquals(0, alias.access & Opcodes.ACC_NATIVE);
        assertTrue(alias.instructions.get(2) instanceof MethodInsnNode call && call.name.startsWith("$jnative$previous$") && call.owner.equals("vendor/Counter"));
        assertTrue(method(helpers, "value").instructions.get(1) instanceof FieldInsnNode field && field.name.equals("value") && field.owner.equals("vendor/Counter"));
        assertEquals("example.methods", path.registry().methodOrigins().get(new Program.MethodId("vendor/Counter", "add", "(I)I")).providerId());
    }

    @Test
    void conflictingProvidersRequireAnExactPreferenceRegardlessOfArtifactOrder() throws Exception {
        Path original = counter();
        Path first = classProvider("first", "provider.first", "First");
        Path second = classProvider("second", "provider.second", "Second");
        for(List<Path> order : List.of(List.of(first, second), List.of(second, first))) {
            assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(order, List.of(), Map.of(), Map.of())));
            var path = new ClassPath(List.of(original), options(order, List.of(), Map.of("vendor.Counter", "provider.second"), Map.of()));
            assertEquals("replacement/Second", path.registry().classReplacement("vendor/Counter").implementation());
        }
        assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(first), List.of(), Map.of("vendor.Missing", "provider.first"), Map.of())));
    }

    @Test
    void indexesDoNotActivateThroughClasspathOrDependencyAndDifferentBuildsStayIndependent() throws Exception {
        Path original = counter();
        Path provider = classProvider("provider", "provider.one", "One");
        var inactive = new ClassPath(List.of(original, provider), options(List.of(), List.of(provider), Map.of(), Map.of()));
        var active = new ClassPath(List.of(original), options(List.of(provider), List.of(), Map.of(), Map.of()));
        assertNull(inactive.registry().classReplacement("vendor/Counter"));
        assertNotNull(active.registry().classReplacement("vendor/Counter"));
        assertNotSame(inactive.read("vendor/Counter"), active.read("vendor/Counter"));
        assertEquals(1, inactive.read("vendor/Counter").fields.size());
        assertEquals(0, active.read("vendor/Counter").fields.size());
    }

    @Test
    void methodCyclesArrayReplacementAndConstructorPatchesAreRejected() throws Exception {
        Path original = counter();
        Path methods = compile("method-cycle", Map.of("replacement/Left.java", "package replacement; public class Left { public static int value() { return 1; } }",
                "replacement/Right.java", "package replacement; public class Right { public static int value() { return 2; } }"));
        var cycle = new SubstitutionProvider("example.method-cycle", methods, List.of(), List.of(
                new MethodSubstitution(new MethodReference("replacement.Left", "value", "()I"), new StaticMethodReference("replacement.Right", "value", "()I")),
                new MethodSubstitution(new MethodReference("replacement.Right", "value", "()I"), new StaticMethodReference("replacement.Left", "value", "()I"))));
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), new SubstitutionOptions(false,
                List.of(), List.of(), List.of(cycle), Map.of(), Map.of()))).getMessage().contains("Method substitution cycle"));
        Path array = compile("array-rule", Map.of("replacement/Array.java", "package replacement; import com.github.xpenatan.jnative.substitution.SubstituteClass; @SubstituteClass(\"[I\") public class Array {}"));
        index(array, "example.array", "replacement.Array");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(array), List.of(), Map.of(), Map.of()))).getMessage().contains("array representation"));
        Path constructors = compile("constructor-rule", Map.of("replacement/Constructor.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteMethod;
                public class Constructor {
                    @SubstituteMethod(owner="vendor.Counter",name="<init>",descriptor="()V")
                    public static void create() {}
                }
                """));
        index(constructors, "example.constructor", "replacement.Constructor");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(constructors), List.of(), Map.of(), Map.of()))).getMessage().contains("complete class replacement"));
    }

    @Test
    void suppliedOriginalStreamInterfacesMatchBuiltinKindsAndHierarchy() throws Exception {
        Path originals = temporary.resolve("explicit-platform-abi");
        for(String type : List.of("BaseStream", "Stream", "IntStream", "LongStream")) {
            String owner = "java/util/stream/" + type;
            Path file = originals.resolve(owner + ".class");
            Files.createDirectories(file.getParent());
            try(var stream = Object.class.getResourceAsStream("/" + owner + ".class")) {
                assertNotNull(stream);
                Files.write(file, stream.readAllBytes());
            }
        }
        // These bytes are explicit test inputs; production resolution never falls back to jrt.
        var path = new ClassPath(List.of(originals), SubstitutionOptions.defaults());
        for(String type : List.of("BaseStream", "Stream", "IntStream", "LongStream")) {
            ClassNode selected = path.read("java/util/stream/" + type);
            assertNotEquals(0, selected.access & Opcodes.ACC_INTERFACE);
            assertFalse(selected.methods.stream().anyMatch(method -> method.name.equals("<init>")));
        }
        assertEquals(List.of("java/util/stream/BaseStream"), path.read("java/util/stream/Stream").interfaces);
    }

    @Test
    void reachableInvocationKindRejectsClassDonorForAnOriginalInterfaceWithoutOriginalMetadata() throws Exception {
        Path original = compile("interface-original", Map.of("vendor/Api.java", "package vendor; public interface Api { int value(); }"));
        Path caller = compile("interface-caller", Map.of("app/Main.java", "package app;\n\nimport vendor.Api; public class Main { public static void main(String[] args) { Api value = null; value.value(); } }"), original);
        Path donor = compile("wrong-interface-donor", Map.of("replacement/Concrete.java", "package replacement; import com.github.xpenatan.jnative.substitution.SubstituteClass; @SubstituteClass(\"vendor.Api\") public class Concrete { public int value() { return 1; } }"));
        index(donor, "example.wrong-kind", "replacement.Concrete");
        var failure = assertThrows(CompilerException.class, () -> new BytecodeCompiler().compile(request(List.of(caller), "app.Main", donor)));
        assertTrue(failure.getMessage().contains("class/interface mismatch"));
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(donor), List.of(), Map.of(), Map.of()))).getMessage().contains("Class kind mismatch"));
    }

    @Test
    void eagerValidationRejectsCyclesDuplicateIdsConflictingHelperBytesAndMissingDeclaredTargets() throws Exception {
        Path original = counter();
        Path cycle = compile("cycle", Map.of("replacement/Left.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("replacement.Right") public class Left {}
                """, "replacement/Right.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("replacement.Left") public class Right {}
                """));
        index(cycle, "example.cycle", "replacement.Left", "replacement.Right");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(cycle), List.of(), Map.of(), Map.of()))).getMessage().contains("cycle"));
        Path first = compile("helper-first", Map.of("replacement/Helper.java", "package replacement; public class Helper { public static int value() { return 1; } }"));
        Path second = compile("helper-second", Map.of("replacement/Helper.java", "package replacement; public class Helper { public static int value() { return 2; } }"));
        index(first, "example.helper", "replacement.Helper");
        index(second, "example.helper", "replacement.Helper");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(first, second), List.of(), Map.of(), Map.of()))).getMessage().contains("Duplicate provider id"));
        index(second, "example.other", "replacement.Helper");
        var helpers = new ClassPath(List.of(original), options(List.of(first, second), List.of(), Map.of(), Map.of()));
        assertTrue(assertThrows(CompilerException.class, () -> helpers.read("replacement/Helper")).getMessage().contains("Conflicting helper bytecode"));
        var missing = new SubstitutionProvider("example.missing", first, List.of(), List.of(new MethodSubstitution(
                new MethodReference("vendor.Counter", "missing", "()I"), new StaticMethodReference("replacement.Helper", "value", "()I"))));
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), new SubstitutionOptions(false,
                List.of(), List.of(), List.of(missing), Map.of(), Map.of()))).getMessage().contains("exact declared method"));
        var donorMissing = new SubstitutionProvider("example.absent", first, List.of(new ClassSubstitution("vendor.Counter", "replacement.Absent")), List.of());
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), new SubstitutionOptions(false,
                List.of(), List.of(), List.of(donorMissing), Map.of(), Map.of()))).getMessage().contains("donor missing"));
    }

    @Test
    void processorAndManualIndexesHaveEquivalentMetadataAndReportsCarryStableHashes() throws Exception {
        Path original = counter();
        String source = "package replacement; import com.github.xpenatan.jnative.substitution.SubstituteClass; @SubstituteClass(\"vendor.Counter\") public class Portable { public int add(int n) { return n * 2; } }";
        Path manual = compile("manual-index", Map.of("replacement/Portable.java", source));
        index(manual, "example.parity", "replacement.Portable");
        Path processed = compileWithProcessor("processor-index", "replacement/Portable.java", source, "example.parity");
        var first = new ClassPath(List.of(original), options(List.of(manual), List.of(), Map.of(), Map.of()));
        var second = new ClassPath(List.of(original), options(List.of(processed), List.of(), Map.of(), Map.of()));
        assertEquals(first.registry().classReplacement("vendor.Counter").implementation(), second.registry().classReplacement("vendor.Counter").implementation());
        assertArrayEquals(Files.readAllBytes(manual.resolve("replacement/Portable.class")), Files.readAllBytes(processed.resolve("replacement/Portable.class")));
        String report = first.registry().reportJson(Set.of(), Set.of());
        assertEquals(report, first.registry().reportJson(Set.of(), Set.of()));
        var document = Json.object(Json.read(report));
        var declaration = Json.object(Json.array(document.get("declarations")).getFirst());
        assertEquals("unused", declaration.get("status"));
        assertTrue(declaration.get("donorClassSha256").toString().matches("[0-9a-f]{64}"));
        assertTrue(declaration.get("originalClassSha256").toString().matches("[0-9a-f]{64}"));
        String tsv = first.registry().reportTsv(Set.of(), Set.of());
        assertTrue(tsv.startsWith("status\tkind\ttarget\tprovider\tdonor\tartifact\n"));
        assertTrue(tsv.contains("unused\tclass\tvendor/Counter\texample.parity\treplacement/Portable"));
    }

    @Test
    void malformedAliasBodiesAndMultipleExecutableBindingsFailBeforeReachability() throws Exception {
        Path original = counter();
        Path invalid = compile("invalid-alias", Map.of("replacement/Methods.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.OriginalMethod;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                import vendor.Counter;
                @SubstituteClass("vendor.Counter")
                public class Methods {
                    @OriginalMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static int original(Counter self, int n) { return n; }
                }
                """), original);
        index(invalid, "example.invalid", "replacement.Methods");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(invalid), List.of(), Map.of(), Map.of()))).getMessage().contains("static native"));
        Path multiple = temporary.resolve("multiple/classes");
        Path file = multiple.resolve("replacement/Aliases.class");
        Files.createDirectories(file.getParent());
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "replacement/Aliases", null, "java/lang/Object", null);
        var accessor = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE, "value", "(Lvendor/Counter;)I", null, null);
        var field = accessor.visitAnnotation("Lcom/github/xpenatan/jnative/substitution/TargetField;", false);
        field.visit("owner", "vendor.Counter");
        field.visit("name", "value");
        field.visit("descriptor", "I");
        field.visitEnum("access", "Lcom/github/xpenatan/jnative/substitution/FieldAccess;", "GET");
        field.visitEnd();
        var nativeImport = accessor.visitAnnotation("Lcom/github/xpenatan/jnative/interop/NativeImport;", false);
        nativeImport.visit("value", "external_symbol");
        nativeImport.visitEnd();
        accessor.visitEnd();
        writer.visitEnd();
        Files.write(file, writer.toByteArray());
        index(multiple, "example.multiple", "replacement.Aliases");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(multiple), List.of(), Map.of(), Map.of()))).getMessage().contains("multiple executable bindings"));
    }

    @Test
    void concurrentCompilationsRetainIndependentSelectionsAndLosingBuiltinDonorsAliasTheWinner() throws Exception {
        Path original = counter();
        Path caller = compile("concurrent-caller", Map.of("app/Main.java", "package app;\n\nimport vendor.Counter; public class Main { public static void main(String[] args) { new Counter().add(3); } }"), original);
        Path first = classProvider("concurrent-first", "example.first", "First");
        Path second = classProvider("concurrent-second", "example.second", "Second");
        try(var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> new BytecodeCompiler().compile(request(List.of(caller, original), "app.Main", first)));
            var two = executor.submit(() -> new BytecodeCompiler().compile(request(List.of(caller, original), "app.Main", second)));
            Program programOne = one.get();
            Program programTwo = two.get();
            assertEquals("replacement/First", programOne.substitutions().classReplacement("vendor.Counter").implementation());
            assertEquals("replacement/Second", programTwo.substitutions().classReplacement("vendor.Counter").implementation());
            assertNotSame(programOne.classes().get("vendor/Counter"), programTwo.classes().get("vendor/Counter"));
        }
        Path list = compile("external-list", Map.of("replacement/PortableList.java", "package replacement; import com.github.xpenatan.jnative.substitution.SubstituteClass; @SubstituteClass(\"java.util.ArrayList\") public class PortableList { public int size() { return 7; } }"));
        index(list, "example.list", "replacement.PortableList");
        var path = new ClassPath(List.of(original), new SubstitutionOptions(true, List.of(list), List.of(), List.of(), Map.of(), Map.of()));
        String builtinDonor = "com/github/xpenatan/jnative/classlib/java/util/ArrayList";
        assertEquals("java/util/ArrayList", path.registry().canonical(builtinDonor));
        assertEquals("java/util/ArrayList", path.read(builtinDonor).name);
        assertEquals("replacement/PortableList", path.origins().get("java/util/ArrayList").substitutionDonor());
    }

    @Test
    void staticTargetTakingItsOwnTypeDoesNotInsertAReceiverAndOtherOverloadsStayUnchanged() throws Exception {
        Path original = compile("static-original", Map.of("vendor/Counter.java", "package vendor; public class Counter { public static int compare(Counter value, int n) { return n; } public static int compare(int n) { return n + 1; } }"));
        Path donor = compile("static-provider", Map.of("replacement/Methods.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.*;
                import vendor.Counter;
                public class Methods {
                    @SubstituteMethod(owner="vendor.Counter",name="compare",descriptor="(Lvendor/Counter;I)I")
                    public static int compare(Counter value, int n) { return n * 2; }
                }
                """), original);
        index(donor, "example.static", "replacement.Methods");
        var path = new ClassPath(List.of(original), options(List.of(donor), List.of(), Map.of(), Map.of()));
        ClassNode target = path.read("vendor/Counter");
        MethodNode patch = target.methods.stream().filter(method -> method.name.equals("compare") && method.desc.equals("(Lvendor/Counter;I)I")).findFirst().orElseThrow();
        var call = (MethodInsnNode)patch.instructions.get(2);
        assertEquals("(Lvendor/Counter;I)I", call.desc);
        assertEquals(2, patch.maxLocals);
        MethodNode overload = target.methods.stream().filter(method -> method.name.equals("compare") && method.desc.equals("(I)I")).findFirst().orElseThrow();
        assertFalse(Arrays.stream(overload.instructions.toArray()).anyMatch(MethodInsnNode.class::isInstance));
    }

    @Test
    void finalFieldWritesAndFixedRepresentationsAreRejectedAndDormantAliasesStayUnbound() throws Exception {
        Path original = compile("final-original", Map.of("vendor/Counter.java", "package vendor; public class Counter { private final int value = 1; public int add(int n) { return value + n; } }"));
        Path donor = compile("final-provider", Map.of("replacement/Methods.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.*;
                import vendor.Counter;
                public class Methods {
                    @SubstituteMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static int add(Counter self, int n) { return n; }
                    @TargetField(owner="vendor.Counter",name="value",descriptor="I",access=FieldAccess.SET)
                    public static native void value(Counter self, int value);
                }
                """), original);
        index(donor, "example.final", "replacement.Methods");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original),
                options(List.of(donor), List.of(), Map.of(), Map.of()))).getMessage().contains("final field"));
        var fixed = new SubstitutionProvider("example.fixed", donor, List.of(new ClassSubstitution("java.lang.String", "replacement.Methods")), List.of());
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), new SubstitutionOptions(false,
                List.of(), List.of(), List.of(fixed), Map.of(), Map.of()))).getMessage().contains("runtime representation"));
        Path dormant = compile("dormant", Map.of("replacement/Alias.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.*;
                import vendor.Counter;
                public class Alias {
                    @TargetField(owner="vendor.Counter",name="value",descriptor="I",access=FieldAccess.GET)
                    public static native int value(Counter self);
                }
                """), original);
        index(dormant, "example.dormant", "replacement.Alias");
        var unused = new ClassPath(List.of(original), options(List.of(dormant), List.of(), Map.of(), Map.of()));
        ClassNode helper = unused.read("replacement/Alias");
        assertNotEquals(0, method(helper, "value").access & Opcodes.ACC_NATIVE);
        assertThrows(CompilerException.class, () -> NativeBinding.read(helper, method(helper, "value")));
    }

    @Test
    void unchangedDependencyJarAndCallerLinkThroughPatchedAndPreviousBodies() throws Exception {
        Path originalClasses = counter();
        Path originalJar = jar(originalClasses, "original.jar");
        try(var loader = new URLClassLoader(new URL[] {originalJar.toUri().toURL()}, null)) {
            Class<?> counter = loader.loadClass("vendor.Counter");
            Object instance = counter.getConstructor().newInstance();
            assertEquals(3, counter.getMethod("add", int.class).invoke(instance, 3));
            assertEquals(42, counter.getMethod("unchanged").invoke(instance));
        }
        Path caller = compile("caller", Map.of("app/Main.java", "package app;\n\nimport vendor.Counter; public class Main { public static void main(String[] args) { Counter counter = new Counter(); if(counter.add(3) != 6 || counter.unchanged() != 42) throw new AssertionError(); } }"), originalJar);
        Path donor = compile("linked-provider", Map.of("replacement/Methods.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.*;
                import vendor.Counter;
                public class Methods {
                    @SubstituteMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static int add(Counter self, int amount) { return original(self, amount * 2); }
                    @OriginalMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static native int original(Counter self, int amount);
                }
                """), originalJar);
        index(donor, "example.linked", "replacement.Methods");
        var settings = new SubstitutionOptions(true, List.of(jar(donor, "provider.jar")), List.of(), List.of(), Map.of(), Map.of());
        var request = new NativeBuildRequest(List.of(caller, originalJar), "app.Main", temporary.resolve("output"), null, null,
                "app", BuildType.DEBUG, false, NativeOptions.defaults(), List.of(), List.of(), ConsoleMode.NORMAL,
                SourceLayout.PACKAGE_FILENAME, DiagnosticsOptions.legacy(), settings);
        Program program = new BytecodeCompiler().compile(request);
        assertTrue(program.methods().containsKey(new Program.MethodId("vendor/Counter", "add", "(I)I")));
        assertTrue(program.methods().containsKey(new Program.MethodId("replacement/Methods", "original", "(Lvendor/Counter;I)I")));
        assertTrue(program.methods().keySet().stream().anyMatch(method -> method.owner().equals("vendor/Counter") && method.name().startsWith("$jnative$previous$")));
        assertTrue(program.methods().containsKey(new Program.MethodId("vendor/Counter", "unchanged", "()I")));
    }

    @Test
    void builtinAndExternalProvidersUseTheSameSelectionAndRuntimeMethodTable() throws Exception {
        Path original = counter();
        var path = new ClassPath(List.of(original), SubstitutionOptions.defaults());
        assertEquals("java/util/ArrayList", path.read("java/util/ArrayList").name);
        assertFalse(path.registry().platform("java/util/ArrayList"));
        assertTrue(path.registry().platform("java/lang/String"));
        var route = path.registry().platformBindings().find(new Program.MethodId("java/lang/String", "substring", "(I)Ljava/lang/String;"));
        assertNotNull(route);
        assertTrue(route.instance());
        assertNotNull(path.read(route.helper().owner()));
    }

    @Test
    void programmaticRulesDoNotNeedAnIndexAndMalformedIndexesAndHelperTypesFail() throws Exception {
        Path original = counter();
        Path donor = compile("programmatic", Map.of("replacement/Methods.java", "package replacement;\n\nimport vendor.Counter; public class Methods { public static long add(Counter self, int n) { return n; } }"), original);
        var target = new MethodReference("vendor.Counter", "add", "(I)I");
        var provider = new SubstitutionProvider("example.manual", donor, List.of(), List.of(new MethodSubstitution(target,
                new StaticMethodReference("replacement.Methods", "add", "(Lvendor/Counter;I)J"))));
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), new SubstitutionOptions(false,
                List.of(), List.of(), List.of(provider), Map.of(), Map.of()))).getMessage().contains("descriptor mismatch"));
        Path index = donor.resolve(SubstitutionArtifact.INDEX);
        Files.createDirectories(index.getParent());
        Files.writeString(index, "{\"schemaVersion\":2,\"providerId\":\"example.manual\",\"declarations\":[]}");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original), options(List.of(donor), List.of(), Map.of(), Map.of()))).getMessage().contains("schemaVersion"));
    }

    @Test
    void methodTargetsAndPreferencesNormalizeDonorOwnersAndDescriptorsBeforeSelection() throws Exception {
        Path original = compile("normalization-original", Map.of("vendor/Counter.java",
                "package vendor; public class Counter { public Counter echo(Counter value) { return value; } }"));
        Path donor = compile("normalization-provider", Map.of(
                "replacement/Portable.java", """
                        package replacement;
                        import com.github.xpenatan.jnative.substitution.SubstituteClass;
                        @SubstituteClass("vendor.Counter")
                        public class Portable { public Portable echo(Portable value) { return value; } }
                        """,
                "replacement/Methods.java", """
                        package replacement;
                        import com.github.xpenatan.jnative.substitution.SubstituteMethod;
                        @SuppressWarnings("unused")
                        public class Methods {
                            @SubstituteMethod(owner="replacement.Portable",name="echo",
                                    descriptor="(Lreplacement/Portable;)Lreplacement/Portable;")
                            public static Portable echo(Portable self, Portable value) { return self; }
                        }
                        """));
        index(donor, "normalized.provider", "replacement.Portable", "replacement.Methods");
        var preference = new MethodReference("replacement.Portable", "echo", "(Lreplacement/Portable;)Lreplacement/Portable;");
        var path = new ClassPath(List.of(original), options(List.of(donor), List.of(), Map.of(), Map.of(preference, "normalized.provider")));
        var target = new Program.MethodId("vendor/Counter", "echo", "(Lvendor/Counter;)Lvendor/Counter;");
        assertTrue(path.registry().methodRules().containsKey(target));
        assertTrue(Arrays.stream(method(path.read("vendor/Counter"), "echo").instructions.toArray())
                .anyMatch(instruction -> instruction instanceof MethodInsnNode call && call.owner.equals("replacement/Methods")
                        && call.desc.equals("(Lvendor/Counter;Lvendor/Counter;)Lvendor/Counter;")));
    }

    @Test
    void methodHelpersInsideShadowedWholeClassDonorsCannotResolveToAnotherProvidersCode() throws Exception {
        Path original = counter();
        Path first = compile("shadowed-helper", Map.of("replacement/First.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.*;
                @SubstituteClass("vendor.Counter")
                public class First {
                    public int add(int n) { return n; }
                    @SubstituteMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static int patch(First self, int n) { return n * 3; }
                }
                """));
        index(first, "shadowed.first", "replacement.First");
        Path second = classProvider("shadowed-winner", "shadowed.second", "Second");
        CompilerException failure = assertThrows(CompilerException.class, () -> new ClassPath(List.of(original),
                options(List.of(first, second), List.of(), Map.of("vendor.Counter", "shadowed.second"), Map.of())));
        assertTrue(failure.getMessage().contains("shadowed full-class implementation"));
        assertTrue(failure.getMessage().contains("JN4022"));
    }

    @Test
    void providerPreferenceCannotHideConflictingDeclarationsWithinThatProvider() throws Exception {
        Path original = counter();
        Path donor = compile("duplicate-slots", Map.of("replacement/Methods.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.*;
                import vendor.Counter;
                public class Methods {
                    @SubstituteMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static int first(Counter self, int value) { return value; }
                    @SubstituteMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                    public static int second(Counter self, int value) { return value * 2; }
                }
                """), original);
        index(donor, "duplicate.slots", "replacement.Methods");
        var target = new MethodReference("vendor.Counter", "add", "(I)I");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original),
                options(List.of(donor), List.of(), Map.of(), Map.of(target, "duplicate.slots"))))
                .getMessage().contains("Multiple method substitution winners"));
    }

    @Test
    void transformedHashesAreRepeatableAndObserveSelectedMethodBodies() throws Exception {
        Path original = counter();
        Path first = classProvider("hash-first", "hash.first", "First");
        Path second = compile("hash-second", Map.of("replacement/Second.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("vendor.Counter")
                public class Second { public int add(int n) { return n * 3; } }
                """));
        index(second, "hash.second", "replacement.Second");
        var one = new ClassPath(List.of(original), options(List.of(first), List.of(), Map.of(), Map.of()));
        var repeat = new ClassPath(List.of(original), options(List.of(first), List.of(), Map.of(), Map.of()));
        var changed = new ClassPath(List.of(original), options(List.of(second), List.of(), Map.of(), Map.of()));
        one.read("vendor/Counter");
        repeat.read("vendor/Counter");
        changed.read("vendor/Counter");
        var method = new Program.MethodId("vendor/Counter", "add", "(I)I");
        assertEquals(one.effectiveClassHash("vendor/Counter"), repeat.effectiveClassHash("vendor/Counter"));
        assertEquals(one.effectiveMethodHash(method), repeat.effectiveMethodHash(method));
        assertNotEquals(one.effectiveClassHash("vendor/Counter"), changed.effectiveClassHash("vendor/Counter"));
        assertNotEquals(one.effectiveMethodHash(method), changed.effectiveMethodHash(method));
        assertTrue(one.registry().reportJson().contains("effectiveClassSha256"));
    }

    @Test
    void suppliedConcreteOriginalRejectsAbstractOrNewlySealedDonorsEagerly() throws Exception {
        Path original = counter();
        Path abstractDonor = compile("abstract-donor", Map.of("replacement/AbstractCounter.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("vendor.Counter")
                public abstract class AbstractCounter { public abstract int add(int n); }
                """));
        index(abstractDonor, "abstract.provider", "replacement.AbstractCounter");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original),
                options(List.of(abstractDonor), List.of(), Map.of(), Map.of())))
                .getMessage().contains("adds abstract restriction"));
        Path sealedDonor = compile("sealed-donor", Map.of("replacement/SealedCounter.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("vendor.Counter")
                public sealed class SealedCounter permits Child { public int add(int n) { return n; } }
                final class Child extends SealedCounter {}
                """));
        index(sealedDonor, "sealed.provider", "replacement.SealedCounter");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original),
                options(List.of(sealedDonor), List.of(), Map.of(), Map.of())))
                .getMessage().contains("narrows permitted subclasses"));
    }

    @Test
    void unavailableJdkApisAggregateDiagnosticsAndExplicitFullDonorsRemainGenerated() throws Exception {
        Path unsupported = compile("unavailable-jdk", Map.of("app/Main.java", """
                package app;

                import java.net.URI;
                import java.time.Instant;
                import java.util.StringJoiner;
                public class Main {
                    public static void main(String[] args) {
                        Instant.now();
                        URI.create("https://example.invalid");
                        new StringJoiner(",");
                    }
                }
                """));
        var unsupportedRequest = new NativeBuildRequest(List.of(unsupported), "app.Main", temporary.resolve("unavailable-output"),
                null, null, "app", BuildType.DEBUG, false, NativeOptions.defaults(), List.of(), List.of(), ConsoleMode.NORMAL,
                SourceLayout.PACKAGE_FILENAME, DiagnosticsOptions.legacy(), SubstitutionOptions.defaults());
        CompilerException failure = assertThrows(CompilerException.class, () -> new BytecodeCompiler().compile(unsupportedRequest));
        assertTrue(failure.getMessage().contains("java.time.Instant.now"), failure.getMessage());
        assertTrue(failure.getMessage().contains("java.net.URI.create"), failure.getMessage());
        assertTrue(failure.getMessage().contains("java.util.StringJoiner.<init>"), failure.getMessage());

        Path caller = compile("replaced-jdk-caller", Map.of("app/Main.java",
                "package app;\n\nimport java.time.Instant; public class Main { public static void main(String[] args) { Instant.now(); } }"));
        Path donor = compile("replaced-jdk-provider", Map.of("portable/ClockValue.java", """
                package portable;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("java.time.Instant")
                public final class ClockValue {
                    public static ClockValue now() { return new ClockValue(); }
                }
                """));
        index(donor, "portable.clock", "portable.ClockValue");
        var selected = new ClassPath(List.of(caller), new SubstitutionOptions(true, List.of(donor), List.of(), List.of(), Map.of(), Map.of()));
        assertFalse(selected.registry().platform("java/time/Instant"));
        assertNull(RuntimeLibrary.forbiddenClassReplacementReason("java/time/Instant"));
        Program program = new BytecodeCompiler().compile(request(List.of(caller), "app.Main", donor));
        assertTrue(program.methods().containsKey(new Program.MethodId("java/time/Instant", "now", "()Ljava/time/Instant;")));
        assertTrue(program.methods().containsKey(new Program.MethodId("java/time/Instant", "<init>", "()V")));

        Path explicit = temporary.resolve("explicit-jdk/classes");
        Path classFile = explicit.resolve("java/time/Instant.class");
        Files.createDirectories(classFile.getParent());
        try(var bytes = Object.class.getResourceAsStream("/java/time/Instant.class")) {
            assertNotNull(bytes);
            Files.write(classFile, bytes.readAllBytes());
        }
        assertFalse(new ClassPath(List.of(explicit), SubstitutionOptions.defaults()).registry().platform("java/time/Instant"));
    }

    @Test
    void suppliedOriginalsKeepAccessibleMemberAbiWhilePrivateStateAndMissingUnusedMembersRemainFree() throws Exception {
        Path original = compile("member-abi-original", Map.of("vendor/Counter.java", """
                package vendor;
                public class Counter {
                    public int call() { return 1; }
                    protected int extend() { return 1; }
                    public static int staticCall() { return 1; }
                    public int value;
                    protected int protectedValue;
                    public static int staticValue;
                    private int privateState;
                    private int privateCall() { return 1; }
                    public int unused() { return 1; }
                }
                """));
        var incompatible = Map.ofEntries(
                Map.entry("method-public", List.of("protected int call() { return 2; }", "narrows visibility of method")),
                Map.entry("method-protected", List.of("int extend() { return 2; }", "narrows visibility of method")),
                Map.entry("method-static", List.of("public static int call() { return 2; }", "changes staticness of method")),
                Map.entry("method-instance", List.of("public int staticCall() { return 2; }", "changes staticness of method")),
                Map.entry("method-final", List.of("public final int call() { return 2; }", "adds final restriction to method")),
                Map.entry("field-public", List.of("protected int value;", "narrows visibility of field")),
                Map.entry("field-protected", List.of("int protectedValue;", "narrows visibility of field")),
                Map.entry("field-static", List.of("public static int value;", "changes staticness of field")),
                Map.entry("field-instance", List.of("public int staticValue;", "changes staticness of field")),
                Map.entry("field-final", List.of("public final int value = 2;", "adds final restriction to field")));
        for(var entry : incompatible.entrySet()) {
            Path donor = compile("member-abi-" + entry.getKey(), Map.of("replacement/Portable.java",
                    "package replacement; import com.github.xpenatan.jnative.substitution.SubstituteClass; @SubstituteClass(\"vendor.Counter\") public class Portable { "
                            + entry.getValue().getFirst() + " }"));
            index(donor, "member.abi", "replacement.Portable");
            CompilerException failure = assertThrows(CompilerException.class, () -> new ClassPath(List.of(original),
                    options(List.of(donor), List.of(), Map.of(), Map.of())));
            assertTrue(failure.getMessage().contains(entry.getValue().get(1)), failure.getMessage());
        }
        Path compatible = compile("member-abi-compatible", Map.of("replacement/Portable.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("vendor.Counter")
                public class Portable {
                    public int extend() { return 2; }
                    private String privateState;
                    private static final int privateCall() { return 2; }
                }
                """));
        index(compatible, "member.compatible", "replacement.Portable");
        assertDoesNotThrow(() -> new ClassPath(List.of(original), options(List.of(compatible), List.of(), Map.of(), Map.of())));
    }

    @Test
    void suppliedAbstractOriginalCannotLoseAnAccessibleConcreteMethodBody() throws Exception {
        Path original = compile("concrete-method-original", Map.of("vendor/Counter.java",
                "package vendor; public abstract class Counter { public int add(int n) { return n; } }"));
        Path donor = compile("abstract-method-donor", Map.of("replacement/Portable.java", """
                package replacement;
                import com.github.xpenatan.jnative.substitution.SubstituteClass;
                @SubstituteClass("vendor.Counter")
                public abstract class Portable { public abstract int add(int n); }
                """));
        index(donor, "abstract.method", "replacement.Portable");
        assertTrue(assertThrows(CompilerException.class, () -> new ClassPath(List.of(original),
                options(List.of(donor), List.of(), Map.of(), Map.of()))).getMessage().contains("abstract"));
    }

    private Path counter() throws IOException {
        return compile("original", Map.of("vendor/Counter.java", "package vendor; public class Counter { private int value; public synchronized int add(int n) { value += n; return value; } public int unchanged() { return 42; } }"));
    }
    private NativeBuildRequest request(List<Path> classpath, String main, Path provider) {
        return new NativeBuildRequest(classpath, main, temporary.resolve("output"), null, null, "app", BuildType.DEBUG,
                false, NativeOptions.defaults(), List.of(), List.of(), ConsoleMode.NORMAL, SourceLayout.PACKAGE_FILENAME,
                DiagnosticsOptions.legacy(), new SubstitutionOptions(true, List.of(provider), List.of(), List.of(), Map.of(), Map.of()));
    }
    private Path compileWithProcessor(String directory, String name, String source, String provider) throws IOException {
        Path output = temporary.resolve(directory + "/classes");
        Path file = temporary.resolve(directory + "/sources/" + name);
        Files.createDirectories(output);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-classpath",
                System.getProperty("java.class.path"), "-d", output.toString(), "-processor",
                "com.github.xpenatan.jnative.substitution.processor.SubstitutionProcessor", "-Ajnative.substitutionProvider=" + provider, file.toString()));
        return output;
    }
    private Path classProvider(String directory, String id, String name) throws IOException {
        Path donor = compile(directory, Map.of("replacement/" + name + ".java", "package replacement; import com.github.xpenatan.jnative.substitution.SubstituteClass; @SubstituteClass(\"vendor.Counter\") public class " + name + " { public int add(int n) { return n * 2; } }"));
        index(donor, id, "replacement." + name);
        return donor;
    }
    private Path compile(String directory, Map<String, String> sources, Path... dependencies) throws IOException {
        Path output = temporary.resolve(directory + "/classes");
        Files.createDirectories(output);
        var arguments = new ArrayList<>(List.of("--release", "17", "-proc:none", "-d", output.toString(), "-classpath",
                System.getProperty("java.class.path") + File.pathSeparator + String.join(File.pathSeparator, Arrays.stream(dependencies).map(Path::toString).toList())));
        for(var source : sources.entrySet()) {
            Path file = temporary.resolve(directory + "/sources/" + source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        return output;
    }
    private static void index(Path output, String id, String... declarations) throws IOException {
        Path file = output.resolve(SubstitutionArtifact.INDEX);
        Files.createDirectories(file.getParent());
        Files.writeString(file, Json.write(Map.of("schemaVersion", 1, "providerId", id, "declarations", List.of(declarations))));
    }
    private Path jar(Path directory, String name) throws IOException {
        Path output = temporary.resolve(name);
        try(var stream = new JarOutputStream(Files.newOutputStream(output)); var walk = Files.walk(directory)) {
            for(Path file : walk.filter(Files::isRegularFile).toList()) {
                stream.putNextEntry(new JarEntry(directory.relativize(file).toString().replace('\\', '/')));
                stream.write(Files.readAllBytes(file));
                stream.closeEntry();
            }
        }
        return output;
    }
    private static SubstitutionOptions options(List<Path> providers, List<Path> dependencies, Map<String, String> classes, Map<MethodReference, String> methods) {
        return new SubstitutionOptions(false, providers, dependencies, List.of(), classes, methods);
    }
    private static MethodNode method(ClassNode owner, String name) { return owner.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
}
