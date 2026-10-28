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

class DispatchHandoffRootsTest {
    @TempDir Path directory;

    @Test
    void onlyLastUseDispatchesCanDeferTheirCallersRoots() throws Exception {
        Path source = directory.resolve("Handoff.java");
        Files.writeString(source, """
                public class Handoff {
                    static class Key {
                        int value = 3;
                        public int hashCode() { System.gc(); return 71; }
                        public boolean equals(Object other) { System.gc(); return this == other; }
                    }
                    static int spread(Object key) {
                        int h = key == null ? 0 : key.hashCode();
                        return h ^ (h >>> 16);
                    }
                    static int unconditional(Object key) { return key.hashCode() + 1; }
                    static boolean equal(Object a, Object b) {
                        return a == b || a != null && a.equals(b);
                    }
                    static int retained(Object a, Object b) {
                        int h = a.hashCode(); return h + b.hashCode();
                    }
                    static int alias(Object key) {
                        Object saved = key; int h = key.hashCode(); return h + saved.hashCode();
                    }
                    static boolean nested(Object key) { return key.equals(key.toString()); }
                    static int sibling(Object key, Key other) { return key.hashCode() + other.value; }
                    static int handled(Object key) {
                        try { return key.hashCode(); }
                        catch (RuntimeException failure) { return key.hashCode(); }
                    }
                    static synchronized int locked(Object key) { return key.hashCode(); }
                    static int loop(Object key, int n) {
                        int h = 0; for (int i = 0; i < n; i++) h += key.hashCode(); return h;
                    }
                    static int arrayLoop(Object[] keys) {
                        int h = 0; for (int i = 0; i < keys.length; i++) h += keys[i].hashCode(); return h;
                    }
                    static int arrayAlias(Object[] keys) {
                        Object saved = keys[0]; int h = saved.hashCode(); return h + saved.hashCode();
                    }
                    public static void main(String[] args) {
                        Object a = new Key(), b = new Key();
                        System.out.println(spread(a) + unconditional(b));
                        System.out.println(equal(a, b));
                        System.out.println(retained(a, b) + alias(a));
                        System.out.println(nested(a));
                        System.out.println(sibling(a, new Key()));
                        System.out.println(handled(a) + locked(b) + loop(a, 2));
                        System.out.println(arrayLoop(new Object[]{a, b}) + arrayAlias(new Object[]{a}));
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "Handoff",
                directory.resolve("out"), null, null, "handoff", BuildType.DEBUG, false);
        var sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
        String cpp = sources.get("classes/Handoff.cpp");
        assertNotNull(cpp, sources.keySet().toString());
        for(String name : List.of("spread", "unconditional", "equal")) {
            String body = method(cpp, name);
            assertTrue(body.contains("collection_pending()"), body);
            assertTrue(body.contains("::jnative::poll_with_roots("), body);
            String initialized = body.replaceFirst(
                    "(?s)    if \\(!Handoff::initialization_complete.*?\\n    }\\n", "");
            assertFalse(initialized.contains("::jnative::RootFrame<"), body);
            assertFalse(initialized.contains("::jnative::RootAddressFrame<"), body);
        }
        for(String name : List.of("retained", "alias", "nested", "sibling", "handled", "locked", "loop")) {
            String body = method(cpp, name);
            assertFalse(body.contains("collection_pending()"), body);
            assertTrue(body.contains("RootFrame<") || body.contains("RootAddressFrame<"), body);
        }
        String equal = method(cpp, "equal");
        assertTrue(equal.indexOf("collection_pending()") > equal.indexOf("if ("), equal);
        String arrayLoop = method(cpp, "arrayLoop");
        assertTrue(arrayLoop.contains("RootFrame<1>") || arrayLoop.contains("RootAddressFrame<1>"), arrayLoop);
        assertTrue(arrayLoop.contains("Object* element ="), arrayLoop);
        assertFalse(arrayLoop.contains("FrameRoot<> element") || arrayLoop.contains("LocalRoot<> element"), arrayLoop);
        String alias = method(cpp, "arrayAlias");
        assertTrue(alias.contains("RootFrame<") && alias.contains("saved"), alias);
    }

    private static String method(String cpp, String name) {
        int start = cpp.indexOf("\nstd::int32_t Handoff::" + name + "(");
        assertTrue(start >= 0, name);
        return cpp.substring(start, cpp.indexOf("\n}", start));
    }
}
