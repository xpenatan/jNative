package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.substitution.SubstituteClass;
import java.io.File;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class SubstitutionExecutionTest {
    @TempDir Path temporary;

    @ParameterizedTest @EnumSource(BuildType.class)
    void unchangedLibraryUsesWholeClassPrivateStatePreviousBodyReflectionAndCpp(BuildType buildType) throws Exception {
        Path library = compile("library", Map.of(
                "vendor/Counter.java", """
                    package vendor;
                    import com.github.xpenatan.jnative.interop.NativeExport;
                    public class Counter {
                        private volatile int value = 5;
                        public synchronized int add(int amount) { value += amount; return value; }
                        public int add() { return 42; }
                        @NativeExport("substitution_counter_scale")
                        public static int scale(int value) { return value * 2; }
                        public static int inspect(Counter counter) { return counter.value; }
                    }
                    """,
                "vendor/Box.java", """
                    package vendor;
                    public class Box {
                        public int value;
                        public Box(int value) { this.value = value; }
                        public int result() { return value; }
                    }
                    """));
        Path original = jar(library, "unchanged-library.jar");
        byte[] originalBytes = Files.readAllBytes(original);
        Path provider = compile("provider", Map.of(
                "different/PortableBox.java", """
                    package different;
                    import com.github.xpenatan.jnative.substitution.*;
                    import java.util.function.Supplier;
                    @SubstituteClass("vendor.Box")
                    public class PortableBox {
                        public int value;
                        private final int multiplier = 10;
                        public PortableBox(int value) { this.value = value; }
                        private class Helper { int apply() { return value * multiplier; } }
                        public int result() {
                            Supplier<Integer> action = () -> new Helper().apply();
                            return action.get();
                        }
                    }
                    """,
                "different/Patches.java", """
                    package different;
                    import com.github.xpenatan.jnative.interop.*;
                    import com.github.xpenatan.jnative.substitution.*;
                    import vendor.Counter;
                    @NativeInclude("substitute.h")
                    public class Patches {
                        @SubstituteMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                        public static int add(Counter self, int amount) {
                            original(self, amount * 2);
                            write(self, read(self) + 3);
                            System.gc();
                            return read(self);
                        }
                        @OriginalMethod(owner="vendor.Counter",name="add",descriptor="(I)I")
                        public static native int original(Counter self, int amount);
                        @TargetField(owner="vendor.Counter",name="value",descriptor="I",access=FieldAccess.GET)
                        public static native int read(Counter self);
                        @TargetField(owner="vendor.Counter",name="value",descriptor="I",access=FieldAccess.SET)
                        public static native void write(Counter self, int value);
                        @SubstituteMethod(owner="vendor.Counter",name="scale",descriptor="(I)I")
                        @NativeImport("substitute_scale")
                        public static native int scale(int value);
                        @SubstituteMethod(owner="vendor.Counter",name="inspect",descriptor="(Lvendor/Counter;)I")
                        public static int inspect(Counter self) { return read(self) + 100; }
                    }
                    """), original);
        index(provider, "test.complete", "different.PortableBox", "different.Patches");
        Path providerJar = jar(provider, "provider.jar");
        Path app = compile("app", Map.of("app/Main.java", """
                    package app;

                    import vendor.Box;
                    import vendor.Counter;
                    public class Main {
                        public static class Child extends Counter {
                            public int add(int amount) { return 100 + amount; }
                            public int parent(int amount) { return super.add(amount); }
                        }
                        public static void main(String[] args) throws Exception {
                            Counter counter = new Counter();
                            System.out.println(counter.add(2));
                            System.out.println(counter.add());
                            System.out.println(Counter.scale(3));
                            System.out.println(Counter.inspect(counter));
                            System.out.println(Counter.class.getMethod("add", int.class).invoke(counter, 1));
                            Child child = new Child();
                            System.out.println(child.add(2));
                            System.out.println(child.parent(2));
                            Box box = new Box(4);
                            System.out.println(box.result());
                            System.out.println(box.getClass().getName());
                            System.out.println(new Box[]{box}[0] instanceof Box);
                        }
                    }
                    """), original);
        var jvm = ProcessHarness.run(temporary, List.of(ProcessHarness.java(), "-cp",
                app + File.pathSeparator + original, "app.Main"), Duration.ofSeconds(30));
        assertEquals(new ProcessHarness.Output(0, "7\n42\n6\n7\n8\n102\n7\n4\nvendor.Box\ntrue\n"), jvm);
        Path header = temporary.resolve("substitute.h");
        Files.writeString(header, "#include <stdint.h>\nextern \"C\" inline int32_t substitute_scale(int32_t value) { return value * 9; }\n");
        var build = ProcessHarness.behavioralBuilder().classpath(app).classpath(original).mainClass("app.Main")
                .substitutionPath(providerJar).nativeFile(header).reflectClass("vendor.Counter")
                .buildType(buildType).sourceLayout(buildType == BuildType.DEBUG ? SourceLayout.PACKAGE_DIRECTORIES : SourceLayout.PACKAGE_FILENAME)
                .cmakeDefine("CMAKE_CXX_STANDARD", buildType == BuildType.DEBUG ? "11" : "17")
                .cmakeDefine("CMAKE_CXX_STANDARD_REQUIRED", "ON").cmakeDefine("CMAKE_CXX_EXTENSIONS", "OFF")
                .buildRoot(temporary.resolve("native")).timeout(Duration.ofMinutes(5)).build();
        var expected = new ProcessHarness.Output(0, "12\n42\n27\n112\n17\n102\n12\n40\nvendor.Box\ntrue\n");
        assertEquals(expected,
                ProcessHarness.run(temporary, List.of(build.executable().toString()), Duration.ofSeconds(45), Map.of("JNATIVE_GC_INTERVAL", "1")));
        assertArrayEquals(originalBytes, Files.readAllBytes(original));
        String report = Files.readString(build.generation().request().generatedSourcesDirectory().resolve("substitutions.json"));
        assertTrue(report.contains("test.complete"), report);
        assertTrue(report.contains("different/PortableBox"), report);
        if(buildType == BuildType.RELEASE) {
            Path exported = NativeProjects.export(build.generation(), temporary.resolve("export")).directory();
            Files.move(original, temporary.resolve("library-retired.jar"));
            Files.move(providerJar, temporary.resolve("provider-retired.jar"));
            Files.move(app, app.resolveSibling("classes-retired"));
            var rebuilt = NativeBuilder.create().buildType(BuildType.RELEASE).compileProject(exported);
            assertEquals(expected, ProcessHarness.run(temporary, List.of(rebuilt.executable().toString()),
                    Duration.ofSeconds(45), Map.of("JNATIVE_GC_INTERVAL", "1")));
        }
    }

    @Test void runtimeCallbacksFactoriesAndClassNameFastPathsObservePatches() throws Exception {
        Path provider = compile("runtime-provider", Map.of(
                "patch/RuntimePatches.java", """
                    package patch;
                    import com.github.xpenatan.jnative.substitution.*;
                    import java.io.FileInputStream;
                    import java.io.IOException;
                    import java.util.zip.CRC32;
                    public class RuntimePatches {
                        public static int calls;
                        @SubstituteMethod(owner="java.lang.String",name="hashCode",descriptor="()I")
                        public static int hash(String self) { calls++; System.gc(); return hashBefore(self) + 7; }
                        @OriginalMethod(owner="java.lang.String",name="hashCode",descriptor="()I")
                        public static native int hashBefore(String self);
                        @SubstituteMethod(owner="java.util.zip.CRC32",name="update",descriptor="(I)V")
                        public static void update(CRC32 self, int value) { updateBefore(self, value ^ 1); }
                        @OriginalMethod(owner="java.util.zip.CRC32",name="update",descriptor="(I)V")
                        public static native void updateBefore(CRC32 self, int value);
                        @SubstituteMethod(owner="java.io.FileInputStream",name="read",descriptor="([BII)I")
                        public static int read(FileInputStream self, byte[] bytes, int offset, int length) throws IOException {
                            int count = readBefore(self, bytes, offset, length);
                            if(count > 0) bytes[offset] = 7;
                            return count;
                        }
                        @OriginalMethod(owner="java.io.FileInputStream",name="read",descriptor="([BII)I")
                        public static native int readBefore(FileInputStream self, byte[] bytes, int offset, int length) throws IOException;
                    }
                    """,
                "patch/NumberFailure.java", """
                    package patch;
                    import com.github.xpenatan.jnative.substitution.*;
                    @SubstituteClass("java.lang.NumberFormatException")
                    public class NumberFailure extends IllegalArgumentException {
                        private String selected;
                        public NumberFailure(String message) { super(message); selected = "selected:" + message; }
                        public String getMessage() { return selected; }
                    }
                    """,
                "patch/Malformed.java", """
                    package patch;
                    import com.github.xpenatan.jnative.substitution.*;
                    import java.nio.charset.CharacterCodingException;
                    @SubstituteClass("java.nio.charset.MalformedInputException")
                    public class Malformed extends CharacterCodingException {
                        private final int length;
                        public Malformed(int length) { this.length = length + 100; }
                        public int getInputLength() { return length; }
                    }
                    """));
        index(provider, "test.runtime", "patch.RuntimePatches", "patch.NumberFailure", "patch.Malformed");
        Path app = compile("runtime-app", Map.of("RuntimeApp.java", """
                    import java.io.FileInputStream;
                    import java.nio.charset.MalformedInputException;
                    import java.nio.file.Files;
                    import java.nio.file.Path;
                    import java.nio.file.Paths;
                    import java.util.*;
                    import java.util.zip.*;
                    import patch.RuntimePatches;
                    public class RuntimeApp {
                        public static void main(String[] args) throws Exception {
                            System.out.println("a".hashCode());
                            System.out.println(((Object)"a").hashCode());
                            int before = RuntimePatches.calls;
                            HashMap<String, Integer> map = new HashMap<>();
                            map.put("a", 9);
                            System.out.println(map.get(new String("a")));
                            System.out.println(RuntimePatches.calls > before);
                            try { Integer.parseInt("bad"); }
                            catch(NumberFormatException e) { System.out.println(e.getMessage().startsWith("selected:")); }
                            byte[] data = {1, 2, 3, 4};
                            CRC32 bulk = new CRC32(), scalar = new CRC32();
                            bulk.update(data);
                            for(byte value : data) scalar.update(value);
                            System.out.println(bulk.getValue() == scalar.getValue());
                            Path path = Paths.get("substitution-input.bin");
                            Files.write(path, new byte[]{-1});
                            try(FileInputStream input = new FileInputStream(path.toString())) {
                                System.out.println(input.read());
                            }
                            try { Files.readString(path); }
                            catch(MalformedInputException failure) {
                                System.out.println(failure.getInputLength());
                            }
                        }
                    }
                    """), provider);
        var build = ProcessHarness.behavioralBuilder().classpath(app).mainClass("RuntimeApp")
                .substitutionPath(jar(provider, "runtime-provider.jar"))
                .buildRoot(temporary.resolve("runtime-out")).timeout(Duration.ofMinutes(5)).build();
        assertEquals(new ProcessHarness.Output(0, "104\n104\n9\ntrue\ntrue\ntrue\n7\n101\n"),
                ProcessHarness.run(temporary, List.of(build.executable().toString()), Duration.ofSeconds(45), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    @Test void externalArrayListWinsAlsoInsideBuiltinDonorCode() throws Exception {
        Path provider = compile("list-provider", Map.of("custom/CompactList.java", """
                package custom;
                import com.github.xpenatan.jnative.substitution.*;
                import java.io.Serializable;
                import java.util.AbstractList;
                import java.util.List;
                import java.util.RandomAccess;
                @SubstituteClass("java.util.ArrayList")
                public class CompactList<E> extends AbstractList<E>
                        implements List<E>, RandomAccess, Cloneable, Serializable {
                    private Object[] values = new Object[16];
                    private int count;
                    public CompactList() {}
                    public int size() { return count; }
                    public E get(int index) { return (E)values[index]; }
                    public boolean add(E value) { values[count++] = "custom:" + value; return true; }
                }
                """));
        index(provider, "test.list", "custom.CompactList");
        Path app = compile("list-app", Map.of("ListApp.java", """
                import java.util.ArrayList;
                import java.util.List;

                public class ListApp {
                    public static void main(String[] args) {
                        ArrayList<String> list = new ArrayList<>();
                        list.add("direct");
                        System.out.println(list.get(0));
                        System.out.println(list.getClass().getName());
                        List<String> parts = List.of("a", "b");
                        System.out.println(parts.get(0) + "/" + parts.get(1));
                    }
                }
                """));
        var build = ProcessHarness.behavioralBuilder().classpath(app).mainClass("ListApp")
                .substitutionPath(provider).buildRoot(temporary.resolve("list-out"))
                .timeout(Duration.ofMinutes(5)).build();
        assertEquals(new ProcessHarness.Output(0, "custom:direct\njava.util.ArrayList\ncustom:a/custom:b\n"),
                ProcessHarness.run(temporary, List.of(build.executable().toString()), Duration.ofSeconds(45), Map.of("JNATIVE_GC_INTERVAL", "1")));
        String report = Files.readString(build.generation().request().generatedSourcesDirectory().resolve("substitutions.json"));
        assertTrue(report.contains("shadowed"), report);
    }

    private Path compile(String name, Map<String, String> sources, Path... dependencies) throws Exception {
        Path root = temporary.resolve(name), classes = root.resolve("classes");
        Files.createDirectories(classes);
        var arguments = new ArrayList<>(List.of("--release", "17", "-g", "-proc:none", "-d", classes.toString(), "-cp"));
        var classpath = new ArrayList<String>();
        classpath.add(Path.of(SubstituteClass.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        classpath.add(Path.of(NativeImport.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        for(Path dependency : dependencies) classpath.add(dependency.toString());
        arguments.add(String.join(File.pathSeparator, classpath));
        for(var entry : new TreeMap<>(sources).entrySet()) {
            Path file = root.resolve("src").resolve(entry.getKey());
            Files.createDirectories(file.getParent()); Files.writeString(file, entry.getValue()); arguments.add(file.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        return classes;
    }

    private void index(Path classes, String id, String... declarations) throws Exception {
        Path index = classes.resolve("META-INF/jnative/substitutions.json");
        Files.createDirectories(index.getParent());
        Files.writeString(index, Json.write(Map.of("schemaVersion", 1, "providerId", id, "declarations", List.of(declarations))));
    }

    private Path jar(Path classes, String name) throws Exception {
        Path file = temporary.resolve(name);
        try(var output = new JarOutputStream(Files.newOutputStream(file)); var files = Files.walk(classes)) {
            for(Path entry : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(entry).toString().replace('\\', '/')));
                output.write(Files.readAllBytes(entry)); output.closeEntry();
            }
        }
        return file;
    }
}
