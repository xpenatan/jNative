package com.github.xpenatan.jnative.compiler;

import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Routes extended String operations through the translated Java class library.
 */
final class LibraryLowering {
    private static final String STRINGS = "java/lang/NativeStrings";
    private static final Set<String> METHODS = methods();

    private static Set<String> methods() {
        var node = new ClassNode();
        new ClassReader(ClassLibrary.read(STRINGS)).accept(node, ClassReader.SKIP_CODE);
        var signatures = new HashSet<String>();
        for(var method : node.methods)
            if((method.access & Opcodes.ACC_PUBLIC) != 0)
                signatures.add(method.name + method.desc);
        return signatures;
    }

    static void lower(ClassNode node) {
        for(var method : node.methods) {
            for(var instruction : method.instructions) {
                if(!(instruction instanceof MethodInsnNode call)
                        || !call.owner.equals("java/lang/String")) continue;
                String name = call.name.equals("<init>") ? "initialize" : call.name;
                String descriptor =
                        call.getOpcode() == Opcodes.INVOKESTATIC
                                ? call.desc
                                : "(Ljava/lang/String;" + call.desc.substring(1);
                if(METHODS.contains(name + descriptor)) {
                    call.owner = STRINGS;
                    call.name = name;
                    call.desc = descriptor;
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.itf = false;
                }
            }
        }
    }
}
