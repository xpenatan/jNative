package java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/** Exact managed bindings for the supported platform API profile. */
@NativeInclude("jn_platform_bindings.hpp")
public final class NativeBuffers {
    private NativeBuffers() {}
    @NativeImport(value = "jnative::platform_Buffer_capacity_f85319a0ad", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.capacity()I",
                    "java/nio/ByteBuffer.capacity()I",
                    "java/nio/DoubleBuffer.capacity()I",
                    "java/nio/FloatBuffer.capacity()I",
                    "java/nio/IntBuffer.capacity()I",
                    "java/nio/LongBuffer.capacity()I",
                    "java/nio/ShortBuffer.capacity()I"})
    public static native int platform_Buffer_capacity_f85319a0ad(Object self);

    @NativeImport(value = "jnative::platform_Buffer_clear_c5a572ddd7", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.clear()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.clear()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.clear()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.clear()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.clear()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.clear()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.clear()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_clear_c5a572ddd7(Object self);

    @NativeImport(value = "jnative::platform_Buffer_flip_456b0a8455", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.flip()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.flip()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.flip()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.flip()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.flip()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.flip()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.flip()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_flip_456b0a8455(Object self);

    @NativeImport(value = "jnative::platform_Buffer_hasArray_afd4b38e9c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.hasArray()Z",
                    "java/nio/ByteBuffer.hasArray()Z",
                    "java/nio/DoubleBuffer.hasArray()Z",
                    "java/nio/FloatBuffer.hasArray()Z",
                    "java/nio/IntBuffer.hasArray()Z",
                    "java/nio/LongBuffer.hasArray()Z",
                    "java/nio/ShortBuffer.hasArray()Z"})
    public static native boolean platform_Buffer_hasArray_afd4b38e9c(Object self);

    @NativeImport(value = "jnative::platform_Buffer_hasRemaining_967b5bd7f6", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.hasRemaining()Z",
                    "java/nio/ByteBuffer.hasRemaining()Z",
                    "java/nio/DoubleBuffer.hasRemaining()Z",
                    "java/nio/FloatBuffer.hasRemaining()Z",
                    "java/nio/IntBuffer.hasRemaining()Z",
                    "java/nio/LongBuffer.hasRemaining()Z",
                    "java/nio/ShortBuffer.hasRemaining()Z"})
    public static native boolean platform_Buffer_hasRemaining_967b5bd7f6(Object self);

    @NativeImport(value = "jnative::platform_Buffer_isDirect_01a1f5fd2d", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.isDirect()Z",
                    "java/nio/ByteBuffer.isDirect()Z",
                    "java/nio/DoubleBuffer.isDirect()Z",
                    "java/nio/FloatBuffer.isDirect()Z",
                    "java/nio/IntBuffer.isDirect()Z",
                    "java/nio/LongBuffer.isDirect()Z",
                    "java/nio/ShortBuffer.isDirect()Z"})
    public static native boolean platform_Buffer_isDirect_01a1f5fd2d(Object self);

    @NativeImport(value = "jnative::platform_Buffer_isReadOnly_54ac9e39fb", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.isReadOnly()Z",
                    "java/nio/ByteBuffer.isReadOnly()Z",
                    "java/nio/DoubleBuffer.isReadOnly()Z",
                    "java/nio/FloatBuffer.isReadOnly()Z",
                    "java/nio/IntBuffer.isReadOnly()Z",
                    "java/nio/LongBuffer.isReadOnly()Z",
                    "java/nio/ShortBuffer.isReadOnly()Z"})
    public static native boolean platform_Buffer_isReadOnly_54ac9e39fb(Object self);

    @NativeImport(value = "jnative::platform_Buffer_limit_4047fe1337", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.limit()I",
                    "java/nio/ByteBuffer.limit()I",
                    "java/nio/DoubleBuffer.limit()I",
                    "java/nio/FloatBuffer.limit()I",
                    "java/nio/IntBuffer.limit()I",
                    "java/nio/LongBuffer.limit()I",
                    "java/nio/ShortBuffer.limit()I"})
    public static native int platform_Buffer_limit_4047fe1337(Object self);

    @NativeImport(value = "jnative::platform_Buffer_limit_ec4f081260", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.limit(I)Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.limit(I)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.limit(I)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.limit(I)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.limit(I)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.limit(I)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.limit(I)Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_limit_ec4f081260(Object self, int arg0);

    @NativeImport(value = "jnative::platform_Buffer_mark_3f16916c1d", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.mark()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.mark()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.mark()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.mark()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.mark()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.mark()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.mark()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_mark_3f16916c1d(Object self);

    @NativeImport(value = "jnative::platform_Buffer_position_d9688558f7", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.position()I",
                    "java/nio/ByteBuffer.position()I",
                    "java/nio/DoubleBuffer.position()I",
                    "java/nio/FloatBuffer.position()I",
                    "java/nio/IntBuffer.position()I",
                    "java/nio/LongBuffer.position()I",
                    "java/nio/ShortBuffer.position()I"})
    public static native int platform_Buffer_position_d9688558f7(Object self);

    @NativeImport(value = "jnative::platform_Buffer_position_e78fb31a90", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.position(I)Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.position(I)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.position(I)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.position(I)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.position(I)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.position(I)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.position(I)Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_position_e78fb31a90(Object self, int arg0);

    @NativeImport(value = "jnative::platform_Buffer_remaining_0cb642caae", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.remaining()I",
                    "java/nio/ByteBuffer.remaining()I",
                    "java/nio/DoubleBuffer.remaining()I",
                    "java/nio/FloatBuffer.remaining()I",
                    "java/nio/IntBuffer.remaining()I",
                    "java/nio/LongBuffer.remaining()I",
                    "java/nio/ShortBuffer.remaining()I"})
    public static native int platform_Buffer_remaining_0cb642caae(Object self);

    @NativeImport(value = "jnative::platform_Buffer_reset_790dfc0c66", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/Buffer.reset()Ljava/nio/Buffer;",
                    "java/nio/ByteBuffer.reset()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.reset()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.reset()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.reset()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.reset()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.reset()Ljava/nio/ShortBuffer;"})
    public static native Object platform_Buffer_reset_790dfc0c66(Object self);

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

    @NativeImport(value = "jnative::platform_BufferOverflowException_addSuppressed_63c2c5022d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/BufferUnderflowException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/InvalidMarkException.addSuppressed(Ljava/lang/Throwable;)V",
                    "java/nio/ReadOnlyBufferException.addSuppressed(Ljava/lang/Throwable;)V"})
    public static native void platform_BufferOverflowException_addSuppressed_63c2c5022d(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_BufferOverflowException_getCause_3553792c2b", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.getCause()Ljava/lang/Throwable;",
                    "java/nio/BufferUnderflowException.getCause()Ljava/lang/Throwable;",
                    "java/nio/InvalidMarkException.getCause()Ljava/lang/Throwable;",
                    "java/nio/ReadOnlyBufferException.getCause()Ljava/lang/Throwable;"})
    public static native Object platform_BufferOverflowException_getCause_3553792c2b(Object self);

    @NativeImport(value = "jnative::platform_BufferOverflowException_getMessage_6ad4c57731", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.getMessage()Ljava/lang/String;",
                    "java/nio/BufferUnderflowException.getMessage()Ljava/lang/String;",
                    "java/nio/InvalidMarkException.getMessage()Ljava/lang/String;",
                    "java/nio/ReadOnlyBufferException.getMessage()Ljava/lang/String;"})
    public static native Object platform_BufferOverflowException_getMessage_6ad4c57731(Object self);

    @NativeImport(value = "jnative::platform_BufferOverflowException_getSuppressed_47b3c123ab", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/BufferUnderflowException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/InvalidMarkException.getSuppressed()[Ljava/lang/Throwable;",
                    "java/nio/ReadOnlyBufferException.getSuppressed()[Ljava/lang/Throwable;"})
    public static native Object platform_BufferOverflowException_getSuppressed_47b3c123ab(Object self);

    @NativeImport(value = "jnative::platform_BufferOverflowException_printStackTrace_2b2aaf2e24", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.printStackTrace()V",
                    "java/nio/BufferUnderflowException.printStackTrace()V",
                    "java/nio/InvalidMarkException.printStackTrace()V",
                    "java/nio/ReadOnlyBufferException.printStackTrace()V"})
    public static native void platform_BufferOverflowException_printStackTrace_2b2aaf2e24(Object self);

    @NativeImport(value = "jnative::platform_BufferOverflowException_printStackTrace_e2dd9a0dfc", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/BufferUnderflowException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/InvalidMarkException.printStackTrace(Ljava/io/PrintStream;)V",
                    "java/nio/ReadOnlyBufferException.printStackTrace(Ljava/io/PrintStream;)V"})
    public static native void platform_BufferOverflowException_printStackTrace_e2dd9a0dfc(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_BufferOverflowException_toString_7fee3d47f1", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/BufferOverflowException.toString()Ljava/lang/String;",
                    "java/nio/BufferUnderflowException.toString()Ljava/lang/String;",
                    "java/nio/InvalidMarkException.toString()Ljava/lang/String;",
                    "java/nio/ReadOnlyBufferException.toString()Ljava/lang/String;"})
    public static native Object platform_BufferOverflowException_toString_7fee3d47f1(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_allocate_ee1be77b1a", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteBuffer.allocate(I)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_allocate_ee1be77b1a(int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_allocateDirect_b795c14e1e", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteBuffer.allocateDirect(I)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_allocateDirect_b795c14e1e(int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_array_ef57a9fa54", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.array()[B"})
    public static native Object platform_ByteBuffer_array_ef57a9fa54(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_arrayOffset_d2679c19b5", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.arrayOffset()I"})
    public static native int platform_ByteBuffer_arrayOffset_d2679c19b5(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_asDoubleBuffer_c74737fa23", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asDoubleBuffer()Ljava/nio/DoubleBuffer;"})
    public static native Object platform_ByteBuffer_asDoubleBuffer_c74737fa23(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_asFloatBuffer_3b377092b5", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asFloatBuffer()Ljava/nio/FloatBuffer;"})
    public static native Object platform_ByteBuffer_asFloatBuffer_3b377092b5(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_asIntBuffer_3be9ddc12e", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asIntBuffer()Ljava/nio/IntBuffer;"})
    public static native Object platform_ByteBuffer_asIntBuffer_3be9ddc12e(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_asLongBuffer_8941a832f8", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asLongBuffer()Ljava/nio/LongBuffer;"})
    public static native Object platform_ByteBuffer_asLongBuffer_8941a832f8(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_asReadOnlyBuffer_464b8df185", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asReadOnlyBuffer()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.asReadOnlyBuffer()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.asReadOnlyBuffer()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.asReadOnlyBuffer()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.asReadOnlyBuffer()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.asReadOnlyBuffer()Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_asReadOnlyBuffer_464b8df185(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_asShortBuffer_938511936d", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.asShortBuffer()Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_asShortBuffer_938511936d(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_duplicate_558ee004a4", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.duplicate()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.duplicate()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.duplicate()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.duplicate()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.duplicate()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.duplicate()Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_duplicate_558ee004a4(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_get_f9684695b7", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.get()B"})
    public static native byte platform_ByteBuffer_get_f9684695b7(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_get_fa4b81cb8c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.get(I)B"})
    public static native byte platform_ByteBuffer_get_fa4b81cb8c(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_get_63822f546f", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.get([B)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.get([D)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.get([F)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.get([I)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.get([J)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.get([S)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_get_63822f546f(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_get_cdc1ced698", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.get([BII)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.get([DII)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.get([FII)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.get([III)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.get([JII)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.get([SII)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_get_cdc1ced698(Object self, Object arg0, int arg1, int arg2);

    @NativeImport(value = "jnative::platform_ByteBuffer_getChar_06c02a1840", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getChar()C"})
    public static native char platform_ByteBuffer_getChar_06c02a1840(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_getChar_9dceb01fb9", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getChar(I)C"})
    public static native char platform_ByteBuffer_getChar_9dceb01fb9(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_getDouble_cf95f5c3b9", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getDouble()D"})
    public static native double platform_ByteBuffer_getDouble_cf95f5c3b9(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_getDouble_746a876414", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getDouble(I)D"})
    public static native double platform_ByteBuffer_getDouble_746a876414(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_getFloat_4b34ddfbd7", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getFloat()F"})
    public static native float platform_ByteBuffer_getFloat_4b34ddfbd7(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_getFloat_567dd4286f", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getFloat(I)F"})
    public static native float platform_ByteBuffer_getFloat_567dd4286f(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_getInt_7595877f21", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getInt()I"})
    public static native int platform_ByteBuffer_getInt_7595877f21(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_getInt_f76208b97e", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getInt(I)I"})
    public static native int platform_ByteBuffer_getInt_f76208b97e(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_getLong_8375eb2e7e", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getLong()J"})
    public static native long platform_ByteBuffer_getLong_8375eb2e7e(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_getLong_b1704d1438", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getLong(I)J"})
    public static native long platform_ByteBuffer_getLong_b1704d1438(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_getShort_01c2790e3c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getShort()S"})
    public static native short platform_ByteBuffer_getShort_01c2790e3c(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_getShort_a44227b4fe", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.getShort(I)S"})
    public static native short platform_ByteBuffer_getShort_a44227b4fe(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_order_1e14eb6811", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/DoubleBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/FloatBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/IntBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/LongBuffer.order()Ljava/nio/ByteOrder;",
                    "java/nio/ShortBuffer.order()Ljava/nio/ByteOrder;"})
    public static native Object platform_ByteBuffer_order_1e14eb6811(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_order_79bbe87457", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.order(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_order_79bbe87457(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_put_8e67d8e24f", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.put(B)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_put_8e67d8e24f(Object self, byte arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_put_6eaee5e384", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.put(IB)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_put_6eaee5e384(Object self, int arg0, byte arg1);

    @NativeImport(value = "jnative::platform_ByteBuffer_put_d7f86c157c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.put(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.put(Ljava/nio/DoubleBuffer;)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.put(Ljava/nio/FloatBuffer;)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.put(Ljava/nio/IntBuffer;)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.put(Ljava/nio/LongBuffer;)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.put(Ljava/nio/ShortBuffer;)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_put_d7f86c157c(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_put_4a1975d465", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.put([B)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.put([D)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.put([F)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.put([I)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.put([J)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.put([S)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_put_4a1975d465(Object self, Object arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_put_3d2788e8cb", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.put([BII)Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.put([DII)Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.put([FII)Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.put([III)Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.put([JII)Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.put([SII)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_put_3d2788e8cb(Object self, Object arg0, int arg1, int arg2);

    @NativeImport(value = "jnative::platform_ByteBuffer_putChar_1bbf371906", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putChar(C)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putChar_1bbf371906(Object self, char arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_putChar_be987fb8d5", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putChar(IC)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putChar_be987fb8d5(Object self, int arg0, char arg1);

    @NativeImport(value = "jnative::platform_ByteBuffer_putDouble_e01d38309d", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putDouble(D)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putDouble_e01d38309d(Object self, double arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_putDouble_6dd28aad87", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putDouble(ID)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putDouble_6dd28aad87(Object self, int arg0, double arg1);

    @NativeImport(value = "jnative::platform_ByteBuffer_putFloat_fc5e735e73", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putFloat(F)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putFloat_fc5e735e73(Object self, float arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_putFloat_3b2ac4d678", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putFloat(IF)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putFloat_3b2ac4d678(Object self, int arg0, float arg1);

    @NativeImport(value = "jnative::platform_ByteBuffer_putInt_b501b45ee6", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putInt(I)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putInt_b501b45ee6(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_putInt_488dd98615", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putInt(II)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putInt_488dd98615(Object self, int arg0, int arg1);

    @NativeImport(value = "jnative::platform_ByteBuffer_putLong_825c6a003a", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putLong(IJ)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putLong_825c6a003a(Object self, int arg0, long arg1);

    @NativeImport(value = "jnative::platform_ByteBuffer_putLong_a7ccb60957", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putLong(J)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putLong_a7ccb60957(Object self, long arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_putShort_0e3f8a56ad", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putShort(IS)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putShort_0e3f8a56ad(Object self, int arg0, short arg1);

    @NativeImport(value = "jnative::platform_ByteBuffer_putShort_04a321ec51", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ByteBuffer.putShort(S)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_putShort_04a321ec51(Object self, short arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_slice_3d9484bebb", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteBuffer.slice()Ljava/nio/ByteBuffer;",
                    "java/nio/DoubleBuffer.slice()Ljava/nio/DoubleBuffer;",
                    "java/nio/FloatBuffer.slice()Ljava/nio/FloatBuffer;",
                    "java/nio/IntBuffer.slice()Ljava/nio/IntBuffer;",
                    "java/nio/LongBuffer.slice()Ljava/nio/LongBuffer;",
                    "java/nio/ShortBuffer.slice()Ljava/nio/ShortBuffer;"})
    public static native Object platform_ByteBuffer_slice_3d9484bebb(Object self);

    @NativeImport(value = "jnative::platform_ByteBuffer_wrap_728ccf7a7c", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteBuffer.wrap([B)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_wrap_728ccf7a7c(Object arg0);

    @NativeImport(value = "jnative::platform_ByteBuffer_wrap_1b2a009105", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteBuffer.wrap([BII)Ljava/nio/ByteBuffer;"})
    public static native Object platform_ByteBuffer_wrap_1b2a009105(Object arg0, int arg1, int arg2);

    @NativeImport(value = "jnative::platform_ByteOrder_nativeOrder_dad3d4bd2c", managed = true, runtimeOnly = true, instance = false,
            targets = {"java/nio/ByteOrder.nativeOrder()Ljava/nio/ByteOrder;"})
    public static native Object platform_ByteOrder_nativeOrder_dad3d4bd2c();

    @NativeImport(value = "jnative::platform_ByteOrder_toString_ea9b2ee65c", managed = true, runtimeOnly = true, instance = true,
            targets = {"java/nio/ByteOrder.toString()Ljava/lang/String;"})
    public static native Object platform_ByteOrder_toString_ea9b2ee65c(Object self);

    @NativeImport(value = "jnative::platform_DoubleBuffer_get_3958b87034", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/DoubleBuffer.get()D"})
    public static native double platform_DoubleBuffer_get_3958b87034(Object self);

    @NativeImport(value = "jnative::platform_DoubleBuffer_get_6223711dd1", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/DoubleBuffer.get(I)D"})
    public static native double platform_DoubleBuffer_get_6223711dd1(Object self, int arg0);

    @NativeImport(value = "jnative::platform_DoubleBuffer_put_147a2e8390", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/DoubleBuffer.put(D)Ljava/nio/DoubleBuffer;"})
    public static native Object platform_DoubleBuffer_put_147a2e8390(Object self, double arg0);

    @NativeImport(value = "jnative::platform_DoubleBuffer_put_9bf9ff1ae3", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/DoubleBuffer.put(ID)Ljava/nio/DoubleBuffer;"})
    public static native Object platform_DoubleBuffer_put_9bf9ff1ae3(Object self, int arg0, double arg1);

    @NativeImport(value = "jnative::platform_FloatBuffer_get_eb1e76693f", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/FloatBuffer.get()F"})
    public static native float platform_FloatBuffer_get_eb1e76693f(Object self);

    @NativeImport(value = "jnative::platform_FloatBuffer_get_56952d7312", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/FloatBuffer.get(I)F"})
    public static native float platform_FloatBuffer_get_56952d7312(Object self, int arg0);

    @NativeImport(value = "jnative::platform_FloatBuffer_put_558aff6187", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/FloatBuffer.put(F)Ljava/nio/FloatBuffer;"})
    public static native Object platform_FloatBuffer_put_558aff6187(Object self, float arg0);

    @NativeImport(value = "jnative::platform_FloatBuffer_put_efd42c3223", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/FloatBuffer.put(IF)Ljava/nio/FloatBuffer;"})
    public static native Object platform_FloatBuffer_put_efd42c3223(Object self, int arg0, float arg1);

    @NativeImport(value = "jnative::platform_IntBuffer_get_33d8e0b9c4", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/IntBuffer.get()I"})
    public static native int platform_IntBuffer_get_33d8e0b9c4(Object self);

    @NativeImport(value = "jnative::platform_IntBuffer_get_0b2ba3eca1", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/IntBuffer.get(I)I"})
    public static native int platform_IntBuffer_get_0b2ba3eca1(Object self, int arg0);

    @NativeImport(value = "jnative::platform_IntBuffer_put_a934313b20", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/IntBuffer.put(I)Ljava/nio/IntBuffer;"})
    public static native Object platform_IntBuffer_put_a934313b20(Object self, int arg0);

    @NativeImport(value = "jnative::platform_IntBuffer_put_25d6823bbd", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/IntBuffer.put(II)Ljava/nio/IntBuffer;"})
    public static native Object platform_IntBuffer_put_25d6823bbd(Object self, int arg0, int arg1);

    @NativeImport(value = "jnative::platform_LongBuffer_get_e6c9bc5a0f", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/LongBuffer.get()J"})
    public static native long platform_LongBuffer_get_e6c9bc5a0f(Object self);

    @NativeImport(value = "jnative::platform_LongBuffer_get_db38278900", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/LongBuffer.get(I)J"})
    public static native long platform_LongBuffer_get_db38278900(Object self, int arg0);

    @NativeImport(value = "jnative::platform_LongBuffer_put_bbb2f82990", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/LongBuffer.put(IJ)Ljava/nio/LongBuffer;"})
    public static native Object platform_LongBuffer_put_bbb2f82990(Object self, int arg0, long arg1);

    @NativeImport(value = "jnative::platform_LongBuffer_put_c46cccd8f3", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/LongBuffer.put(J)Ljava/nio/LongBuffer;"})
    public static native Object platform_LongBuffer_put_c46cccd8f3(Object self, long arg0);

    @NativeImport(value = "jnative::platform_ShortBuffer_get_6b0c1c6235", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ShortBuffer.get()S"})
    public static native short platform_ShortBuffer_get_6b0c1c6235(Object self);

    @NativeImport(value = "jnative::platform_ShortBuffer_get_0a54d54aa3", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ShortBuffer.get(I)S"})
    public static native short platform_ShortBuffer_get_0a54d54aa3(Object self, int arg0);

    @NativeImport(value = "jnative::platform_ShortBuffer_put_bcbea28d13", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ShortBuffer.put(IS)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ShortBuffer_put_bcbea28d13(Object self, int arg0, short arg1);

    @NativeImport(value = "jnative::platform_ShortBuffer_put_a123412b1c", managed = true, runtimeOnly = true, boundedAccess = true, instance = true,
            targets = {"java/nio/ShortBuffer.put(S)Ljava/nio/ShortBuffer;"})
    public static native Object platform_ShortBuffer_put_a123412b1c(Object self, short arg0);

}
