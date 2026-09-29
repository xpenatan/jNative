package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.compiler.PlatformBindings;
import com.github.xpenatan.jnative.compiler.Program;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class ScalarBuffersTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void scalarWidthsOrdersSlicesAndFailuresMatchJvm(BuildType buildType) throws Exception {
        Path source = temporary.resolve("ScalarBuffers.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.nio.*;
                        public class ScalarBuffers {
                            static int argument() { System.out.println("argument"); return 0; }
                            static void check(ByteBuffer buffer) {
                                buffer.put(0, (byte) -127);
                                buffer.putChar(1, (char) 65535);
                                buffer.putShort(3, Short.MIN_VALUE);
                                buffer.putInt(5, Integer.MIN_VALUE);
                                buffer.putLong(9, Long.MIN_VALUE);
                                buffer.putFloat(17, Float.intBitsToFloat(0x7fc00007));
                                buffer.putDouble(21, -0d);
                                System.out.println(buffer.get(0) + ":" + (int) buffer.getChar(1)
                                    + ":" + buffer.getShort(3) + ":" + buffer.getInt(5) + ":" + buffer.getLong(9));
                                System.out.println(Float.floatToRawIntBits(buffer.getFloat(17))
                                    + ":" + Double.doubleToRawLongBits(buffer.getDouble(21)) + ":" + buffer.position());
                                buffer.position(39);
                                buffer.put((byte) -5).putChar((char) 65534).putShort(Short.MIN_VALUE)
                                    .putInt(Integer.MIN_VALUE).putLong(Long.MAX_VALUE).putFloat(-0f)
                                    .putDouble(Double.longBitsToDouble(0x7ff8000000000007L));
                                buffer.position(39);
                                System.out.println(buffer.get() + ":" + (int) buffer.getChar() + ":" + buffer.getShort()
                                    + ":" + buffer.getInt() + ":" + buffer.getLong());
                                System.out.println(Float.floatToRawIntBits(buffer.getFloat())
                                    + ":" + Double.doubleToRawLongBits(buffer.getDouble()) + ":" + buffer.position());
                                buffer.clear();
                                ShortBuffer shorts = buffer.asShortBuffer();
                                shorts.put(1, Short.MIN_VALUE).position(2).put((short) -1);
                                System.out.println(shorts.get(1) + ":" + shorts.position(2).get() + ":" + shorts.position());
                                IntBuffer ints = buffer.asIntBuffer();
                                ints.put(1, Integer.MIN_VALUE).position(2).put(Integer.MAX_VALUE);
                                System.out.println(ints.get(1) + ":" + ints.position(2).get() + ":" + ints.position());
                                LongBuffer longs = buffer.asLongBuffer();
                                longs.put(1, Long.MIN_VALUE).position(2).put(Long.MAX_VALUE);
                                System.out.println(longs.get(1) + ":" + longs.position(2).get() + ":" + longs.position());
                                FloatBuffer floats = buffer.asFloatBuffer();
                                floats.put(1, -0f).position(2).put(Float.intBitsToFloat(0x7fc00007));
                                System.out.println(Float.floatToRawIntBits(floats.get(1))
                                    + ":" + Float.floatToRawIntBits(floats.position(2).get()) + ":" + floats.position());
                                DoubleBuffer doubles = buffer.asDoubleBuffer();
                                doubles.put(1, -0d).position(2).put(Double.longBitsToDouble(0x7ff8000000000007L));
                                System.gc();
                                System.out.println(Double.doubleToRawLongBits(doubles.get(1))
                                    + ":" + Double.doubleToRawLongBits(doubles.position(2).get()) + ":" + doubles.position());
                                ByteBuffer readonly = buffer.asReadOnlyBuffer().limit(0);
                                try { readonly.putInt(-1, 1); }
                                catch (ReadOnlyBufferException expected) { System.out.println("readonly absolute"); }
                                try { readonly.putLong(1); }
                                catch (ReadOnlyBufferException expected) { System.out.println("readonly relative"); }
                                buffer.position(2).limit(4);
                                try { buffer.getInt(); }
                                catch (BufferUnderflowException expected) { System.out.println("underflow:" + buffer.position()); }
                                try { buffer.putInt(1); }
                                catch (BufferOverflowException expected) { System.out.println("overflow:" + buffer.position()); }
                                for (int index : new int[]{-1, 1, Integer.MAX_VALUE}) {
                                    try { buffer.getInt(index); }
                                    catch (IndexOutOfBoundsException expected) { System.out.println("absolute:" + buffer.position()); }
                                }
                                floats.limit(1).position(1);
                                try { floats.get(); }
                                catch (BufferUnderflowException expected) { System.out.println("float underflow:" + floats.position()); }
                                try { floats.put(1f); }
                                catch (BufferOverflowException expected) { System.out.println("float overflow:" + floats.position()); }
                            }
                            public static void main(String[] args) {
                                for (boolean direct : new boolean[]{false, true}) {
                                    for (ByteOrder order : new ByteOrder[]{ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
                                        ByteBuffer owner = direct ? ByteBuffer.allocateDirect(96) : ByteBuffer.allocate(96);
                                        owner.position(3).limit(83);
                                        check(owner.slice().order(order));
                                    }
                                }
                                ByteBuffer absent = null;
                                try { absent.getInt(argument()); }
                                catch (NullPointerException expected) { System.out.println("null"); }
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "ScalarBuffers"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("ScalarBuffers")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generation = builder.generate();
        String cpp =
                Files.readString(
                        generation
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/ScalarBuffers.cpp"));
        var read = PlatformBindings.find(new Program.MethodId("java/nio/ByteBuffer", "getInt", "(I)I"));
        var write = PlatformBindings.find(new Program.MethodId("java/nio/ByteBuffer", "putInt", "(II)Ljava/nio/ByteBuffer;"));
        assertTrue(cpp.contains("::" + read.binding().symbol() + "("), cpp);
        assertTrue(cpp.contains("::" + write.binding().symbol() + "("), cpp);
        String nativeAdapters = Files.readString(generation.request().buildRoot()
                .resolve("native/runtime/jn_platform_bindings.hpp"));
        assertTrue(nativeAdapters.contains("::jnative::buffer_read_scalar<"));
        assertTrue(nativeAdapters.contains("::jnative::buffer_write_scalar<"));
        assertFalse(cpp.contains("::jnative::java_api::java::nio::ByteBuffer::getInt("), cpp);
        var result = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
