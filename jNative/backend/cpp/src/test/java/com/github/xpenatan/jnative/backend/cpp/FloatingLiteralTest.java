package com.github.xpenatan.jnative.backend.cpp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FloatingLiteralTest {
    @Test void finiteFloatLiteralsUseCpp11DecimalSyntaxAndRetainSignedZero() {
        assertEquals("0.0f", CppEmitter.floating(0.0f, true));
        assertEquals("-0.0f", CppEmitter.floating(-0.0f, true));
        assertEquals("0.75f", CppEmitter.floating(0.75f, true));
        assertEquals("1.0000001f", CppEmitter.floating(Math.nextUp(1.0f), true));
        assertEquals("3.4028235E38f", CppEmitter.floating(Float.MAX_VALUE, true));
        assertEquals("-3.4028235E38f", CppEmitter.floating(-Float.MAX_VALUE, true));
        assertEquals("1.4E-45f", CppEmitter.floating(Float.MIN_VALUE, true));
        assertEquals("-1.4E-45f", CppEmitter.floating(-Float.MIN_VALUE, true));
    }

    @Test void finiteDoubleLiteralsUseCpp11DecimalSyntaxAndRetainSignedZero() {
        assertEquals("0.0", CppEmitter.floating(0.0, false));
        assertEquals("-0.0", CppEmitter.floating(-0.0, false));
        assertEquals("0.75", CppEmitter.floating(0.75, false));
        assertEquals("1.0000000000000002", CppEmitter.floating(Math.nextUp(1.0), false));
        assertEquals("1.7976931348623157E308", CppEmitter.floating(Double.MAX_VALUE, false));
        assertEquals("-1.7976931348623157E308", CppEmitter.floating(-Double.MAX_VALUE, false));
        assertEquals("4.9E-324", CppEmitter.floating(Double.MIN_VALUE, false));
        assertEquals("-4.9E-324", CppEmitter.floating(-Double.MIN_VALUE, false));
    }

    @Test void specialLiteralsKeepTypedNumericLimitsExpressions() {
        assertEquals("std::numeric_limits<float>::quiet_NaN()", CppEmitter.floating(Float.NaN, true));
        assertEquals("std::numeric_limits<double>::quiet_NaN()", CppEmitter.floating(Double.NaN, false));
        assertEquals("std::numeric_limits<float>::infinity()", CppEmitter.floating(Float.POSITIVE_INFINITY, true));
        assertEquals("-std::numeric_limits<float>::infinity()", CppEmitter.floating(Float.NEGATIVE_INFINITY, true));
        assertEquals("std::numeric_limits<double>::infinity()", CppEmitter.floating(Double.POSITIVE_INFINITY, false));
        assertEquals("-std::numeric_limits<double>::infinity()", CppEmitter.floating(Double.NEGATIVE_INFINITY, false));
    }
}
