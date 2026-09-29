package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

class ColdRejoiningRootsTest {
    @TempDir Path directory;

    @Test
    void onlyBoundedNoncollectingSurroundingsUseBranchAddressFrames() throws Exception {
        Path source = directory.resolve("ColdRejoiningRoots.java");
        Files.writeString(source, """
                public class ColdRejoiningRoots {
                    int[] field;
                    static class Late {
                        static int value = initialize();
                        static int initialize() { System.gc(); return 9; }
                    }
                    static int rejoin(int[] first, int[] second, boolean cold) {
                        if (cold) {
                            int[] temporary = new int[] { first[0] };
                            System.gc();
                            second[0] = temporary[0];
                        }
                        return first[0] + second[0];
                    }
                    static int[] returned(int[] first, int mode) {
                        int[] selected = first;
                        if (mode < 0) {
                            selected = new int[] { Late.value };
                            if (mode == -1) {
                                int[] nested = new int[] { first[0] };
                                System.gc(); selected[0] += nested[0];
                            }
                            System.gc();
                        } else if (mode == 0) {
                            selected = new int[] { first[0] + 1 };
                            System.gc();
                        }
                        return selected;
                    }
                    static boolean collecting() { System.gc(); return true; }
                    static int recursive(int[] value, boolean cold, int depth) {
                        if (cold) {
                            if (depth > 0) return recursive(value, cold, depth - 1);
                            System.gc();
                        }
                        return value[0];
                    }
                    static int scalarRecursive(boolean cold, int depth) {
                        if (cold) {
                            if (depth > 0) return scalarRecursive(cold, depth - 1);
                            System.gc();
                        }
                        return depth;
                    }
                    static int consumedBeforeCollection(ColdRejoiningRoots owner, boolean cold) {
                        int[] early = owner.field;
                        owner.field = null;
                        if (cold) {
                            int number = early[0];
                            System.gc();
                            return number;
                        }
                        return 0;
                    }
                    static int condition(int[] value) {
                        if (collecting()) value[0]++;
                        return value[0];
                    }
                    static int continuation(int[] value, boolean cold) {
                        if (cold) System.gc();
                        System.gc(); return value[0];
                    }
                    static int loop(int[] value, int count) {
                        while (count-- > 0) { if (count == 2) System.gc(); value[0]++; }
                        return value[0];
                    }
                    static int caught(int[] value, boolean cold) {
                        try { if (cold) { System.gc(); throw new IllegalStateException(); } }
                        catch (IllegalStateException expected) { System.gc(); }
                        return value[0];
                    }
                    static int locked(int[] value, boolean cold) {
                        synchronized (value) { if (cold) System.gc(); }
                        return value[0];
                    }
                    static int initializing(int[] value, boolean cold) {
                        int extra = Late.value;
                        if (cold) System.gc();
                        return value[0] + extra;
                    }
                    public static void main(String[] args) {
                        int[] value = new int[] { 7 }, other = new int[] { 3 };
                        System.out.println(rejoin(value, other, false));
                        System.out.println(returned(value, -1)[0]);
                        System.out.println(condition(value) + continuation(value, true));
                        System.out.println(loop(value, 3) + caught(value, true));
                        System.out.println(locked(value, true) + initializing(value, true));
                        System.out.println(recursive(value, true, 3) + scalarRecursive(true, 3));
                        ColdRejoiningRoots owner = new ColdRejoiningRoots(); owner.field = value;
                        System.out.println(consumedBeforeCollection(owner, true));
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "ColdRejoiningRoots",
                directory.resolve("out"), null, null, "cold-roots", BuildType.DEBUG, false);
        String cpp = new CppEmitter().emit(new BytecodeCompiler().compile(request), request)
                .get("classes/ColdRejoiningRoots.cpp");
        for(String name : List.of("rejoin", "returned", "recursive", "consumedBeforeCollection")) {
            String body = body(cpp, name);
            assertTrue(body.contains("RootValue<>"), body);
            assertTrue(body.contains("RootAddressFrame<"), body);
            assertFalse(body.contains("RootFrame<") || body.contains("DeferredRootFrame<"), body);
            assertFalse(body.contains("poll_if_requested"), body);
            String afterInitialization = body.substring(body.indexOf("::ensure_initialized();")
                    + "::ensure_initialized();".length());
            assertTrue(afterInitialization.indexOf("RootAddressFrame<")
                    > afterInitialization.indexOf("if ("), body);
            assertTrue(afterInitialization.indexOf("::jnative::safepoint();")
                    > afterInitialization.indexOf("RootAddressFrame<"), body);
            assertFalse(afterInitialization.substring(0, afterInitialization.indexOf("if ("))
                    .contains("safepoint"), body);
        }
        String rejoin = body(cpp, "rejoin");
        assertTrue(rejoin.contains("RootValue<> temporary(nullptr)"), rejoin);
        assertTrue(rejoin.substring(rejoin.indexOf("if (cold"))
                .contains("temporary.address()"), rejoin);
        assertTrue(body(cpp, "returned").contains("LocalRoot<> array("), cpp);
        String recursive = body(cpp, "recursive");
        assertTrue(recursive.indexOf("::jnative::safepoint();")
                < recursive.lastIndexOf("ColdRejoiningRoots::recursive("), recursive);
        String scalarRecursive = body(cpp, "scalarRecursive");
        assertFalse(scalarRecursive.contains("RootAddressFrame<"), scalarRecursive);
        assertTrue(scalarRecursive.indexOf("::jnative::safepoint();")
                > scalarRecursive.indexOf("if (cold"), scalarRecursive);
        assertTrue(scalarRecursive.indexOf("::jnative::safepoint();")
                < scalarRecursive.lastIndexOf("ColdRejoiningRoots::scalarRecursive("), scalarRecursive);
        String early = body(cpp, "consumedBeforeCollection");
        assertTrue(early.contains("RootValue<> early(nullptr)"), early);
        assertTrue(early.substring(early.indexOf("if (cold"))
                .contains("early.address()"), early);
        for(String name : List.of("condition", "continuation", "loop", "caught", "locked", "initializing")) {
            String body = body(cpp, name);
            assertTrue(body.contains("RootFrame<"), body);
            assertFalse(body.contains("RootAddressFrame<"), body);
        }
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* ColdRejoiningRoots::"
                + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        int start = declaration.start();
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > start, name);
        return cpp.substring(start, end);
    }
}
