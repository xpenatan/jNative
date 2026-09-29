package com.github.xpenatan.jnative;

import java.util.Objects;

final class SubstitutionNames {
    private SubstitutionNames() {}

    static String binaryName(String value, String label) {
        Objects.requireNonNull(value, label);
        String name = value.strip().replace('/', '.');
        if(name.isEmpty()) throw new IllegalArgumentException("Invalid " + label + ": " + value);
        for(String part : name.split("\\.", -1)) {
            if(part.isEmpty() || !Character.isJavaIdentifierStart(part.charAt(0)))
                throw new IllegalArgumentException("Invalid " + label + ": " + value);
            for(int i = 1; i < part.length(); i++) {
                if(!Character.isJavaIdentifierPart(part.charAt(i)))
                    throw new IllegalArgumentException("Invalid " + label + ": " + value);
            }
        }
        return name;
    }

    static String memberName(String value) {
        Objects.requireNonNull(value, "name");
        String name = value.strip();
        if(name.equals("<init>") || name.equals("<clinit>"))
            throw new IllegalArgumentException("Constructor and initializer substitutions require a complete class replacement");
        if(name.isEmpty() || name.chars().anyMatch(c -> c == '.' || c == ';' || c == '[' || c == '/' || c == '<' || c == '>' || Character.isWhitespace(c)))
            throw new IllegalArgumentException("Invalid method name: " + value);
        return name;
    }

    static String methodDescriptor(String value) {
        Objects.requireNonNull(value, "descriptor");
        String descriptor = value.strip();
        try {
            if(descriptor.charAt(0) != '(') throw new IllegalArgumentException();
            int offset = 1;
            int slots = 0;
            while(descriptor.charAt(offset) != ')') {
                char kind = descriptor.charAt(offset);
                offset = typeEnd(descriptor, offset, false);
                slots += kind == 'J' || kind == 'D' ? 2 : 1;
            }
            if(slots > 255 || typeEnd(descriptor, offset + 1, true) != descriptor.length())
                throw new IllegalArgumentException();
        } catch(IndexOutOfBoundsException | IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid JVM method descriptor: " + value);
        }
        return descriptor;
    }

    private static int typeEnd(String descriptor, int offset, boolean allowVoid) {
        char kind = descriptor.charAt(offset++);
        if(kind == 'V' && allowVoid) return offset;
        if("BCDFIJSZ".indexOf(kind) >= 0) return offset;
        if(kind == '[') {
            int start = offset - 1;
            while(descriptor.charAt(offset) == '[') offset++;
            if(offset - start > 255) throw new IllegalArgumentException();
            return typeEnd(descriptor, offset, false);
        }
        if(kind == 'L') {
            int end = descriptor.indexOf(';', offset);
            if(end < 0) throw new IllegalArgumentException();
            String internal = descriptor.substring(offset, end);
            if(internal.contains(".") || !binaryName(internal, "descriptor class").replace('.', '/').equals(internal))
                throw new IllegalArgumentException();
            return end + 1;
        }
        throw new IllegalArgumentException();
    }

    static String providerId(String value) {
        Objects.requireNonNull(value, "providerId");
        String id = value.strip();
        if(!id.matches("[A-Za-z0-9_][A-Za-z0-9_.-]*"))
            throw new IllegalArgumentException("Invalid substitution provider id: " + value);
        return id;
    }
}
