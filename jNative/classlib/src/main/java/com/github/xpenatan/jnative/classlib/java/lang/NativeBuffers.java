package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteMethod;

/** Exact managed bindings for the supported platform API profile. */
@NativeInclude("jn_platform_bindings.hpp")
public final class NativeBuffers {
    private NativeBuffers() {}
    @SubstituteMethod(owner = "java.nio.Buffer", name = "capacity", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "capacity", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "capacity", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "capacity", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "capacity", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "capacity", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "capacity", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Buffer_capacity_f85319a0ad", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.capacity()I",
                    "java/nio/ByteBuffer.capacity()I",
                    "java/nio/DoubleBuffer.capacity()I",
                    "java/nio/FloatBuffer.capacity()I",
                    "java/nio/IntBuffer.capacity()I",
                    "java/nio/LongBuffer.capacity()I",
                    "java/nio/ShortBuffer.capacity()I"})
    public static native int platform_Buffer_capacity_f85319a0ad(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "clear", descriptor = "()Ljava/nio/Buffer;")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "clear", descriptor = "()Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "clear", descriptor = "()Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "clear", descriptor = "()Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "clear", descriptor = "()Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "clear", descriptor = "()Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "clear", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_Buffer_clear_c5a572ddd7", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.clear()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.clear()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.clear()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.clear()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.clear()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.clear()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.clear()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_clear_c5a572ddd7(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "flip", descriptor = "()Ljava/nio/Buffer;")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "flip", descriptor = "()Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "flip", descriptor = "()Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "flip", descriptor = "()Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "flip", descriptor = "()Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "flip", descriptor = "()Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "flip", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_Buffer_flip_456b0a8455", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.flip()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.flip()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.flip()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.flip()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.flip()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.flip()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.flip()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_flip_456b0a8455(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "hasArray", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "hasArray", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "hasArray", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "hasArray", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "hasArray", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "hasArray", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "hasArray", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Buffer_hasArray_afd4b38e9c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.hasArray()Z",
                    "java/nio/ByteBuffer.hasArray()Z",
                    "java/nio/DoubleBuffer.hasArray()Z",
                    "java/nio/FloatBuffer.hasArray()Z",
                    "java/nio/IntBuffer.hasArray()Z",
                    "java/nio/LongBuffer.hasArray()Z",
                    "java/nio/ShortBuffer.hasArray()Z"})
    public static native boolean platform_Buffer_hasArray_afd4b38e9c(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "hasRemaining", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "hasRemaining", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "hasRemaining", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "hasRemaining", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "hasRemaining", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "hasRemaining", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "hasRemaining", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Buffer_hasRemaining_967b5bd7f6", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.hasRemaining()Z",
                    "java/nio/ByteBuffer.hasRemaining()Z",
                    "java/nio/DoubleBuffer.hasRemaining()Z",
                    "java/nio/FloatBuffer.hasRemaining()Z",
                    "java/nio/IntBuffer.hasRemaining()Z",
                    "java/nio/LongBuffer.hasRemaining()Z",
                    "java/nio/ShortBuffer.hasRemaining()Z"})
    public static native boolean platform_Buffer_hasRemaining_967b5bd7f6(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "isDirect", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "isDirect", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "isDirect", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "isDirect", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "isDirect", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "isDirect", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "isDirect", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Buffer_isDirect_01a1f5fd2d", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.isDirect()Z",
                    "java/nio/ByteBuffer.isDirect()Z",
                    "java/nio/DoubleBuffer.isDirect()Z",
                    "java/nio/FloatBuffer.isDirect()Z",
                    "java/nio/IntBuffer.isDirect()Z",
                    "java/nio/LongBuffer.isDirect()Z",
                    "java/nio/ShortBuffer.isDirect()Z"})
    public static native boolean platform_Buffer_isDirect_01a1f5fd2d(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "isReadOnly", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "isReadOnly", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "isReadOnly", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "isReadOnly", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "isReadOnly", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "isReadOnly", descriptor = "()Z")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "isReadOnly", descriptor = "()Z")
    @NativeImport(value = "jnative::platform_Buffer_isReadOnly_54ac9e39fb", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.isReadOnly()Z",
                    "java/nio/ByteBuffer.isReadOnly()Z",
                    "java/nio/DoubleBuffer.isReadOnly()Z",
                    "java/nio/FloatBuffer.isReadOnly()Z",
                    "java/nio/IntBuffer.isReadOnly()Z",
                    "java/nio/LongBuffer.isReadOnly()Z",
                    "java/nio/ShortBuffer.isReadOnly()Z"})
    public static native boolean platform_Buffer_isReadOnly_54ac9e39fb(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "limit", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "limit", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "limit", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "limit", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "limit", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "limit", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "limit", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Buffer_limit_4047fe1337", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.limit()I",
                    "java/nio/ByteBuffer.limit()I",
                    "java/nio/DoubleBuffer.limit()I",
                    "java/nio/FloatBuffer.limit()I",
                    "java/nio/IntBuffer.limit()I",
                    "java/nio/LongBuffer.limit()I",
                    "java/nio/ShortBuffer.limit()I"})
    public static native int platform_Buffer_limit_4047fe1337(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "limit", descriptor = "(I)Ljava/nio/Buffer;")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "limit", descriptor = "(I)Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "limit", descriptor = "(I)Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "limit", descriptor = "(I)Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "limit", descriptor = "(I)Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "limit", descriptor = "(I)Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "limit", descriptor = "(I)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_Buffer_limit_ec4f081260", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.limit(I)Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.limit(I)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.limit(I)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.limit(I)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.limit(I)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.limit(I)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.limit(I)Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_limit_ec4f081260(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "mark", descriptor = "()Ljava/nio/Buffer;")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "mark", descriptor = "()Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "mark", descriptor = "()Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "mark", descriptor = "()Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "mark", descriptor = "()Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "mark", descriptor = "()Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "mark", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_Buffer_mark_3f16916c1d", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.mark()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.mark()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.mark()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.mark()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.mark()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.mark()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.mark()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_mark_3f16916c1d(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "position", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "position", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "position", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "position", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "position", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "position", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "position", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Buffer_position_d9688558f7", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.position()I",
                    "java/nio/ByteBuffer.position()I",
                    "java/nio/DoubleBuffer.position()I",
                    "java/nio/FloatBuffer.position()I",
                    "java/nio/IntBuffer.position()I",
                    "java/nio/LongBuffer.position()I",
                    "java/nio/ShortBuffer.position()I"})
    public static native int platform_Buffer_position_d9688558f7(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "position", descriptor = "(I)Ljava/nio/Buffer;")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "position", descriptor = "(I)Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "position", descriptor = "(I)Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "position", descriptor = "(I)Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "position", descriptor = "(I)Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "position", descriptor = "(I)Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "position", descriptor = "(I)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_Buffer_position_e78fb31a90", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.position(I)Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.position(I)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.position(I)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.position(I)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.position(I)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.position(I)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.position(I)Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_position_e78fb31a90(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "remaining", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "remaining", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "remaining", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "remaining", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "remaining", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "remaining", descriptor = "()I")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "remaining", descriptor = "()I")
    @NativeImport(value = "jnative::platform_Buffer_remaining_0cb642caae", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.remaining()I",
                    "java/nio/ByteBuffer.remaining()I",
                    "java/nio/DoubleBuffer.remaining()I",
                    "java/nio/FloatBuffer.remaining()I",
                    "java/nio/IntBuffer.remaining()I",
                    "java/nio/LongBuffer.remaining()I",
                    "java/nio/ShortBuffer.remaining()I"})
    public static native int platform_Buffer_remaining_0cb642caae(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "reset", descriptor = "()Ljava/nio/Buffer;")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "reset", descriptor = "()Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "reset", descriptor = "()Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "reset", descriptor = "()Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "reset", descriptor = "()Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "reset", descriptor = "()Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "reset", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_Buffer_reset_790dfc0c66", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.reset()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.reset()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.reset()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.reset()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.reset()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.reset()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.reset()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_reset_790dfc0c66(Object self);

    @SubstituteMethod(owner = "java.nio.Buffer", name = "rewind", descriptor = "()Ljava/nio/Buffer;")
    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "rewind", descriptor = "()Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "rewind", descriptor = "()Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "rewind", descriptor = "()Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "rewind", descriptor = "()Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "rewind", descriptor = "()Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "rewind", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_Buffer_rewind_060dddde23", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.rewind()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.rewind()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.rewind()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.rewind()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.rewind()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.rewind()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.rewind()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_rewind_060dddde23(Object self);

    @NativeImport(value = "jnative::platform_BufferOverflowException_initialize_cf74e29223", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.<init>()V",
                    "java/nio/BufferUnderflowException.<init>()V",
                    "java/nio/InvalidMarkException.<init>()V",
                    "java/nio/ReadOnlyBufferException.<init>()V"})
    public static native void platform_BufferOverflowException_initialize_cf74e29223(Object self);

    @NativeImport(value = "jnative::platform_BufferOverflowException_initialize_290ae12af7", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.<init>(Ljava/lang/String;)V",
                    "java/nio/BufferUnderflowException.<init>(Ljava/lang/String;)V",
                    "java/nio/InvalidMarkException.<init>(Ljava/lang/String;)V",
                    "java/nio/ReadOnlyBufferException.<init>(Ljava/lang/String;)V"})
    public static native void platform_BufferOverflowException_initialize_290ae12af7(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_BufferOverflowException_initialize_d3c48fbc2b", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/BufferUnderflowException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/InvalidMarkException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V",
                    "java/nio/ReadOnlyBufferException.<init>(Ljava/lang/String;Ljava/lang/Throwable;)V"})
    public static native void platform_BufferOverflowException_initialize_d3c48fbc2b(Object self, Object arg0, Object arg1);

    @NativeImport(value = "jnative::platform_BufferOverflowException_initialize_4716cc1a62", managed = true, callbacksSynchronous = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/BufferUnderflowException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/InvalidMarkException.<init>(Ljava/lang/Throwable;)V",
                    "java/nio/ReadOnlyBufferException.<init>(Ljava/lang/Throwable;)V"},
            callbacks = {"java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {-1})
    public static native void platform_BufferOverflowException_initialize_4716cc1a62(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.BufferOverflowException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.BufferUnderflowException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.InvalidMarkException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @SubstituteMethod(owner = "java.nio.ReadOnlyBufferException", name = "addSuppressed", descriptor = "(Ljava/lang/Throwable;)V")
    @NativeImport(value = "jnative::platform_BufferOverflowException_addSuppressed_63c2c5022d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/BufferUnderflowException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/InvalidMarkException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/ReadOnlyBufferException.addSuppressed(Ljava/lang/Throwable;)V"})
    public static native void platform_BufferOverflowException_addSuppressed_63c2c5022d(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.BufferOverflowException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.BufferUnderflowException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.InvalidMarkException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.ReadOnlyBufferException", name = "getCause", descriptor = "()Ljava/lang/Throwable;")
    @NativeImport(value = "jnative::platform_BufferOverflowException_getCause_3553792c2b", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.getCause()Ljava/lang/Throwable;",
                    "java/nio/BufferUnderflowException.getCause()Ljava/lang/Throwable;",
                    "java/nio/InvalidMarkException.getCause()Ljava/lang/Throwable;",
                    "java/nio/ReadOnlyBufferException.getCause()Ljava/lang/Throwable;"})
    public static native Object platform_BufferOverflowException_getCause_3553792c2b(Object self);

    @SubstituteMethod(owner = "java.nio.BufferOverflowException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.BufferUnderflowException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.InvalidMarkException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.ReadOnlyBufferException", name = "getMessage", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_BufferOverflowException_getMessage_6ad4c57731", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.getMessage()Ljava/lang/String;",
                    "java/nio/BufferUnderflowException.getMessage()Ljava/lang/String;",
                    "java/nio/InvalidMarkException.getMessage()Ljava/lang/String;",
                    "java/nio/ReadOnlyBufferException.getMessage()Ljava/lang/String;"})
    public static native Object platform_BufferOverflowException_getMessage_6ad4c57731(Object self);

    @SubstituteMethod(owner = "java.nio.BufferOverflowException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.BufferUnderflowException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.InvalidMarkException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @SubstituteMethod(owner = "java.nio.ReadOnlyBufferException", name = "getSuppressed", descriptor = "()[Ljava/lang/Throwable;")
    @NativeImport(value = "jnative::platform_BufferOverflowException_getSuppressed_47b3c123ab", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/BufferUnderflowException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/InvalidMarkException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/ReadOnlyBufferException.getSuppressed()[Ljava/lang/Throwable;"})
    public static native Object platform_BufferOverflowException_getSuppressed_47b3c123ab(Object self);

    @SubstituteMethod(owner = "java.nio.BufferOverflowException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.BufferUnderflowException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.InvalidMarkException", name = "printStackTrace", descriptor = "()V")
    @SubstituteMethod(owner = "java.nio.ReadOnlyBufferException", name = "printStackTrace", descriptor = "()V")
    @NativeImport(value = "jnative::platform_BufferOverflowException_printStackTrace_2b2aaf2e24", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.printStackTrace()V",
                    "java/nio/BufferUnderflowException.printStackTrace()V",
                    "java/nio/InvalidMarkException.printStackTrace()V",
                    "java/nio/ReadOnlyBufferException.printStackTrace()V"})
    public static native void platform_BufferOverflowException_printStackTrace_2b2aaf2e24(Object self);

    @SubstituteMethod(owner = "java.nio.BufferOverflowException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.BufferUnderflowException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.InvalidMarkException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @SubstituteMethod(owner = "java.nio.ReadOnlyBufferException", name = "printStackTrace", descriptor = "(Ljava/io/PrintStream;)V")
    @NativeImport(value = "jnative::platform_BufferOverflowException_printStackTrace_e2dd9a0dfc", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/BufferUnderflowException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/InvalidMarkException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/ReadOnlyBufferException.printStackTrace(Ljava/io/PrintStream;)V"})
    public static native void platform_BufferOverflowException_printStackTrace_e2dd9a0dfc(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.BufferOverflowException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.BufferUnderflowException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.InvalidMarkException", name = "toString", descriptor = "()Ljava/lang/String;")
    @SubstituteMethod(owner = "java.nio.ReadOnlyBufferException", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_BufferOverflowException_toString_7fee3d47f1", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.toString()Ljava/lang/String;",
                    "java/nio/BufferUnderflowException.toString()Ljava/lang/String;",
                    "java/nio/InvalidMarkException.toString()Ljava/lang/String;",
                    "java/nio/ReadOnlyBufferException.toString()Ljava/lang/String;"})
    public static native Object platform_BufferOverflowException_toString_7fee3d47f1(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "allocate", descriptor = "(I)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_allocate_ee1be77b1a", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteBuffer.allocate(I)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_allocate_ee1be77b1a(int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "allocateDirect", descriptor = "(I)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_allocateDirect_b795c14e1e", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteBuffer.allocateDirect(I)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_allocateDirect_b795c14e1e(int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "array", descriptor = "()[B")
    @NativeImport(value = "jnative::platform_ByteBuffer_array_ef57a9fa54", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.array()[B"})
    public static native Object platform_ByteBuffer_array_ef57a9fa54(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "arrayOffset", descriptor = "()I")
    @NativeImport(value = "jnative::platform_ByteBuffer_arrayOffset_d2679c19b5", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.arrayOffset()I"})
    public static native int platform_ByteBuffer_arrayOffset_d2679c19b5(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "asDoubleBuffer", descriptor = "()Ljava/nio/DoubleBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_asDoubleBuffer_c74737fa23", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asDoubleBuffer()Ljava/nio/DoubleBuffer;"})
    public static native Object platform_ByteBuffer_asDoubleBuffer_c74737fa23(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "asFloatBuffer", descriptor = "()Ljava/nio/FloatBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_asFloatBuffer_3b377092b5", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asFloatBuffer()Ljava/nio/FloatBuffer;"})
    public static native Object platform_ByteBuffer_asFloatBuffer_3b377092b5(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "asIntBuffer", descriptor = "()Ljava/nio/IntBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_asIntBuffer_3be9ddc12e", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asIntBuffer()Ljava/nio/IntBuffer;"})
    public static native Object platform_ByteBuffer_asIntBuffer_3be9ddc12e(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "asLongBuffer", descriptor = "()Ljava/nio/LongBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_asLongBuffer_8941a832f8", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asLongBuffer()Ljava/nio/LongBuffer;"})
    public static native Object platform_ByteBuffer_asLongBuffer_8941a832f8(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "asReadOnlyBuffer", descriptor = "()Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "asReadOnlyBuffer", descriptor = "()Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "asReadOnlyBuffer", descriptor = "()Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "asReadOnlyBuffer", descriptor = "()Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "asReadOnlyBuffer", descriptor = "()Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "asReadOnlyBuffer", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_asReadOnlyBuffer_464b8df185", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asReadOnlyBuffer()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.asReadOnlyBuffer()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.asReadOnlyBuffer()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.asReadOnlyBuffer()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.asReadOnlyBuffer()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.asReadOnlyBuffer()Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_asReadOnlyBuffer_464b8df185(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "asShortBuffer", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_asShortBuffer_938511936d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asShortBuffer()Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_asShortBuffer_938511936d(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "duplicate", descriptor = "()Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "duplicate", descriptor = "()Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "duplicate", descriptor = "()Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "duplicate", descriptor = "()Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "duplicate", descriptor = "()Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "duplicate", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_duplicate_558ee004a4", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.duplicate()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.duplicate()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.duplicate()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.duplicate()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.duplicate()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.duplicate()Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_duplicate_558ee004a4(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "get", descriptor = "()B")
    @NativeImport(value = "jnative::platform_ByteBuffer_get_f9684695b7", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.get()B"})
    public static native byte platform_ByteBuffer_get_f9684695b7(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "get", descriptor = "(I)B")
    @NativeImport(value = "jnative::platform_ByteBuffer_get_fa4b81cb8c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.get(I)B"})
    public static native byte platform_ByteBuffer_get_fa4b81cb8c(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "get", descriptor = "([B)Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "get", descriptor = "([D)Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "get", descriptor = "([F)Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "get", descriptor = "([I)Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "get", descriptor = "([J)Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "get", descriptor = "([S)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_get_63822f546f", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.get([B)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.get([D)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.get([F)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.get([I)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.get([J)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.get([S)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_get_63822f546f(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "get", descriptor = "([BII)Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "get", descriptor = "([DII)Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "get", descriptor = "([FII)Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "get", descriptor = "([III)Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "get", descriptor = "([JII)Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "get", descriptor = "([SII)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_get_cdc1ced698", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.get([BII)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.get([DII)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.get([FII)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.get([III)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.get([JII)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.get([SII)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_get_cdc1ced698(Object self, Object arg0, int arg1, int arg2);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getChar", descriptor = "()C")
    @NativeImport(value = "jnative::platform_ByteBuffer_getChar_06c02a1840", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getChar()C"})
    public static native char platform_ByteBuffer_getChar_06c02a1840(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getChar", descriptor = "(I)C")
    @NativeImport(value = "jnative::platform_ByteBuffer_getChar_9dceb01fb9", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getChar(I)C"})
    public static native char platform_ByteBuffer_getChar_9dceb01fb9(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getDouble", descriptor = "()D")
    @NativeImport(value = "jnative::platform_ByteBuffer_getDouble_cf95f5c3b9", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getDouble()D"})
    public static native double platform_ByteBuffer_getDouble_cf95f5c3b9(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getDouble", descriptor = "(I)D")
    @NativeImport(value = "jnative::platform_ByteBuffer_getDouble_746a876414", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getDouble(I)D"})
    public static native double platform_ByteBuffer_getDouble_746a876414(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getFloat", descriptor = "()F")
    @NativeImport(value = "jnative::platform_ByteBuffer_getFloat_4b34ddfbd7", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getFloat()F"})
    public static native float platform_ByteBuffer_getFloat_4b34ddfbd7(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getFloat", descriptor = "(I)F")
    @NativeImport(value = "jnative::platform_ByteBuffer_getFloat_567dd4286f", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getFloat(I)F"})
    public static native float platform_ByteBuffer_getFloat_567dd4286f(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getInt", descriptor = "()I")
    @NativeImport(value = "jnative::platform_ByteBuffer_getInt_7595877f21", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getInt()I"})
    public static native int platform_ByteBuffer_getInt_7595877f21(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getInt", descriptor = "(I)I")
    @NativeImport(value = "jnative::platform_ByteBuffer_getInt_f76208b97e", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getInt(I)I"})
    public static native int platform_ByteBuffer_getInt_f76208b97e(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getLong", descriptor = "()J")
    @NativeImport(value = "jnative::platform_ByteBuffer_getLong_8375eb2e7e", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getLong()J"})
    public static native long platform_ByteBuffer_getLong_8375eb2e7e(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getLong", descriptor = "(I)J")
    @NativeImport(value = "jnative::platform_ByteBuffer_getLong_b1704d1438", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getLong(I)J"})
    public static native long platform_ByteBuffer_getLong_b1704d1438(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getShort", descriptor = "()S")
    @NativeImport(value = "jnative::platform_ByteBuffer_getShort_01c2790e3c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getShort()S"})
    public static native short platform_ByteBuffer_getShort_01c2790e3c(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "getShort", descriptor = "(I)S")
    @NativeImport(value = "jnative::platform_ByteBuffer_getShort_a44227b4fe", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getShort(I)S"})
    public static native short platform_ByteBuffer_getShort_a44227b4fe(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "order", descriptor = "()Ljava/nio/ByteOrder;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "order", descriptor = "()Ljava/nio/ByteOrder;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "order", descriptor = "()Ljava/nio/ByteOrder;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "order", descriptor = "()Ljava/nio/ByteOrder;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "order", descriptor = "()Ljava/nio/ByteOrder;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "order", descriptor = "()Ljava/nio/ByteOrder;")
    @NativeImport(value = "jnative::platform_ByteBuffer_order_1e14eb6811", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/DoubleBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/FloatBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/IntBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/LongBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/ShortBuffer.order()Ljava/nio/ByteOrder;"})
    public static native Object platform_ByteBuffer_order_1e14eb6811(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "order", descriptor = "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_order_79bbe87457", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.order(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_order_79bbe87457(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "put", descriptor = "(B)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_put_8e67d8e24f", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.put(B)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_put_8e67d8e24f(Object self, byte arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "put", descriptor = "(IB)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_put_6eaee5e384", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.put(IB)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_put_6eaee5e384(Object self, int arg0, byte arg1);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "put", descriptor = "(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "put", descriptor = "(Ljava/nio/DoubleBuffer;)Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "put", descriptor = "(Ljava/nio/FloatBuffer;)Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "put", descriptor = "(Ljava/nio/IntBuffer;)Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "put", descriptor = "(Ljava/nio/LongBuffer;)Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "put", descriptor = "(Ljava/nio/ShortBuffer;)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_put_d7f86c157c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.put(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.put(Ljava/nio/DoubleBuffer;)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.put(Ljava/nio/FloatBuffer;)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.put(Ljava/nio/IntBuffer;)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.put(Ljava/nio/LongBuffer;)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.put(Ljava/nio/ShortBuffer;)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_put_d7f86c157c(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "put", descriptor = "([B)Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "put", descriptor = "([D)Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "put", descriptor = "([F)Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "put", descriptor = "([I)Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "put", descriptor = "([J)Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "put", descriptor = "([S)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_put_4a1975d465", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.put([B)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.put([D)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.put([F)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.put([I)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.put([J)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.put([S)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_put_4a1975d465(Object self, Object arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "put", descriptor = "([BII)Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "put", descriptor = "([DII)Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "put", descriptor = "([FII)Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "put", descriptor = "([III)Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "put", descriptor = "([JII)Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "put", descriptor = "([SII)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_put_3d2788e8cb", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.put([BII)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.put([DII)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.put([FII)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.put([III)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.put([JII)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.put([SII)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_put_3d2788e8cb(Object self, Object arg0, int arg1, int arg2);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putChar", descriptor = "(C)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putChar_1bbf371906", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putChar(C)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putChar_1bbf371906(Object self, char arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putChar", descriptor = "(IC)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putChar_be987fb8d5", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putChar(IC)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putChar_be987fb8d5(Object self, int arg0, char arg1);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putDouble", descriptor = "(D)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putDouble_e01d38309d", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putDouble(D)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putDouble_e01d38309d(Object self, double arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putDouble", descriptor = "(ID)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putDouble_6dd28aad87", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putDouble(ID)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putDouble_6dd28aad87(Object self, int arg0, double arg1);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putFloat", descriptor = "(F)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putFloat_fc5e735e73", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putFloat(F)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putFloat_fc5e735e73(Object self, float arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putFloat", descriptor = "(IF)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putFloat_3b2ac4d678", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putFloat(IF)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putFloat_3b2ac4d678(Object self, int arg0, float arg1);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putInt", descriptor = "(I)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putInt_b501b45ee6", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putInt(I)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putInt_b501b45ee6(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putInt", descriptor = "(II)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putInt_488dd98615", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putInt(II)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putInt_488dd98615(Object self, int arg0, int arg1);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putLong", descriptor = "(IJ)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putLong_825c6a003a", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putLong(IJ)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putLong_825c6a003a(Object self, int arg0, long arg1);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putLong", descriptor = "(J)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putLong_a7ccb60957", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putLong(J)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putLong_a7ccb60957(Object self, long arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putShort", descriptor = "(IS)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putShort_0e3f8a56ad", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putShort(IS)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putShort_0e3f8a56ad(Object self, int arg0, short arg1);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "putShort", descriptor = "(S)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_putShort_04a321ec51", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putShort(S)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putShort_04a321ec51(Object self, short arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "slice", descriptor = "()Ljava/nio/ByteBuffer;")
    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "slice", descriptor = "()Ljava/nio/DoubleBuffer;")
    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "slice", descriptor = "()Ljava/nio/FloatBuffer;")
    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "slice", descriptor = "()Ljava/nio/IntBuffer;")
    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "slice", descriptor = "()Ljava/nio/LongBuffer;")
    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "slice", descriptor = "()Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_slice_3d9484bebb", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.slice()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.slice()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.slice()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.slice()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.slice()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.slice()Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_slice_3d9484bebb(Object self);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "wrap", descriptor = "([B)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_wrap_728ccf7a7c", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteBuffer.wrap([B)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_wrap_728ccf7a7c(Object arg0);

    @SubstituteMethod(owner = "java.nio.ByteBuffer", name = "wrap", descriptor = "([BII)Ljava/nio/ByteBuffer;")
    @NativeImport(value = "jnative::platform_ByteBuffer_wrap_1b2a009105", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteBuffer.wrap([BII)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_wrap_1b2a009105(Object arg0, int arg1, int arg2);

    @SubstituteMethod(owner = "java.nio.ByteOrder", name = "nativeOrder", descriptor = "()Ljava/nio/ByteOrder;")
    @NativeImport(value = "jnative::platform_ByteOrder_nativeOrder_dad3d4bd2c", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteOrder.nativeOrder()Ljava/nio/ByteOrder;"})
    public static native Object platform_ByteOrder_nativeOrder_dad3d4bd2c();

    @SubstituteMethod(owner = "java.nio.ByteOrder", name = "toString", descriptor = "()Ljava/lang/String;")
    @NativeImport(value = "jnative::platform_ByteOrder_toString_ea9b2ee65c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteOrder.toString()Ljava/lang/String;"})
    public static native Object platform_ByteOrder_toString_ea9b2ee65c(Object self);

    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "get", descriptor = "()D")
    @NativeImport(value = "jnative::platform_DoubleBuffer_get_3958b87034", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/DoubleBuffer.get()D"})
    public static native double platform_DoubleBuffer_get_3958b87034(Object self);

    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "get", descriptor = "(I)D")
    @NativeImport(value = "jnative::platform_DoubleBuffer_get_6223711dd1", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/DoubleBuffer.get(I)D"})
    public static native double platform_DoubleBuffer_get_6223711dd1(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "put", descriptor = "(D)Ljava/nio/DoubleBuffer;")
    @NativeImport(value = "jnative::platform_DoubleBuffer_put_147a2e8390", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/DoubleBuffer.put(D)Ljava/nio/DoubleBuffer;"})
    public static native Object platform_DoubleBuffer_put_147a2e8390(Object self, double arg0);

    @SubstituteMethod(owner = "java.nio.DoubleBuffer", name = "put", descriptor = "(ID)Ljava/nio/DoubleBuffer;")
    @NativeImport(value = "jnative::platform_DoubleBuffer_put_9bf9ff1ae3", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/DoubleBuffer.put(ID)Ljava/nio/DoubleBuffer;"})
    public static native Object platform_DoubleBuffer_put_9bf9ff1ae3(Object self, int arg0, double arg1);

    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "get", descriptor = "()F")
    @NativeImport(value = "jnative::platform_FloatBuffer_get_eb1e76693f", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/FloatBuffer.get()F"})
    public static native float platform_FloatBuffer_get_eb1e76693f(Object self);

    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "get", descriptor = "(I)F")
    @NativeImport(value = "jnative::platform_FloatBuffer_get_56952d7312", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/FloatBuffer.get(I)F"})
    public static native float platform_FloatBuffer_get_56952d7312(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "put", descriptor = "(F)Ljava/nio/FloatBuffer;")
    @NativeImport(value = "jnative::platform_FloatBuffer_put_558aff6187", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/FloatBuffer.put(F)Ljava/nio/FloatBuffer;"})
    public static native Object platform_FloatBuffer_put_558aff6187(Object self, float arg0);

    @SubstituteMethod(owner = "java.nio.FloatBuffer", name = "put", descriptor = "(IF)Ljava/nio/FloatBuffer;")
    @NativeImport(value = "jnative::platform_FloatBuffer_put_efd42c3223", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/FloatBuffer.put(IF)Ljava/nio/FloatBuffer;"})
    public static native Object platform_FloatBuffer_put_efd42c3223(Object self, int arg0, float arg1);

    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "get", descriptor = "()I")
    @NativeImport(value = "jnative::platform_IntBuffer_get_33d8e0b9c4", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/IntBuffer.get()I"})
    public static native int platform_IntBuffer_get_33d8e0b9c4(Object self);

    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "get", descriptor = "(I)I")
    @NativeImport(value = "jnative::platform_IntBuffer_get_0b2ba3eca1", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/IntBuffer.get(I)I"})
    public static native int platform_IntBuffer_get_0b2ba3eca1(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "put", descriptor = "(I)Ljava/nio/IntBuffer;")
    @NativeImport(value = "jnative::platform_IntBuffer_put_a934313b20", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/IntBuffer.put(I)Ljava/nio/IntBuffer;"})
    public static native Object platform_IntBuffer_put_a934313b20(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.IntBuffer", name = "put", descriptor = "(II)Ljava/nio/IntBuffer;")
    @NativeImport(value = "jnative::platform_IntBuffer_put_25d6823bbd", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/IntBuffer.put(II)Ljava/nio/IntBuffer;"})
    public static native Object platform_IntBuffer_put_25d6823bbd(Object self, int arg0, int arg1);

    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "get", descriptor = "()J")
    @NativeImport(value = "jnative::platform_LongBuffer_get_e6c9bc5a0f", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/LongBuffer.get()J"})
    public static native long platform_LongBuffer_get_e6c9bc5a0f(Object self);

    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "get", descriptor = "(I)J")
    @NativeImport(value = "jnative::platform_LongBuffer_get_db38278900", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/LongBuffer.get(I)J"})
    public static native long platform_LongBuffer_get_db38278900(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "put", descriptor = "(IJ)Ljava/nio/LongBuffer;")
    @NativeImport(value = "jnative::platform_LongBuffer_put_bbb2f82990", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/LongBuffer.put(IJ)Ljava/nio/LongBuffer;"})
    public static native Object platform_LongBuffer_put_bbb2f82990(Object self, int arg0, long arg1);

    @SubstituteMethod(owner = "java.nio.LongBuffer", name = "put", descriptor = "(J)Ljava/nio/LongBuffer;")
    @NativeImport(value = "jnative::platform_LongBuffer_put_c46cccd8f3", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/LongBuffer.put(J)Ljava/nio/LongBuffer;"})
    public static native Object platform_LongBuffer_put_c46cccd8f3(Object self, long arg0);

    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "get", descriptor = "()S")
    @NativeImport(value = "jnative::platform_ShortBuffer_get_6b0c1c6235", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ShortBuffer.get()S"})
    public static native short platform_ShortBuffer_get_6b0c1c6235(Object self);

    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "get", descriptor = "(I)S")
    @NativeImport(value = "jnative::platform_ShortBuffer_get_0a54d54aa3", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ShortBuffer.get(I)S"})
    public static native short platform_ShortBuffer_get_0a54d54aa3(Object self, int arg0);

    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "put", descriptor = "(IS)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ShortBuffer_put_bcbea28d13", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ShortBuffer.put(IS)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ShortBuffer_put_bcbea28d13(Object self, int arg0, short arg1);

    @SubstituteMethod(owner = "java.nio.ShortBuffer", name = "put", descriptor = "(S)Ljava/nio/ShortBuffer;")
    @NativeImport(value = "jnative::platform_ShortBuffer_put_a123412b1c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ShortBuffer.put(S)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ShortBuffer_put_a123412b1c(Object self, short arg0);

}
