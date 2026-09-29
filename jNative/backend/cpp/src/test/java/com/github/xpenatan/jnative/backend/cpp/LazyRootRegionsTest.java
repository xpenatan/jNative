package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.tree.MethodInsnNode;

class LazyRootRegionsTest {
    @TempDir Path directory;

    @Test
    void completePublishedRegionsLeaveBoundedWarmPathsUnpublished() throws Exception {
        Path source = directory.resolve("LazyRegions.java");
        Files.writeString(source, """
                public class LazyRegions {
                    static int loops(int[] values, int count, boolean cold) {
                        if (cold) System.gc();
                        int i = 0;
                        while (i < count) { System.gc(); values[0]++; i++; }
                        return values[0];
                    }
                    static int twoLoops(int[] values, int count) {
                        int i = 0;
                        while (i < count) { values[0]++; i++; }
                        i = 0;
                        while (i < count) { System.gc(); values[0]++; i++; }
                        return values[0];
                    }
                    static int copy(int[] source, int[] target, int count, boolean cold) {
                        if (cold) System.gc();
                        System.arraycopy(source, 0, target, 0, count);
                        return source[0] + target[0];
                    }
                    static int mutation(int[] first, int[] second, int count) {
                        int[] selected = first;
                        int i = 0;
                        while (i < count) { System.gc(); selected = second; i++; }
                        selected = first;
                        if (count != 0) System.gc();
                        return selected[0];
                    }
                    static int recursive(int[] values, int count) {
                        if (count > 0) return recursive(values, count - 1);
                        return values[0];
                    }
                    static int nested(int[] values, int count) {
                        int i = 0;
                        while (i < count) { int j = 0; while (j < count) { values[0]++; j++; } i++; }
                        return values[0];
                    }
                    static int doLoop(int[] values, int count) {
                        int i = 0; do { values[0]++; i++; } while (i < count);
                        return values[0];
                    }
                    static boolean collectingHeader() { System.gc(); return false; }
                    static int header(int[] values) { while (collectingHeader()) values[0]++; return values[0]; }
                    static int externalExit(int[] values, int count) {
                        int i = 0; while (i < count) { if (values[0] < 0) break; values[0]++; i++; }
                        return values[0];
                    }
                    static int caught(int[] values, int count) {
                        try { int i = 0; while (i < count) { System.gc(); i++; } }
                        catch (RuntimeException failure) { System.gc(); }
                        return values[0];
                    }
                    static int locked(int[] values, int count) {
                        synchronized (values) { int i = 0; while (i < count) { System.gc(); i++; } }
                        return values[0];
                    }
                    static int caller(int[] live, int[] source, int[] target, int count) {
                        copy(source, target, count, false); return live[0];
                    }
                    static int[] terminalTarget(int[] values, int count) {
                        int i = 0; while (i < count) { System.gc(); i++; } return values;
                    }
                    static int[] terminalCaller(int[] values, int count) { return terminalTarget(values, count); }
                    static int nestedFrontier(int[] values, int[] source, int[] target, int count,
                                               boolean outer, boolean cold) {
                        int result = 0;
                        if (outer) { System.gc(); }
                        else { if (cold) { System.gc(); result = values[0]; } }
                        System.arraycopy(source, 0, target, 0, count);
                        return result + values[0];
                    }
                    static int rollback(int[] values, int[] source, int[] target, int count,
                                         boolean outer, boolean cold) {
                        if (outer) {
                            if (cold) System.gc();
                            System.arraycopy(source, 0, target, 0, count);
                            System.gc();
                        }
                        System.arraycopy(source, 0, target, 0, count);
                        return values[0];
                    }
                    static int duplicatedTail(int[] values, int[] source, int[] target, int count,
                                               boolean first, boolean second, boolean cold) {
                        if (first || second) { if (cold) System.gc(); values[0]++; }
                        System.arraycopy(source, 0, target, 0, count);
                        return values[0];
                    }
                    static int largeFallback(int[] values, boolean outer, boolean cold, int count) {
                        int result = 0;
                        if (outer) { %s if (cold) System.gc(); }
                        int i = 0;
                        while (i < count) { values[0]++; i++; }
                        return result;
                    }
                    public static void main(String[] args) {
                        int[] a = new int[80], b = new int[80];
                        System.out.println(loops(a, 0, false) + twoLoops(a, 1) + copy(a, b, 65, false));
                        System.out.println(mutation(a, b, 1) + recursive(a, 1) + nested(a, 1) + doLoop(a, 1));
                        System.out.println(header(a) + externalExit(a, 1) + caught(a, 1) + locked(a, 1));
                        System.out.println(caller(new int[]{9}, a, b, 0));
                        System.out.println(terminalCaller(a, 0)[0]);
                        System.out.println(nestedFrontier(a, a, b, 0, false, true));
                        System.out.println(rollback(a, a, b, 0, true, true));
                        System.out.println(duplicatedTail(a, a, b, 0, true, false, true));
                        System.out.println(largeFallback(a, true, true, 0));
                    }
                }
                """.formatted("result += values[0];\n".repeat(100)));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "LazyRegions", directory.resolve("out"),
                null, null, "lazy-regions", BuildType.DEBUG, false);
        var program = new BytecodeCompiler().compile(request);
        var emitter = new CppEmitter();
        var emitted = emitter.emit(program, request);
        String cpp = emitted.get("classes/LazyRegions.cpp");
        String effects = emitted.get("method-effects.tsv");
        for(String name : List.of("loops", "twoLoops", "copy", "mutation", "terminalTarget")) {
            String effect = effects.lines().filter(line -> line.startsWith("LazyRegions." + name + "("))
                    .findFirst().orElseThrow();
            String[] columns = effect.split("\\t", -1);
            assertEquals("false", columns[1], effect);
            assertEquals("0", columns[8], effect);
        }
        assertTrue(body(cpp, "terminalTarget").contains("RootAddressFrame<"), cpp);
        String terminal = body(cpp, "terminalCaller");
        assertFalse(terminal.contains("gc_roots") || terminal.contains("safepoint"), terminal);
        MethodId target = new MethodId("LazyRegions", "terminalTarget", "([II)[I");
        MethodId caller = new MethodId("LazyRegions", "terminalCaller", "([II)[I");
        var names = new CppNames(program, request.sourceLayout());
        names.registerInitializedMethods(program, Set.of(target));
        var ordinary = new ReadableMethodEmitter(emitter, program, names, request,
                program.methods().get(target)).emit();
        var initialized = new ReadableMethodEmitter(emitter, program, names, request,
                program.methods().get(target), true).emit();
        for(var result : List.of(ordinary, initialized)) {
            assertNotNull(result.source(), result.reason());
            assertFalse(result.protectsEntryArguments(), result.source());
            assertTrue(result.protectsTerminalArguments(), result.source());
            assertFalse(result.protectsArguments(), result.source());
            assertNull(result.cooperativeWork());
        }
        MethodEffects proof = new MethodEffects(program, ignored -> false);
        Map<MethodId, MethodEffects.TerminalArgumentProtection> protection = new HashMap<>();
        proof.tailLowerings().forEach(id -> protection.put(id,
                new MethodEffects.TerminalArgumentProtection(true, false)));
        protection.put(target, new MethodEffects.TerminalArgumentProtection(false, true));
        assertTrue(proof.tailArgumentsProtected(protection));
        assertFalse(proof.validateTailArguments(protection));
        assertTrue(proof.delegatesRoots(caller));
        // Missing ordinary OR initialized target evidence cannot protect a consumer.
        protection.put(target, MethodEffects.TerminalArgumentProtection.NONE);
        assertFalse(proof.tailArgumentsProtected(protection));
        assertTrue(proof.validateTailArguments(protection));
        assertFalse(proof.delegatesRoots(caller));
        proof = new MethodEffects(program, ignored -> false);
        proof.fallback(Set.of(target));
        assertFalse(proof.delegatesRoots(caller));
        for(String name : List.of("loops", "twoLoops", "copy", "mutation")) {
            String body = body(cpp, name);
            assertTrue(body.contains("RootValue<>"), body);
            assertTrue(body.contains("RootAddressFrame<"), body);
            assertFalse(body.contains("DeferredRootFrame<"), body);
            int branch = body.indexOf("if (");
            assertTrue(branch >= 0, body);
            assertFalse(body.substring(0, branch).contains("safepoint"), body);
            assertFalse(body.contains("gc_roots"), body);
        }
        String loop = body(cpp, "loops");
        int loopPoll = loop.indexOf("::jnative::LoopSafepoint");
        assertTrue(loopPoll > loop.indexOf("if (i"), loop);
        assertTrue(loop.lastIndexOf("RootAddressFrame<", loopPoll) > loop.indexOf("if (i"), loop);
        String copy = body(cpp, "copy");
        int fast = copy.indexOf("<= 64");
        int slow = copy.indexOf("} else {", fast);
        assertTrue(fast >= 0 && slow > fast, copy);
        assertTrue(copy.substring(fast, slow).contains("bounded_primitive_array_copy"), copy);
        assertFalse(copy.substring(fast, slow).contains("RootAddressFrame<"), copy);
        assertTrue(copy.substring(slow).contains("RootAddressFrame<"), copy);
        assertTrue(copy.substring(slow).contains("::jnative::safepoint();"), copy);
        String nested = body(cpp, "nestedFrontier");
        int outerElse = nested.indexOf("} else {");
        int inner = nested.indexOf("if (cold", outerElse);
        assertTrue(inner > outerElse, nested);
        assertFalse(nested.substring(outerElse, inner).contains("RootAddressFrame<"), nested);
        assertFalse(nested.substring(outerElse, inner).contains("safepoint"), nested);
        assertTrue(nested.indexOf("RootAddressFrame<", inner) > inner, nested);
        String rollback = body(cpp, "rollback");
        int outer = rollback.indexOf("if (outer");
        assertTrue(rollback.indexOf("RootAddressFrame<", outer) < rollback.indexOf("if (cold", outer), rollback);
        assertEquals(3, rollback.split("::jnative::safepoint\\(\\);", -1).length - 1, rollback);
        String duplicated = body(cpp, "duplicatedTail");
        assertEquals(2, duplicated.split("::platform_System_gc_", -1).length - 1, duplicated);
        assertEquals(3, duplicated.split("::jnative::safepoint\\(\\);", -1).length - 1, duplicated);
        MethodId duplicateId = new MethodId("LazyRegions", "duplicatedTail", "([I[I[IIZZZ)I");
        int bytecodeGc = 0;
        for(var instruction : program.methods().get(duplicateId).bytecode().instructions)
            if(instruction instanceof MethodInsnNode call && call.owner.equals("java/lang/System")
                    && call.name.equals("gc")) bytecodeGc++;
        assertEquals(1, bytecodeGc); // One shared bytecode tail, two emitted occurrences.
        String large = body(cpp, "largeFallback");
        outer = large.indexOf("if (outer");
        assertTrue(large.indexOf("RootAddressFrame<", outer) < large.indexOf("if (cold", outer), large);
        for(MethodId id : List.of(duplicateId,
                new MethodId("LazyRegions", "rollback", "([I[I[IIZZ)I"),
                new MethodId("LazyRegions", "largeFallback", "([IZZI)I"))) {
            var result = new ReadableMethodEmitter(emitter, program, names, request,
                    program.methods().get(id)).emit();
            assertTrue(result.protectsTerminalArguments(), result.source());
            assertFalse(result.protectsEntryArguments(), result.source());
            assertFalse(result.protectsArguments(), result.source());
        }
        assertFalse(body(cpp, "recursive").contains("DeferredRootFrame<"), cpp);
        assertTrue(body(cpp, "recursive").contains("::jnative::safepoint();"), cpp);
        for(String name : List.of("nested", "doLoop", "header", "externalExit", "caught", "locked", "caller")) {
            String body = body(cpp, name);
            assertTrue(body.contains("RootFrame<"), body);
            assertFalse(body.contains("DeferredRootFrame<"), body);
        }
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* LazyRegions::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        return cpp.substring(declaration.start(), cpp.indexOf("\n}", declaration.end()));
    }
}

