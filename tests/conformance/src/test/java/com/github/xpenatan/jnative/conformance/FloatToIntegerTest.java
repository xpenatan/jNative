package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FloatToIntegerTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void conversionsMatchJvmAtSentinelsAndPreserveForeignEnvironment(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Conversions.java");
        Files.writeString(source, """
                import com.github.xpenatan.jnative.interop.NativeExport;
                import com.github.xpenatan.jnative.interop.NativeImport;
                import com.github.xpenatan.jnative.interop.NativeInclude;
                @NativeInclude("jnative_imports.h")
                public class Conversions {
                    @NativeExport("convert_float_int")
                    static int floatInt(float value) { return (int) value; }
                    @NativeExport("convert_float_long")
                    static long floatLong(float value) { return (long) value; }
                    @NativeExport("convert_double_int")
                    static int doubleInt(double value) { return (int) value; }
                    @NativeExport("convert_double_long")
                    static long doubleLong(double value) { return (long) value; }
                    @NativeImport("conversion_environment")
                    static boolean environmentPreserved() { return true; }
                    static void floating(float value) {
                        System.out.println(Float.floatToRawIntBits(value) + ":" + floatInt(value)
                            + ":" + floatLong(value) + ":" + (byte) value + ":" + (short) value + ":" + (int) (char) value);
                    }
                    static void floating(double value) {
                        System.out.println(Double.doubleToRawLongBits(value) + ":" + doubleInt(value)
                            + ":" + doubleLong(value) + ":" + (byte) value + ":" + (short) value + ":" + (int) (char) value);
                    }
                    public static void main(String[] args) {
                        int[] floatBits = {0, 0x80000000, 1, 0x80000001, 0x007fffff, 0x00800000,
                            0x3f000000, 0xbf000000, 0x3fffffff, 0xbfffffff, 0x4effffff, 0x4f000000,
                            0x4f000001, 0xceffffff, 0xcf000000, 0xcf000001, 0x5effffff, 0x5f000000,
                            0x5f000001, 0xdeffffff, 0xdf000000, 0xdf000001, 0x7f7fffff, 0xff7fffff,
                            0x7f800000, 0xff800000, 0x7fc00007, 0xffc00007, 0x7f800001, 0xff800001};
                        for (int bits : floatBits) floating(Float.intBitsToFloat(bits));
                        long[] doubleBits = {0L, 0x8000000000000000L, 1L, 0x8000000000000001L,
                            0x000fffffffffffffL, 0x0010000000000000L, 0x3fe0000000000000L,
                            0xbfe0000000000000L, 0x3fffffffffffffffL, 0xbfffffffffffffffL,
                            0x41dfffffffc00000L, 0x41dfffffffffffffL, 0x41e0000000000000L,
                            0x41e0000000000001L, 0xc1dfffffffffffffL, 0xc1e0000000000000L,
                            0xc1e0000000000001L, 0xc1e0000000100000L, 0x43dfffffffffffffL,
                            0x43e0000000000000L, 0x43e0000000000001L, 0xc3dfffffffffffffL,
                            0xc3e0000000000000L, 0xc3e0000000000001L, 0x7fefffffffffffffL,
                            0xffefffffffffffffL, 0x7ff0000000000000L, 0xfff0000000000000L,
                            0x7ff8000000000007L, 0xfff8000000000007L, 0x7ff0000000000001L, 0xfff0000000000001L};
                        for (long bits : doubleBits) floating(Double.longBitsToDouble(bits));
                        int state = 0x12345678;
                        long wide = 0x123456789abcdef0L;
                        for (int i = 0; i < 256; i++) {
                            state = state * 1664525 + 1013904223;
                            wide = wide * 6364136223846793005L + 1442695040888963407L;
                            floating(Float.intBitsToFloat(state));
                            floating(Double.longBitsToDouble(wide));
                        }
                        System.out.println(environmentPreserved());
                    }
                }
                """);
        Path nativeFile = temporary.resolve("conversion_environment.cpp");
        Files.writeString(nativeFile, """
                #include "jnative_exports.h"
                #include "jn_runtime.hpp"
                #include <cfenv>
                #if defined(__SSE2__) || defined(_M_X64)
                #include <xmmintrin.h>
                #endif
                extern "C" int32_t conversion_environment() {
                    jnative::platform::FloatingState original{};
                    jnative::platform::save_floating(original);
                    std::fesetround(FE_UPWARD);
                    std::feclearexcept(FE_ALL_EXCEPT);
                    std::feraiseexcept(FE_OVERFLOW);
                    const int foreign_exceptions = std::fetestexcept(FE_ALL_EXCEPT);
                #if defined(__SSE2__) || defined(_M_X64)
                    // A callback must mask invalid conversion traps inside Java,
                    // then restore this native caller's masks and sticky flags.
                    const unsigned foreign_control = _mm_getcsr() & ~(1u << 7);
                    _mm_setcsr(foreign_control);
                #endif
                    int32_t integer = 0;
                    int64_t wide = 0;
                    bool correct = convert_float_int(std::numeric_limits<float>::quiet_NaN(), &integer) == JN_OK
                        && integer == 0;
                    correct = correct && convert_double_int(std::numeric_limits<double>::infinity(), &integer) == JN_OK
                        && integer == INT32_MAX;
                    correct = correct && convert_float_long(-std::numeric_limits<float>::infinity(), &wide) == JN_OK
                        && wide == INT64_MIN;
                    correct = correct && convert_double_long(std::numeric_limits<double>::quiet_NaN(), &wide) == JN_OK
                        && wide == 0;
                    correct = correct && std::fegetround() == FE_UPWARD
                        && std::fetestexcept(FE_ALL_EXCEPT) == foreign_exceptions;
                #if defined(__SSE2__) || defined(_M_X64)
                    correct = correct && _mm_getcsr() == foreign_control;
                #endif
                    jnative::platform::restore_floating(original);
                    return correct;
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "Conversions"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("Conversions")
                .nativeFile(nativeFile).buildRoot(temporary.resolve("out")).buildType(buildType);
        var generation = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generation.request().generatedSourcesDirectory(), "Conversions");
        assertTrue(cpp.contains("::jnative::float_to_integer<std::int32_t>"), cpp);
        assertTrue(cpp.contains("::jnative::float_to_integer<std::int64_t>"), cpp);
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(result.executable().toString()), Duration.ofSeconds(60)));
    }
}
