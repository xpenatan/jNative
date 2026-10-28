package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BufferTransfersTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void typedTransfersPreserveOverlappingBitsOrderPositionsAndFailures(BuildType buildType) throws Exception {
        StringBuilder methods = new StringBuilder();
        StringBuilder calls = new StringBuilder();
        for(String type : List.of("Byte", "Short", "Int", "Long", "Float", "Double")) {
            int width = type.equals("Byte") ? 1 : type.equals("Short") ? 2
                    : type.equals("Int") || type.equals("Float") ? 4 : 8;
            String view = type.equals("Byte") ? "" : ".as" + type + "Buffer()";
            methods.append("""
                    static void check%1$s(boolean direct, boolean alias, boolean swap, int gap, int count) {
                        ByteOrder order = ByteOrder.nativeOrder();
                        ByteOrder other = order == ByteOrder.LITTLE_ENDIAN ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
                        ByteBuffer input = storage(direct), output = alias ? input : storage(direct);
                        byte[] original = snapshot(input), expected = snapshot(output);
                        %1$sBuffer source = range(input, 32, order)%2$s;
                        %1$sBuffer target = range(output, 32 + gap, swap ? other : order)%2$s;
                        source.position(1).limit(1 + count).mark();
                        target.position(2).limit(2 + count).mark();
                        for(int i = 0; i < count * %3$d; i++) {
                            int read = (i / %3$d) * %3$d + (swap ? %3$d - 1 - i %% %3$d : i %% %3$d);
                            expected[32 + gap + 2 * %3$d + i] = original[32 + %3$d + read];
                        }
                        if(target.put(source) != target || source.position() != 1 + count || target.position() != 2 + count)
                            throw new IllegalStateException("positions");
                        if(!Arrays.equals(expected, snapshot(output))) throw new IllegalStateException("payload");
                        source.reset(); target.reset();
                        if(source.position() != 1 || target.position() != 2) throw new IllegalStateException("marks");
                        source.limit(2); target.limit(2);
                        try { target.put(source); throw new IllegalStateException("overflow accepted"); }
                        catch(BufferOverflowException expectedError) { }
                        if(source.position() != 1 || target.position() != 2) throw new IllegalStateException("failed positions");
                        if(!Arrays.equals(expected, snapshot(output))) throw new IllegalStateException("failed payload");
                        try { source.put(source); throw new IllegalStateException("self accepted"); }
                        catch(IllegalArgumentException expectedError) { }
                        source.limit(1);
                        try { target.asReadOnlyBuffer().put(source); throw new IllegalStateException("readonly accepted"); }
                        catch(ReadOnlyBufferException expectedError) { }
                        try { target.put((%1$sBuffer)null); throw new IllegalStateException("null accepted"); }
                        catch(NullPointerException expectedError) { }
                    }
                    """.formatted(type, view, width));
            calls.append("check").append(type).append("(direct, alias, swap, gap, count);\n");
        }
        Path source = temporary.resolve("BufferTransfers.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                import java.nio.ByteBuffer;
                import java.nio.ByteOrder;
                import java.nio.ShortBuffer;
                import java.nio.IntBuffer;
                import java.nio.LongBuffer;
                import java.nio.FloatBuffer;
                import java.nio.DoubleBuffer;
                import java.nio.BufferOverflowException;
                import java.nio.ReadOnlyBufferException;
                import java.util.Arrays;
                public class BufferTransfers {
                    static ByteBuffer storage(boolean direct) {
                        ByteBuffer result = direct ? ByteBuffer.allocateDirect(1024) : ByteBuffer.allocate(1024);
                        for(int i = 0; i < result.capacity(); i++) result.put(i, (byte)(i * 37 + 11));
                        // Quiet NaN payloads, infinities and signed zeros are copied as bytes.
                        result.order(ByteOrder.nativeOrder()).putInt(36, 0x7fc12345).putInt(40, 0x80000000);
                        result.putLong(48, 0x7ff8123456789abCL).putLong(56, 0x8000000000000000L);
                        return result;
                    }
                    static ByteBuffer range(ByteBuffer buffer, int offset, ByteOrder order) {
                        return buffer.duplicate().position(offset).limit(offset + 400).slice().order(order);
                    }
                    static byte[] snapshot(ByteBuffer buffer) {
                        byte[] result = new byte[buffer.capacity()];
                        buffer.duplicate().clear().get(result);
                        return result;
                    }
                    %s
                    public static void main(String[] args) {
                        for(boolean direct : new boolean[]{false, true})
                            for(boolean alias : new boolean[]{false, true})
                                for(boolean swap : new boolean[]{false, true})
                                    for(int gap : new int[]{-17, -8, -1, 0, 1, 8, 17})
                                        for(int count : new int[]{0, 1, 7, 8, 9, 16, 17, 33}) {
                                            %s
                                        }
                        System.out.println("all transfer bytes, positions, marks and failures verified");
                    }
                }
                """.formatted(methods, calls));
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "BufferTransfers"), Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("BufferTransfers")
                .buildRoot(temporary.resolve("out")).buildType(buildType).build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(120), Map.of("JNATIVE_GC_INTERVAL", "2")));
    }
}
