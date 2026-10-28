package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceStoresTest {
    @TempDir Path directory;

    @Test void onlyExactArraysAndNullValuesLoseCovarianceChecksAndRoots() throws Exception {
        Path source = directory.resolve("Stores.java");
        Files.writeString(source, """
                public class Stores {
                    Object[] exact = new Object[4];
                    Object[] mixed = new Object[4];
                    Object[] covariant = new String[4];
                    void own(Object value) { exact[0] = value; }
                    void clear(Object[] values) { values[0] = null; }
                    void incoming(Object[] values, Object value) { values[0] = value; }
                    void wrong(Object value) { covariant[0] = value; }
                    void changed(Object value) { mixed[0] = value; }
                    void replace(Object[] values) { mixed = values; }
                    static Object[] fresh(Object value) {
                        Object[] first = new Object[1];
                        Object[] alias = first;
                        alias[0] = value;
                        return first;
                    }
                    public static void main(String[] args) {
                        Stores stores = new Stores();
                        stores.own(args); stores.clear(args); stores.incoming(args, "x");
                        stores.wrong("ok"); stores.replace(args); stores.changed("x");
                        fresh(stores);
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "Stores", directory.resolve("out"),
                null, null, "stores", BuildType.RELEASE, false);
        String cpp = new CppEmitter().emit(new BytecodeCompiler().compile(request), request)
                .get("classes/Stores.cpp");
        for(String name : List.of("own", "clear", "fresh"))
            assertTrue(body(cpp, name).contains("reference_set_exact("), cpp);
        for(String name : List.of("own", "clear")) {
            assertFalse(body(cpp, name).contains("RootFrame<"), cpp);
            assertFalse(body(cpp, name).contains("safepoint"), cpp);
        }
        for(String name : List.of("incoming", "wrong", "changed")) {
            assertTrue(body(cpp, name).contains("reference_set("), cpp);
            assertFalse(body(cpp, name).contains("reference_set_exact("), cpp);
        }
    }

    @Test void compatibleDescriptorsProveStoresWithoutAssumingCovarianceAway() throws Exception {
        Path source = directory.resolve("Stores.java");
        Files.writeString(source, """
                public class Stores {
                    static class Base { }
                    static final class Leaf extends Base { }
                    float[][] rows = new float[2][];
                    float[] row() { return new float[1]; }
                    void field(float[] value) { rows[0] = value; }
                    static void nested(float[][] target, float[] value) { target[0] = value; }
                    static void deep(float[][][] target, float[][] value) { target[0] = value; }
                    static void loaded(float[][] target, float[][] source) { target[0] = source[1]; }
                    static void returned(float[][] target, Stores source) { target[0] = source.row(); }
                    static void leaf(Leaf[] target, Leaf value) { target[0] = value; }
                    static void leafRows(Leaf[][] target, Leaf[] value) { target[0] = value; }
                    static void cast(Object target, Object value) { ((float[][])target)[0] = (float[])value; }
                    static void nullable(float[][] target, float[] value, boolean use) {
                        float[][] destination = use ? target : null;
                        float[] selected = use ? value : null;
                        destination[0] = selected;
                    }
                    static void nullableLeaf(Leaf[] target, Leaf value, boolean use) {
                        Leaf selected = use ? value : null;
                        target[0] = selected;
                    }
                    static void covariant(Object[][] target, Object[] value) { target[0] = value; }
                    static void base(Base[] target, Base value) { target[0] = value; }
                    static void unknown(Object[] target, Object value) { target[0] = value; }
                    static void mixed(float[][] first, int[][] second, float[] value, boolean use) {
                        Object[] target = use ? first : second;
                        target[0] = value;
                    }
                    public static void main(String[] args) {
                        Stores stores = new Stores();
                        float[] value = new float[1];
                        stores.field(value); nested(stores.rows, value);
                        deep(new float[1][][], stores.rows); loaded(stores.rows, stores.rows);
                        returned(stores.rows, stores); cast(stores.rows, value);
                        nullable(stores.rows, value, true);
                        Leaf item = new Leaf(); Leaf[] leaves = new Leaf[1];
                        leaf(leaves, item); leafRows(new Leaf[1][], leaves);
                        nullableLeaf(leaves, item, false);
                        covariant(new Leaf[1][], leaves); base(leaves, item);
                        unknown(stores.rows, value); mixed(stores.rows, new int[1][], value, true);
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g:none", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "Stores", directory.resolve("out"),
                null, null, "stores", BuildType.RELEASE, false);
        String cpp = new CppEmitter().emit(new BytecodeCompiler().compile(request), request).get("classes/Stores.cpp");
        for(String name : List.of("field", "nested", "deep", "loaded", "returned", "leaf", "leafRows",
                "cast", "nullable", "nullableLeaf")) {
            assertTrue(body(cpp, name).contains("reference_set_exact("), name + "\n" + cpp);
            assertFalse(body(cpp, name).contains("::jnative::reference_set("), name + "\n" + cpp);
        }
        for(String name : List.of("nested", "deep", "loaded", "leaf", "leafRows"))
            assertFalse(body(cpp, name).contains("RootFrame<"), name + "\n" + cpp);
        for(String name : List.of("covariant", "base", "unknown", "mixed")) {
            assertTrue(body(cpp, name).contains("::jnative::reference_set("), name + "\n" + cpp);
            assertFalse(body(cpp, name).contains("reference_set_exact("), name + "\n" + cpp);
        }
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* Stores::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        return cpp.substring(declaration.start(), cpp.indexOf("\n}", declaration.end()));
    }
}
