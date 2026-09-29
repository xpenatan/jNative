package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@NativeInclude("jn_classlib.hpp")
@SubstituteClass("java.lang.Math")
public final class Math {
    public static final double PI = 3.14159265358979323846;
    public static final double E = 2.7182818284590452354;

    private Math() {
    }

    @NativeImport(value = "std::sqrt", managed = true, runtimeOnly = true, bounded = true)
    public static native double sqrt(double value);

    @NativeImport(value = "std::ceil", managed = true, runtimeOnly = true, bounded = true)
    public static native double ceil(double value);

    @NativeImport(value = "std::floor", managed = true, runtimeOnly = true, bounded = true)
    public static native double floor(double value);

    @NativeImport(value = "std::sin", managed = true, runtimeOnly = true, bounded = true)
    public static native double sin(double value);

    @NativeImport(value = "std::cos", managed = true, runtimeOnly = true, bounded = true)
    public static native double cos(double value);

    @NativeImport(value = "std::tan", managed = true, runtimeOnly = true, bounded = true)
    public static native double tan(double value);

    @NativeImport(value = "std::asin", managed = true, runtimeOnly = true, bounded = true)
    public static native double asin(double value);

    @NativeImport(value = "std::acos", managed = true, runtimeOnly = true, bounded = true)
    public static native double acos(double value);

    @NativeImport(value = "std::atan", managed = true, runtimeOnly = true, bounded = true)
    public static native double atan(double value);

    @NativeImport(value = "std::atan2", managed = true, runtimeOnly = true, bounded = true)
    public static native double atan2(double y, double x);

    @NativeImport(value = "std::exp", managed = true, runtimeOnly = true, bounded = true)
    public static native double exp(double value);

    @NativeImport(value = "std::log", managed = true, runtimeOnly = true, bounded = true)
    public static native double log(double value);

    @NativeImport(value = "std::pow", managed = true, runtimeOnly = true, bounded = true)
    public static native double pow(double value, double power);

    public static double toRadians(double value) {
        return value * (PI / 180.0);
    }

    public static double toDegrees(double value) {
        return value * (180.0 / PI);
    }

    @NativeImport(value = "jnative::math_round", managed = true, runtimeOnly = true, bounded = true)
    public static int round(float value) {
        if(Float.isNaN(value)) return 0;
        return (int)floor((double)value + 0.5);
    }

    @NativeImport(value = "jnative::math_round", managed = true, runtimeOnly = true, bounded = true)
    public static long round(double value) {
        if(Double.isNaN(value)) return 0;
        if(abs(value) >= 0x1.0p52) return (long)value;
        double whole = floor(value);
        return (long)(value - whole >= 0.5 ? whole + 1 : whole);
    }

    public static int floorDiv(int a, int b) {
        int result = a / b;
        return (a ^ b) < 0 && result * b != a ? result - 1 : result;
    }

    public static long floorDiv(long a, long b) {
        long result = a / b;
        return (a ^ b) < 0 && result * b != a ? result - 1 : result;
    }

    public static long floorDiv(long a, int b) {
        return floorDiv(a, (long)b);
    }

    public static int floorMod(int a, int b) {
        return a - floorDiv(a, b) * b;
    }

    public static long floorMod(long a, long b) {
        return a - floorDiv(a, b) * b;
    }

    public static long addExact(long a, long b) {
        long value = a + b;
        if(((a ^ value) & (b ^ value)) < 0) throw new ArithmeticException("long overflow");
        return value;
    }

    public static long multiplyExact(long a, long b) {
        long value = a * b;
        if((b != 0 && value / b != a) || (a == Long.MIN_VALUE && b == -1))
            throw new ArithmeticException("long overflow");
        return value;
    }

    public static float copySign(float value, float sign) {
        return Float.intBitsToFloat(
                (Float.floatToRawIntBits(value) & 0x7fffffff)
                        | (Float.floatToRawIntBits(sign) & 0x80000000));
    }

    public static float ulp(float value) {
        int exponent = (Float.floatToRawIntBits(value) >>> 23) & 255;
        if(exponent == 255) return abs(value);
        if(exponent <= 23) return Float.intBitsToFloat(exponent == 0 ? 1 : 1 << (exponent - 1));
        return Float.intBitsToFloat((exponent - 23) << 23);
    }

    public static int abs(int value) {
        return value < 0 ? -value : value;
    }

    public static long abs(long value) {
        return value < 0 ? -value : value;
    }

    public static float abs(float value) {
        return value <= 0 ? 0 - value : value;
    }

    public static double abs(double value) {
        return value <= 0 ? 0 - value : value;
    }

    @NativeImport(value = "jnative::math_min", managed = true, runtimeOnly = true, bounded = true)
    public static int min(int a, int b) {
        return a < b ? a : b;
    }

    @NativeImport(value = "jnative::math_min", managed = true, runtimeOnly = true, bounded = true)
    public static long min(long a, long b) {
        return a < b ? a : b;
    }

    @NativeImport(value = "jnative::math_max", managed = true, runtimeOnly = true, bounded = true)
    public static int max(int a, int b) {
        return a > b ? a : b;
    }

    @NativeImport(value = "jnative::math_max", managed = true, runtimeOnly = true, bounded = true)
    public static long max(long a, long b) {
        return a > b ? a : b;
    }

    @NativeImport(value = "jnative::math_min", managed = true, runtimeOnly = true, bounded = true)
    public static float min(float a, float b) {
        if(a != a) return a;
        if(a == 0 && b == 0)
            return Float.intBitsToFloat(Float.floatToRawIntBits(a) | Float.floatToRawIntBits(b));
        return a <= b ? a : b;
    }

    @NativeImport(value = "jnative::math_max", managed = true, runtimeOnly = true, bounded = true)
    public static float max(float a, float b) {
        if(a != a) return a;
        if(a == 0 && b == 0)
            return Float.intBitsToFloat(Float.floatToRawIntBits(a) & Float.floatToRawIntBits(b));
        return a >= b ? a : b;
    }

    @NativeImport(value = "jnative::math_min", managed = true, runtimeOnly = true, bounded = true)
    public static double min(double a, double b) {
        if(a != a) return a;
        if(a == 0 && b == 0)
            return Double.longBitsToDouble(
                    Double.doubleToRawLongBits(a) | Double.doubleToRawLongBits(b));
        return a <= b ? a : b;
    }

    @NativeImport(value = "jnative::math_max", managed = true, runtimeOnly = true, bounded = true)
    public static double max(double a, double b) {
        if(a != a) return a;
        if(a == 0 && b == 0)
            return Double.longBitsToDouble(
                    Double.doubleToRawLongBits(a) & Double.doubleToRawLongBits(b));
        return a >= b ? a : b;
    }

    public static int multiplyExact(int a, int b) {
        long product = (long)a * b;
        if(product != (int)product) throw new ArithmeticException("integer overflow");
        return (int)product;
    }

    public static int addExact(int a, int b) {
        int sum = a + b;
        if(((a ^ sum) & (b ^ sum)) < 0) throw new ArithmeticException("integer overflow");
        return sum;
    }

    public static int toIntExact(long value) {
        if(value != (int)value) throw new ArithmeticException("integer overflow");
        return (int)value;
    }
}
