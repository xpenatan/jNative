package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class EmptyInitializationTest {
    @TempDir Path directory;

    @Test void includesSuperclassDefaultInterfaceAndConstantStringWork() throws Exception {
        Path source = directory.resolve("EmptyInitialization.java");
        Files.writeString(source, """
                public class EmptyInitialization {
                    static int total;
                    static void cold(int[] values, boolean collect) { if (collect) System.gc(); total += values[0]; }
                    static void call(boolean collect) { cold(new int[]{7}, collect); total++; }
                    static class Plain { static final int NUMBER = 42; static int read() { return NUMBER; } }
                    static class PlainChild extends Plain { }
                    static class Active { static int value = 7; }
                    static class ActiveChild extends Active { }
                    interface ActiveDefault {
                        Object VALUE = new Object();
                        default int value() { return 7; }
                    }
                    interface InheritedDefault extends ActiveDefault { }
                    static class DefaultChild implements InheritedDefault { }
                    interface Marker { Object VALUE = new Object(); }
                    static class MarkerChild implements Marker { }
                    static class Text { static final String TEXT = "retained string"; }
                    public static void main(String[] args) {
                        call(false);
                        System.out.println(new PlainChild().read() + new ActiveChild().value
                                + new DefaultChild().value() + new MarkerChild().hashCode()
                                + new Text().hashCode() + ActiveDefault.VALUE.hashCode() + Marker.VALUE.hashCode());
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "EmptyInitialization", directory.resolve("out"),
                null, null, "empty-initialization", BuildType.RELEASE, false);
        var program = new BytecodeCompiler().compile(request);
        var effects = new ClassInitializationEffects(program);
        for (String name : List.of("Plain", "PlainChild", "MarkerChild", "InheritedDefault"))
            assertTrue(effects.empty("EmptyInitialization$" + name), name);
        for (String name : List.of("Active", "ActiveChild", "ActiveDefault", "DefaultChild", "Marker", "Text"))
            assertFalse(effects.empty("EmptyInitialization$" + name), name);
        assertFalse(effects.empty("unknown/Type"));
        var sources = new CppEmitter().emit(program, request);
        String support = sources.get("runtime_support.cpp");
        assertFalse(support.contains("EmptyInitialization_Plain::initialize_slow()"), support);
        assertTrue(support.contains("EmptyInitialization_ActiveChild::initialize_slow()"), support);
        assertTrue(support.contains("EmptyInitialization_DefaultChild::initialize_slow()"), support);
        assertTrue(support.contains("EmptyInitialization_Text::initialize_slow()"), support);
        String cpp = sources.get("classes/EmptyInitialization.cpp");
        int start = cpp.indexOf("\nvoid EmptyInitialization::call(");
        String caller = cpp.substring(start, cpp.indexOf("\n}", start));
        assertFalse(caller.contains("FrameRoot<> array"), caller);
        assertTrue(caller.contains("::jnative::Object* array"), caller);
    }
}
