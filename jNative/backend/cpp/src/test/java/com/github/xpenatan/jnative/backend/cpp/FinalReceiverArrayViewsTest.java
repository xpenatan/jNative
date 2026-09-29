package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;
import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import com.github.xpenatan.jnative.compiler.NativeBinding;
import com.github.xpenatan.jnative.compiler.Program;
import com.github.xpenatan.jnative.compiler.ReflectionPlan;
import com.github.xpenatan.jnative.compiler.ReflectionPlan.FieldId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;

class FinalReceiverArrayViewsTest {
    @TempDir Path directory;
    private NativeBuildRequest request;

    private Program fixture() throws Exception {
        Path source = directory.resolve("FinalArrays.java");
        Files.writeString(source, """
                public class FinalArrays {
                    static final int[] TABLE = {2,3,5,7};
                    static Object escaped;
                    static class State {
                        final int[] input;
                        final int[][] rows;
                        int[] mutable;
                        volatile int[] changing;
                        State(int[] input, int[][] rows) {
                            this.input=input; this.rows=rows; mutable=input; changing=input;
                        }
                    }
                    static class Escaping {
                        final int[] input;
                        Escaping(int[] input) { escaped=this; this.input=input; }
                    }
                    static class Derived extends State {
                        Derived(int[] input, int[][] rows) { super(input,rows); }
                    }
                    static class Parent { Parent() { escaped=this; } }
                    static class PublishingSuper extends Parent {
                        final int[] input;
                        PublishingSuper(int[] input) { this.input=input; }
                    }
                    static class Delegating {
                        final int[] input;
                        Delegating(int[] input) { this(input,0); }
                        Delegating(int[] input,int ignored) { escaped=this; this.input=input; }
                    }
                    static class Reentrant {
                        final int[] input;
                        Reentrant(int[] input) { reentrant(this,0); this.input=input; }
                    }
                    static void kernel(State s, int count, int index) {
                        for(int i=0;i<count;i++) {
                            int value=s.input[i]+1;
                            if(value>5) value-=3;
                            s.input[i]=value;
                            int[] row=s.rows[i];
                            row[0]=TABLE[index&3]; row[1]=value;
                        }
                    }
                    static int reassign(State s, State other, int count) {
                        int sum=0; for(int i=0;i<count;i++) { sum+=s.input[i]; s=other; } return sum;
                    }
                    static int escaping(Escaping s, int count) {
                        int sum=0; for(int i=0;i<count;i++) sum+=s.input[i]; return sum;
                    }
                    static int inherited(Derived s,int count) {
                        int sum=0; for(int i=0;i<count;i++) sum+=s.input[i]; return sum;
                    }
                    static int publishingSuper(PublishingSuper s,int count) {
                        int sum=0; for(int i=0;i<count;i++) sum+=s.input[i]; return sum;
                    }
                    static int delegating(Delegating s,int count) {
                        int sum=0; for(int i=0;i<count;i++) sum+=s.input[i]; return sum;
                    }
                    static int reentrant(Reentrant s, int count) {
                        int sum=0; for(int i=0;i<count;i++) sum+=s.input[i]; return sum;
                    }
                    static int mutableRead(State s, int count) {
                        int sum=0; for(int i=0;i<count;i++) sum+=s.mutable[i]+s.changing[i]; return sum;
                    }
                    static int negativeMask(int count, int index) {
                        int sum=0; for(int i=0;i<count;i++) sum+=TABLE[index&-1]; return sum;
                    }
                    static int divergentMask(State s,int count) {
                        int sum=0; for(int i=0;i<count;i++) {
                            int index; if(s.input[i]>0) index=i&1; else index=i&3;
                            sum+=TABLE[index];
                        } return sum;
                    }
                    static void stores(State s, Object[] values, Object value, int count) {
                        for(int i=0;i<count;i++) { values[i]=value; s.input[i]++; }
                    }
                    static void marker(State s, int[] value) { s.mutable=value; }
                    public static void main(String[] args) {
                        State s=new State(new int[]{1,2},new int[][]{{0,0},{0,0}});
                        kernel(s,2,1); stores(s,new Object[2],s,2); marker(s,s.input);
                        System.out.println(reassign(s,s,2)+escaping(new Escaping(s.input),2)
                            +reentrant(new Reentrant(s.input),2)+mutableRead(s,2)+negativeMask(2,0)
                            +inherited(new Derived(s.input,s.rows),2)+publishingSuper(new PublishingSuper(s.input),2)
                            +delegating(new Delegating(s.input),2)+divergentMask(s,2));
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        request = new NativeBuildRequest(List.of(directory), "FinalArrays", directory.resolve("out"),
                null, null, "final-receivers", BuildType.DEBUG, false);
        return new BytecodeCompiler().compile(request);
    }

    private String emit(Program program) {
        return new CppEmitter().emit(program, request).get("classes/FinalArrays.cpp");
    }

    @Test void guardedViewsCrossBranchesAndKeepRowsChecked() throws Exception {
        String kernel = body(emit(fixture()), "kernel");
        assertTrue(kernel.contains("PrimitiveArrayView<std::int32_t> input_elements"), kernel);
        assertTrue(kernel.contains("ReferenceArrayView rows_elements"), kernel);
        assertTrue(kernel.indexOf("input_elements(") < kernel.indexOf("while ("), kernel);
        assertTrue(kernel.contains("input_elements.get_unchecked("), kernel);
        assertTrue(kernel.contains("input_elements.set_unchecked("), kernel);
        assertTrue(kernel.contains("rows_elements.get_unchecked("), kernel);
        assertTrue(kernel.contains("TABLE_elements.covers(0LL, 3LL)"), kernel);
        assertTrue(kernel.contains("TABLE_elements.get_unchecked("), kernel);
        assertTrue(kernel.contains("chunk_end"), kernel);
        assertTrue(kernel.contains(" + 64LL"), kernel);
        assertTrue(kernel.contains("row_store_elements.covers(0, 1)"), kernel);
        assertTrue(kernel.contains("::jnative::array_set<std::int32_t>(row, 0,"), kernel);
        assertTrue(kernel.contains("::jnative::array_set<std::int32_t>(row, 1,"), kernel);
        assertFalse(kernel.contains("ConfinedArrayView<"), kernel);
        assertTrue(kernel.contains("require_non_null(s.get())"), kernel); // checked fallback
    }

    @Test void excludesReassignmentPublicationInitializationAndReferenceStores() throws Exception {
        String cpp = emit(fixture());
        for(String name : List.of("reassign", "escaping", "reentrant", "publishingSuper", "delegating"))
            assertFalse(body(cpp,name).contains("input_elements("), body(cpp,name));
        String mutable = body(cpp,"mutableRead");
        assertFalse(mutable.contains("mutable_elements("), mutable);
        assertFalse(mutable.contains("changing_elements("), mutable);
        assertFalse(body(cpp,"negativeMask").contains("TABLE_elements.get_unchecked("));
        assertFalse(body(cpp,"divergentMask").contains("TABLE_elements.get_unchecked("));
        assertTrue(body(cpp,"inherited").contains("input_elements.get_unchecked("));
        String stores = body(cpp,"stores");
        assertTrue(stores.contains("::jnative::reference_set("), stores);
        assertFalse(stores.contains("ReferenceArrayView values_elements"), stores);
    }

    @Test void excludesReflectionAndWritesOutsideConstructor() throws Exception {
        Program program = fixture();
        var fields = new HashSet<>(program.reflection().fields());
        fields.add(new FieldId("FinalArrays$State", "input", "[I"));
        Program reflected = new Program(program.entry(),program.classes(),program.methods(),
                program.instantiatedClasses(),new ReflectionPlan(program.reflection().classes(),
                program.reflection().methods(),fields),program.origins(),program.substitutions());
        assertFalse(body(emit(reflected),"kernel").contains("input_elements("));
        var marker = program.methods().get(new Program.MethodId("FinalArrays","marker","(LFinalArrays$State;[I)V"));
        for(var instruction : marker.bytecode().instructions)
            if(instruction instanceof FieldInsnNode write && write.getOpcode()==PUTFIELD) write.name="input";
        assertFalse(body(emit(program),"kernel").contains("input_elements("));
    }

    @Test void excludesOpaqueImportsExportsAndExposedNativeFields() throws Exception {
        for(int mode=0;mode<4;mode++) {
            Program program=fixture();
            var methods=new LinkedHashMap<>(program.methods());
            var id=new Program.MethodId("FinalArrays","marker","(LFinalArrays$State;[I)V");
            var marker=methods.get(id);
            if(mode==0) {
                marker.bytecode().invisibleAnnotations=new ArrayList<>();
                marker.bytecode().invisibleAnnotations.add(new AnnotationNode(
                        "Lcom/github/xpenatan/jnative/interop/NativeExport;"));
            } else {
                var binding=new NativeBinding("final_hook","final_hook.hpp",true,false,false,List.of(),
                        mode==2 ? List.of(new NativeBinding.Field("FinalArrays$State","input","[I")) : List.of(),
                        List.of(),false,mode!=1,false,false,false);
                methods.put(id,new Program.Method(id,marker.bytecode(),marker.frames(),marker.blocks(),
                        binding,marker.reachabilityPath()));
            }
            program=new Program(program.entry(),program.classes(),methods,program.instantiatedClasses(),
                    program.reflection(),program.origins(),program.substitutions());
            assertEquals(mode==3,body(emit(program),"kernel").contains("input_elements("),"mode="+mode);
        }
    }

    private static String body(String cpp,String name) {
        var declaration=Pattern.compile("(?m)^\\S[^\\n]* FinalArrays::"+Pattern.quote(name)+"\\(").matcher(cpp);
        assertTrue(declaration.find(),name);
        int end=cpp.indexOf("\n}",declaration.end());
        assertTrue(end>declaration.start(),name);
        return cpp.substring(declaration.start(),end);
    }
}
