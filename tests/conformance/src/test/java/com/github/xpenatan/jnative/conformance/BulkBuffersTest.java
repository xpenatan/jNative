package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class BulkBuffersTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void arrayTransfersPreserveValuesViewsAndFailures(BuildType buildType) throws Exception {
        Path source = temporary.resolve("BulkBuffers.java");
        Path classes = temporary.resolve("classes");
        StringBuilder fixture =
                new StringBuilder(
                        """
                                import java.nio.*;
                                import java.util.Arrays;
                                public class BulkBuffers {
                                    static volatile boolean done;
                                    static ByteBuffer storage(boolean direct, ByteOrder order) {
                                        ByteBuffer result = direct ? ByteBuffer.allocateDirect(80) : ByteBuffer.allocate(80);
                                        result.position(3).limit(67);
                                        return result.slice().order(order);
                                    }
                                    public static void main(String[] args) throws Exception {
                                        for (boolean direct : new boolean[] {false, true}) {
                                            for (ByteOrder order : new ByteOrder[] {ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN}) {
                                                System.out.println(direct + ":" + order);
                                """);
        for(String[] type :
                List.of(
                        new String[]{"byte", "Byte", "1, -128, 127, 42, 9", ""},
                        new String[]{
                                "short", "Short", "1, -32768, 32767, 12345, 9", "asShortBuffer()"
                        },
                        new String[]{
                                "int",
                                "Int",
                                "1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x12345678, 9",
                                "asIntBuffer()"
                        },
                        new String[]{
                                "long",
                                "Long",
                                "1, Long.MIN_VALUE, Long.MAX_VALUE, 0x123456789ABCDEFL, 9",
                                "asLongBuffer()"
                        },
                        new String[]{
                                "float",
                                "Float",
                                "1, -0.0f, Float.intBitsToFloat(0x7fc12345), Float.POSITIVE_INFINITY,"
                                        + " 9",
                                "asFloatBuffer()"
                        },
                        new String[]{
                                "double",
                                "Double",
                                "1, -0.0, Double.longBitsToDouble(0x7ff8123456789ABCL),"
                                        + " Double.NEGATIVE_INFINITY, 9",
                                "asDoubleBuffer()"
                        })) {
            fixture.append("{\n")
                    .append(type[0])
                    .append("[] input = {")
                    .append(type[2])
                    .append("};\n")
                    .append("ByteBuffer raw = storage(direct, order);\n")
                    .append(type[1])
                    .append("Buffer buffer = raw")
                    .append(type[3].isEmpty() ? "" : "." + type[3])
                    .append(";\n")
                    .append("buffer.position(1).limit(4).mark();\n")
                    .append("buffer.put(input, 1, 3); System.out.println(buffer.position());\n")
                    .append("buffer.reset();\n")
                    .append(type[0])
                    .append("[] output = new ")
                    .append(type[0])
                    .append("[5];\n")
                    .append(
                            "buffer.get(output, 1, 3);"
                                    + " System.out.println(Arrays.toString(output));\n")
                    .append("System.out.println(buffer.position());\n")
                    .append("buffer.get(output, 5, 0);\n")
                    .append(
                            "try { buffer.put(input, 0, 1); } catch (BufferOverflowException e) {"
                                    + " System.out.println(\"overflow:\" + buffer.position()); }\n")
                    .append(
                            "try { buffer.get(output, 0, 1); } catch (BufferUnderflowException e) {"
                                    + " System.out.println(\"underflow:\" + buffer.position()); }\n")
                    .append(
                            "try { buffer.put(input, -1, 1); } catch (IndexOutOfBoundsException e)"
                                    + " { System.out.println(\"range:\" + buffer.position()); }\n")
                    .append(
                            "try { buffer.clear().asReadOnlyBuffer().put(input, 0, 1); } catch"
                                    + " (ReadOnlyBufferException e) { System.out.println(\"readonly\");"
                                    + " }\n")
                    .append("try { buffer.get((")
                    .append(type[0])
                    .append(
                            "[])null, 0, 0); } catch (NullPointerException e) {"
                                    + " System.out.println(\"null\"); }\n");
            if(type[0].equals("float"))
                fixture.append(
                        "System.out.println(Float.floatToRawIntBits(output[1]) + \":\" +"
                                + " Float.floatToRawIntBits(output[2]));\n");
            if(type[0].equals("double"))
                fixture.append(
                        "System.out.println(Double.doubleToRawLongBits(output[1]) + \":\" +"
                                + " Double.doubleToRawLongBits(output[2]));\n");
            String suffix = type[0].equals("byte") ? "" : type[1];
            String bits =
                    switch(type[0]) {
                        case "float" -> "Float.floatToRawIntBits(value)";
                        case "double" -> "Double.doubleToRawLongBits(value)";
                        default -> "value";
                    };
            fixture.append(
                    """
                            for (int alignment = 0; alignment < 8; alignment++) {
                                ByteBuffer scalar = storage(direct, order);
                                scalar.position(alignment);
                                for ($type value : input) scalar.put$suffix(value);
                                int end = scalar.position();
                                scalar.position(alignment);
                                for (int index = 0; index < input.length; index++) {
                                    int address = scalar.position();
                                    $type value = scalar.get$suffix();
                                    System.out.println($bits);
                                    value = scalar.get$suffix(address);
                                    System.out.println($bits);
                                    scalar.put$suffix(address, input[input.length - 1 - index]);
                                }
                                System.out.println(end + ":" + scalar.position());
                                scalar.position(scalar.limit());
                                try { scalar.get$suffix(); } catch (BufferUnderflowException e) {
                                    System.out.println("scalar underflow:" + scalar.position());
                                }
                                try { scalar.put$suffix(input[0]); } catch (BufferOverflowException e) {
                                    System.out.println("scalar overflow:" + scalar.position());
                                }
                                try { scalar.get$suffix(-1); } catch (IndexOutOfBoundsException e) {
                                    System.out.println("scalar negative:" + scalar.position());
                                }
                                try { scalar.put$suffix(scalar.limit(), input[0]); } catch (IndexOutOfBoundsException e) {
                                    System.out.println("scalar limit:" + scalar.position());
                                }
                                try { scalar.asReadOnlyBuffer().put$suffix(0, input[0]); } catch (ReadOnlyBufferException e) {
                                    System.out.println("scalar readonly:" + scalar.position());
                                }
                                scalar.clear();
                                byte[] bytes = new byte[64]; scalar.get(bytes);
                                System.out.println(Arrays.toString(bytes));
                            }
                            """
                            .replace("$type", type[0])
                            .replace("$suffix", suffix)
                            .replace("$bits", bits));
            fixture.append("for (int size : new int[] {0, 1, 3, 4, 5, 7, 8, 9, 63, 65}) {\n")
                    .append(
                            "ByteBuffer repeatedRaw = (direct ? ByteBuffer.allocateDirect((size +"
                                    + " 4) * 8) : ByteBuffer.allocate((size + 4) * 8));\n")
                    .append(
                            "repeatedRaw.position(1); repeatedRaw ="
                                    + " repeatedRaw.slice().order(order);\n")
                    .append(type[1])
                    .append("Buffer repeatedBuffer = repeatedRaw")
                    .append(type[3].isEmpty() ? "" : "." + type[3])
                    .append(";\n")
                    .append(type[0])
                    .append("[] repeated = new ")
                    .append(type[0])
                    .append("[size + 2], copied = new ")
                    .append(type[0])
                    .append("[size + 2];\n")
                    .append(
                            "for (int index = 0; index < size; index++) repeated[index + 1] ="
                                    + " input[index % input.length];\n")
                    .append(
                            "repeatedBuffer.put(repeated, 1, size).flip();"
                                    + " repeatedBuffer.get(copied, 1, size);\n")
                    .append(
                            "System.out.println(size + \":\" + Arrays.equals(repeated, copied) +"
                                    + " \":\" + repeatedBuffer.position());\n");
            if(type[0].equals("float"))
                fixture.append(
                        "for (float value : copied)"
                                + " System.out.println(Float.floatToRawIntBits(value));\n");
            if(type[0].equals("double"))
                fixture.append(
                        "for (double value : copied)"
                                + " System.out.println(Double.doubleToRawLongBits(value));\n");
            fixture.append("}\n");
            fixture.append(
                    "byte[] representation = new byte[64]; raw.clear().get(representation);"
                            + " System.out.println(Arrays.toString(representation));\n"
                            + "}");
        }
        fixture.append(
                """
                                    }
                                }
                                byte[] alias = {0, 1, 2, 3, 4, 5, 6, 7};
                                ByteBuffer.wrap(alias).position(2).put(alias, 0, 5);
                                System.out.println(Arrays.toString(alias));
                                ByteBuffer.wrap(alias).limit(5).get(alias, 2, 5);
                                System.out.println(Arrays.toString(alias));
                                FloatBuffer large = ByteBuffer.allocateDirect(16384).order(ByteOrder.nativeOrder()).asFloatBuffer();
                                float[] input = new float[4096], output = new float[4096];
                                for (int i = 0; i < input.length; i++) input[i] = i * 0.25f;
                                large.put(input).flip(); System.gc(); large.get(output);
                                System.out.println(Arrays.equals(input, output));
                                Thread collector = new Thread(() -> { while (!done) System.gc(); });
                                collector.start();
                                int checksum = 0;
                                try {
                                    for (int round = 0; round < 128; round++) {
                                        ByteBuffer data = ByteBuffer.allocateDirect(129).position(1).slice()
                                                .order(ByteOrder.nativeOrder());
                                        for (int index = 0; index < 32; index++) data.putInt(index * 4, round + index);
                                        data.position(0).mark();
                                        for (int index = 0; index < 32; index++) checksum += data.getInt();
                                        data.reset();
                                        try { data.getInt(126); } catch (IndexOutOfBoundsException expected) { }
                                        checksum += data.getInt();
                                        data.clear().limit(8).position(8);
                                        try { data.putInt(9); } catch (BufferOverflowException expected) { }
                                        checksum += data.position();
                                    }
                                } finally {
                                    done = true;
                                    collector.join();
                                }
                                System.out.println(checksum);
                            }
                        }
                        """);
        Files.writeString(source, fixture);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "BulkBuffers"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("BulkBuffers")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType)
                        .build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString()),
                        Duration.ofSeconds(30),
                        Map.of("JNATIVE_GC_INTERVAL", "2")));
    }
}
