package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.objectweb.asm.Opcodes.*;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

class FloatingPointLoweringTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void comparisonsAndMathMatchJvm(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Floating.java"), classes = temporary.resolve("classes");
        StringBuilder stubs = new StringBuilder("class Comparisons {\n");
        StringBuilder floatCalls = new StringBuilder(), doubleCalls = new StringBuilder();
        var bytecode = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        bytecode.visit(V17, ACC_PUBLIC, "Comparisons", null, "java/lang/Object", null);
        for(int comparison : new int[]{FCMPL, FCMPG, DCMPL, DCMPG}) {
            boolean wide = comparison == DCMPL || comparison == DCMPG;
            String type = wide ? "double" : "float", descriptor = wide ? "(DD)I" : "(FF)I";
            for(int branch : new int[]{IRETURN, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, DUP}) {
                String name = "compare" + comparison + "_" + branch;
                stubs.append("static int ")
                        .append(name)
                        .append('(')
                        .append(type)
                        .append(" a, ")
                        .append(type)
                        .append(" b) { return 0; }\n");
                (wide ? doubleCalls : floatCalls)
                        .append("mix(Comparisons.")
                        .append(name)
                        .append("(a, b));\n");
                var method =
                        bytecode.visitMethod(ACC_PUBLIC | ACC_STATIC, name, descriptor, null, null);
                method.visitCode();
                method.visitVarInsn(wide ? DLOAD : FLOAD, 0);
                method.visitVarInsn(wide ? DLOAD : FLOAD, wide ? 2 : 1);
                method.visitInsn(comparison);
                if(branch == DUP) {
                    method.visitInsn(DUP);
                    method.visitInsn(IADD);
                }
                else if(branch != IRETURN) {
                    var selected = new Label();
                    method.visitJumpInsn(branch, selected);
                    method.visitInsn(ICONST_0);
                    method.visitInsn(IRETURN);
                    method.visitLabel(selected);
                    method.visitInsn(ICONST_1);
                }
                method.visitInsn(IRETURN);
                method.visitMaxs(0, 0);
                method.visitEnd();
            }
        }
        stubs.append("}\n");
        bytecode.visitEnd();
        Files.writeString(
                source,
                """
                        public class Floating {
                            static long hash = 7;
                            static int sequence;
                            static volatile float field;
                            static void mix(long value) { hash = hash * 31 + value; }
                            static float next(int expected, float value) {
                                if (++sequence != expected) throw new IllegalStateException("evaluation order");
                                field = value;
                                return value;
                            }
                            static void floats(float a, float b) {
                                mix(a < b ? 1 : 0); mix(a <= b ? 1 : 0); mix(a > b ? 1 : 0);
                                mix(a >= b ? 1 : 0); mix(a == b ? 1 : 0); mix(a != b ? 1 : 0);
                                mix(!(a < b) ? 1 : 0); mix(!(a >= b) ? 1 : 0);
                                mix(Float.floatToIntBits(Math.min(a, b)));
                                mix(Float.floatToIntBits(Math.max(a, b)));
                                mix(Math.round(a)); mix((int)a); mix((long)a);
                                FLOAT_CALLS
                            }
                            static void doubles(double a, double b) {
                                mix(a < b ? 1 : 0); mix(a <= b ? 1 : 0); mix(a > b ? 1 : 0);
                                mix(a >= b ? 1 : 0); mix(a == b ? 1 : 0); mix(a != b ? 1 : 0);
                                mix(!(a > b) ? 1 : 0); mix(!(a <= b) ? 1 : 0);
                                mix(Double.doubleToLongBits(Math.min(a, b)));
                                mix(Double.doubleToLongBits(Math.max(a, b)));
                                mix(Math.round(a)); mix((int)a); mix((long)a);
                                DOUBLE_CALLS
                            }
                            public static void main(String[] args) {
                                int[] floatBits = {0, 0x80000000, 1, 0x80000001, 0x007fffff, 0x00800000,
                                    0x3effffff, 0x3f000000, 0x3f000001, 0xbeffffff, 0xbf000000, 0xbf000001,
                                    0x3fc00000, 0xbfc00000, 0x7f800000, 0xff800000, 0x7fc00000, 0xffc12345,
                                    0x7f800001, 0x7f7fffff, 0xff7fffff, 0x4effffff, 0x4f000000, 0x5f000000};
                                for (int x : floatBits) for (int y : floatBits) {
                                    floats(Float.intBitsToFloat(x), Float.intBitsToFloat(y));
                                    System.out.println("float " + x + ":" + y + ":" + hash);
                                }
                                System.out.println(hash);
                                long[] doubleBits = {0, Long.MIN_VALUE, 1, 0x8000000000000001L,
                                    0x000fffffffffffffL, 0x0010000000000000L, 0x3fdfffffffffffffL,
                                    0x3fe0000000000000L, 0x3fe0000000000001L, 0xbfdfffffffffffffL,
                                    0xbfe0000000000000L, 0xbfe0000000000001L, 0x3ff8000000000000L,
                                    0xbff8000000000000L, 0x7ff0000000000000L, 0xfff0000000000000L,
                                    0x7ff8000000000000L, 0xfff8000000012345L, 0x7ff0000000000001L,
                                    0x7fefffffffffffffL, 0xffefffffffffffffL, 0x432fffffffffffffL,
                                    0x4330000000000000L, 0x4330000000000001L, 0x43e0000000000000L};
                                for (long x : doubleBits) for (long y : doubleBits) {
                                    doubles(Double.longBitsToDouble(x), Double.longBitsToDouble(y));
                                    System.out.println("double " + x + ":" + y + ":" + hash);
                                }
                                System.out.println(hash);
                                long[] integers = {Long.MIN_VALUE, Integer.MIN_VALUE, -1, 0, 1,
                                    Integer.MAX_VALUE, Long.MAX_VALUE};
                                for (long a : integers) for (long b : integers) {
                                    mix(a < b ? 1 : 0); mix(a <= b ? 1 : 0); mix(a > b ? 1 : 0);
                                    mix(a >= b ? 1 : 0); mix(a == b ? 1 : 0); mix(a != b ? 1 : 0);
                                    mix(Math.min(a, b)); mix(Math.max(a, b));
                                    mix(Math.min((int)a, (int)b)); mix(Math.max((int)a, (int)b));
                                }
                                System.out.println(hash);
                                for (int bits : floatBits) {
                                    float a = Float.intBitsToFloat(bits);
                                    sequence = 0;
                                    mix(next(1, a) <= next(2, 1) ? 1 : 0);
                                    mix(next(3, a) > next(4, 1) ? 1 : 0);
                                    mix(Float.floatToIntBits(Math.min(next(5, a), next(6, 1))));
                                    mix(Float.floatToIntBits(Math.max(next(7, a), next(8, 1))));
                                    mix(Float.isFinite(next(9, a)) ? 1 : 0);
                                    mix(Float.isInfinite(next(10, a)) ? 1 : 0);
                                    mix(Float.isNaN(next(11, a)) ? 1 : 0);
                                    if (sequence != 11) throw new IllegalStateException("repeated operand");
                                }
                                System.out.println(hash);
                                long randomBits = 0x123456789abcdefL;
                                for (int n = 0; n < 200000; n++) {
                                    randomBits = randomBits * 6364136223846793005L + 1442695040888963407L;
                                    mix(Math.round(Float.intBitsToFloat((int)(randomBits >>> 32))));
                                    mix(Math.round(Double.longBitsToDouble(randomBits)));
                                    float singleValue = Float.intBitsToFloat((int)(randomBits >>> 32));
                                    double wideValue = Double.longBitsToDouble(randomBits);
                                    mix(Float.isFinite(singleValue) ? 1 : 0);
                                    mix(Float.isInfinite(singleValue) ? 1 : 0);
                                    mix(Float.isNaN(singleValue) ? 1 : 0);
                                    mix(Double.isFinite(wideValue) ? 1 : 0);
                                    mix(Double.isInfinite(wideValue) ? 1 : 0);
                                    mix(Double.isNaN(wideValue) ? 1 : 0);
                                }
                                for (int n = -2048; n <= 2048; n++) {
                                    float half = n + 0.5f;
                                    int single = Float.floatToRawIntBits(half);
                                    long wide = Double.doubleToRawLongBits(n + 0.5);
                                    for (int adjacent = -2; adjacent <= 2; adjacent++) {
                                        mix(Math.round(Float.intBitsToFloat(single + adjacent)));
                                        mix(Math.round(Double.longBitsToDouble(wide + adjacent)));
                                    }
                                }
                                for (int exponent = 0; exponent < 2048; exponent++) {
                                    for (long mantissa : new long[] {0, 1, 0x7ffffffffffffL, 0xfffffffffffffL}) {
                                        long bits = ((long) exponent << 52) | mantissa;
                                        mix(Math.round(Double.longBitsToDouble(bits)));
                                        mix(Math.round(Double.longBitsToDouble(bits | Long.MIN_VALUE)));
                                        int single = ((exponent & 255) << 23) | (int)(mantissa >>> 29);
                                        mix(Math.round(Float.intBitsToFloat(single)));
                                        mix(Math.round(Float.intBitsToFloat(single | Integer.MIN_VALUE)));
                                        for (long signed : new long[] {bits, bits | Long.MIN_VALUE}) {
                                            double value = Double.longBitsToDouble(signed);
                                            mix(Double.isFinite(value) ? 1 : 0);
                                            mix(Double.isInfinite(value) ? 1 : 0);
                                            mix(Double.isNaN(value) ? 1 : 0);
                                        }
                                        for (int signed : new int[] {single, single | Integer.MIN_VALUE}) {
                                            float value = Float.intBitsToFloat(signed);
                                            mix(Float.isFinite(value) ? 1 : 0);
                                            mix(Float.isInfinite(value) ? 1 : 0);
                                            mix(Float.isNaN(value) ? 1 : 0);
                                        }
                                    }
                                }
                                System.out.println(hash);
                            }
                        }
                        """
                        .replace("FLOAT_CALLS", floatCalls)
                        .replace("DOUBLE_CALLS", doubleCalls)
                        + stubs);
        ProcessHarness.javac(source, classes, 25);
        Files.write(classes.resolve("Comparisons.class"), bytecode.toByteArray());
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Floating"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Floating")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var compiled = builder.compile(builder.generate());
        var actual =
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(30));
        if(!expected.equals(actual)) {
            Path evidence =
                    Path.of(System.getProperty("java.io.tmpdir")).resolve("floating-" + buildType);
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("expected.txt"), expected.text());
            Files.writeString(evidence.resolve("actual.txt"), actual.text());
        }
        assertEquals(expected.exitCode(), actual.exitCode(), actual.text());
        var expectedLines = expected.text().lines().toList();
        var actualLines = actual.text().lines().toList();
        assertEquals(expectedLines.size(), actualLines.size(), actual.text());
        for(int index = 0; index < expectedLines.size(); index++) {
            assertEquals(expectedLines.get(index), actualLines.get(index), "Numeric case " + index);
        }
    }
}
