package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TailCallRootsTest {
    @TempDir Path directory;
    private NativeBuildRequest request;

    private Program fixture() throws Exception {
        Path source = directory.resolve("TailRoots.java");
        Files.writeString(source, """
                public class TailRoots {
                    static int scalar;
                    static class First { static int value = initialize(); }
                    static class Second { static int value = initialize(); }
                    static int initialize() { System.gc(); return 7; }
                    static class Payload {
                        int value;
                        Payload read(Payload[] rows) {
                            if(rows.length == 0) throw new IllegalArgumentException();
                            value += First.value + Second.value;
                            return rows[0];
                        }
                        Payload prefix(Payload[] rows) { return collect(read(rows)); }
                        Payload warmedRead(Payload[] rows, boolean fail) {
                            Payload other = rows[0];
                            value += First.value + Second.value;
                            if(fail) { System.gc(); scalar += other.value + value; throw new IllegalStateException(); }
                            return other;
                        }
                        Payload handledRead(Payload[] rows) {
                            try { return read(rows); } catch(RuntimeException e) { throw e; }
                        }
                        Payload switchedRead(Payload[] rows, int kind) {
                            switch(kind) { case 1: return read(rows); case 7: return read(rows); default: return read(rows); }
                        }
                        final Payload collect(Payload other) {
                            System.gc(); other.value += value; return other;
                        }
                    }
                    static Payload target(Payload a) { System.gc(); scalar += a.value; return a; }
                    static Payload allocatingPrefix(Payload a) { return target(a.read(new Payload[0])); }
                    static Payload warmed(Payload a) { return target(a); }
                    static Payload dependencies(Payload a) { return target(dependencyRead(a)); }
                    static Payload dependencyRead(Payload a) { scalar += First.value + Second.value; return a; }
                    static void scopedTarget(Payload a, boolean cold) { if(cold) System.gc(); scalar += a.value; }
                    static void scopedWrapper(Payload a, boolean cold) { scopedTarget(a, cold); }
                    static Payload allocating(Payload a) { return target(new Payload()); }
                    static Payload handled(Payload a) {
                        try { return target(dependencyRead(a)); } catch(RuntimeException e) { return a; }
                    }
                    static synchronized Payload locked(Payload a) { return target(dependencyRead(a)); }
                    static Payload later(Payload a) { target(dependencyRead(a)); return a; }
                    static Payload cycleA(Payload a) { return cycleB(a); }
                    static Payload cycleB(Payload a) { return cycleA(a); }
                    public static void main(String[] args) {
                        Payload a = new Payload(); Payload[] rows = {a};
                        a.prefix(rows); dependencies(a); warmed(a); allocatingPrefix(a); allocating(a);
                        a.warmedRead(rows, false); a.handledRead(rows); a.switchedRead(rows, 1);
                        handled(a); locked(a); later(a); scopedWrapper(a, true);
                        if(args.length > 100) cycleA(a);
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        request = new NativeBuildRequest(List.of(directory), "TailRoots", directory.resolve("out"),
                null, null, "tail-roots", BuildType.DEBUG, true);
        return new BytecodeCompiler().compile(request);
    }

    @Test void boundedReturningWarmEntryScopesCollectingFailureRootsAndKeepsExcludedShapes() throws Exception {
        Program program = fixture();
        Map<String, String> sources = new CppEmitter().emit(program, request);
        String cpp = sources.get("classes/TailRoots_Payload.cpp");
        for(String name : List.of("read", "warmedRead")) {
            String body = body(cpp, "TailRoots_Payload", name);
            int cold = body.indexOf("    ::jnative::DeferredRootFrame<");
            assertTrue(cold > 0, body);
            String warm = body.substring(0, cold);
            assertTrue(warm.contains("TailRoots_First::initialization_complete.load"), warm);
            assertTrue(warm.contains("TailRoots_Second::initialization_complete.load"), warm);
            assertFalse(warm.contains("gc_roots") || warm.contains("DeferredRootFrame<"), warm);
            assertTrue(warm.contains("RootValue<> keep_alive"), warm);
            assertTrue(warm.contains("RootAddressFrame<"), warm);
            assertTrue(body.substring(cold).contains("gc_roots.activate()"), body);
            if(name.equals("warmedRead")) {
                int branch = warm.indexOf("if (fail");
                int published = warm.indexOf("RootAddressFrame<", branch);
                assertTrue(branch > 0 && published > branch, warm);
                assertTrue(warm.substring(published).contains("other.address()"), warm);
                assertTrue(warm.substring(published).contains("keep_alive.address()"), warm);
                assertTrue(published < warm.indexOf("System", published), warm);
            }
        }
        // Handlers and switches retain the conservative original lowering.
        for(String name : List.of("handledRead", "switchedRead")) {
            String body = body(cpp, "TailRoots_Payload", name);
            assertTrue(body.contains("RootFrame<"), body);
            assertFalse(body.contains("RootValue<> keep_alive"), body);
        }
        String effect = sources.get("method-effects.tsv").lines()
                .filter(line -> line.startsWith("TailRoots$Payload.warmedRead("))
                .findFirst().orElseThrow();
        assertEquals("false", effect.split("\t", -1)[1], effect);
        assertEquals("true", effect.split("\t", -1)[5], effect);
        assertEquals("false", effect.split("\t", -1)[7], effect);
    }

    @Test void exactPrefixGuardsPrecedeRootlessReturningPrefixAndRootedColdFallback() throws Exception {
        Program program = fixture();
        Map<String, String> sources = new CppEmitter().emit(program, request);
        String cpp = sources.get("classes/TailRoots.cpp");
        for(String owner : List.of("TailRoots", "TailRoots_Payload")) {
            String body = body(sources.get("classes/" + owner + ".cpp"), owner,
                    owner.equals("TailRoots") ? "dependencies" : "prefix");
            int cold = body.indexOf("    ::jnative::RootFrame<");
            assertTrue(cold > 0, body);
            String warm = body.substring(0, cold);
            assertTrue(warm.contains("TailRoots_First::initialization_complete.load(std::memory_order_acquire)"), warm);
            assertTrue(warm.contains("TailRoots_Second::initialization_complete.load(std::memory_order_acquire)"), warm);
            assertFalse(warm.contains("FrameRoot<") || warm.contains("gc_roots") || warm.contains("safepoint"), warm);
            assertTrue(body.substring(cold).contains("gc_roots.heap().poll_if_requested()"), body);
            if(owner.equals("TailRoots_Payload"))
                assertTrue(body.substring(cold).contains("FrameRoot<> keep_alive"), body);
        }
        String collect = body(sources.get("classes/TailRoots_Payload.cpp"), "TailRoots_Payload", "collect");
        assertTrue(collect.indexOf("FrameRoot<> keep_alive") < collect.indexOf("poll_if_requested()"), collect);
        assertTrue(collect.indexOf("FrameRoot<> other") < collect.indexOf("poll_if_requested()"), collect);
        for(String name : List.of("allocating", "handled", "locked", "later", "cycleA", "cycleB", "allocatingPrefix", "scopedWrapper")) {
            String body = body(cpp, "TailRoots", name);
            assertTrue(body.contains("gc_roots"), body);
            assertFalse(body.contains("TailRoots_First::initialization_complete.load"), body);
        }
        String warmed = body(cpp, "TailRoots", "warmed");
        assertFalse(warmed.contains("gc_roots") || warmed.contains("safepoint"), warmed);
        String effect = sources.get("method-effects.tsv").lines()
                .filter(line -> line.startsWith("TailRoots.dependencies("))
                .findFirst().orElseThrow();
        assertEquals("false", effect.split("\t", -1)[1], effect);
        assertEquals("false", effect.split("\t", -1)[5], effect);
        assertEquals("true", effect.split("\t", -1)[7], effect);
    }

    @Test void emittedPublicationAndFallbackRevokeDelegationWithoutBoundingTheBody() throws Exception {
        Program program = fixture();
        MethodId caller = new MethodId("TailRoots", "dependencies", "(LTailRoots$Payload;)LTailRoots$Payload;");
        MethodId target = new MethodId("TailRoots", "target", caller.descriptor());
        MethodEffects effects = new MethodEffects(program, ignored -> false);
        assertTrue(effects.delegatesRoots(caller));
        assertEquals(Set.of("TailRoots$First", "TailRoots$Second"),
                effects.delegationInitializationDependencies(caller));
        assertFalse(effects.boundedAfterInitialization(caller));
        Map<MethodId, Boolean> entries = new HashMap<>();
        effects.tailLowerings().forEach(id -> entries.put(id, true));
        entries.put(target, false);
        assertFalse(effects.tailEntriesProtected(entries));
        assertTrue(effects.validateTailEntries(entries));
        assertFalse(effects.delegatesRoots(caller));
        assertTrue(effects.delegationInitializationDependencies(caller).isEmpty());
        effects = new MethodEffects(program, ignored -> false);
        effects.fallback(Set.of(target));
        assertFalse(effects.delegatesRoots(caller));
    }

    private static String body(String cpp, String owner, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* " + Pattern.quote(owner + "::" + name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), owner + "." + name + " declarations: "
                + cpp.lines().filter(line -> line.contains(name + "(")).toList());
        return cpp.substring(declaration.start(), cpp.indexOf("\n}", declaration.end()));
    }
}
