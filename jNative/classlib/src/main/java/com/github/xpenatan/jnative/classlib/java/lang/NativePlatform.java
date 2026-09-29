package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteMethod;

/** Exact managed bindings for the supported platform API profile. */
@NativeInclude("jn_platform_bindings.hpp")
public final class NativePlatform {
    private NativePlatform() {}
    @NativeImport(value = "jnative::platform_IOException_initialize_c2f18a8416", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.<init>()V",
                    "java/lang/AbstractMethodError.<init>()V",
                    "java/lang/ArithmeticException.<init>()V",
                    "java/lang/ArrayIndexOutOfBoundsException.<init>()V",
                    "java/lang/ArrayStoreException.<init>()V",
                    "java/lang/ClassCastException.<init>()V",
                    "java/lang/ClassNotFoundException.<init>()V",
                    "java/lang/Error.<init>()V",
                    "java/lang/Exception.<init>()V",
                    "java/lang/ExceptionInInitializerError.<init>()V",
                    "java/lang/IllegalAccessException.<init>()V",
                    "java/lang/IllegalArgumentException.<init>()V",
                    "java/lang/IllegalMonitorStateException.<init>()V",
                    "java/lang/IllegalStateException.<init>()V",
                    "java/lang/IllegalThreadStateException.<init>()V",
                    "java/lang/IndexOutOfBoundsException.<init>()V",
                    "java/lang/InstantiationException.<init>()V",
                    "java/lang/InterruptedException.<init>()V",
                    "java/lang/LinkageError.<init>()V",
                    "java/lang/NegativeArraySizeException.<init>()V",
                    "java/lang/NoClassDefFoundError.<init>()V",
                    "java/lang/NoSuchFieldException.<init>()V",
                    "java/lang/NoSuchMethodException.<init>()V",
                    "java/lang/NullPointerException.<init>()V",
                    "java/lang/Number.<init>()V",
                    "java/lang/Object.<init>()V",
                    "java/lang/OutOfMemoryError.<init>()V",
                    "java/lang/ReflectiveOperationException.<init>()V",
                    "java/lang/RuntimeException.<init>()V",
                    "java/lang/StringIndexOutOfBoundsException.<init>()V",
                    "java/lang/ThreadLocal.<init>()V",
                    "java/lang/Throwable.<init>()V",
                    "java/lang/UnsupportedOperationException.<init>()V",
                    "java/nio/charset/CharacterCodingException.<init>()V",
                    "java/nio/file/AccessDeniedException.<init>()V",
                    "java/nio/file/DirectoryNotEmptyException.<init>()V",
                    "java/nio/file/FileAlreadyExistsException.<init>()V",
                    "java/nio/file/FileSystemException.<init>()V",
                    "java/nio/file/InvalidPathException.<init>()V",
                    "java/nio/file/NoSuchFileException.<init>()V",
                    "java/util/ConcurrentModificationException.<init>()V",
                    "java/util/NoSuchElementException.<init>()V",
                    "java/util/zip/DataFormatException.<init>()V"})
    public static native void platform_IOException_initialize_c2f18a8416(Object self);

    @NativeImport(value = "jnative::platform_IOException_initialize_199d7d150b", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.<init>(Ljava/lang/String;)V",
                    "java/lang/AbstractMethodError.<init>(Ljava/lang/String;)V",
                    "java/lang/ArithmeticException.<init>(Ljava/lang/String;)V",
                    "java/lang/ArrayIndexOutOfBoundsException.<init>(Ljava/lang/String;)V",
                    "java/lang/ArrayStoreException.<init>(Ljava/lang/String;)V",
                    "java/lang/ClassCastException.<init>(Ljava/lang/String;)V",
                    "java/lang/ClassNotFoundException.<init>(Ljava/lang/String;)V",
                    "java/lang/Error.<init>(Ljava/lang/String;)V",
                    "java/lang/Exception.<init>(Ljava/lang/String;)V",
                    "java/lang/ExceptionInInitializerError.<init>(Ljava/lang/String;)V",
                    "java/lang/IllegalAccessException.<init>(Ljava/lang/String;)V",
                    "java/lang/IllegalArgumentException.<init>(Ljava/lang/String;)V",
                    "java/lang/IllegalMonitorStateException.<init>(Ljava/lang/String;)V",
                    "java/lang/IllegalStateException.<init>(Ljava/lang/String;)V",
                    "java/lang/IllegalThreadStateException.<init>(Ljava/lang/String;)V",
                    "java/lang/IndexOutOfBoundsException.<init>(Ljava/lang/String;)V",
                    "java/lang/InstantiationException.<init>(Ljava/lang/String;)V",
                    "java/lang/InterruptedException.<init>(Ljava/lang/String;)V",
                    "java/lang/LinkageError.<init>(Ljava/lang/String;)V",
                    "java/lang/NegativeArraySizeException.<init>(Ljava/lang/String;)V",
                    "java/lang/NoClassDefFoundError.<init>(Ljava/lang/String;)V",
                    "java/lang/NoSuchFieldException.<init>(Ljava/lang/String;)V",
                    "java/lang/NoSuchMethodException.<init>(Ljava/lang/String;)V",
                    "java/lang/NullPointerException.<init>(Ljava/lang/String;)V",
                    "java/lang/OutOfMemoryError.<init>(Ljava/lang/String;)V",
                    "java/lang/ReflectiveOperationException.<init>(Ljava/lang/String;)V",
                    "java/lang/RuntimeException.<init>(Ljava/lang/String;)V",
                    "java/lang/StringIndexOutOfBoundsException.<init>(Ljava/lang/String;)V",
                    "java/lang/Throwable.<init>(Ljava/lang/String;)V",
                    "java/lang/UnsupportedOperationException.<init>(Ljava/lang/String;)V",
                    "java/nio/charset/CharacterCodingException.<init>(Ljava/lang/String;)V",
                    "java/nio/file/AccessDeniedException.<init>(Ljava/lang/String;)V",
                    "java/nio/file/DirectoryNotEmptyException.<init>(Ljava/lang/String;)V",
                    "java/nio/file/FileAlreadyExistsException.<init>(Ljava/lang/String;)V",
                    "java/nio/file/FileSystemException.<init>(Ljava/lang/String;)V",
                    "java/nio/file/InvalidPathException.<init>(Ljava/lang/String;)V",
                    "java/nio/file/NoSuchFileException.<init>(Ljava/lang/String;)V",
                    "java/util/ConcurrentModificationException.<init>(Ljava/lang/String;)V",
                    "java/util/NoSuchElementException.<init>(Ljava/lang/String;)V",
                    "java/util/zip/DataFormatException.<init>(Ljava/lang/String;)V"})
    public static native void platform_IOException_initialize_199d7d150b(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_IOException_initialize_29c514e70d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/AbstractMethodError.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/ArithmeticException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/ArrayIndexOutOfBoundsException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/ArrayStoreException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/ClassCastException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/ClassNotFoundException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/Error.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/Exception.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/ExceptionInInitializerError.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/IllegalAccessException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/IllegalArgumentException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/IllegalMonitorStateException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/IllegalStateException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/IllegalThreadStateException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/IndexOutOfBoundsException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/InstantiationException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/InterruptedException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/LinkageError.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/NegativeArraySizeException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/NoClassDefFoundError.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/NoSuchFieldException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/NoSuchMethodException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/NullPointerException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/OutOfMemoryError.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/ReflectiveOperationException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/RuntimeException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/StringIndexOutOfBoundsException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/Throwable.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/lang/UnsupportedOperationException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/charset/CharacterCodingException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/file/AccessDeniedException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/file/DirectoryNotEmptyException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/file/FileAlreadyExistsException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/file/FileSystemException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/file/InvalidPathException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/file/NoSuchFileException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/util/ConcurrentModificationException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/util/NoSuchElementException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/util/zip/DataFormatException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V"})
    public static native void platform_IOException_initialize_29c514e70d(Object self, Object arg0, Object arg1);

    @NativeImport(value = "jnative::platform_IOException_initialize_79140ec83e", managed = true, callbacksSynchronous = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/AbstractMethodError.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/ArithmeticException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/ArrayIndexOutOfBoundsException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/ArrayStoreException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/ClassCastException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/ClassNotFoundException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/Error.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/Exception.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/ExceptionInInitializerError.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalAccessException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalArgumentException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalMonitorStateException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalStateException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalThreadStateException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/IndexOutOfBoundsException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/InstantiationException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/InterruptedException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/LinkageError.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/NegativeArraySizeException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/NoClassDefFoundError.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/NoSuchFieldException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/NoSuchMethodException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/NullPointerException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/OutOfMemoryError.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/ReflectiveOperationException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/RuntimeException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/StringIndexOutOfBoundsException.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/Throwable.<init>(Ljava/lang/Throwable;)V",
                    "java/lang/UnsupportedOperationException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/charset/CharacterCodingException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/file/AccessDeniedException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/file/DirectoryNotEmptyException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/file/FileAlreadyExistsException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/file/FileSystemException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/file/InvalidPathException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/file/NoSuchFileException.<init>(Ljava/lang/Throwable;)V",
                    "java/util/ConcurrentModificationException.<init>(Ljava/lang/Throwable;)V",
                    "java/util/NoSuchElementException.<init>(Ljava/lang/Throwable;)V",
                    "java/util/zip/DataFormatException.<init>(Ljava/lang/Throwable;)V"},
            callbacks = {"java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {-1})
    public static native void platform_IOException_initialize_79140ec83e(Object self, Object arg0);

    @SubstituteMethod(owner = "java.io.IOException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.AbstractMethodError", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.ArithmeticException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.ArrayIndexOutOfBoundsException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.ArrayStoreException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.ClassCastException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.ClassNotFoundException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.Error", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.Exception", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.ExceptionInInitializerError", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.IllegalAccessException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.IllegalArgumentException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.IllegalMonitorStateException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.IllegalStateException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.IllegalThreadStateException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.IndexOutOfBoundsException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.InstantiationException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.InterruptedException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.LinkageError", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.NegativeArraySizeException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.NoClassDefFoundError", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.NoSuchFieldException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.NoSuchMethodException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.NullPointerException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.OutOfMemoryError", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.ReflectiveOperationException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.RuntimeException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.StringIndexOutOfBoundsException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.Throwable", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.lang.UnsupportedOperationException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.charset.CharacterCodingException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.file.AccessDeniedException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.file.DirectoryNotEmptyException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.file.FileAlreadyExistsException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.file.FileSystemException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.file.InvalidPathException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.file.NoSuchFileException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.util.ConcurrentModificationException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.util.NoSuchElementException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.util.zip.DataFormatException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @NativeImport(value = "jnative::platform_IOException_addSuppressed_051e0eb658", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/AbstractMethodError.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/ArithmeticException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/ArrayIndexOutOfBoundsException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/ArrayStoreException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/ClassCastException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/ClassNotFoundException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/Error.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/Exception.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/ExceptionInInitializerError.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalAccessException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalArgumentException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalMonitorStateException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalStateException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/IllegalThreadStateException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/IndexOutOfBoundsException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/InstantiationException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/InterruptedException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/LinkageError.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/NegativeArraySizeException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/NoClassDefFoundError.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/NoSuchFieldException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/NoSuchMethodException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/NullPointerException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/OutOfMemoryError.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/ReflectiveOperationException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/RuntimeException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/StringIndexOutOfBoundsException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/Throwable.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/lang/UnsupportedOperationException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/charset/CharacterCodingException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/file/AccessDeniedException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/file/DirectoryNotEmptyException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/file/FileAlreadyExistsException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/file/FileSystemException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/file/InvalidPathException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/file/NoSuchFileException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/util/ConcurrentModificationException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/util/NoSuchElementException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/util/zip/DataFormatException.addSuppressed(Ljava/lang/Throwable;)V"})
    public static native void platform_IOException_addSuppressed_051e0eb658(Object self, Object arg0);

    @SubstituteMethod(owner = "java.io.IOException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.AbstractMethodError", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ArithmeticException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ArrayIndexOutOfBoundsException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ArrayStoreException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ClassCastException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ClassNotFoundException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.Error", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.Exception", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ExceptionInInitializerError", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalAccessException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalArgumentException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalMonitorStateException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalStateException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalThreadStateException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IndexOutOfBoundsException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.InstantiationException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.InterruptedException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.LinkageError", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NegativeArraySizeException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NoClassDefFoundError", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NoSuchFieldException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NoSuchMethodException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NullPointerException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.OutOfMemoryError", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ReflectiveOperationException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.RuntimeException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.StringIndexOutOfBoundsException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.Throwable", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.UnsupportedOperationException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.charset.CharacterCodingException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.AccessDeniedException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.DirectoryNotEmptyException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.FileAlreadyExistsException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.FileSystemException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.InvalidPathException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.NoSuchFileException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.util.ConcurrentModificationException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.util.NoSuchElementException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.util.zip.DataFormatException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @NativeImport(value = "jnative::platform_IOException_getCause_9ca74ef4d3", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.getCause()Ljava/lang/Throwable;",
                    "java/lang/AbstractMethodError.getCause()Ljava/lang/Throwable;",
                    "java/lang/ArithmeticException.getCause()Ljava/lang/Throwable;",
                    "java/lang/ArrayIndexOutOfBoundsException.getCause()Ljava/lang/Throwable;",
                    "java/lang/ArrayStoreException.getCause()Ljava/lang/Throwable;",
                    "java/lang/ClassCastException.getCause()Ljava/lang/Throwable;",
                    "java/lang/ClassNotFoundException.getCause()Ljava/lang/Throwable;",
                    "java/lang/Error.getCause()Ljava/lang/Throwable;",
                    "java/lang/Exception.getCause()Ljava/lang/Throwable;",
                    "java/lang/ExceptionInInitializerError.getCause()Ljava/lang/Throwable;",
                    "java/lang/IllegalAccessException.getCause()Ljava/lang/Throwable;",
                    "java/lang/IllegalArgumentException.getCause()Ljava/lang/Throwable;",
                    "java/lang/IllegalMonitorStateException.getCause()Ljava/lang/Throwable;",
                    "java/lang/IllegalStateException.getCause()Ljava/lang/Throwable;",
                    "java/lang/IllegalThreadStateException.getCause()Ljava/lang/Throwable;",
                    "java/lang/IndexOutOfBoundsException.getCause()Ljava/lang/Throwable;",
                    "java/lang/InstantiationException.getCause()Ljava/lang/Throwable;",
                    "java/lang/InterruptedException.getCause()Ljava/lang/Throwable;",
                    "java/lang/LinkageError.getCause()Ljava/lang/Throwable;",
                    "java/lang/NegativeArraySizeException.getCause()Ljava/lang/Throwable;",
                    "java/lang/NoClassDefFoundError.getCause()Ljava/lang/Throwable;",
                    "java/lang/NoSuchFieldException.getCause()Ljava/lang/Throwable;",
                    "java/lang/NoSuchMethodException.getCause()Ljava/lang/Throwable;",
                    "java/lang/NullPointerException.getCause()Ljava/lang/Throwable;",
                    "java/lang/OutOfMemoryError.getCause()Ljava/lang/Throwable;",
                    "java/lang/ReflectiveOperationException.getCause()Ljava/lang/Throwable;",
                    "java/lang/RuntimeException.getCause()Ljava/lang/Throwable;",
                    "java/lang/StringIndexOutOfBoundsException.getCause()Ljava/lang/Throwable;",
                    "java/lang/Throwable.getCause()Ljava/lang/Throwable;",
                    "java/lang/UnsupportedOperationException.getCause()Ljava/lang/Throwable;",
                    "java/nio/charset/CharacterCodingException.getCause()Ljava/lang/Throwable;",
                    "java/nio/file/AccessDeniedException.getCause()Ljava/lang/Throwable;",
                    "java/nio/file/DirectoryNotEmptyException.getCause()Ljava/lang/Throwable;",
                    "java/nio/file/FileAlreadyExistsException.getCause()Ljava/lang/Throwable;",
                    "java/nio/file/FileSystemException.getCause()Ljava/lang/Throwable;",
                    "java/nio/file/InvalidPathException.getCause()Ljava/lang/Throwable;",
                    "java/nio/file/NoSuchFileException.getCause()Ljava/lang/Throwable;",
                    "java/util/ConcurrentModificationException.getCause()Ljava/lang/Throwable;",
                    "java/util/NoSuchElementException.getCause()Ljava/lang/Throwable;",
                    "java/util/zip/DataFormatException.getCause()Ljava/lang/Throwable;"})
    public static native Object platform_IOException_getCause_9ca74ef4d3(Object self);

    @SubstituteMethod(owner = "java.io.IOException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.AbstractMethodError", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ArithmeticException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ArrayIndexOutOfBoundsException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ArrayStoreException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ClassCastException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ClassNotFoundException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.Error", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.Exception", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ExceptionInInitializerError", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalAccessException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalArgumentException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalMonitorStateException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalStateException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalThreadStateException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IndexOutOfBoundsException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.InstantiationException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.InterruptedException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.LinkageError", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NegativeArraySizeException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NoClassDefFoundError", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NoSuchFieldException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NoSuchMethodException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NullPointerException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.OutOfMemoryError", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ReflectiveOperationException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.RuntimeException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.StringIndexOutOfBoundsException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.Throwable", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.UnsupportedOperationException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.charset.CharacterCodingException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.AccessDeniedException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.DirectoryNotEmptyException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.FileAlreadyExistsException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.FileSystemException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.InvalidPathException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.NoSuchFileException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.util.ConcurrentModificationException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.util.NoSuchElementException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.util.zip.DataFormatException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_IOException_getMessage_41154c8426", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.getMessage()Ljava/lang/String;",
                    "java/lang/AbstractMethodError.getMessage()Ljava/lang/String;",
                    "java/lang/ArithmeticException.getMessage()Ljava/lang/String;",
                    "java/lang/ArrayIndexOutOfBoundsException.getMessage()Ljava/lang/String;",
                    "java/lang/ArrayStoreException.getMessage()Ljava/lang/String;",
                    "java/lang/ClassCastException.getMessage()Ljava/lang/String;",
                    "java/lang/ClassNotFoundException.getMessage()Ljava/lang/String;",
                    "java/lang/Error.getMessage()Ljava/lang/String;",
                    "java/lang/Exception.getMessage()Ljava/lang/String;",
                    "java/lang/ExceptionInInitializerError.getMessage()Ljava/lang/String;",
                    "java/lang/IllegalAccessException.getMessage()Ljava/lang/String;",
                    "java/lang/IllegalArgumentException.getMessage()Ljava/lang/String;",
                    "java/lang/IllegalMonitorStateException.getMessage()Ljava/lang/String;",
                    "java/lang/IllegalStateException.getMessage()Ljava/lang/String;",
                    "java/lang/IllegalThreadStateException.getMessage()Ljava/lang/String;",
                    "java/lang/IndexOutOfBoundsException.getMessage()Ljava/lang/String;",
                    "java/lang/InstantiationException.getMessage()Ljava/lang/String;",
                    "java/lang/InterruptedException.getMessage()Ljava/lang/String;",
                    "java/lang/LinkageError.getMessage()Ljava/lang/String;",
                    "java/lang/NegativeArraySizeException.getMessage()Ljava/lang/String;",
                    "java/lang/NoClassDefFoundError.getMessage()Ljava/lang/String;",
                    "java/lang/NoSuchFieldException.getMessage()Ljava/lang/String;",
                    "java/lang/NoSuchMethodException.getMessage()Ljava/lang/String;",
                    "java/lang/NullPointerException.getMessage()Ljava/lang/String;",
                    "java/lang/OutOfMemoryError.getMessage()Ljava/lang/String;",
                    "java/lang/ReflectiveOperationException.getMessage()Ljava/lang/String;",
                    "java/lang/RuntimeException.getMessage()Ljava/lang/String;",
                    "java/lang/StringIndexOutOfBoundsException.getMessage()Ljava/lang/String;",
                    "java/lang/Throwable.getMessage()Ljava/lang/String;",
                    "java/lang/UnsupportedOperationException.getMessage()Ljava/lang/String;",
                    "java/nio/charset/CharacterCodingException.getMessage()Ljava/lang/String;",
                    "java/nio/file/AccessDeniedException.getMessage()Ljava/lang/String;",
                    "java/nio/file/DirectoryNotEmptyException.getMessage()Ljava/lang/String;",
                    "java/nio/file/FileAlreadyExistsException.getMessage()Ljava/lang/String;",
                    "java/nio/file/FileSystemException.getMessage()Ljava/lang/String;",
                    "java/nio/file/InvalidPathException.getMessage()Ljava/lang/String;",
                    "java/nio/file/NoSuchFileException.getMessage()Ljava/lang/String;",
                    "java/util/ConcurrentModificationException.getMessage()Ljava/lang/String;",
                    "java/util/NoSuchElementException.getMessage()Ljava/lang/String;",
                    "java/util/zip/DataFormatException.getMessage()Ljava/lang/String;"})
    public static native Object platform_IOException_getMessage_41154c8426(Object self);

    @SubstituteMethod(owner = "java.io.IOException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.AbstractMethodError", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ArithmeticException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ArrayIndexOutOfBoundsException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ArrayStoreException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ClassCastException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ClassNotFoundException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.Error", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.Exception", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ExceptionInInitializerError", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalAccessException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalArgumentException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalMonitorStateException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalStateException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IllegalThreadStateException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.IndexOutOfBoundsException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.InstantiationException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.InterruptedException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.LinkageError", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NegativeArraySizeException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NoClassDefFoundError", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NoSuchFieldException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NoSuchMethodException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.NullPointerException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.OutOfMemoryError", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.ReflectiveOperationException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.RuntimeException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.StringIndexOutOfBoundsException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.Throwable", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.lang.UnsupportedOperationException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.charset.CharacterCodingException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.AccessDeniedException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.DirectoryNotEmptyException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.FileAlreadyExistsException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.FileSystemException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.InvalidPathException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.file.NoSuchFileException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.util.ConcurrentModificationException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.util.NoSuchElementException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.util.zip.DataFormatException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @NativeImport(value = "jnative::platform_IOException_getSuppressed_6306cc2fd4", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/AbstractMethodError.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/ArithmeticException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/ArrayIndexOutOfBoundsException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/ArrayStoreException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/ClassCastException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/ClassNotFoundException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/Error.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/Exception.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/ExceptionInInitializerError.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/IllegalAccessException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/IllegalArgumentException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/IllegalMonitorStateException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/IllegalStateException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/IllegalThreadStateException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/IndexOutOfBoundsException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/InstantiationException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/InterruptedException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/LinkageError.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/NegativeArraySizeException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/NoClassDefFoundError.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/NoSuchFieldException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/NoSuchMethodException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/NullPointerException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/OutOfMemoryError.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/ReflectiveOperationException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/RuntimeException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/StringIndexOutOfBoundsException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/Throwable.getSuppressed()[Ljava/lang/Throwable;",
                    "java/lang/UnsupportedOperationException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/charset/CharacterCodingException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/file/AccessDeniedException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/file/DirectoryNotEmptyException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/file/FileAlreadyExistsException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/file/FileSystemException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/file/InvalidPathException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/file/NoSuchFileException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/util/ConcurrentModificationException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/util/NoSuchElementException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/util/zip/DataFormatException.getSuppressed()[Ljava/lang/Throwable;"})
    public static native Object platform_IOException_getSuppressed_6306cc2fd4(Object self);

    @SubstituteMethod(owner = "java.io.IOException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.AbstractMethodError", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.ArithmeticException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.ArrayIndexOutOfBoundsException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.ArrayStoreException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.ClassCastException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.ClassNotFoundException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.Error", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.Exception", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.ExceptionInInitializerError", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.IllegalAccessException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.IllegalArgumentException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.IllegalMonitorStateException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.IllegalStateException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.IllegalThreadStateException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.IndexOutOfBoundsException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.InstantiationException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.InterruptedException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.LinkageError", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.NegativeArraySizeException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.NoClassDefFoundError", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.NoSuchFieldException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.NoSuchMethodException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.NullPointerException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.OutOfMemoryError", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.ReflectiveOperationException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.RuntimeException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.StringIndexOutOfBoundsException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.Throwable", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.lang.UnsupportedOperationException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.charset.CharacterCodingException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.file.AccessDeniedException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.file.DirectoryNotEmptyException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.file.FileAlreadyExistsException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.file.FileSystemException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.file.InvalidPathException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.file.NoSuchFileException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.util.ConcurrentModificationException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.util.NoSuchElementException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.util.zip.DataFormatException", name = "printStackTrace", descriptor = "()V")
    @NativeImport(value = "jnative::platform_IOException_printStackTrace_1aa2e1b53c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.printStackTrace()V",
                    "java/lang/AbstractMethodError.printStackTrace()V",
                    "java/lang/ArithmeticException.printStackTrace()V",
                    "java/lang/ArrayIndexOutOfBoundsException.printStackTrace()V",
                    "java/lang/ArrayStoreException.printStackTrace()V",
                    "java/lang/ClassCastException.printStackTrace()V",
                    "java/lang/ClassNotFoundException.printStackTrace()V",
                    "java/lang/Error.printStackTrace()V",
                    "java/lang/Exception.printStackTrace()V",
                    "java/lang/ExceptionInInitializerError.printStackTrace()V",
                    "java/lang/IllegalAccessException.printStackTrace()V",
                    "java/lang/IllegalArgumentException.printStackTrace()V",
                    "java/lang/IllegalMonitorStateException.printStackTrace()V",
                    "java/lang/IllegalStateException.printStackTrace()V",
                    "java/lang/IllegalThreadStateException.printStackTrace()V",
                    "java/lang/IndexOutOfBoundsException.printStackTrace()V",
                    "java/lang/InstantiationException.printStackTrace()V",
                    "java/lang/InterruptedException.printStackTrace()V",
                    "java/lang/LinkageError.printStackTrace()V",
                    "java/lang/NegativeArraySizeException.printStackTrace()V",
                    "java/lang/NoClassDefFoundError.printStackTrace()V",
                    "java/lang/NoSuchFieldException.printStackTrace()V",
                    "java/lang/NoSuchMethodException.printStackTrace()V",
                    "java/lang/NullPointerException.printStackTrace()V",
                    "java/lang/OutOfMemoryError.printStackTrace()V",
                    "java/lang/ReflectiveOperationException.printStackTrace()V",
                    "java/lang/RuntimeException.printStackTrace()V",
                    "java/lang/StringIndexOutOfBoundsException.printStackTrace()V",
                    "java/lang/Throwable.printStackTrace()V",
                    "java/lang/UnsupportedOperationException.printStackTrace()V",
                    "java/nio/charset/CharacterCodingException.printStackTrace()V",
                    "java/nio/file/AccessDeniedException.printStackTrace()V",
                    "java/nio/file/DirectoryNotEmptyException.printStackTrace()V",
                    "java/nio/file/FileAlreadyExistsException.printStackTrace()V",
                    "java/nio/file/FileSystemException.printStackTrace()V",
                    "java/nio/file/InvalidPathException.printStackTrace()V",
                    "java/nio/file/NoSuchFileException.printStackTrace()V",
                    "java/util/ConcurrentModificationException.printStackTrace()V",
                    "java/util/NoSuchElementException.printStackTrace()V",
                    "java/util/zip/DataFormatException.printStackTrace()V"})
    public static native void platform_IOException_printStackTrace_1aa2e1b53c(Object self);

    @SubstituteMethod(owner = "java.io.IOException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.AbstractMethodError", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.ArithmeticException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.ArrayIndexOutOfBoundsException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.ArrayStoreException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.ClassCastException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.ClassNotFoundException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.Error", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.Exception", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.ExceptionInInitializerError", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.IllegalAccessException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.IllegalArgumentException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.IllegalMonitorStateException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.IllegalStateException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.IllegalThreadStateException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.IndexOutOfBoundsException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.InstantiationException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.InterruptedException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.LinkageError", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.NegativeArraySizeException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.NoClassDefFoundError", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.NoSuchFieldException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.NoSuchMethodException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.NullPointerException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.OutOfMemoryError", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.ReflectiveOperationException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.RuntimeException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.StringIndexOutOfBoundsException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.Throwable", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.lang.UnsupportedOperationException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.charset.CharacterCodingException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.file.AccessDeniedException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.file.DirectoryNotEmptyException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.file.FileAlreadyExistsException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.file.FileSystemException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.file.InvalidPathException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.file.NoSuchFileException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.util.ConcurrentModificationException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.util.NoSuchElementException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.util.zip.DataFormatException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @NativeImport(value = "jnative::platform_IOException_printStackTrace_b1f8b4d932", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/AbstractMethodError.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/ArithmeticException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/ArrayIndexOutOfBoundsException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/ArrayStoreException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/ClassCastException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/ClassNotFoundException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/Error.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/Exception.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/ExceptionInInitializerError.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/IllegalAccessException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/IllegalArgumentException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/IllegalMonitorStateException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/IllegalStateException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/IllegalThreadStateException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/IndexOutOfBoundsException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/InstantiationException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/InterruptedException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/LinkageError.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/NegativeArraySizeException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/NoClassDefFoundError.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/NoSuchFieldException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/NoSuchMethodException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/NullPointerException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/OutOfMemoryError.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/ReflectiveOperationException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/RuntimeException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/StringIndexOutOfBoundsException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/Throwable.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/lang/UnsupportedOperationException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/charset/CharacterCodingException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/file/AccessDeniedException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/file/DirectoryNotEmptyException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/file/FileAlreadyExistsException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/file/FileSystemException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/file/InvalidPathException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/file/NoSuchFileException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/util/ConcurrentModificationException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/util/NoSuchElementException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/util/zip/DataFormatException.printStackTrace(Ljava/io/PrintStream;)V"})
    public static native void platform_IOException_printStackTrace_b1f8b4d932(Object self, Object arg0);

    @SubstituteMethod(owner = "java.io.IOException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.AbstractMethodError", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ArithmeticException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ArrayIndexOutOfBoundsException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ArrayStoreException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ClassCastException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ClassNotFoundException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.Error", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.Exception", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ExceptionInInitializerError", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalAccessException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalArgumentException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalMonitorStateException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalStateException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IllegalThreadStateException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.IndexOutOfBoundsException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.InstantiationException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.InterruptedException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.LinkageError", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NegativeArraySizeException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NoClassDefFoundError", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NoSuchFieldException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NoSuchMethodException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.NullPointerException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.OutOfMemoryError", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.ReflectiveOperationException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.RuntimeException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.StringIndexOutOfBoundsException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.Throwable", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.lang.UnsupportedOperationException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.charset.CharacterCodingException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.AccessDeniedException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.DirectoryNotEmptyException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.FileAlreadyExistsException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.FileSystemException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.InvalidPathException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.file.NoSuchFileException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.util.ConcurrentModificationException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.util.NoSuchElementException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.util.zip.DataFormatException", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_IOException_toString_95f805c195", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/IOException.toString()Ljava/lang/String;",
                    "java/lang/AbstractMethodError.toString()Ljava/lang/String;",
                    "java/lang/ArithmeticException.toString()Ljava/lang/String;",
                    "java/lang/ArrayIndexOutOfBoundsException.toString()Ljava/lang/String;",
                    "java/lang/ArrayStoreException.toString()Ljava/lang/String;",
                    "java/lang/ClassCastException.toString()Ljava/lang/String;",
                    "java/lang/ClassNotFoundException.toString()Ljava/lang/String;",
                    "java/lang/Error.toString()Ljava/lang/String;",
                    "java/lang/Exception.toString()Ljava/lang/String;",
                    "java/lang/ExceptionInInitializerError.toString()Ljava/lang/String;",
                    "java/lang/IllegalAccessException.toString()Ljava/lang/String;",
                    "java/lang/IllegalArgumentException.toString()Ljava/lang/String;",
                    "java/lang/IllegalMonitorStateException.toString()Ljava/lang/String;",
                    "java/lang/IllegalStateException.toString()Ljava/lang/String;",
                    "java/lang/IllegalThreadStateException.toString()Ljava/lang/String;",
                    "java/lang/IndexOutOfBoundsException.toString()Ljava/lang/String;",
                    "java/lang/InstantiationException.toString()Ljava/lang/String;",
                    "java/lang/InterruptedException.toString()Ljava/lang/String;",
                    "java/lang/LinkageError.toString()Ljava/lang/String;",
                    "java/lang/NegativeArraySizeException.toString()Ljava/lang/String;",
                    "java/lang/NoClassDefFoundError.toString()Ljava/lang/String;",
                    "java/lang/NoSuchFieldException.toString()Ljava/lang/String;",
                    "java/lang/NoSuchMethodException.toString()Ljava/lang/String;",
                    "java/lang/NullPointerException.toString()Ljava/lang/String;",
                    "java/lang/OutOfMemoryError.toString()Ljava/lang/String;",
                    "java/lang/ReflectiveOperationException.toString()Ljava/lang/String;",
                    "java/lang/RuntimeException.toString()Ljava/lang/String;",
                    "java/lang/StringIndexOutOfBoundsException.toString()Ljava/lang/String;",
                    "java/lang/Throwable.toString()Ljava/lang/String;",
                    "java/lang/UnsupportedOperationException.toString()Ljava/lang/String;",
                    "java/nio/charset/CharacterCodingException.toString()Ljava/lang/String;",
                    "java/nio/file/AccessDeniedException.toString()Ljava/lang/String;",
                    "java/nio/file/DirectoryNotEmptyException.toString()Ljava/lang/String;",
                    "java/nio/file/FileAlreadyExistsException.toString()Ljava/lang/String;",
                    "java/nio/file/FileSystemException.toString()Ljava/lang/String;",
                    "java/nio/file/InvalidPathException.toString()Ljava/lang/String;",
                    "java/nio/file/NoSuchFileException.toString()Ljava/lang/String;",
                    "java/util/ConcurrentModificationException.toString()Ljava/lang/String;",
                    "java/util/NoSuchElementException.toString()Ljava/lang/String;",
                    "java/util/zip/DataFormatException.toString()Ljava/lang/String;"})
    public static native Object platform_IOException_toString_95f805c195(Object self);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "flush", descriptor = "()V")
    @NativeImport(value = "jnative::platform_PrintStream_flush_569bacc77f", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.flush()V"})
    public static native void platform_PrintStream_flush_569bacc77f(Object self);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "print", descriptor = "(C)V")
    @NativeImport(value = "jnative::platform_PrintStream_print_b4edf72725", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.print(C)V"})
    public static native void platform_PrintStream_print_b4edf72725(Object self, char arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "print", descriptor = "(D)V")
    @NativeImport(value = "jnative::platform_PrintStream_print_3f9f64fcc0", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.print(D)V"})
    public static native void platform_PrintStream_print_3f9f64fcc0(Object self, double arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "print", descriptor = "(F)V")
    @NativeImport(value = "jnative::platform_PrintStream_print_f685585226", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.print(F)V"})
    public static native void platform_PrintStream_print_f685585226(Object self, float arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "print", descriptor = "(I)V")
    @NativeImport(value = "jnative::platform_PrintStream_print_214528a1a0", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.print(I)V"})
    public static native void platform_PrintStream_print_214528a1a0(Object self, int arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "print", descriptor = "(J)V")
    @NativeImport(value = "jnative::platform_PrintStream_print_b1db16f7dd", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.print(J)V"})
    public static native void platform_PrintStream_print_b1db16f7dd(Object self, long arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "print", descriptor = "(Ljava/lang/Object;)V")
    @NativeImport(value = "jnative::platform_PrintStream_print_5e3eff6064", managed = true, callbacksSynchronous = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.print(Ljava/lang/Object;)V"},
            callbacks = {"java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {-1})
    public static native void platform_PrintStream_print_5e3eff6064(Object self, Object arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "print", descriptor = "(Ljava/lang/String;)V")
    @NativeImport(value = "jnative::platform_PrintStream_print_a9d9b8201d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.print(Ljava/lang/String;)V"})
    public static native void platform_PrintStream_print_a9d9b8201d(Object self, Object arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "print", descriptor = "(Z)V")
    @NativeImport(value = "jnative::platform_PrintStream_print_e0c3d489f8", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.print(Z)V"})
    public static native void platform_PrintStream_print_e0c3d489f8(Object self, boolean arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "()V")
    @NativeImport(value = "jnative::platform_PrintStream_println_9ea8d825fc", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println()V"})
    public static native void platform_PrintStream_println_9ea8d825fc(Object self);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "(C)V")
    @NativeImport(value = "jnative::platform_PrintStream_println_11928e47be", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println(C)V"})
    public static native void platform_PrintStream_println_11928e47be(Object self, char arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "(D)V")
    @NativeImport(value = "jnative::platform_PrintStream_println_4783062b70", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println(D)V"})
    public static native void platform_PrintStream_println_4783062b70(Object self, double arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "(F)V")
    @NativeImport(value = "jnative::platform_PrintStream_println_8a9895feec", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println(F)V"})
    public static native void platform_PrintStream_println_8a9895feec(Object self, float arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "(I)V")
    @NativeImport(value = "jnative::platform_PrintStream_println_16e84a65e9", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println(I)V"})
    public static native void platform_PrintStream_println_16e84a65e9(Object self, int arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "(J)V")
    @NativeImport(value = "jnative::platform_PrintStream_println_8c831a058a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println(J)V"})
    public static native void platform_PrintStream_println_8c831a058a(Object self, long arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "(Ljava/lang/Object;)V")
    @NativeImport(value = "jnative::platform_PrintStream_println_3dfcfb275c", managed = true, callbacksSynchronous = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println(Ljava/lang/Object;)V"},
            callbacks = {"java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {-1})
    public static native void platform_PrintStream_println_3dfcfb275c(Object self, Object arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "(Ljava/lang/String;)V")
    @NativeImport(value = "jnative::platform_PrintStream_println_9e65445681", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println(Ljava/lang/String;)V"})
    public static native void platform_PrintStream_println_9e65445681(Object self, Object arg0);

    @SubstituteMethod(owner = "java.io.PrintStream", name = "println", descriptor = "(Z)V")
    @NativeImport(value = "jnative::platform_PrintStream_println_80e32983cd", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/io/PrintStream.println(Z)V"})
    public static native void platform_PrintStream_println_80e32983cd(Object self, boolean arg0);

    @NativeImport(value = "jnative::platform_IndexOutOfBoundsException_initialize_a2147fa036", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/IndexOutOfBoundsException.<init>(I)V"})
    public static native void platform_IndexOutOfBoundsException_initialize_a2147fa036(Object self, int arg0);

    @SubstituteMethod(owner = "java.lang.Number", name = "byteValue", descriptor = "()B")
    @NativeImport(value = "jnative::platform_Number_byteValue_c683afad90", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Number.byteValue()B"})
    public static native byte platform_Number_byteValue_c683afad90(Object self);

    @SubstituteMethod(owner = "java.lang.Number", name = "doubleValue", descriptor = "()D")
    @NativeImport(value = "jnative::platform_Number_doubleValue_941a7cea4d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Number.doubleValue()D"})
    public static native double platform_Number_doubleValue_941a7cea4d(Object self);

    @SubstituteMethod(owner = "java.lang.Number", name = "floatValue", descriptor = "()F")
    @NativeImport(value = "jnative::platform_Number_floatValue_c10cb04e38", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Number.floatValue()F"})
    public static native float platform_Number_floatValue_c10cb04e38(Object self);

    @SubstituteMethod(owner = "java.lang.Number", name = "intValue", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Number_intValue_5abe74ab1f", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Number.intValue()I"})
    public static native int platform_Number_intValue_5abe74ab1f(Object self);

    @SubstituteMethod(owner = "java.lang.Number", name = "longValue", descriptor = "()J")
    @NativeImport(value = "jnative::platform_Number_longValue_d79f9ab79b", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Number.longValue()J"})
    public static native long platform_Number_longValue_d79f9ab79b(Object self);

    @SubstituteMethod(owner = "java.lang.Number", name = "shortValue", descriptor = "()S")
    @NativeImport(value = "jnative::platform_Number_shortValue_bfb67e8778", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Number.shortValue()S"})
    public static native short platform_Number_shortValue_bfb67e8778(Object self);

    @SubstituteMethod(owner = "java.lang.Object", name = "equals", descriptor = "(Ljava/lang/Object;)Z")
    @NativeImport(value = "jnative::platform_Object_equals_3e07e34c31", managed = true, managesRoots = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.equals(Ljava/lang/Object;)Z"})
    public static native boolean platform_Object_equals_3e07e34c31(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.Object", name = "getClass", descriptor = "()Ljava/lang/Class;")
    @NativeImport(value = "jnative::platform_Object_getClass_3bfb230c80", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.getClass()Ljava/lang/Class;"})
    public static native Object platform_Object_getClass_3bfb230c80(Object self);

    @SubstituteMethod(owner = "java.lang.Object", name = "hashCode", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Object_hashCode_339f483e8c", managed = true, managesRoots = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.hashCode()I"})
    public static native int platform_Object_hashCode_339f483e8c(Object self);

    @SubstituteMethod(owner = "java.lang.Object", name = "notify", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Object_notify_474c2f2e3b", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.notify()V"})
    public static native void platform_Object_notify_474c2f2e3b(Object self);

    @SubstituteMethod(owner = "java.lang.Object", name = "notifyAll", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Object_notifyAll_d04ed98017", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.notifyAll()V"})
    public static native void platform_Object_notifyAll_d04ed98017(Object self);

    @SubstituteMethod(owner = "java.lang.Object", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Object_toString_18a112723c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.toString()Ljava/lang/String;"})
    public static native Object platform_Object_toString_18a112723c(Object self);

    @SubstituteMethod(owner = "java.lang.Object", name = "wait", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Object_wait_d16e9f03ca", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.wait()V"})
    public static native void platform_Object_wait_d16e9f03ca(Object self);

    @SubstituteMethod(owner = "java.lang.Object", name = "wait", descriptor = "(J)V")
    @NativeImport(value = "jnative::platform_Object_wait_1df4e5ea91", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.wait(J)V"})
    public static native void platform_Object_wait_1df4e5ea91(Object self, long arg0);

    @SubstituteMethod(owner = "java.lang.Object", name = "wait", descriptor = "(JI)V")
    @NativeImport(value = "jnative::platform_Object_wait_f4f6c4ba64", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.wait(JI)V"})
    public static native void platform_Object_wait_f4f6c4ba64(Object self, long arg0, int arg1);

    @SubstituteMethod(owner = "java.lang.Runnable", name = "run", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Runnable_run_53e103ffd3", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Runnable.run()V"})
    public static native void platform_Runnable_run_53e103ffd3(Object self);

    @NativeImport(value = "jnative::platform_String_initialize_d7e6a88c7a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.<init>()V"})
    public static native void platform_String_initialize_d7e6a88c7a(Object self);

    @NativeImport(value = "jnative::platform_String_initialize_02a41a989d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.<init>(Ljava/lang/String;)V"})
    public static native void platform_String_initialize_02a41a989d(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_String_initialize_e2427f6bf8", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.<init>([C)V"})
    public static native void platform_String_initialize_e2427f6bf8(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_String_initialize_09257d1619", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.<init>([CII)V"})
    public static native void platform_String_initialize_09257d1619(Object self, Object arg0, int arg1, int arg2);

    @SubstituteMethod(owner = "java.lang.String", name = "charAt", descriptor = "(I)C")
    @NativeImport(value = "jnative::platform_String_charAt_5c9be27598", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/lang/String.charAt(I)C"})
    public static native char platform_String_charAt_5c9be27598(Object self, int arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "compareTo", descriptor = "(Ljava/lang/String;)I")
    @NativeImport(value = "jnative::platform_String_compareTo_59a2e06096", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.compareTo(Ljava/lang/String;)I"})
    public static native int platform_String_compareTo_59a2e06096(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "concat", descriptor = "(Ljava/lang/String;)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_concat_cf618bfbe6", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.concat(Ljava/lang/String;)Ljava/lang/String;"})
    public static native Object platform_String_concat_cf618bfbe6(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "equals", descriptor = "(Ljava/lang/Object;)Z")
    @NativeImport(value = "jnative::platform_String_equals_e3dd1f40ec", managed = true, managesRoots = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.equals(Ljava/lang/Object;)Z"})
    public static native boolean platform_String_equals_e3dd1f40ec(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "hashCode", descriptor = "()I")
    @NativeImport(value = "jnative::platform_String_hashCode_74fe13f362", managed = true, managesRoots = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.hashCode()I"})
    public static native int platform_String_hashCode_74fe13f362(Object self);

    @SubstituteMethod(owner = "java.lang.String", name = "isEmpty", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_String_isEmpty_ac0293776c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/lang/String.isEmpty()Z"})
    public static native boolean platform_String_isEmpty_ac0293776c(Object self);

    @SubstituteMethod(owner = "java.lang.String", name = "length", descriptor = "()I")
    @NativeImport(value = "jnative::platform_String_length_ff7a5a86ad", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/lang/String.length()I"})
    public static native int platform_String_length_ff7a5a86ad(Object self);

    @SubstituteMethod(owner = "java.lang.String", name = "substring", descriptor = "(I)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_substring_47ce72e87f", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.substring(I)Ljava/lang/String;"})
    public static native Object platform_String_substring_47ce72e87f(Object self, int arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "substring", descriptor = "(II)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_substring_6300fff947", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.substring(II)Ljava/lang/String;"})
    public static native Object platform_String_substring_6300fff947(Object self, int arg0, int arg1);

    @SubstituteMethod(owner = "java.lang.String", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_toString_1dec10508a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.toString()Ljava/lang/String;"})
    public static native Object platform_String_toString_1dec10508a(Object self);

    @SubstituteMethod(owner = "java.lang.String", name = "trim", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_trim_9502992899", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/String.trim()Ljava/lang/String;"})
    public static native Object platform_String_trim_9502992899(Object self);

    @SubstituteMethod(owner = "java.lang.String", name = "valueOf", descriptor = "(C)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_valueOf_66a68b15d3", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/String.valueOf(C)Ljava/lang/String;"})
    public static native Object platform_String_valueOf_66a68b15d3(char arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "valueOf", descriptor = "(D)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_valueOf_36abe0a8e0", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/String.valueOf(D)Ljava/lang/String;"})
    public static native Object platform_String_valueOf_36abe0a8e0(double arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "valueOf", descriptor = "(F)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_valueOf_e217bb627e", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/String.valueOf(F)Ljava/lang/String;"})
    public static native Object platform_String_valueOf_e217bb627e(float arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "valueOf", descriptor = "(I)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_valueOf_77bb0617b4", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/String.valueOf(I)Ljava/lang/String;"})
    public static native Object platform_String_valueOf_77bb0617b4(int arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "valueOf", descriptor = "(J)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_valueOf_05c966fa11", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/String.valueOf(J)Ljava/lang/String;"})
    public static native Object platform_String_valueOf_05c966fa11(long arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "valueOf", descriptor = "(Ljava/lang/Object;)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_valueOf_f3f690310c", managed = true, callbacksSynchronous = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/String.valueOf(Ljava/lang/Object;)Ljava/lang/String;"},
            callbacks = {"java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {-1})
    public static native Object platform_String_valueOf_f3f690310c(Object arg0);

    @SubstituteMethod(owner = "java.lang.String", name = "valueOf", descriptor = "(Z)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_String_valueOf_12e41bcdc0", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/String.valueOf(Z)Ljava/lang/String;"})
    public static native Object platform_String_valueOf_12e41bcdc0(boolean arg0);

    @SubstituteMethod(owner = "java.lang.System", name = "arraycopy", descriptor = "(Ljava/lang/Object;ILjava/lang/Object;II)V")
    @NativeImport(value = "jnative::platform_System_arraycopy_7b15f0890b", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/System.arraycopy(Ljava/lang/Object;ILjava/lang/Object;II)V"})
    public static native void platform_System_arraycopy_7b15f0890b(Object arg0, int arg1, Object arg2, int arg3, int arg4);

    @SubstituteMethod(owner = "java.lang.System", name = "clearProperty", descriptor = "(Ljava/lang/String;)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_System_clearProperty_f0493b8d44", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/System.clearProperty(Ljava/lang/String;)Ljava/lang/String;"})
    public static native Object platform_System_clearProperty_f0493b8d44(Object arg0);

    @SubstituteMethod(owner = "java.lang.System", name = "currentTimeMillis", descriptor = "()J")
    @NativeImport(value = "jnative::platform_System_currentTimeMillis_8e3c800ee9", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/System.currentTimeMillis()J"})
    public static native long platform_System_currentTimeMillis_8e3c800ee9();

    @SubstituteMethod(owner = "java.lang.System", name = "gc", descriptor = "()V")
    @NativeImport(value = "jnative::platform_System_gc_01ad5d387f", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/System.gc()V"})
    public static native void platform_System_gc_01ad5d387f();

    @SubstituteMethod(owner = "java.lang.System", name = "getProperty", descriptor = "(Ljava/lang/String;)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_System_getProperty_96986fbc5b", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/System.getProperty(Ljava/lang/String;)Ljava/lang/String;"})
    public static native Object platform_System_getProperty_96986fbc5b(Object arg0);

    @SubstituteMethod(owner = "java.lang.System", name = "getProperty", descriptor = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_System_getProperty_f04ecb2061", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/System.getProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"})
    public static native Object platform_System_getProperty_f04ecb2061(Object arg0, Object arg1);

    @SubstituteMethod(owner = "java.lang.System", name = "identityHashCode", descriptor = "(Ljava/lang/Object;)I")
    @NativeImport(value = "jnative::platform_System_identityHashCode_012516278d", managed = true, runtimeOnly = true, boundedAccess = true, instance = false,
            targets = {"java/lang/System.identityHashCode(Ljava/lang/Object;)I"})
    public static native int platform_System_identityHashCode_012516278d(Object arg0);

    @SubstituteMethod(owner = "java.lang.System", name = "nanoTime", descriptor = "()J")
    @NativeImport(value = "jnative::platform_System_nanoTime_490d23097b", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/System.nanoTime()J"})
    public static native long platform_System_nanoTime_490d23097b();

    @SubstituteMethod(owner = "java.lang.System", name = "setProperty", descriptor = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_System_setProperty_03d4db1313", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/System.setProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"})
    public static native Object platform_System_setProperty_03d4db1313(Object arg0, Object arg1);

    @NativeImport(value = "jnative::platform_Thread_initialize_2e1fc8a903", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.<init>()V"})
    public static native void platform_Thread_initialize_2e1fc8a903(Object self);

    @NativeImport(value = "jnative::platform_Thread_initialize_b4d7f5241e", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.<init>(Ljava/lang/Runnable;)V"})
    public static native void platform_Thread_initialize_b4d7f5241e(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_Thread_initialize_6e868cf185", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.<init>(Ljava/lang/Runnable;Ljava/lang/String;)V"})
    public static native void platform_Thread_initialize_6e868cf185(Object self, Object arg0, Object arg1);

    @NativeImport(value = "jnative::platform_Thread_initialize_82c2c25344", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.<init>(Ljava/lang/String;)V"})
    public static native void platform_Thread_initialize_82c2c25344(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.Thread", name = "currentThread", descriptor = "()Ljava/lang/Thread;")
    @NativeImport(value = "jnative::platform_Thread_currentThread_b9c9e64fd9", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/Thread.currentThread()Ljava/lang/Thread;"})
    public static native Object platform_Thread_currentThread_b9c9e64fd9();

    @SubstituteMethod(owner = "java.lang.Thread", name = "getId", descriptor = "()J")
    @SubstituteMethod(owner = "java.lang.Thread", name = "threadId", descriptor = "()J")
    @NativeImport(value = "jnative::platform_Thread_getId_b79446e798", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.getId()J",
                    "java/lang/Thread.threadId()J"})
    public static native long platform_Thread_getId_b79446e798(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "getName", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Thread_getName_dfea60f5af", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.getName()Ljava/lang/String;"})
    public static native Object platform_Thread_getName_dfea60f5af(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "holdsLock", descriptor = "(Ljava/lang/Object;)Z")
    @NativeImport(value = "jnative::platform_Thread_holdsLock_8b665dd3a7", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/Thread.holdsLock(Ljava/lang/Object;)Z"})
    public static native boolean platform_Thread_holdsLock_8b665dd3a7(Object arg0);

    @SubstituteMethod(owner = "java.lang.Thread", name = "interrupt", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Thread_interrupt_1da2f2ca75", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.interrupt()V"})
    public static native void platform_Thread_interrupt_1da2f2ca75(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "interrupted", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Thread_interrupted_260cf35ae7", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/Thread.interrupted()Z"})
    public static native boolean platform_Thread_interrupted_260cf35ae7();

    @SubstituteMethod(owner = "java.lang.Thread", name = "isAlive", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Thread_isAlive_0a6fe2b1df", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.isAlive()Z"})
    public static native boolean platform_Thread_isAlive_0a6fe2b1df(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "isDaemon", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Thread_isDaemon_20bf151f0b", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.isDaemon()Z"})
    public static native boolean platform_Thread_isDaemon_20bf151f0b(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "isInterrupted", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Thread_isInterrupted_6736e453bf", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.isInterrupted()Z"})
    public static native boolean platform_Thread_isInterrupted_6736e453bf(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "join", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Thread_join_1a25eb1a8a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.join()V"})
    public static native void platform_Thread_join_1a25eb1a8a(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "join", descriptor = "(J)V")
    @NativeImport(value = "jnative::platform_Thread_join_52180908f5", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.join(J)V"})
    public static native void platform_Thread_join_52180908f5(Object self, long arg0);

    @SubstituteMethod(owner = "java.lang.Thread", name = "join", descriptor = "(JI)V")
    @NativeImport(value = "jnative::platform_Thread_join_df352605a4", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.join(JI)V"})
    public static native void platform_Thread_join_df352605a4(Object self, long arg0, int arg1);

    @SubstituteMethod(owner = "java.lang.Thread", name = "run", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Thread_run_5b09d1c532", managed = true, callbacksSynchronous = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.run()V"},
            callbacks = {"java/lang/Runnable.run()V"},
            callbackReceivers = {-1})
    public static native void platform_Thread_run_5b09d1c532(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "setDaemon", descriptor = "(Z)V")
    @NativeImport(value = "jnative::platform_Thread_setDaemon_753e002dc7", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.setDaemon(Z)V"})
    public static native void platform_Thread_setDaemon_753e002dc7(Object self, boolean arg0);

    @SubstituteMethod(owner = "java.lang.Thread", name = "setName", descriptor = "(Ljava/lang/String;)V")
    @NativeImport(value = "jnative::platform_Thread_setName_6254ccd854", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.setName(Ljava/lang/String;)V"})
    public static native void platform_Thread_setName_6254ccd854(Object self, Object arg0);

    @SubstituteMethod(owner = "java.lang.Thread", name = "sleep", descriptor = "(J)V")
    @NativeImport(value = "jnative::platform_Thread_sleep_bcf91178bf", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/Thread.sleep(J)V"})
    public static native void platform_Thread_sleep_bcf91178bf(long arg0);

    @SubstituteMethod(owner = "java.lang.Thread", name = "sleep", descriptor = "(JI)V")
    @NativeImport(value = "jnative::platform_Thread_sleep_59ff56b193", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/Thread.sleep(JI)V"})
    public static native void platform_Thread_sleep_59ff56b193(long arg0, int arg1);

    @SubstituteMethod(owner = "java.lang.Thread", name = "start", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Thread_start_142b8288d1", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Thread.start()V"},
            callbacks = {"java/lang/Thread.run()V"},
            callbackReceivers = {-1})
    public static native void platform_Thread_start_142b8288d1(Object self);

    @SubstituteMethod(owner = "java.lang.Thread", name = "yield", descriptor = "()V")
    @NativeImport(value = "jnative::platform_Thread_yield_6461fc6583", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/lang/Thread.yield()V"})
    public static native void platform_Thread_yield_6461fc6583();

    @SubstituteMethod(owner = "java.lang.ThreadLocal", name = "get", descriptor = "()Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_ThreadLocal_get_35577f265a", managed = true, callbacksSynchronous = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/ThreadLocal.get()Ljava/lang/Object;"},
            callbacks = {"java/lang/ThreadLocal.initialValue()Ljava/lang/Object;"},
            callbackReceivers = {-1})
    public static native Object platform_ThreadLocal_get_35577f265a(Object self);

    @SubstituteMethod(owner = "java.lang.ThreadLocal", name = "initialValue", descriptor = "()Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_ThreadLocal_initialValue_4964262b11", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/ThreadLocal.initialValue()Ljava/lang/Object;"})
    public static native Object platform_ThreadLocal_initialValue_4964262b11(Object self);

    @SubstituteMethod(owner = "java.lang.ThreadLocal", name = "remove", descriptor = "()V")
    @NativeImport(value = "jnative::platform_ThreadLocal_remove_b3dc9a2db3", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/ThreadLocal.remove()V"})
    public static native void platform_ThreadLocal_remove_b3dc9a2db3(Object self);

    @SubstituteMethod(owner = "java.lang.ThreadLocal", name = "set", descriptor = "(Ljava/lang/Object;)V")
    @NativeImport(value = "jnative::platform_ThreadLocal_set_d3ef418bff", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/ThreadLocal.set(Ljava/lang/Object;)V"})
    public static native void platform_ThreadLocal_set_d3ef418bff(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.file.Files", name = "delete", descriptor = "(Ljava/nio/file/Path;)V")
    @NativeImport(value = "jnative::platform_Files_delete_2f9b7147bb", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/file/Files.delete(Ljava/nio/file/Path;)V"})
    public static native void platform_Files_delete_2f9b7147bb(Object arg0);

    @SubstituteMethod(owner = "java.nio.file.Files", name = "deleteIfExists", descriptor = "(Ljava/nio/file/Path;)Z")
    @NativeImport(value = "jnative::platform_Files_deleteIfExists_a4f0f637ea", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/file/Files.deleteIfExists(Ljava/nio/file/Path;)Z"})
    public static native boolean platform_Files_deleteIfExists_a4f0f637ea(Object arg0);

    @SubstituteMethod(owner = "java.nio.file.Files", name = "exists", descriptor = "(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z")
    @NativeImport(value = "jnative::platform_Files_exists_52f52b8c08", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/file/Files.exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"})
    public static native boolean platform_Files_exists_52f52b8c08(Object arg0, Object arg1);

    @SubstituteMethod(owner = "java.nio.file.Files", name = "readAllBytes", descriptor = "(Ljava/nio/file/Path;)[B")
    @NativeImport(value = "jnative::platform_Files_readAllBytes_8a22c8f106", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/file/Files.readAllBytes(Ljava/nio/file/Path;)[B"})
    public static native Object platform_Files_readAllBytes_8a22c8f106(Object arg0);

    @SubstituteMethod(owner = "java.nio.file.Files", name = "readString", descriptor = "(Ljava/nio/file/Path;)Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Files_readString_5150861ab9", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/file/Files.readString(Ljava/nio/file/Path;)Ljava/lang/String;"})
    public static native Object platform_Files_readString_5150861ab9(Object arg0);

    @SubstituteMethod(owner = "java.nio.file.Files", name = "write", descriptor = "(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;")
    @NativeImport(value = "jnative::platform_Files_write_efb1409a93", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/file/Files.write(Ljava/nio/file/Path;[B[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"})
    public static native Object platform_Files_write_efb1409a93(Object arg0, Object arg1, Object arg2);

    @SubstituteMethod(owner = "java.nio.file.Files", name = "writeString", descriptor = "(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;")
    @NativeImport(value = "jnative::platform_Files_writeString_a8383b7e99", managed = true, callbacksSynchronous = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/file/Files.writeString(Ljava/nio/file/Path;Ljava/lang/CharSequence;[Ljava/nio/file/OpenOption;)Ljava/nio/file/Path;"},
            callbacks = {"java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {-1})
    public static native Object platform_Files_writeString_a8383b7e99(Object arg0, Object arg1, Object arg2);

    @SubstituteMethod(owner = "java.nio.file.Path", name = "compareTo", descriptor = "(Ljava/nio/file/Path;)I")
    @NativeImport(value = "jnative::platform_Path_compareTo_9750be30b9", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/file/Path.compareTo(Ljava/nio/file/Path;)I"})
    public static native int platform_Path_compareTo_9750be30b9(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.file.Path", name = "getFileName", descriptor = "()Ljava/nio/file/Path;")
    @NativeImport(value = "jnative::platform_Path_getFileName_e88758c243", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/file/Path.getFileName()Ljava/nio/file/Path;"})
    public static native Object platform_Path_getFileName_e88758c243(Object self);

    @SubstituteMethod(owner = "java.nio.file.Path", name = "getParent", descriptor = "()Ljava/nio/file/Path;")
    @NativeImport(value = "jnative::platform_Path_getParent_2bd72ebc12", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/file/Path.getParent()Ljava/nio/file/Path;"})
    public static native Object platform_Path_getParent_2bd72ebc12(Object self);

    @SubstituteMethod(owner = "java.nio.file.Path", name = "normalize", descriptor = "()Ljava/nio/file/Path;")
    @NativeImport(value = "jnative::platform_Path_normalize_a8bad16ac6", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/file/Path.normalize()Ljava/nio/file/Path;"})
    public static native Object platform_Path_normalize_a8bad16ac6(Object self);

    @SubstituteMethod(owner = "java.nio.file.Path", name = "of", descriptor = "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;")
    @SubstituteMethod(owner = "java.nio.file.Paths", name = "get", descriptor = "(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;")
    @NativeImport(value = "jnative::platform_Path_of_e918b9c819", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/file/Path.of(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;",
                    "java/nio/file/Paths.get(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;"})
    public static native Object platform_Path_of_e918b9c819(Object arg0, Object arg1);

    @SubstituteMethod(owner = "java.nio.file.Path", name = "resolve", descriptor = "(Ljava/lang/String;)Ljava/nio/file/Path;")
    @SubstituteMethod(owner = "java.nio.file.Path", name = "resolve", descriptor = "(Ljava/nio/file/Path;)Ljava/nio/file/Path;")
    @NativeImport(value = "jnative::platform_Path_resolve_134a148b96", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/file/Path.resolve(Ljava/lang/String;)Ljava/nio/file/Path;",
                    "java/nio/file/Path.resolve(Ljava/nio/file/Path;)Ljava/nio/file/Path;"})
    public static native Object platform_Path_resolve_134a148b96(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.file.Path", name = "toAbsolutePath", descriptor = "()Ljava/nio/file/Path;")
    @NativeImport(value = "jnative::platform_Path_toAbsolutePath_b52376450e", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/file/Path.toAbsolutePath()Ljava/nio/file/Path;"})
    public static native Object platform_Path_toAbsolutePath_b52376450e(Object self);

    @SubstituteMethod(owner = "java.nio.file.Path", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_Path_toString_f226956f83", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/file/Path.toString()Ljava/lang/String;"})
    public static native Object platform_Path_toString_f226956f83(Object self);

    @NativeImport(value = "jnative::platform_AtomicInteger_initialize_98e5b91b74", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.<init>()V"})
    public static native void platform_AtomicInteger_initialize_98e5b91b74(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "lazySet", descriptor = "(I)V")
    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "set", descriptor = "(I)V")
    @NativeImport(value = "jnative::platform_AtomicInteger_initialize_e77a1a0072", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.<init>(I)V",
                    "java/util/concurrent/atomic/AtomicInteger.lazySet(I)V",
                    "java/util/concurrent/atomic/AtomicInteger.set(I)V"})
    public static native void platform_AtomicInteger_initialize_e77a1a0072(Object self, int arg0);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "addAndGet", descriptor = "(I)I")
    @NativeImport(value = "jnative::platform_AtomicInteger_addAndGet_a48e5fabaf", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.addAndGet(I)I"})
    public static native int platform_AtomicInteger_addAndGet_a48e5fabaf(Object self, int arg0);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "compareAndSet", descriptor = "(II)Z")
    @NativeImport(value = "jnative::platform_AtomicInteger_compareAndSet_b7516866c5", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.compareAndSet(II)Z"})
    public static native boolean platform_AtomicInteger_compareAndSet_b7516866c5(Object self, int arg0, int arg1);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "decrementAndGet", descriptor = "()I")
    @NativeImport(value = "jnative::platform_AtomicInteger_decrementAndGet_875ee982e2", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.decrementAndGet()I"})
    public static native int platform_AtomicInteger_decrementAndGet_875ee982e2(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "doubleValue", descriptor = "()D")
    @NativeImport(value = "jnative::platform_AtomicInteger_doubleValue_5d9a774217", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.doubleValue()D"})
    public static native double platform_AtomicInteger_doubleValue_5d9a774217(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "floatValue", descriptor = "()F")
    @NativeImport(value = "jnative::platform_AtomicInteger_floatValue_9e0dc8679a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.floatValue()F"})
    public static native float platform_AtomicInteger_floatValue_9e0dc8679a(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "get", descriptor = "()I")
    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "intValue", descriptor = "()I")
    @NativeImport(value = "jnative::platform_AtomicInteger_get_5254dac96e", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.get()I",
                    "java/util/concurrent/atomic/AtomicInteger.intValue()I"})
    public static native int platform_AtomicInteger_get_5254dac96e(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "getAndAdd", descriptor = "(I)I")
    @NativeImport(value = "jnative::platform_AtomicInteger_getAndAdd_7f05bae453", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.getAndAdd(I)I"})
    public static native int platform_AtomicInteger_getAndAdd_7f05bae453(Object self, int arg0);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "getAndDecrement", descriptor = "()I")
    @NativeImport(value = "jnative::platform_AtomicInteger_getAndDecrement_49112bee2f", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.getAndDecrement()I"})
    public static native int platform_AtomicInteger_getAndDecrement_49112bee2f(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "getAndIncrement", descriptor = "()I")
    @NativeImport(value = "jnative::platform_AtomicInteger_getAndIncrement_7819566d19", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.getAndIncrement()I"})
    public static native int platform_AtomicInteger_getAndIncrement_7819566d19(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "getAndSet", descriptor = "(I)I")
    @NativeImport(value = "jnative::platform_AtomicInteger_getAndSet_75bde512cb", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.getAndSet(I)I"})
    public static native int platform_AtomicInteger_getAndSet_75bde512cb(Object self, int arg0);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "incrementAndGet", descriptor = "()I")
    @NativeImport(value = "jnative::platform_AtomicInteger_incrementAndGet_9b1824cf4c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.incrementAndGet()I"})
    public static native int platform_AtomicInteger_incrementAndGet_9b1824cf4c(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "longValue", descriptor = "()J")
    @NativeImport(value = "jnative::platform_AtomicInteger_longValue_41274bb5fb", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.longValue()J"})
    public static native long platform_AtomicInteger_longValue_41274bb5fb(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicInteger", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_AtomicInteger_toString_d5efe908d3", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicInteger.toString()Ljava/lang/String;"})
    public static native Object platform_AtomicInteger_toString_d5efe908d3(Object self);

    @NativeImport(value = "jnative::platform_AtomicLong_initialize_be304dd814", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.<init>()V"})
    public static native void platform_AtomicLong_initialize_be304dd814(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "lazySet", descriptor = "(J)V")
    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "set", descriptor = "(J)V")
    @NativeImport(value = "jnative::platform_AtomicLong_initialize_02f56cacb8", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.<init>(J)V",
                    "java/util/concurrent/atomic/AtomicLong.lazySet(J)V",
                    "java/util/concurrent/atomic/AtomicLong.set(J)V"})
    public static native void platform_AtomicLong_initialize_02f56cacb8(Object self, long arg0);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "addAndGet", descriptor = "(J)J")
    @NativeImport(value = "jnative::platform_AtomicLong_addAndGet_b23e6f696c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.addAndGet(J)J"})
    public static native long platform_AtomicLong_addAndGet_b23e6f696c(Object self, long arg0);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "compareAndSet", descriptor = "(JJ)Z")
    @NativeImport(value = "jnative::platform_AtomicLong_compareAndSet_5aa60de7d8", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.compareAndSet(JJ)Z"})
    public static native boolean platform_AtomicLong_compareAndSet_5aa60de7d8(Object self, long arg0, long arg1);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "decrementAndGet", descriptor = "()J")
    @NativeImport(value = "jnative::platform_AtomicLong_decrementAndGet_6e3babb184", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.decrementAndGet()J"})
    public static native long platform_AtomicLong_decrementAndGet_6e3babb184(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "doubleValue", descriptor = "()D")
    @NativeImport(value = "jnative::platform_AtomicLong_doubleValue_4aa1a842c8", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.doubleValue()D"})
    public static native double platform_AtomicLong_doubleValue_4aa1a842c8(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "floatValue", descriptor = "()F")
    @NativeImport(value = "jnative::platform_AtomicLong_floatValue_7b5fe2b2ca", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.floatValue()F"})
    public static native float platform_AtomicLong_floatValue_7b5fe2b2ca(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "get", descriptor = "()J")
    @NativeImport(value = "jnative::platform_AtomicLong_get_e896fdfab7", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.get()J"})
    public static native long platform_AtomicLong_get_e896fdfab7(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "getAndAdd", descriptor = "(J)J")
    @NativeImport(value = "jnative::platform_AtomicLong_getAndAdd_d24eb67c15", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.getAndAdd(J)J"})
    public static native long platform_AtomicLong_getAndAdd_d24eb67c15(Object self, long arg0);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "getAndDecrement", descriptor = "()J")
    @NativeImport(value = "jnative::platform_AtomicLong_getAndDecrement_0cbb8e4e5f", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.getAndDecrement()J"})
    public static native long platform_AtomicLong_getAndDecrement_0cbb8e4e5f(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "getAndIncrement", descriptor = "()J")
    @NativeImport(value = "jnative::platform_AtomicLong_getAndIncrement_e6e1d95ed1", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.getAndIncrement()J"})
    public static native long platform_AtomicLong_getAndIncrement_e6e1d95ed1(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "getAndSet", descriptor = "(J)J")
    @NativeImport(value = "jnative::platform_AtomicLong_getAndSet_237f6a45b3", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.getAndSet(J)J"})
    public static native long platform_AtomicLong_getAndSet_237f6a45b3(Object self, long arg0);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "incrementAndGet", descriptor = "()J")
    @NativeImport(value = "jnative::platform_AtomicLong_incrementAndGet_d8a56ca2da", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.incrementAndGet()J"})
    public static native long platform_AtomicLong_incrementAndGet_d8a56ca2da(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "intValue", descriptor = "()I")
    @NativeImport(value = "jnative::platform_AtomicLong_intValue_0b8acce88a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.intValue()I"})
    public static native int platform_AtomicLong_intValue_0b8acce88a(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "longValue", descriptor = "()J")
    @NativeImport(value = "jnative::platform_AtomicLong_longValue_efba78d3fc", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.longValue()J"})
    public static native long platform_AtomicLong_longValue_efba78d3fc(Object self);

    @SubstituteMethod(owner = "java.util.concurrent.atomic.AtomicLong", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_AtomicLong_toString_defd705b88", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/util/concurrent/atomic/AtomicLong.toString()Ljava/lang/String;"})
    public static native Object platform_AtomicLong_toString_defd705b88(Object self);

    @SubstituteMethod(owner = "java.lang.Object", name = "clone", descriptor = "()Ljava/lang/Object;")
    @NativeImport(value = "jnative::platform_Object_clone_530a324f6a", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/lang/Object.clone()Ljava/lang/Object;"})
    public static native Object platform_Object_clone_530a324f6a(Object self);

    @NativeImport(value = "jnative::platform_System_out", managed = true, runtimeOnly = true, staticFields = {"java/lang/System.out:Ljava/io/PrintStream;"})
    public static native Object platform_System_out();

    @NativeImport(value = "jnative::platform_System_err", managed = true, runtimeOnly = true, staticFields = {"java/lang/System.err:Ljava/io/PrintStream;"})
    public static native Object platform_System_err();

    @NativeImport(value = "jnative::platform_ByteOrder_LITTLE_ENDIAN", managed = true, runtimeOnly = true, staticFields = {"java/nio/ByteOrder.LITTLE_ENDIAN:Ljava/nio/ByteOrder;"})
    public static native Object platform_ByteOrder_LITTLE_ENDIAN();

    @NativeImport(value = "jnative::platform_ByteOrder_BIG_ENDIAN", managed = true, runtimeOnly = true, staticFields = {"java/nio/ByteOrder.BIG_ENDIAN:Ljava/nio/ByteOrder;"})
    public static native Object platform_ByteOrder_BIG_ENDIAN();

}
