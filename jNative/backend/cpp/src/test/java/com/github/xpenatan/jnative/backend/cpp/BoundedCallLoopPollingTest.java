package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.NativeBinding;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.PlatformBindings;
import com.github.xpenatan.jnative.compiler.SubstitutionRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class BoundedCallLoopPollingTest {
    @TempDir Path directory;
    private NativeBuildRequest request;

    private Program fixture() throws Exception {
        return fixture(true);
    }

    private Program fixture(boolean initializingTarget) throws Exception {
        Path source = directory.resolve("PollCalls.java");
        Files.writeString(source, """
                public class PollCalls extends Parent {
                    static int scalar = 3;
                    static volatile int[] changing = {5};
                    static class FinalStep { final int step(int x) { return x + 1; } }
                    interface Step { int step(int x); }
                    static class First implements Step { public int step(int x) { return x + 2; } }
                    static class Second implements Step { public int step(int x) { return x - 2; } }
                    interface Risk { int step(int x); }
                    static class Safe implements Risk { public int step(int x) { return x; } }
                    static class Collecting implements Risk {
                        public int step(int x) { System.gc(); return x; }
                    }
                    static class Initializing implements Step {
                        public int step(int x) { return Foreign.value + x; }
                    }
                    static class Foreign {
                        static int value = initialize();
                        static int initialize() { System.gc(); return 7; }
                        static int step(int x) { return value + x; }
                    }
                    static class Holder {
                        final int[] fixed;
                        final int[][] rows;
                        int[] mutable;
                        Holder(int[] values,int[][] rows) { fixed=values; mutable=values; this.rows=rows; }
                    }
                    static int step(int x) { return x + 1; }
                    static int read(int[] a,int i) { return a[i]; }
                    static void mutate(int[] a,int i) { a[i]++; }
                    static void replace(Holder h,int[] a) { h.mutable=a; }
                    static void copyRow(int[][] source,int[][] target) { System.arraycopy(source,0,target,0,1); }
                    static void replaceRow(Holder h,int[] a) { h.rows[0]=a; }
                    static int index(int i) { return i; }
                    static int readCall(int[] a,int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]+read(a,i); return sum;
                    }
                    static int aliasMutation(int[] a,int[] alias,int n) {
                        int sum=0; for(int i=0;i<n;i++) { mutate(alias,i); sum+=a[i]; } return sum;
                    }
                    static int finalMutation(Holder h,int[] alias,int n) {
                        int sum=0; for(int i=0;i<n;i++) { mutate(alias,i); sum+=h.fixed[i]; } return sum;
                    }
                    static int mutableReplacement(Holder h,int[] a,int n) {
                        int sum=0; for(int i=0;i<n;i++) {
                            sum+=h.mutable[i]; replace(h,a); sum+=h.mutable[i];
                        } return sum;
                    }
                    static int rowReplacement(Holder h,int[][] replacement,int n) {
                        int sum=0; for(int i=0;i<n;i++) {
                            sum+=h.rows[i][0]; copyRow(replacement,h.rows); sum+=h.rows[i][1];
                        } return sum;
                    }
                    static int directRowReplacement(Holder h,int[] a,int n) {
                        int sum=0; for(int i=0;i<n;i++) { replaceRow(h,a); sum+=h.rows[i][0]; } return sum;
                    }
                    static int unknownIndex(int[] a,int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[index(i)]+step(i); return sum;
                    }
                    static int maskedIndex(int[] a,int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[index(i)&3]+step(i); return sum;
                    }
                    static int changedLimit(int[] a,int n) {
                        int sum=0; for(int i=0;i<n;i++) { sum+=a[i]+step(i); n--; } return sum;
                    }
                    static int boundRead() { return 17; }
                    static int recursive(int x) { return x == 0 ? 0 : recursive(x - 1); }
                    static int leaf(int x) { %s return x; }
                    static int transitive(int x) { return leaf(x) + leaf(x); }
                    static int staticCall(int[] a, int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]+step(i); return sum;
                    }
                    static int finalCall(FinalStep s, int[] a, int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]+s.step(i); return sum;
                    }
                    static int virtualCall(Step s, int[] a, int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]+s.step(i); return sum;
                    }
                    static int safeVirtual(Safe s, int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=s.step(i); return sum;
                    }
                    static int collectingVirtual(Risk s, int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=s.step(i); return sum;
                    }
                    static int recursiveCall(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=recursive(i); return sum;
                    }
                    static int expensiveCall(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=transitive(i); return sum;
                    }
                    static int repeatedCalls(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=leaf(i)+leaf(i); return sum;
                    }
                    static int foreignCall(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=Foreign.step(i); return sum;
                    }
                    static int ownRead(int[] a,int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]+scalar; return sum;
                    }
                    static int volatileRead(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=changing[i]; return sum;
                    }
                    static int inheritedRead(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=PollCalls.shared; return sum;
                    }
                    static int foreignRead(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=Foreign.value; return sum;
                    }
                    int instanceRead(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=scalar; return sum;
                    }
                    static int guarded(int[] a,int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=a[i]; return sum;
                    }
                    static int handled(int n) {
                        try { int sum=0; for(int i=0;i<n;i++) sum+=step(i); return sum; }
                        catch(RuntimeException failure) { return -1; }
                    }
                    static synchronized int monitored(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=step(i); return sum;
                    }
                    static synchronized int monitoredArithmetic(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=i; return sum;
                    }
                    public static void main(String[] args) {
                        int[] a={1,2,3};
                        Holder h=new Holder(a,new int[][]{a});
                        System.out.println(staticCall(a,3)+finalCall(new FinalStep(),a,3)
                            +virtualCall(new First(),a,3)+virtualCall(new Second(),a,3)
                            +safeVirtual(new Safe(),0)+collectingVirtual(new Safe(),0)
                            +collectingVirtual(new Collecting(),0)%s
                            +recursiveCall(0)+expensiveCall(0)+repeatedCalls(0)+foreignCall(0)
                            +ownRead(a,3)+volatileRead(0)+inheritedRead(0)+foreignRead(0)
                            +new PollCalls().instanceRead(0)+Parent.ownRead(0)+guarded(a,3)
                            +handled(0)+monitored(0)+monitoredArithmetic(0)+boundRead()
                            +readCall(a,0)+aliasMutation(a,a,0)+finalMutation(h,a,0)
                            +mutableReplacement(h,a,0)+rowReplacement(h,new int[][]{a},0)
                            +directRowReplacement(h,a,0)+unknownIndex(a,0)+maskedIndex(a,0)+changedLimit(a,0));
                    }
                }
                class Parent {
                    static int shared=11;
                    static int ownRead(int n) {
                        int sum=0; for(int i=0;i<n;i++) sum+=PollCalls.shared; return sum;
                    }
                }
                """.formatted("x += 7; ".repeat(140),
                initializingTarget ? "+virtualCall(new Initializing(),a,0)" : ""));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        request = new NativeBuildRequest(List.of(directory), "PollCalls", directory.resolve("out"),
                null, null, "bounded-call-polling", BuildType.DEBUG, false);
        Program program = new BytecodeCompiler().compile(request);
        // Linking canonicalizes field owners. Restore a legal subclass qualifier
        // to exercise the emitter's declaring-owner proof independently of linking.
        var ownRead = program.methods().get(new MethodId("Parent", "ownRead", "(I)I"));
        for(var instruction : ownRead.bytecode().instructions)
            if(instruction instanceof FieldInsnNode field && field.name.equals("shared"))
                field.owner = "PollCalls";
        return program;
    }

    @Test void provenCallsGuardStableCallerArraysAndKeepCheckedFallbackAndCallee() throws Exception {
        Program program = fixture(false);
        String cpp = new CppEmitter().emit(program, request).get("classes/PollCalls.cpp");
        for(String name : List.of("staticCall", "finalCall", "virtualCall", "safeVirtual", "ownRead")) {
            String body = body(cpp, "PollCalls", name);
            assertTrue(body.contains("++ & 63u"), body);
            assertEquals(!name.equals("safeVirtual"), body.contains("get_unchecked("), body);
            assertEquals(!name.equals("safeVirtual"), body.contains("chunk_end"), body);
            if(!name.equals("safeVirtual")) assertTrue(body.contains(".get("), body);
        }
        String guarded = body(cpp, "PollCalls", "guarded");
        assertTrue(guarded.contains("get_unchecked("), guarded);
        assertTrue(guarded.contains("chunk_end"), guarded);
        String readCall = body(cpp, "PollCalls", "readCall");
        assertTrue(readCall.contains("get_unchecked("), readCall);
        assertTrue(readCall.contains("PollCalls::initialized_read("), readCall);
        String read = body(cpp, "PollCalls", "read");
        assertTrue(read.contains("::jnative::array_get<"), read);
        assertFalse(read.contains("get_unchecked("), read);
    }

    @Test void rejectsUnprovenCallsAndWholeTreeOrRepeatedWorkAboveLoopCeiling() throws Exception {
        Program program = fixture();
        var effects = new MethodEffects(program, ignored -> false);
        MethodId expensive = new MethodId("PollCalls", "expensiveCall", "(I)I");
        MethodInsnNode call = firstCall(program, expensive);
        assertTrue(effects.boundedCall(expensive, call));
        assertTrue(effects.boundedCallWork(expensive, call) > 256);
        MethodId foreign = new MethodId("PollCalls", "foreignCall", "(I)I");
        assertTrue(effects.boundedAfterInitialization(new MethodId("PollCalls$Foreign", "step", "(I)I")));
        assertEquals(0, effects.boundedCallWork(foreign, firstCall(program, foreign)));
        String cpp = new CppEmitter().emit(program, request).get("classes/PollCalls.cpp");
        for(String name : List.of("recursiveCall", "expensiveCall", "repeatedCalls", "foreignCall",
                "collectingVirtual", "virtualCall", "foreignRead", "inheritedRead", "instanceRead",
                "handled", "monitored"))
            assertFalse(body(cpp, "PollCalls", name).contains("poll_budget"), body(cpp, "PollCalls", name));
    }

    @Test void currentStaticProofUsesActualFieldOwnerAndKeepsVolatileLoadsInLoop() throws Exception {
        Map<String, String> sources = new CppEmitter().emit(fixture(), request);
        String own = body(sources.get("classes/Parent.cpp"), "Parent", "ownRead");
        assertTrue(own.contains("++ & 63u"), own);
        String inherited = body(sources.get("classes/PollCalls.cpp"), "PollCalls", "inheritedRead");
        assertFalse(inherited.contains("poll_budget"), inherited);
        String volatileRead = body(sources.get("classes/PollCalls.cpp"), "PollCalls", "volatileRead");
        assertTrue(volatileRead.contains("++ & 63u"), volatileRead);
        int firstTest = volatileRead.indexOf("if (i < n)");
        int activation = volatileRead.indexOf("gc_roots.activate();", firstTest);
        int constructor = volatileRead.indexOf("::jnative::LoopSafepoint", firstTest);
        int iteration = volatileRead.indexOf("do {", constructor);
        assertTrue(firstTest >= 0 && constructor > firstTest, volatileRead);
        if(volatileRead.contains("gc_roots"))
            assertTrue(activation > firstTest && constructor > activation, volatileRead);
        assertTrue(iteration > constructor && volatileRead.indexOf("changing.get()") > iteration, volatileRead);
        assertTrue(sources.get("classes/PollCalls.hpp").contains("StaticReference changing"));
        assertFalse(volatileRead.contains("get_unchecked("), volatileRead);
        assertFalse(volatileRead.contains("changing_elements("), volatileRead);
    }

    @Test void excludesBoundFieldsAtSymbolicAndResolvedOwners() throws Exception {
        for(String owner : List.of("PollCalls", "Parent")) {
            Program program = fixture();
            var fields = new LinkedHashMap<>(program.bindings().fields());
            var field = new PlatformBindings.StaticField(owner, "shared", "I");
            fields.put(field, new PlatformBindings.FieldTarget(field,
                    new MethodId("PollCalls", "boundRead", "()I"),
                    new NativeBinding("bound_read", "bound_read.hpp", true, false, false,
                            List.of(), List.of(), List.of(), false, false, false, false, false)));
            // Use an isolated compilation's table to exercise both exact binding keys
            // without introducing a production-only configuration seam.
            var bindings = SubstitutionRegistry.class.getDeclaredField("bindings");
            bindings.setAccessible(true);
            bindings.set(program.substitutions(), new PlatformBindings.Table(program.bindings().targets(), fields));
            String cpp = new CppEmitter().emit(program, request).get("classes/Parent.cpp");
            String body = body(cpp, "Parent", "ownRead");
            assertFalse(body.contains("poll_budget"), body);
        }
    }

    @Test void preservesExistingSynchronizedArithmeticBudgetWithoutAdmittingManagedCalls() throws Exception {
        String cpp = new CppEmitter().emit(fixture(), request).get("classes/PollCalls.cpp");
        String arithmetic = body(cpp, "PollCalls", "monitoredArithmetic");
        assertTrue(arithmetic.contains("MonitorGuard synchronized_method"), arithmetic);
        assertTrue(arithmetic.contains("++ & 63u"), arithmetic);
        String managed = body(cpp, "PollCalls", "monitored");
        assertTrue(managed.contains("MonitorGuard synchronized_method"), managed);
        assertFalse(managed.contains("poll_budget"), managed);
    }

    @Test void guardsImmutableReferencesWhilePreservingPayloadMutationsAndRowReloads() throws Exception {
        String cpp = new CppEmitter().emit(fixture(), request).get("classes/PollCalls.cpp");
        for(String name : List.of("aliasMutation", "finalMutation", "rowReplacement", "maskedIndex")) {
            String body = body(cpp, "PollCalls", name);
            assertTrue(body.contains("chunk_end"), body);
            assertTrue(body.contains("get_unchecked("), body);
            assertTrue(body.contains(".get("), body); // guard failure remains checked
        }
        String mutate = body(cpp, "PollCalls", "mutate");
        assertTrue(mutate.contains(".set(") || mutate.contains("::jnative::array_set<"), mutate);
        assertFalse(mutate.contains("set_unchecked("), mutate);
        String rows = body(cpp, "PollCalls", "rowReplacement");
        assertTrue(rows.contains("rows_elements.get_unchecked("), rows);
        assertTrue(rows.contains("::jnative::array_get<"), rows);
        assertFalse(rows.contains("ReferenceArrayView replacement_elements"), rows);
        String masked = body(cpp, "PollCalls", "maskedIndex");
        assertTrue(masked.contains(".covers(0LL, 3LL)"), masked);
    }

    @Test void rejectsMutableReferencesCallDerivedIndicesAndChangingLoopLimits() throws Exception {
        String cpp = new CppEmitter().emit(fixture(), request).get("classes/PollCalls.cpp");
        for(String name : List.of("mutableReplacement", "directRowReplacement", "unknownIndex", "changedLimit")) {
            String body = body(cpp, "PollCalls", name);
            assertFalse(body.contains("get_unchecked("), body);
            assertFalse(body.contains("chunk_end"), body);
        }
        String replacement = body(cpp, "PollCalls", "mutableReplacement");
        assertTrue(replacement.contains("PollCalls::initialized_replace("), replacement);
        assertTrue(replacement.contains("++ & 63u"), replacement);
        assertFalse(body(cpp, "PollCalls", "directRowReplacement").contains("poll_budget"));
    }

    private static MethodInsnNode firstCall(Program program, MethodId id) {
        for(var instruction : program.methods().get(id).bytecode().instructions)
            if(instruction instanceof MethodInsnNode call) return call;
        throw new AssertionError(id);
    }

    private static String body(String cpp, String owner, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* " + Pattern.quote(owner) + "::"
                + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), owner + "." + name);
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }
}
