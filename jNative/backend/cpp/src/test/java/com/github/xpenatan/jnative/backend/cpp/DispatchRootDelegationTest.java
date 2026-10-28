package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DispatchRootDelegationTest {
    @TempDir Path directory;

    @Test
    void dispatchProtectsOnlyTargetsWithoutAnArgumentCertificate() throws Exception {
        Path source = directory.resolve("DispatchRoots.java");
        Files.writeString(source, """
                public class DispatchRoots {
                    interface Safe { Object select(Object first, Object second, boolean collect); }
                    static class Left implements Safe {
                        int bias = 1;
                        public Object select(Object first, Object second, boolean collect) {
                            System.gc();
                            return collect && bias > 0 ? first : second;
                        }
                    }
                    static class Right implements Safe {
                        int bias = 1;
                        public Object select(Object first, Object second, boolean collect) {
                            System.gc();
                            return first == null || bias == 0 ? second : first;
                        }
                    }
                    static class Fast implements Safe {
                        public Object select(Object first, Object second, boolean collect) { return first; }
                    }
                    static class MixedMonitor implements Safe {
                        public synchronized Object select(Object first, Object second, boolean collect) {
                            System.gc();
                            return first;
                        }
                    }
                    interface Guarded { Object locked(Object value); }
                    static class Monitor implements Guarded {
                        public synchronized Object locked(Object value) { System.gc(); return value; }
                    }
                    interface Conditional { Object conditional(Object value, boolean collect); }
                    static class Lazy implements Conditional {
                        public Object conditional(Object value, boolean collect) {
                            if (collect) System.gc();
                            return value;
                        }
                    }
                    public static void main(String[] args) {
                        Safe operation = args.length == 0 ? new Left() : new Right();
                        Object first = new Object(), second = new Object();
                        System.out.println(operation.select(first, second, true) == first);
                        Safe fast = new Fast(), monitor = new MixedMonitor();
                        System.out.println(fast.select(first, second, false) == monitor.select(first, second, true));
                        Guarded guarded = new Monitor();
                        System.out.println(guarded.locked(second) == second);
                        Conditional conditional = new Lazy();
                        System.out.println(conditional.conditional(first, true) == first);
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "DispatchRoots",
                directory.resolve("out"), null, null, "dispatch-roots", BuildType.DEBUG, false);
        var sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
        String support = String.join("\n", sources.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith(".cpp")).map(entry -> entry.getValue()).toList());
        String safe = adapter(support, "select");
        assertTrue(safe.contains("selected dispatch branch protects arguments"), safe);
        assertFalse(safe.contains("RootAddressFrame<") || safe.contains("LocalRoot<"), safe);
        String dispatch = adapter(support, CppEmitter.dispatchName(new MethodId("DispatchRoots$Safe",
                "select", "(Ljava/lang/Object;Ljava/lang/Object;Z)Ljava/lang/Object;")));
        assertFalse(branch(dispatch, "Left").contains("LocalRoot<"), dispatch);
        assertFalse(branch(dispatch, "Right").contains("LocalRoot<"), dispatch);
        assertFalse(branch(dispatch, "Fast").contains("LocalRoot<"), dispatch);
        assertTrue(branch(dispatch, "MixedMonitor").contains("LocalRoot<> receiver_root(receiver)"), dispatch);
        assertTrue(branch(dispatch, "MixedMonitor").contains("LocalRoot<> arg1_root(arg1)"), dispatch);
        String guarded = adapter(support, CppEmitter.dispatchName(new MethodId("DispatchRoots$Guarded",
                "locked", "(Ljava/lang/Object;)Ljava/lang/Object;")));
        assertTrue(guarded.contains("LocalRoot<> receiver_root(receiver)"), guarded);
        assertTrue(guarded.contains("LocalRoot<> arg0_root(arg0)"), guarded);
        String conditional = adapter(support, CppEmitter.dispatchName(new MethodId("DispatchRoots$Conditional",
                "conditional", "(Ljava/lang/Object;Z)Ljava/lang/Object;")));
        assertTrue(conditional.contains("LocalRoot<> receiver_root"), conditional);
    }

    private static String branch(String dispatch, String implementation) {
        int start = dispatch.indexOf("case ::generated::DispatchRoots_" + implementation + "::class_id:");
        assertTrue(start >= 0, dispatch);
        return dispatch.substring(start, dispatch.indexOf("\n        }", start));
    }

    private static String adapter(String cpp, String name) {
        int start = cpp.indexOf(" " + name + "(::jnative::Object* receiver");
        assertTrue(start >= 0, name);
        int end = cpp.indexOf("\n}", start);
        assertTrue(end > start, name);
        return cpp.substring(start, end);
    }
}
