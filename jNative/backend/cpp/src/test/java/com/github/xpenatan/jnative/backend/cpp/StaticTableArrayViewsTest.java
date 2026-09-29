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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

class StaticTableArrayViewsTest {
    @TempDir Path directory;
    private NativeBuildRequest request;

    private Program fixture() throws Exception {
        Path source = directory.resolve("Tables.java");
        Files.writeString(source, """
                public class Tables {
                    static final int[] TABLE = new int[]{3, 5};
                    static int[] mutable = new int[]{7};
                    static volatile int[] changing = new int[]{11};
                    final int[] owned = new int[]{13, 17};
                    static int initialized;
                    static { for (int i=0;i<2;i++) initialized += TABLE[i & 1]; }
                    static class Child extends Tables {}
                    static class Foreign { static final int[] TABLE = new int[]{19}; }
                    static int read(int count, int index) {
                        int result=0; for(int i=0;i<count;i++) result += TABLE[index & 1]; return result;
                    }
                    static int affine(int count) {
                        int result=0; for(int i=0;i<count;i++) result += TABLE[i]; return result;
                    }
                    int instance(int count) {
                        int result=0; for(int i=0;i<count;i++) result += TABLE[i & 1]; return result;
                    }
                    int confined(int count) {
                        int result=0; for(int i=0;i<count;i++) result += owned[i & 1]; return result;
                    }
                    static int nonfinal(int count) {
                        int result=0; for(int i=0;i<count;i++) result += mutable[i & 1]; return result;
                    }
                    static int volatileRead(int count) {
                        int result=0; for(int i=0;i<count;i++) result += changing[i & 1]; return result;
                    }
                    static int foreign(int count) {
                        int result=0; for(int i=0;i<count;i++) result += Foreign.TABLE[i & 1]; return result;
                    }
                    static void marker() { mutable = new int[]{23}; }
                    public static void main(String[] args) {
                        Tables table = new Tables(); marker();
                        System.out.println(read(2,1)+affine(2)+table.instance(2)+table.confined(2)
                            +nonfinal(1)+volatileRead(1)+foreign(1)+Child.TABLE[0]+initialized);
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        request = new NativeBuildRequest(List.of(directory), "Tables", directory.resolve("out"),
                null, null, "static-tables", BuildType.DEBUG, false);
        return new BytecodeCompiler().compile(request);
    }

    private String emit(Program program) {
        return new CppEmitter().emit(program, request).get("classes/Tables.cpp");
    }

    @Test void hoistsOnlyOrdinarySameOwnerStaticFinalMetadata() throws Exception {
        String cpp = emit(fixture());
        for(String name : List.of("read", "affine")) {
            String body = body(cpp, name);
            assertTrue(body.contains("const ::jnative::PrimitiveArrayView<std::int32_t> TABLE_elements"), body);
            assertTrue(body.indexOf("TABLE_elements(") < body.indexOf("while ("), body);
            assertTrue(body.contains("TABLE_elements.get("), body);
            assertTrue(body.contains("TABLE_elements.get_unchecked("), body);
            assertTrue(body.contains("TABLE_elements.covers("), body);
            assertTrue(body.contains("} else {"), body);
            assertFalse(body.contains("ConfinedArrayView<"), body);
            assertTrue(body.contains("loop_safepoints.poll()"), body);
        }
        for(String name : List.of("instance", "foreign"))
            assertFalse(body(cpp, name).contains("TABLE_elements("), body(cpp, name));
        assertFalse(body(cpp, "nonfinal").contains("mutable_elements("), body(cpp, "nonfinal"));
        assertFalse(body(cpp, "volatileRead").contains("changing_elements("), body(cpp, "volatileRead"));
        assertTrue(body(cpp, "confined").contains("ConfinedArrayView<std::int32_t> owned_elements"), cpp);
        assertFalse(body(cpp, "class_initialize").contains("TABLE_elements("), cpp);
    }

    @Test void rejectsReferenceWritesThroughResolvedInheritedOwner() throws Exception {
        Program program = fixture();
        var marker = program.methods().get(new Program.MethodId("Tables", "marker", "()V"));
        for(var instruction : marker.bytecode().instructions)
            if(instruction instanceof FieldInsnNode write && write.getOpcode() == PUTSTATIC) {
                write.owner = "Tables$Child"; write.name = "TABLE";
            }
        assertFalse(body(emit(program), "read").contains("TABLE_elements("));
    }

    @Test void rejectsExplicitInitializerCalls() throws Exception {
        Program program = fixture();
        var main = program.methods().get(program.entry());
        for(var instruction : main.bytecode().instructions)
            if(instruction instanceof MethodInsnNode call && call.name.equals("marker"))
                call.name = "<clinit>";
        assertFalse(body(emit(program), "read").contains("TABLE_elements("));
    }

    @Test void excludesExactReflectionExposure() throws Exception {
        Program program = fixture();
        Set<FieldId> fields = new HashSet<>(program.reflection().fields());
        fields.add(new FieldId("Tables", "TABLE", "[I"));
        program = new Program(program.entry(), program.classes(), program.methods(),
                program.instantiatedClasses(), new ReflectionPlan(program.reflection().classes(),
                program.reflection().methods(), fields), program.origins(), program.substitutions());
        assertFalse(body(emit(program), "read").contains("TABLE_elements("));
    }

    @Test void excludesOpaqueImportsExportsAndDeclaredNativeFieldAccess() throws Exception {
        for(int mode=0; mode<4; mode++) {
            Program program = fixture();
            var methods = new LinkedHashMap<>(program.methods());
            var id = new Program.MethodId("Tables", "marker", "()V");
            var marker = methods.get(id);
            if(mode == 0) {
                marker.bytecode().invisibleAnnotations = new ArrayList<>();
                marker.bytecode().invisibleAnnotations.add(new AnnotationNode(
                        "Lcom/github/xpenatan/jnative/interop/NativeExport;"));
            } else {
                boolean runtimeOnly = mode != 1;
                var binding = new NativeBinding("test_table_hook", "table_hook.hpp", true, false,
                        false, List.of(), mode == 2
                        ? List.of(new NativeBinding.Field("Tables", "TABLE", "[I")) : List.of(),
                        List.of(), false, runtimeOnly, false, false, false);
                methods.put(id, new Program.Method(id, marker.bytecode(), marker.frames(),
                        marker.blocks(), binding, marker.reachabilityPath()));
            }
            program = new Program(program.entry(), program.classes(), methods,
                    program.instantiatedClasses(), program.reflection(), program.origins(), program.substitutions());
            assertEquals(mode == 3, body(emit(program), "read").contains("TABLE_elements("), "mode=" + mode);
        }
    }

    private static String body(String cpp, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* Tables::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        int end = cpp.indexOf("\n}", declaration.end());
        assertTrue(end > declaration.start(), name);
        return cpp.substring(declaration.start(), end);
    }
}
