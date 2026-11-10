package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class FallbackArrayStorageTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void irreducibleCallerAndStructuredCalleeAgreeOnLocalArrayStorage(BuildType buildType) throws Exception {
        Path source = temporary.resolve("FallbackArrays.java"), classes = temporary.resolve("classes");
        Files.writeString(source, """
                public class FallbackArrays {
                    static int sum(int[] values) {
                        System.gc();
                        int total = 0;
                        for (int i = 0; i < values.length; i++) total += values[i];
                        return total;
                    }
                    static int caller(int choice) { return choice; }
                    public static void main(String[] args) {
                        for (int i = 0; i < 9; i++) System.out.println(caller(i));
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 17);
        Path bytecode = classes.resolve("FallbackArrays.class");
        var owner = new ClassNode();
        new ClassReader(Files.readAllBytes(bytecode)).accept(owner, 0);
        var caller = owner.methods.stream().filter(method -> method.name.equals("caller")).findFirst().orElseThrow();
        var code = new InsnList();
        var first = new LabelNode();
        var second = new LabelNode();
        var done = new LabelNode();
        code.add(new InsnNode(ICONST_4));
        code.add(new IntInsnNode(NEWARRAY, T_INT));
        code.add(new VarInsnNode(ASTORE, 1));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new InsnNode(ICONST_0));
        code.add(new IntInsnNode(BIPUSH, 7));
        code.add(new InsnNode(IASTORE));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new InsnNode(ICONST_3));
        code.add(new IntInsnNode(BIPUSH, 11));
        code.add(new InsnNode(IASTORE));
        code.add(new VarInsnNode(ILOAD, 0));
        code.add(new InsnNode(ICONST_1));
        code.add(new InsnNode(IAND));
        code.add(new JumpInsnNode(IFNE, second));
        code.add(first);
        code.add(new IincInsnNode(0, -1));
        code.add(new VarInsnNode(ILOAD, 0));
        code.add(new JumpInsnNode(IFGT, second));
        code.add(new JumpInsnNode(GOTO, done));
        code.add(second);
        code.add(new IincInsnNode(0, -1));
        code.add(new VarInsnNode(ILOAD, 0));
        code.add(new JumpInsnNode(IFGT, first));
        code.add(done);
        code.add(new MethodInsnNode(INVOKESTATIC, "java/lang/System", "gc", "()V", false));
        code.add(new VarInsnNode(ALOAD, 1));
        code.add(new MethodInsnNode(INVOKESTATIC, "FallbackArrays", "sum", "([I)I", false));
        code.add(new InsnNode(IRETURN));
        caller.instructions = code;
        caller.tryCatchBlocks.clear();
        if(caller.localVariables != null) caller.localVariables.clear();
        var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        owner.accept(writer);
        Files.write(bytecode, writer.toByteArray());

        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "FallbackArrays"), Duration.ofSeconds(30));
        assertEquals(new ProcessHarness.Output(0, "18\n".repeat(9)), expected);
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("FallbackArrays")
                .buildRoot(temporary.resolve("out")).buildType(buildType);
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(), "FallbackArrays");
        assertTrue(cpp.contains("Low-level fallback"), cpp);
        assertTrue(cpp.contains("new_confined_array<std::int32_t>"), cpp);
        assertTrue(cpp.contains("ConfinedArrayView<std::int32_t>"), cpp);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(30), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
