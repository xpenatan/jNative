package com.github.xpenatan.jnative.compiler;

import org.objectweb.asm.Type;

import java.util.Set;

/**
 * Explicit allowlist. A platform method absent here is an error, never a stub.
 */
public final class RuntimeLibrary {
    private static final java.util.Map<String, String> IO_EXCEPTIONS =
            java.util.Map.ofEntries(
                    java.util.Map.entry(
                            "java/nio/BufferUnderflowException", "java/lang/RuntimeException"),
                    java.util.Map.entry(
                            "java/nio/BufferOverflowException", "java/lang/RuntimeException"),
                    java.util.Map.entry(
                            "java/nio/InvalidMarkException", "java/lang/IllegalStateException"),
                    java.util.Map.entry(
                            "java/nio/ReadOnlyBufferException",
                            "java/lang/UnsupportedOperationException"),
                    java.util.Map.entry("java/io/IOException", "java/lang/Exception"),
                    java.util.Map.entry("java/util/zip/DataFormatException", "java/lang/Exception"),
                    java.util.Map.entry("java/nio/file/FileSystemException", "java/io/IOException"),
                    java.util.Map.entry(
                            "java/nio/file/NoSuchFileException",
                            "java/nio/file/FileSystemException"),
                    java.util.Map.entry(
                            "java/nio/file/AccessDeniedException",
                            "java/nio/file/FileSystemException"),
                    java.util.Map.entry(
                            "java/nio/file/FileAlreadyExistsException",
                            "java/nio/file/FileSystemException"),
                    java.util.Map.entry(
                            "java/nio/file/DirectoryNotEmptyException",
                            "java/nio/file/FileSystemException"),
                    java.util.Map.entry(
                            "java/nio/file/InvalidPathException",
                            "java/lang/IllegalArgumentException"),
                    java.util.Map.entry(
                            "java/nio/charset/CharacterCodingException", "java/io/IOException"),
                    java.util.Map.entry(
                            "java/nio/charset/MalformedInputException",
                            "java/nio/charset/CharacterCodingException"));
    private static final Set<String> THROWABLES =
            Set.of(
                    "Throwable",
                    "Exception",
                    "RuntimeException",
                    "Error",
                    "LinkageError",
                    "ExceptionInInitializerError",
                    "NoClassDefFoundError",
                    "AbstractMethodError",
                    "OutOfMemoryError",
                    "NullPointerException",
                    "ArithmeticException",
                    "IndexOutOfBoundsException",
                    "ArrayIndexOutOfBoundsException",
                    "StringIndexOutOfBoundsException",
                    "ArrayStoreException",
                    "ClassCastException",
                    "NegativeArraySizeException",
                    "IllegalArgumentException",
                    "IllegalStateException",
                    "IllegalMonitorStateException",
                    "IllegalThreadStateException",
                    "InterruptedException",
                    "UnsupportedOperationException",
                    "ReflectiveOperationException",
                    "ClassNotFoundException",
                    "NoSuchFieldException",
                    "NoSuchMethodException",
                    "IllegalAccessException",
                    "InstantiationException");

    private RuntimeLibrary() {
    }

    public static boolean platform(String owner) {
        int lambda = owner.indexOf("$jNativeLambda$");
        if(lambda >= 0 && ClassLibrary.contains(owner.substring(0, lambda))) return false;
        return owner.startsWith("[")
                || !ClassLibrary.contains(owner)
                && (owner.startsWith("java/")
                || owner.startsWith("javax/")
                || owner.startsWith("jdk/"));
    }

    public static boolean method(String owner, String name, String descriptor) {
        if(BufferLibrary.method(owner, name, descriptor)) return true;
        if(owner.startsWith("[")) return (name + descriptor).equals("clone()Ljava/lang/Object;");
        if(owner.equals("java/io/PrintStream") && (name + descriptor).equals("flush()V"))
            return true;
        if(owner.equals("java/util/Arrays") && name.equals("copyOf")) {
            for(String type :
                    java.util.List.of("B", "Z", "C", "S", "I", "J", "F", "D", "Ljava/lang/Object;"))
                if(descriptor.equals("([" + type + "I)[" + type)) return true;
            return false;
        }
        if(owner.equals("java/lang/reflect/InvocationTargetException") && name.equals("<init>"))
            return Set.of(
                            "()V",
                            "(Ljava/lang/Throwable;)V",
                            "(Ljava/lang/Throwable;Ljava/lang/String;)V")
                    .contains(descriptor);
        if(ReflectionLibrary.method(owner, name, descriptor)) return true;
        if(owner.equals("java/nio/file/Paths"))
            return (name + descriptor)
                    .equals("get(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;");
        if(owner.equals("java/nio/file/Path"))
            return Set.of(
                            "of(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;",
                            "resolve(Ljava/lang/String;)Ljava/nio/file/Path;",
                            "resolve(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
                            "toAbsolutePath()Ljava/nio/file/Path;",
                            "normalize()Ljava/nio/file/Path;",
                            "compareTo(Ljava/nio/file/Path;)I",
                            "getFileName()Ljava/nio/file/Path;",
                            "getParent()Ljava/nio/file/Path;",
                            "toString()Ljava/lang/String;")
                    .contains(name + descriptor);
        if(owner.equals("java/nio/file/Files"))
            return Set.of(
                            "readString(Ljava/nio/file/Path;)Ljava/lang/String;",
                            "readAllBytes(Ljava/nio/file/Path;)[B",
                            "writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;",
                            "write(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;",
                            "exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z",
                            "delete(Ljava/nio/file/Path;)V",
                            "deleteIfExists(Ljava/nio/file/Path;)Z")
                    .contains(name + descriptor);
        if(owner.equals("java/io/PrintStream")
                && (name.equals("println") || name.equals("print"))) {
            return Set.of(
                            "()V",
                            "(I)V",
                            "(J)V",
                            "(F)V",
                            "(D)V",
                            "(Z)V",
                            "(C)V",
                            "(Ljava/lang/String;)V",
                            "(Ljava/lang/Object;)V")
                    .contains(descriptor);
        }
        if(owner.equals("java/lang/Object"))
            return Set.of(
                            "<init>()V",
                            "equals(Ljava/lang/Object;)Z",
                            "hashCode()I",
                            "toString()Ljava/lang/String;",
                            "wait()V",
                            "wait(J)V",
                            "wait(JI)V",
                            "notify()V",
                            "notifyAll()V",
                            "getClass()Ljava/lang/Class;")
                    .contains(name + descriptor);
        if(owner.equals("java/lang/Number"))
            return Set.of(
                            "<init>()V",
                            "intValue()I",
                            "longValue()J",
                            "floatValue()F",
                            "doubleValue()D",
                            "byteValue()B",
                            "shortValue()S")
                    .contains(name + descriptor);
        if(owner.equals("java/lang/Runnable")) return (name + descriptor).equals("run()V");
        if(owner.equals("java/lang/Thread"))
            return Set.of(
                            "<init>()V",
                            "<init>(Ljava/lang/Runnable;)V",
                            "<init>(Ljava/lang/String;)V",
                            "<init>(Ljava/lang/Runnable;Ljava/lang/String;)V",
                            "start()V",
                            "run()V",
                            "join()V",
                            "join(J)V",
                            "join(JI)V",
                            "sleep(J)V",
                            "sleep(JI)V",
                            "yield()V",
                            "currentThread()Ljava/lang/Thread;",
                            "interrupt()V",
                            "interrupted()Z",
                            "isInterrupted()Z",
                            "isAlive()Z",
                            "getId()J",
                            "threadId()J",
                            "getName()Ljava/lang/String;",
                            "setName(Ljava/lang/String;)V",
                            "holdsLock(Ljava/lang/Object;)Z",
                            "isDaemon()Z",
                            "setDaemon(Z)V")
                    .contains(name + descriptor);
        if(owner.equals("java/lang/ThreadLocal"))
            return Set.of(
                            "<init>()V",
                            "get()Ljava/lang/Object;",
                            "set(Ljava/lang/Object;)V",
                            "remove()V",
                            "initialValue()Ljava/lang/Object;")
                    .contains(name + descriptor);
        if(owner.equals("java/util/concurrent/atomic/AtomicInteger")
                || owner.equals("java/util/concurrent/atomic/AtomicLong")) {
            String t = owner.endsWith("AtomicInteger") ? "I" : "J";
            return Set.of(
                            "<init>()V",
                            "<init>(" + t + ")V",
                            "get()" + t,
                            "set(" + t + ")V",
                            "lazySet(" + t + ")V",
                            "getAndSet(" + t + ")" + t,
                            "compareAndSet(" + t + t + ")Z",
                            "getAndAdd(" + t + ")" + t,
                            "addAndGet(" + t + ")" + t,
                            "getAndIncrement()" + t,
                            "incrementAndGet()" + t,
                            "getAndDecrement()" + t,
                            "decrementAndGet()" + t,
                            "intValue()I",
                            "longValue()J",
                            "floatValue()F",
                            "doubleValue()D",
                            "toString()Ljava/lang/String;")
                    .contains(name + descriptor);
        }
        if(throwable(owner)) {
            if(owner.equals("java/lang/IndexOutOfBoundsException")
                    && (name + descriptor).equals("<init>(I)V")) return true;
            return Set.of(
                            "<init>()V",
                            "<init>(Ljava/lang/String;)V",
                            "<init>(Ljava/lang/Throwable;)V",
                            "<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                            "getMessage()Ljava/lang/String;",
                            "getCause()Ljava/lang/Throwable;",
                            "toString()Ljava/lang/String;",
                            "printStackTrace()V",
                            "printStackTrace(Ljava/io/PrintStream;)V",
                            "addSuppressed(Ljava/lang/Throwable;)V",
                            "getSuppressed()[Ljava/lang/Throwable;")
                    .contains(name + descriptor);
        }
        if(owner.equals("java/lang/String")) {
            return Set.of(
                            "<init>()V",
                            "<init>(Ljava/lang/String;)V",
                            "<init>([C)V",
                            "<init>([CII)V",
                            "length()I",
                            "isEmpty()Z",
                            "charAt(I)C",
                            "equals(Ljava/lang/Object;)Z",
                            "hashCode()I",
                            "toString()Ljava/lang/String;",
                            "trim()Ljava/lang/String;",
                            "concat(Ljava/lang/String;)Ljava/lang/String;",
                            "substring(I)Ljava/lang/String;",
                            "substring(II)Ljava/lang/String;",
                            "compareTo(Ljava/lang/String;)I",
                            "valueOf(I)Ljava/lang/String;",
                            "valueOf(J)Ljava/lang/String;",
                            "valueOf(Z)Ljava/lang/String;",
                            "valueOf(C)Ljava/lang/String;",
                            "valueOf(F)Ljava/lang/String;",
                            "valueOf(D)Ljava/lang/String;",
                            "valueOf(Ljava/lang/Object;)Ljava/lang/String;")
                    .contains(name + descriptor);
        }
        if(owner.equals("java/lang/System"))
            return Set.of(
                            "gc()V",
                            "currentTimeMillis()J",
                            "nanoTime()J",
                            "identityHashCode(Ljava/lang/Object;)I",
                            "arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V",
                            "getProperty(Ljava/lang/String;)Ljava/lang/String;",
                            "setProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
                            "clearProperty(Ljava/lang/String;)Ljava/lang/String;",
                            "getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
                    .contains(name + descriptor);
        return false;
    }

    public static boolean field(String owner, String name, String descriptor) {
        if(BufferLibrary.field(owner, name, descriptor)) return true;
        if(primitiveClassField(owner, name, descriptor)) return true;
        return owner.equals("java/lang/System")
                && (name.equals("out") || name.equals("err"))
                && descriptor.equals("Ljava/io/PrintStream;");
    }

    public static boolean scalar(Type type) {
        return type.getSort() >= Type.VOID && type.getSort() <= Type.DOUBLE;
    }

    public static boolean throwable(String owner) {
        return (owner.startsWith("java/lang/")
                && THROWABLES.contains(owner.substring("java/lang/".length())))
                || owner.equals("java/util/NoSuchElementException")
                || owner.equals("java/util/ConcurrentModificationException")
                || owner.equals("java/lang/reflect/InvocationTargetException")
                || IO_EXCEPTIONS.containsKey(owner);
    }

    public static String parent(String owner) {
        if(Set.of(
                        "java/nio/ByteBuffer",
                        "java/nio/FloatBuffer",
                        "java/nio/ShortBuffer",
                        "java/nio/IntBuffer",
                        "java/nio/LongBuffer",
                        "java/nio/DoubleBuffer")
                .contains(owner)) return "java/nio/Buffer";
        if(owner.equals("java/lang/reflect/InvocationTargetException"))
            return "java/lang/ReflectiveOperationException";
        if(IO_EXCEPTIONS.containsKey(owner)) return IO_EXCEPTIONS.get(owner);
        if(owner.startsWith("java/util/concurrent/atomic/Atomic")) return "java/lang/Number";
        if(!throwable(owner)) return owner.equals("java/lang/Object") ? null : "java/lang/Object";
        return switch(owner) {
            case "java/lang/Throwable" -> "java/lang/Object";
            case "java/lang/Exception", "java/lang/Error" -> "java/lang/Throwable";
            case "java/lang/RuntimeException" -> "java/lang/Exception";
            case "java/lang/InterruptedException" -> "java/lang/Exception";
            case "java/lang/ReflectiveOperationException" -> "java/lang/Exception";
            case "java/lang/ClassNotFoundException",
                 "java/lang/NoSuchMethodException",
                 "java/lang/NoSuchFieldException",
                 "java/lang/IllegalAccessException",
                 "java/lang/InstantiationException" -> "java/lang/ReflectiveOperationException";
            case "java/lang/IllegalThreadStateException" -> "java/lang/IllegalArgumentException";
            case "java/lang/LinkageError", "java/lang/OutOfMemoryError" -> "java/lang/Error";
            case "java/lang/ExceptionInInitializerError",
                 "java/lang/NoClassDefFoundError",
                 "java/lang/AbstractMethodError" -> "java/lang/LinkageError";
            case "java/lang/ArrayIndexOutOfBoundsException",
                 "java/lang/StringIndexOutOfBoundsException" -> "java/lang/IndexOutOfBoundsException";
            default -> "java/lang/RuntimeException";
        };
    }

    public static String wrapper(String descriptor) {
        return "java/lang/"
                + switch(descriptor) {
            case "Z" -> "Boolean";
            case "B" -> "Byte";
            case "C" -> "Character";
            case "S" -> "Short";
            case "I" -> "Integer";
            case "J" -> "Long";
            case "F" -> "Float";
            case "D" -> "Double";
            case "V" -> "Void";
            default -> throw new IllegalArgumentException(
                    "Not a primitive descriptor: " + descriptor);
        };
    }

    public static String primitive(String owner) {
        for(String descriptor : java.util.List.of("Z", "B", "C", "S", "I", "J", "F", "D", "V"))
            if(wrapper(descriptor).equals(owner)) return descriptor;
        return null;
    }

    public static boolean primitiveClassField(String owner, String name, String descriptor) {
        return name.equals("TYPE")
                && descriptor.equals("Ljava/lang/Class;")
                && primitive(owner) != null;
    }

    public static boolean intrinsic(String owner, String name, String descriptor) {
        // Library declarations are linked through their NativeImport metadata.
        return false;
    }
}
