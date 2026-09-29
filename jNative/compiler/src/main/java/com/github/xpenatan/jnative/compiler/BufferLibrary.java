package com.github.xpenatan.jnative.compiler;

import java.util.Set;

/**
 * Direct byte buffers supported by the common native runtime.
 */
public final class BufferLibrary {
    private BufferLibrary() {
    }

    public static boolean method(String owner, String name, String descriptor) {
        if(owner.equals("java/nio/ByteOrder"))
            return Set.of("nativeOrder()Ljava/nio/ByteOrder;", "toString()Ljava/lang/String;")
                    .contains(name + descriptor);
        if(!Set.of(
                        "java/nio/Buffer",
                        "java/nio/ByteBuffer",
                        "java/nio/FloatBuffer",
                        "java/nio/ShortBuffer",
                        "java/nio/IntBuffer",
                        "java/nio/LongBuffer",
                        "java/nio/DoubleBuffer")
                .contains(owner)) return false;
        if(Set.of(
                        "capacity()I",
                        "position()I",
                        "limit()I",
                        "remaining()I",
                        "hasRemaining()Z",
                        "isReadOnly()Z",
                        "isDirect()Z",
                        "hasArray()Z")
                .contains(name + descriptor)) return true;
        String result = "L" + owner + ";";
        if(Set.of(
                        "position(I)" + result,
                        "limit(I)" + result,
                        "clear()" + result,
                        "flip()" + result,
                        "rewind()" + result,
                        "mark()" + result,
                        "reset()" + result)
                .contains(name + descriptor)) return true;
        if(!owner.equals("java/nio/ByteBuffer")) {
            String element =
                    switch(owner) {
                        case "java/nio/FloatBuffer" -> "F";
                        case "java/nio/ShortBuffer" -> "S";
                        case "java/nio/IntBuffer" -> "I";
                        case "java/nio/LongBuffer" -> "J";
                        case "java/nio/DoubleBuffer" -> "D";
                        default -> "";
                    };
            return !element.isEmpty()
                    && Set.of(
                            "get()" + element,
                            "get(I)" + element,
                            "put(" + element + ")" + result,
                            "put(I" + element + ")" + result,
                            "put(" + result + ")" + result,
                            "get([" + element + ")" + result,
                            "get([" + element + "II)" + result,
                            "put([" + element + ")" + result,
                            "put([" + element + "II)" + result,
                            "duplicate()" + result,
                            "slice()" + result,
                            "asReadOnlyBuffer()" + result,
                            "order()Ljava/nio/ByteOrder;")
                    .contains(name + descriptor);
        }
        if(Set.of(
                        "allocateDirect(I)Ljava/nio/ByteBuffer;",
                        "allocate(I)Ljava/nio/ByteBuffer;",
                        "wrap([B)Ljava/nio/ByteBuffer;",
                        "wrap([BII)Ljava/nio/ByteBuffer;",
                        "array()[B",
                        "arrayOffset()I",
                        "put(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
                        "asFloatBuffer()Ljava/nio/FloatBuffer;",
                        "asShortBuffer()Ljava/nio/ShortBuffer;",
                        "asIntBuffer()Ljava/nio/IntBuffer;",
                        "asLongBuffer()Ljava/nio/LongBuffer;",
                        "asDoubleBuffer()Ljava/nio/DoubleBuffer;",
                        "duplicate()Ljava/nio/ByteBuffer;",
                        "slice()Ljava/nio/ByteBuffer;",
                        "asReadOnlyBuffer()Ljava/nio/ByteBuffer;",
                        "order()Ljava/nio/ByteOrder;",
                        "order(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;",
                        "get([B)Ljava/nio/ByteBuffer;",
                        "get([BII)Ljava/nio/ByteBuffer;",
                        "put([B)Ljava/nio/ByteBuffer;",
                        "put([BII)Ljava/nio/ByteBuffer;")
                .contains(name + descriptor)) return true;
        String type =
                switch(name) {
                    case "get", "put" -> "B";
                    case "getChar", "putChar" -> "C";
                    case "getShort", "putShort" -> "S";
                    case "getInt", "putInt" -> "I";
                    case "getLong", "putLong" -> "J";
                    case "getFloat", "putFloat" -> "F";
                    case "getDouble", "putDouble" -> "D";
                    default -> "";
                };
        if(type.isEmpty()) return false;
        return name.startsWith("get")
                ? Set.of("()" + type, "(I)" + type).contains(descriptor)
                : Set.of("(" + type + ")" + result, "(I" + type + ")" + result)
                .contains(descriptor);
    }

    public static boolean field(String owner, String name, String descriptor) {
        return owner.equals("java/nio/ByteOrder")
                && descriptor.equals("Ljava/nio/ByteOrder;")
                && Set.of("BIG_ENDIAN", "LITTLE_ENDIAN").contains(name);
    }
}
