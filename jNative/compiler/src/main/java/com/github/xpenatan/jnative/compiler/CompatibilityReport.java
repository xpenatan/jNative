package com.github.xpenatan.jnative.compiler;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/**
 * Reproducible capability and dependency inventory for the bundled library profile.
 */
public final class CompatibilityReport {
    public static final String PROFILE = "headless-v1";
    public static final String LIBRARY_VERSION = "0.1.0-classlib1";

    private CompatibilityReport() {
    }

    public static String libraryFingerprint() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for(String name : new TreeSet<>(ClassLibrary.classes())) {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update(ClassLibrary.read(name));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    public static String libraryInventory() {
        var text =
                new StringBuilder(
                        "profile\t"
                                + PROFILE
                                + "\nclass-library\t"
                                + LIBRARY_VERSION
                                + "\nclass-library-sha256\t"
                                + libraryFingerprint()
                                + "\n\nowner\tmethod\tdescriptor\n");
        for(String name : new TreeSet<>(ClassLibrary.classes())) {
            var node = new ClassNode();
            new ClassReader(ClassLibrary.read(name))
                    .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            for(MethodNode method : node.methods)
                if((method.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0)
                    text.append(name.replace('/', '.'))
                            .append('\t')
                            .append(method.name)
                            .append('\t')
                            .append(method.desc)
                            .append('\n');
        }
        return text.toString();
    }

    public static String dependencies(Program program) {
        var lines = new TreeSet<String>();
        for(var method : program.methods().values()) {
            String kind =
                    ClassLibrary.contains(method.id().owner())
                            ? "class-library"
                            : method.id().owner().contains("$jNativeLambda$")
                            ? "lambda"
                            : "application";
            lines.add(kind + "\t" + method.id());
            if(method.nativeSymbol() != null) continue;
            for(AbstractInsnNode instruction : method.bytecode().instructions)
                if(instruction instanceof MethodInsnNode call
                        && program.platform(call.owner))
                    lines.add("runtime\t" + new Program.MethodId(call.owner, call.name, call.desc));
        }
        return "profile\t"
                + PROFILE
                + "\nclass-library\t"
                + LIBRARY_VERSION
                + "\n\nimplementation\tjava-method\n"
                + String.join("\n", lines)
                + "\n";
    }
}
