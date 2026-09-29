package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.tree.MethodInsnNode;

class LastUseArgumentRootsTest {
    @TempDir Path directory;
    private NativeBuildRequest request;

    private Program fixture() throws Exception {
        Path source = directory.resolve("LastUseRoots.java");
        Files.writeString(source, """
                public class LastUseRoots extends RootParent {
                    static int scalar;
                    interface Sink { void accept(int[] a,boolean c); }
                    static class First implements Sink {
                        public void accept(int[] a,boolean c) { if(c) System.gc(); scalar+=a[0]; }
                    }
                    static class Second implements Sink {
                        public void accept(int[] a,boolean c) { System.gc(); scalar+=a[0]; }
                    }
                    static void cold(int[] a,boolean c) { if(c) System.gc(); scalar+=a[0]; }
                    static void coldTwo(int[] a,int[] b,boolean c) {
                        if(c) System.gc(); scalar+=a[0]+b[0];
                    }
                    static void coldScalar(int[] a,int number,boolean c) {
                        if(c) System.gc(); scalar+=a[0]+number;
                    }
                    static void reassigned(int[] a,boolean c) {
                        if(c) { a=new int[]{41}; System.gc(); } scalar+=a[0];
                    }
                    static void recursive(int[] a,boolean c,int depth) {
                        if(c) { if(depth>0) recursive(a,c,depth-1); System.gc(); } scalar+=a[0];
                    }
                    static void reassignCaller(boolean c) { reassigned(new int[]{7},c); scalar++; }
                    static void recurseCaller(boolean c) { recursive(new int[]{7},c,1); scalar++; }
                    static void subclass(boolean c) { LastUseRoots.inherited(new int[]{7},c); scalar++; }
                    static void delegated(int[] a,boolean c) { cold(a,c); }
                    static int[] fresh() { System.gc(); return new int[]{11}; }
                    static int collectScalar() { System.gc(); return 3; }
                    static void positive(boolean c) { cold(new int[]{7},c); scalar++; }
                    static void rows(int[][] rows,int n,boolean c) {
                        for(int i=0;i<n;i++) cold(rows[i],c); scalar+=rows.length;
                    }
                    static void laterArgument(boolean c) { coldTwo(new int[]{7},fresh(),c); scalar++; }
                    static void inlineScalar(boolean c) { coldScalar(new int[]{7},collectScalar(),c); scalar++; }
                    static void liveAfter(boolean c) {
                        int[] a=new int[]{7}; cold(a,c); scalar+=a[0];
                    }
                    static void liveAlias(boolean c) {
                        Object alias;
                        cold((int[])(alias=new int[]{7}),c); scalar+=((int[])alias)[0];
                    }
                    static void caught(boolean c) {
                        try { cold(new int[]{7},c); } catch(RuntimeException failure) { System.gc(); }
                        scalar++;
                    }
                    static synchronized void monitored(boolean c) { cold(new int[]{7},c); scalar++; }
                    static void wrapper(boolean c) { delegated(new int[]{7},c); scalar++; }
                    static void mixedDispatch(Sink sink,boolean c) { sink.accept(new int[]{7},c); scalar++; }
                    public static void main(String[] args) {
                        positive(false); rows(new int[][]{{7}},1,false); laterArgument(false);
                        inlineScalar(false); liveAfter(false); liveAlias(false); caught(false);
                        monitored(false); wrapper(false); mixedDispatch(new First(),false); mixedDispatch(new Second(),false);
                        reassignCaller(false); recurseCaller(false); subclass(false); RootParent.inside(new int[]{7},false);
                    }
                }
                class RootParent {
                    static int counter;
                    static void inherited(int[] a,boolean c) { if(c) System.gc(); counter+=a[0]; }
                    static void inside(int[] a,boolean c) { LastUseRoots.inherited(a,c); counter++; }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        request = new NativeBuildRequest(List.of(directory), "LastUseRoots", directory.resolve("out"),
                null, null, "last-use-roots", BuildType.DEBUG, false);
        Program program = new BytecodeCompiler().compile(request);
        for(var instruction : program.methods().get(new MethodId("RootParent", "inside", "([IZ)V")).bytecode().instructions)
            if(instruction instanceof MethodInsnNode call && call.name.equals("inherited")) call.owner = "LastUseRoots";
        return program;
    }

    @Test void onlyFinalEvaluatedOperandsHandOffToPublishedStaticVoidCallees() throws Exception {
        var sources = new CppEmitter().emit(fixture(), request);
        String cpp = sources.get("classes/LastUseRoots.cpp");
        for(String name : List.of("positive", "rows")) {
            String body = body(cpp, name);
            assertTrue(body.contains("::initialized_cold("), body);
            assertTrue(body.contains("::jnative::Object* array") || body.contains("::jnative::Object* element"), body);
            assertFalse(body.contains("FrameRoot<> array") || body.contains("FrameRoot<> element"), body);
        }
        String rows = body(cpp, "rows");
        assertTrue(rows.contains("RootValue<> rows"), rows);
        assertTrue(rows.contains("rows.address()"), rows);
        assertTrue(rows.indexOf("RootAddressFrame<", rows.indexOf("if (i"))
                < rows.indexOf("::jnative::LoopSafepoint"), rows);
        assertTrue(rows.contains("loop_safepoints"), rows);
        for(String name : List.of("laterArgument", "inlineScalar", "caught", "monitored", "wrapper", "mixedDispatch"))
            assertTrue(body(cpp, name).contains("FrameRoot<> array("), body(cpp, name));
        assertTrue(body(cpp, "laterArgument").contains("::jnative::Object* fresh_result"), body(cpp, "laterArgument"));
        assertTrue(body(cpp, "inlineScalar").contains("initialized_coldScalar(array.get(), LastUseRoots::collectScalar()"),
                body(cpp, "inlineScalar"));
        assertTrue(body(cpp, "liveAfter").contains("FrameRoot<> a("), body(cpp, "liveAfter"));
        assertTrue(body(cpp, "liveAlias").contains("FrameRoot<> object("), body(cpp, "liveAlias"));
        assertTrue(body(cpp, "liveAlias").contains("FrameRoot<> alias("), body(cpp, "liveAlias"));
        String cold = body(cpp, "cold");
        assertTrue(cold.contains("RootValue<> a"), cold);
        assertTrue(cold.indexOf("a.address()") < cold.indexOf("::ensure_initialized()"), cold);
        assertTrue(cold.indexOf("a.address()", cold.indexOf("::ensure_initialized()"))
                < cold.indexOf("::jnative::safepoint()"), cold);
    }

    @Test void cooperativeVariantsPublishReassignedParametersAndKeepOwnerAndRecursionExclusions() throws Exception {
        var sources = new CppEmitter().emit(fixture(), request);
        String cpp = sources.get("classes/LastUseRoots.cpp");
        for(String name : List.of("cold", "coldTwo", "reassigned")) {
            String ordinary = body(cpp, name), initialized = body(cpp, "initialized_" + name);
            assertTrue(ordinary.contains("::ensure_initialized()"), ordinary);
            assertFalse(initialized.contains("::ensure_initialized()") || initialized.contains("initialization_complete"), initialized);
            assertTrue(initialized.contains("RootValue<> a"), initialized);
            assertTrue(initialized.indexOf("a.address()") < initialized.indexOf("::jnative::safepoint()"), initialized);
        }
        String reassign = body(cpp, "reassignCaller");
        assertTrue(reassign.contains("::initialized_reassigned(array, c)"), reassign);
        assertFalse(reassign.contains("FrameRoot<> array"), reassign);
        String two = body(cpp, "initialized_coldTwo");
        assertTrue(two.contains("a.address(), b.address()"), two);
        assertFalse(sources.get("classes/LastUseRoots.hpp").contains(" initialized_recursive("));
        assertTrue(body(cpp, "recurseCaller").contains("FrameRoot<> array("));
        assertTrue(body(cpp, "subclass").contains("RootParent::inherited("));
        assertFalse(body(cpp, "subclass").contains("initialized_inherited("));
        String parent = sources.get("classes/RootParent.cpp");
        assertTrue(parent.contains("RootParent::initialized_inherited("), parent);
    }

    @Test void workAloneCannotAdvertiseArgumentProtectionAndFallbackRevokesIt() throws Exception {
        Program program = fixture();
        MethodId id = new MethodId("LastUseRoots", "cold", "([IZ)V");
        var effects = new MethodEffects(program, ignored -> false);
        var call = new MethodInsnNode(INVOKESTATIC, id.owner(), id.name(), id.descriptor(), false);
        effects.validatedCooperative(id, new MethodEffects.Summary(20, "test returning work", Set.of()));
        assertFalse(effects.protectsCallArguments(call));
        effects.validatedArgumentProtection(id, true);
        assertTrue(effects.protectsCallArguments(call));
        assertFalse(effects.boundedCall(id, call));
        assertFalse(effects.protectsCallArguments(new MethodInsnNode(INVOKEVIRTUAL,
                id.owner(), id.name(), id.descriptor(), false)));
        MethodId delegated = new MethodId("LastUseRoots", "delegated", "([IZ)V");
        assertTrue(effects.delegatesRoots(delegated));
        effects.validatedCooperative(delegated, new MethodEffects.Summary(20, "test work", Set.of()));
        effects.validatedArgumentProtection(delegated, true);
        assertFalse(effects.protectsArguments(delegated));
        MethodId returned = new MethodId("LastUseRoots", "fresh", "()[I");
        effects.validatedCooperative(returned, new MethodEffects.Summary(20, "test work", Set.of()));
        effects.validatedArgumentProtection(returned, true);
        assertFalse(effects.protectsArguments(returned));
        program.methods().get(id).bytecode().access |= ACC_NATIVE;
        var nativeEffects = new MethodEffects(program, ignored -> false);
        nativeEffects.validatedCooperative(id, new MethodEffects.Summary(20, "test work", Set.of()));
        nativeEffects.validatedArgumentProtection(id, true);
        assertFalse(nativeEffects.protectsCallArguments(call));
        program.methods().get(id).bytecode().access &= ~ACC_NATIVE;
        effects.fallback(Set.of(id));
        effects.validatedArgumentProtection(id, true);
        assertFalse(effects.protectsCallArguments(call));
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* LastUseRoots::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        return cpp.substring(declaration.start(), cpp.indexOf("\n}", declaration.end()));
    }
}
