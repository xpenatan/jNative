package com.github.xpenatan.jnative.classlib.java.math;

import java.math.*;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@NativeInclude("jn_classlib_numbers.hpp")
@SubstituteClass("java.math.BigDecimal")
public class BigDecimal extends Number implements Comparable<BigDecimal> {
    private final String digits;
    private final boolean negative;
    private final int scale;

    public BigDecimal(String text) {
        // Declare Java final-field initialization before the native adapter
        // commits the parsed state. No partially parsed state escapes this call.
        this.digits = null;
        this.scale = 0;
        this.negative = false;
        initialize(this, text);
    }

    @NativeImport(value = "jnative::big_decimal_initialize", managed = true, runtimeOnly = true,
            types = {"java/lang/NumberFormatException"},
            fields = {"java/math/BigDecimal.digits:Ljava/lang/String;", "java/math/BigDecimal.scale:I",
                    "java/math/BigDecimal.negative:Z"})
    private static native void initialize(BigDecimal target, String text);

    public int scale() {
        return scale;
    }

    public int precision() {
        return digits.length();
    }

    public int signum() {
        return digits.equals("0") ? 0 : negative ? -1 : 1;
    }

    public int intValue() {
        return (int)longValue();
    }

    public long longValue() {
        return integral(false);
    }

    public int intValueExact() {
        long value = longValueExact();
        if(value != (int)value) throw new ArithmeticException("Overflow");
        return (int)value;
    }

    public long longValueExact() {
        return integral(true);
    }

    private long integral(boolean exact) {
        if(signum() == 0) return 0;
        return integralDigits(digits, negative, scale, exact);
    }

    @NativeImport(value = "jnative::big_decimal_integral", managed = true, runtimeOnly = true)
    private static native long integralDigits(String digits, boolean negative, int scale, boolean exact);

    public float floatValue() {
        return Float.parseFloat(toString());
    }

    public double doubleValue() {
        return Double.parseDouble(toString());
    }

    public int compareTo(BigDecimal other) {
        int sign = signum(), otherSign = other.signum();
        return compareDigits(digits, scale, other.digits, other.scale, sign, otherSign);
    }

    @NativeImport(value = "jnative::big_decimal_compare", managed = true, runtimeOnly = true)
    private static native int compareDigits(String digits, int scale, String otherDigits,
            int otherScale, int sign, int otherSign);

    public boolean equals(Object other) {
        return other instanceof BigDecimal
                && scale == ((BigDecimal)other).scale
                && negative == ((BigDecimal)other).negative
                && digits.equals(((BigDecimal)other).digits);
    }

    public int hashCode() {
        return 31 * (negative ? -digits.hashCode() : digits.hashCode()) + scale;
    }

    public String toString() {
        return render(digits, negative, scale);
    }

    @NativeImport(value = "jnative::big_decimal_string", managed = true, runtimeOnly = true)
    private static native String render(String digits, boolean negative, int scale);
}
