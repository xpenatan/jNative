package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.objectweb.asm.Opcodes.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

class ArrayTypesTest {
    @Test
    void primitiveLeafArraysHaveNoCovariantRuntimeSubtypeAtAnyDimension() {
        for(String leaf : List.of("Z", "B", "C", "S", "I", "J", "F", "D")) {
            for(int dimensions : List.of(2, 3, 5)) {
                String target = "[".repeat(dimensions) + leaf;
                MethodNode method = store(target, target.substring(1));
                assertTrue(ArrayTypes.compatibleStores("Stores", method, name -> false)
                        .contains(method.instructions.get(3)), target);
            }
        }
    }

    @Test
    void covarianceUnknownValuesAndMismatchedDimensionsKeepChecks() {
        for(String[] types : List.of(
                new String[]{"[[F", "[I"}, new String[]{"[[F", "[[F"},
                new String[]{"[[F", "Ljava/lang/Object;"},
                new String[]{"[[Ljava/lang/Object;", "[Ljava/lang/Object;"},
                new String[]{"[LBase;", "LBase;"},
                new String[]{"[Ljava/lang/Cloneable;", "[F"})) {
            assertTrue(ArrayTypes.compatibleStores("Stores", store(types[0], types[1]), name -> false).isEmpty());
        }
        assertTrue(ArrayTypes.compatibleStores("Stores", store("[LLeaf;", "LLeaf;"), name -> false).isEmpty());
        for(int dimensions : List.of(1, 2, 3)) {
            String target = "[".repeat(dimensions) + "LLeaf;";
            MethodNode method = store(target, target.substring(1));
            assertTrue(ArrayTypes.compatibleStores("Stores", method, "Leaf"::equals)
                    .contains(method.instructions.get(3)));
        }
    }

    @Test
    void overwrittenTargetAndLoopMergesInvalidateStoreProofs() {
        MethodNode overwritten = store("[[F", "[F");
        overwritten.instructions.insertBefore(overwritten.instructions.getFirst(), new InsnNode(ICONST_1));
        overwritten.instructions.insertBefore(overwritten.instructions.get(1), new TypeInsnNode(ANEWARRAY, "[I"));
        overwritten.instructions.insertBefore(overwritten.instructions.get(2), new VarInsnNode(ASTORE, 0));
        assertTrue(ArrayTypes.compatibleStores("Stores", overwritten, name -> false).isEmpty());

        MethodNode loop = store("[[F", "[F");
        var header = new LabelNode();
        loop.instructions.insert(header);
        loop.instructions.remove(loop.instructions.getLast());
        loop.instructions.add(new InsnNode(ICONST_1));
        loop.instructions.add(new TypeInsnNode(ANEWARRAY, "[I"));
        loop.instructions.add(new VarInsnNode(ASTORE, 0));
        loop.instructions.add(new JumpInsnNode(GOTO, header));
        assertTrue(ArrayTypes.compatibleStores("Stores", loop, name -> false).isEmpty());
    }

    @Test
    void oversizeOrFailedAnalysisCannotProveStores() {
        MethodNode large = store("[[F", "[F");
        for(int i = 0; i < 4096; i++) large.instructions.insert(new InsnNode(NOP));
        assertTrue(ArrayTypes.compatibleStores("Stores", large, name -> false).isEmpty());
        MethodNode invalid = store("[[F", "[F");
        invalid.maxStack = 0;
        assertTrue(ArrayTypes.compatibleStores("Stores", invalid, name -> false).isEmpty());
    }

    private static MethodNode store(String target, String value) {
        MethodNode method = new MethodNode(ACC_STATIC, "store", "(" + target + value + ")V", null, null);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new InsnNode(AASTORE));
        method.instructions.add(new InsnNode(RETURN));
        method.maxLocals = 2;
        method.maxStack = 3;
        return method;
    }

    @Test
    void oversizedMethodsRetainOnlyUnmodifiedParameterTypes() {
        MethodNode method = new MethodNode(ACC_STATIC, "large", "([[F[[I)V", null, null);
        for(int i = 0; i < 4096; i++) method.instructions.add(new InsnNode(NOP));
        method.instructions.add(new VarInsnNode(ALOAD, 1));
        method.instructions.add(new VarInsnNode(ASTORE, 0));
        var overwritten = new VarInsnNode(ALOAD, 0);
        var untouched = new VarInsnNode(ALOAD, 1);
        method.instructions.add(overwritten);
        method.instructions.add(new InsnNode(POP));
        method.instructions.add(untouched);
        method.instructions.add(new InsnNode(POP));
        method.instructions.add(new InsnNode(RETURN));
        method.maxLocals = 2;
        method.maxStack = 1;
        var types = ArrayTypes.analyze("Arrays", method);
        assertFalse(types.containsKey(overwritten));
        assertEquals(Type.getType("[[I"), types.get(untouched));
    }

    @Test
    void reusedParameterSlotDoesNotKeepItsOriginalArrayKind() {
        MethodNode method = new MethodNode(ACC_STATIC, "reuse", "([[F)V", null, null);
        method.instructions.add(new InsnNode(ICONST_1));
        method.instructions.add(new TypeInsnNode(ANEWARRAY, "[I"));
        method.instructions.add(new VarInsnNode(ASTORE, 0));
        var load = new VarInsnNode(ALOAD, 0);
        var element = new InsnNode(AALOAD);
        method.instructions.add(load);
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(element);
        method.instructions.add(new InsnNode(POP));
        method.instructions.add(new InsnNode(RETURN));
        method.maxLocals = 1;
        method.maxStack = 2;
        var types = ArrayTypes.analyze("Arrays", method);
        assertEquals(Type.getType("[[I"), types.get(load));
        assertEquals(Type.getType("[I"), types.get(element));
    }

    @Test
    void loopBackedgeWithDifferentKindInvalidatesTheEarlierProof() {
        MethodNode method = new MethodNode(ACC_STATIC, "loop", "([[F)V", null, null);
        var header = new LabelNode();
        var element = new InsnNode(AALOAD);
        method.instructions.add(header);
        method.instructions.add(new VarInsnNode(ALOAD, 0));
        method.instructions.add(new InsnNode(ICONST_0));
        method.instructions.add(element);
        method.instructions.add(new InsnNode(POP));
        method.instructions.add(new InsnNode(ICONST_1));
        method.instructions.add(new TypeInsnNode(ANEWARRAY, "[I"));
        method.instructions.add(new VarInsnNode(ASTORE, 0));
        method.instructions.add(new JumpInsnNode(GOTO, header));
        method.maxLocals = 1;
        method.maxStack = 2;
        assertFalse(ArrayTypes.analyze("Arrays", method).containsKey(element));
    }
}
