package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Bundled Java library bytecode is translated as application code, never loaded into the host JVM.
 */
public final class ClassLibrary {
    private static final Set<String> CLASSES = readIndex();

    private ClassLibrary() {
    }

    private static Set<String> readIndex() {
        try(var input = ClassLibrary.class.getResourceAsStream("/classlib/classes.list")) {
            if(input == null)
                throw new CompilerException("JN4001 Bundled class-library index is missing");
            return Set.copyOf(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)
                            .lines()
                            .filter(s -> !s.isBlank())
                            .toList());
        } catch(IOException error) {
            throw new CompilerException("JN4001 Cannot read class-library index", error);
        }
    }

    public static boolean contains(String name) {
        return CLASSES.contains(name);
    }

    public static Set<String> classes() {
        return CLASSES;
    }

    static byte[] read(String name) {
        try(var input = ClassLibrary.class.getResourceAsStream("/classlib/" + name + ".class")) {
            if(input == null)
                throw new CompilerException("JN4001 Bundled class is missing: " + name);
            return input.readAllBytes();
        } catch(IOException error) {
            throw new CompilerException("JN4001 Cannot read bundled class " + name, error);
        }
    }
}
