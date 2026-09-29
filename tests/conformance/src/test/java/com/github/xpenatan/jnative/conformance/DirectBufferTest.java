package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DirectBufferTest {
    @TempDir
    Path temporary;

    @Test
    void directStorageViewsByteOrderAndNativeAccessMatchJava() throws Exception {
        Path source = temporary.resolve("Buffers.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.nio.*;
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        @com.github.xpenatan.jnative.interop.NativeInclude("jnative_imports.h")
                        public class Buffers {
                            @NativeImport("buffer_sum") static int sum(ByteBuffer value) {
                                int sum = 0;
                                for (int i = value.position(); i < value.limit(); ++i) sum += value.get(i) & 255;
                                return sum;
                            }
                            public static void main(String[] args) {
                                ByteBuffer value = ByteBuffer.allocateDirect(40);
                                System.out.println(value.isDirect() + ":" + value.hasArray() + ":" + value.capacity());
                                System.out.println(value.order());
                                value.putInt(0x01020304).putLong(Long.MIN_VALUE).putFloat(-0f).putDouble(Double.NaN);
                                value.flip();
                                System.out.println(value.getInt() + ":" + value.getLong());
                                System.out.println(Float.floatToRawIntBits(value.getFloat()));
                                System.out.println(Double.isNaN(value.getDouble()));
                                value.clear().order(ByteOrder.LITTLE_ENDIAN).putInt(0x04030201).putShort((short)-2).putChar('Z');
                                System.out.println(value.get(0) + ":" + value.get(3) + ":" + value.getShort(4) + ":" + value.getChar(6));
                                value.position(2).limit(8).mark();
                                ByteBuffer slice = value.slice();
                                ByteBuffer copy = value.duplicate();
                                System.out.println(slice.order() + ":" + copy.order());
                                slice.put(0, (byte)99);
                                System.gc();
                                System.out.println(copy.get(2) + ":" + slice.capacity() + ":" + value.position());
                                System.out.println(sum(slice));
                                value.position(4).reset();
                                System.out.println(value.position());
                                ByteBuffer readOnly = slice.asReadOnlyBuffer();
                                try { readOnly.put((byte)1); }
                                catch (ReadOnlyBufferException expected) { System.out.println("readonly"); }
                                byte[] data = new byte[6];
                                readOnly.get(data);
                                System.out.println((data[0] & 255) + ":" + readOnly.remaining());
                                try { readOnly.get(); }
                                catch (BufferUnderflowException expected) { System.out.println("underflow:" + readOnly.position()); }
                                value.clear().put(new byte[]{-1,2,3}, 1, 2).flip();
                                System.out.println(value.get() + ":" + value.get());
                                try { value.putInt(0, 10); }
                                catch (IndexOutOfBoundsException expected) { System.out.println("absolute bounds"); }
                                try { value.reset(); }
                                catch (InvalidMarkException expected) { System.out.println("mark"); }
                                ByteBuffer empty = ByteBuffer.allocateDirect(0);
                                System.out.println(empty.remaining() + ":" + empty.hasRemaining());
                            }
                        }
                        """);
        Path nativeSource = temporary.resolve("buffers.cpp");
        Files.writeString(
                nativeSource,
                """
                        #include "jn_abi.hpp"
                        extern "C" int32_t buffer_sum(jn_handle handle) {
                            uint8_t* bytes;
                            int32_t count;
                            {
                                jnative::ManagedEntry managed;
                                auto buffer = jnative::as_byte_buffer(jnative::Heap::instance().resolve(handle));
                                count = buffer->limit.get() - buffer->position.get();
                                bytes = jnative::byte_buffer_data(buffer, count, false);
                            }
                            int32_t sum = 0;
                            for (int32_t i = 0; i < count; ++i) sum += bytes[i];
                            return sum;
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Buffers"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Buffers")
                        .buildRoot(temporary.resolve("output"))
                        .nativeFile(nativeSource)
                        .buildType(BuildType.RELEASE)
                        .nativeSymbols(NativeSymbols.NONE)
                        .stackTraces(StackTraceMode.NONE)
                        .crashReports(CrashReportMode.OFF);
        var result = builder.build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
