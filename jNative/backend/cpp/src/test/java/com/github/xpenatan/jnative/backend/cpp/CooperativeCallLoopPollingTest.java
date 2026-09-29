package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.NativeBinding;
import com.github.xpenatan.jnative.compiler.PlatformBindings;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.SubstitutionRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.tree.MethodInsnNode;

class CooperativeCallLoopPollingTest {
    @TempDir Path directory;
    private NativeBuildRequest request;

    private Program fixture() throws Exception {
        Path source = directory.resolve("CooperativeCalls.java");
        Files.writeString(source, """
                public class CooperativeCalls {
                    static volatile int scalar;
                    interface Step { int apply(int[] a,boolean cold); }
                    static class First implements Step {
                        public int apply(int[] a,boolean cold) { if(cold) System.gc(); return a[0]; }
                    }
                    static class FinalStep {
                        final int apply(int[] a,boolean cold) { if(cold) System.gc(); return a[0]; }
                    }
                    static class Foreign {
                        static int value=initialize();
                        static int initialize() { System.gc(); return 7; }
                        static int apply(int[] a,boolean cold) { if(cold) System.gc(); return a[0]+value; }
                    }
                    static int cold(int[] a,boolean collect) {
                        if(collect) { int[] temp=new int[20]; temp[0]=a[0]; System.gc();
                            if(temp[0]!=a[0]) throw new IllegalStateException("temporary"); }
                        return a[0]+scalar;
                    }
                    static int huge(int[] a,boolean collect) {
                        if(collect) { int[] temp=new int[65]; System.gc(); a[0]+=temp[0]; }
                        return a[0];
                    }
                    static int variable(int[] a,boolean collect,int size) {
                        if(collect) { int[] temp=new int[size]; System.gc(); a[0]+=temp[0]; }
                        return a[0];
                    }
                    static int negative(int[] a,boolean collect) {
                        if(collect) { int[] temp=new int[-1]; System.gc(); a[0]+=temp[0]; }
                        return a[0];
                    }
                    static int recursive(int[] a,boolean collect,int depth) {
                        if(collect) { if(depth>0) a[0]+=recursive(a,true,depth-1); System.gc(); }
                        return a[0];
                    }
                    static int leaf(int x) { %s return x; }
                    static int expensive(int[] a,boolean collect) {
                        if(collect) { a[0]=leaf(a[0])+leaf(a[0])+leaf(a[0])+leaf(a[0]); System.gc(); } return a[0];
                    }
                    static int medium(int[] a,boolean collect) {
                        if(collect) { a[0]=leaf(a[0]); System.gc(); } return a[0];
                    }
                    static int handled(int[] a,boolean collect) {
                        try { if(collect) System.gc(); } catch(RuntimeException failure) { return -1; }
                        return a[0];
                    }
                    static synchronized int monitored(int[] a,boolean collect) {
                        if(collect) System.gc(); return a[0];
                    }
                    static int stable(int[][] rows,int[] live,int n,boolean collect) {
                        int sum=0; for(int i=0;i<n;i++) sum+=rows[i][0]+cold(live,collect); return sum+live[1];
                    }
                    static int finalLoop(FinalStep s,int[] a,int n,boolean c) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]+s.apply(a,c); return sum;
                    }
                    static int dispatch(Step s,int[] a,int n,boolean c) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]+s.apply(a,c); return sum;
                    }
                    static int foreign(int[] a,int n,boolean c) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]+Foreign.apply(a,c); return sum;
                    }
                    static int rejected(int[] a,int n,boolean c) {
                        int sum=0; for(int i=0;i<n;i++) sum+=huge(a,c)+variable(a,c,20)
                            +negative(a,c)+recursive(a,c,2)+handled(a,c)+monitored(a,c); return sum;
                    }
                    static int tooExpensive(int[] a,int n,boolean c) {
                        int sum=0; for(int i=0;i<n;i++) sum+=expensive(a,c); return sum;
                    }
                    static int repeated(int[] a,int n,boolean c) {
                        int sum=0; for(int i=0;i<n;i++) sum+=medium(a,c)+medium(a,c); return sum;
                    }
                    public static void main(String[] args) {
                        int[] a={3,5}; int[][] rows={a};
                        System.out.println(stable(rows,a,0,false)+finalLoop(new FinalStep(),a,0,false)
                            +dispatch(new First(),a,0,false)+foreign(a,0,false)+rejected(a,0,false)
                            +tooExpensive(a,0,false)+repeated(a,0,false));
                    }
                }
                """.formatted("x+=7;".repeat(140)));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        request = new NativeBuildRequest(List.of(directory), "CooperativeCalls", directory.resolve("out"),
                null, null, "cooperative-calls", BuildType.DEBUG, false);
        return new BytecodeCompiler().compile(request);
    }

    @Test void collectingCallsRetainCallerRootsAndOnlyGuardStableOuterArrays() throws Exception {
        Program program = fixture();
        var sources = new CppEmitter().emit(program, request);
        String cpp = sources.get("classes/CooperativeCalls.cpp");
        for(String name : List.of("stable", "finalLoop")) {
            String body = body(cpp, name);
            assertTrue(body.contains("++ & 63u"), body);
            assertTrue(body.contains("chunk_end"), body);
            assertTrue(body.contains("get_unchecked("), body);
            assertTrue(body.contains("RootAddressFrame<"), body);
            assertFalse(body.contains("DeferredRootFrame<"), body);
            assertTrue(body.indexOf("RootAddressFrame<", body.indexOf("if (i"))
                    < body.indexOf("::jnative::LoopSafepoint"), body);
            assertTrue(body.contains(".get("), body);
        }
        String stable = body(cpp, "stable");
        assertTrue(stable.contains("rows_elements.get_unchecked("), stable);
        assertTrue(stable.contains("::jnative::array_get<"), stable);
        assertTrue(stable.contains("CooperativeCalls::initialized_cold(live.get()"), stable);
        String cold = body(cpp, "cold");
        assertTrue(cold.contains("RootAddressFrame<"), cold);
        assertTrue(cold.contains("temp.address()"), cold);
        assertFalse(cold.contains("get_unchecked("), cold);
        var effects = new MethodEffects(program, ignored -> false);
        MethodId id = new MethodId("CooperativeCalls", "stable", "([[I[IIZ)I");
        for(var instruction : program.methods().get(id).bytecode().instructions)
            if(instruction instanceof MethodInsnNode call)
                assertFalse(effects.boundedCall(id, call));
    }

    @Test void rejectsRecursionUnknownAllocationDispatchInitializationAndSummedWork() throws Exception {
        var sources = new CppEmitter().emit(fixture(), request);
        String cpp = sources.get("classes/CooperativeCalls.cpp");
        for(String name : List.of("dispatch", "foreign", "rejected", "tooExpensive", "repeated"))
            assertFalse(body(cpp, name).contains("poll_budget"), body(cpp, name));
        String report = sources.get("method-effects.tsv");
        for(String name : List.of("huge", "variable", "negative", "recursive", "expensive", "handled", "monitored"))
            assertTrue(report.lines().filter(line -> line.startsWith("CooperativeCalls." + name + "("))
                    .allMatch(line -> line.endsWith("\t0")), report);
        assertTrue(report.lines().filter(line -> line.startsWith("CooperativeCalls.medium("))
                .noneMatch(line -> line.endsWith("\t0")), report);
    }

    @Test void rejectsNoncanonicalGcBindingDespiteIdenticalApiName() throws Exception {
        Program program = fixture();
        var targets = new LinkedHashMap<>(program.bindings().targets());
        var id = new MethodId("java/lang/System", "gc", "()V");
        var original = targets.get(id);
        var binding = new NativeBinding("custom_gc", "custom_gc.hpp", true, false, false,
                List.of(), List.of(), List.of(), false, false, false, false, false);
        targets.put(id, new PlatformBindings.Target(id, original.helper(), original.instance(), binding));
        var bindings = SubstitutionRegistry.class.getDeclaredField("bindings");
        bindings.setAccessible(true);
        bindings.set(program.substitutions(), new PlatformBindings.Table(targets, program.bindings().fields()));
        String cpp = new CppEmitter().emit(program, request).get("classes/CooperativeCalls.cpp");
        assertFalse(body(cpp, "stable").contains("poll_budget"), cpp);
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* CooperativeCalls::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }
}
