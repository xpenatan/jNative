import java.math.BigDecimal;
import java.util.Locale;

public class TextNumberNative {
    enum Colour { RED, BLUE }
    static StringBuilder trace = new StringBuilder();

    static void failure(String label, Runnable action) {
        try { action.run(); System.out.println(label + ":ok"); }
        catch (RuntimeException error) {
            System.gc();
            System.out.println(label + ":" + error.getClass().getName());
        }
    }

    public static void main(String[] args) {
        if (args.length != 0) { callbacks(); return; }
        numbers();
        text();
        decimals();
        builders();
    }

    static void numbers() {
        for (String value : new String[] {"0", "+12", "-2147483648", "2147483647",
                "2147483648", "-9223372036854775808", "9223372036854775807", "+", "",
                "\uff11\uff12", "\u0662\u0664", "7f", "-80000000", "z", null}) {
            for (int radix : new int[] {1, 2, 10, 16, 36, 37}) {
                failure("int", () -> System.out.println(Integer.parseInt(value, radix)));
                failure("long", () -> System.out.println(Long.parseLong(value, radix)));
            }
        }
        for (String value : new String[] {"0", "012", "0x7fffffff", "-0X80000000", "+#ff",
                "09", "0x-1", "-#"}) failure("decode", () -> System.out.println(Integer.decode(value)));
        for (int value : new int[] {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x12345678}) {
            System.out.println("int-text:" + Integer.toString(value) + ":" + Integer.toHexString(value)
                    + ":" + Integer.numberOfLeadingZeros(value));
        }
        for (long value : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE})
            System.out.println("long-text:" + Long.toString(value) + ":" + Long.toUnsignedString(value));
        for (int code : new int[] {-1, '0', '9', 'z', 'Z', 0x660, 0xff10, 0xff21, 0x1d7ce, 0x10ffff})
            for (int radix : new int[] {1, 2, 10, 16, 36, 37})
                System.out.println("digit:" + Character.digit(code, radix));
        System.out.println("enum:" + Enum.valueOf(Colour.class, "BLUE"));
        failure("enum-missing", () -> Enum.valueOf(Colour.class, "missing"));
        failure("enum-null", () -> Enum.valueOf(Colour.class, null));
        failure("float-invalid", () -> Float.parseFloat("invalid"));
        failure("double-invalid", () -> Double.parseDouble("invalid"));
    }

    static void text() {
        String upper = new String(new char[] {(char)0xd801, (char)0xdc00});
        String lower = new String(new char[] {(char)0xd801, (char)0xdc28});
        System.out.println("ignore:" + upper.compareToIgnoreCase(lower) + ":" + "AbC".compareToIgnoreCase("abc"));
        for (String value : new String[] {"", " ", "\u2003\t", "a", "\u0085", upper})
            System.out.println("blank:" + value.isBlank());
        System.out.println("case:" + "Stra\u00dfe".toUpperCase(Locale.ROOT)
                + ":" + "\u0130".toLowerCase(Locale.ROOT)
                + ":" + "I\u0130i".toLowerCase(new Locale("tr")));
        String value = new String("ababa");
        System.out.println("replace:" + value.replace('a', 'x') + ":" + value.replace("aba", "longer")
                + ":" + value.replace("", "-") + ":" + (value.replace("absent", "x") == value));
        System.out.println("repeat:" + "ab".repeat(4) + ":" + (value.repeat(1) == value));
        failure("repeat-negative", () -> value.repeat(-1));
        System.out.println("format:" + String.format(Locale.ROOT, "%s|%S|%b|%c|%d|%x", "test", "word", null, 'Q', 17, 31));
        System.out.println("flags:" + String.format(Locale.ROOT, "%+06d|%-5s|%2$s/%1$s/%<s|%.2f", -12, "x", 1.5));
        System.out.println("precision:" + String.format(Locale.ROOT, "%.2s", "abcd"));
        String large = "a".repeat(4097) + "b";
        System.out.println("large:" + large.replace("aaab", "end").length()
                + ":" + large.toUpperCase(Locale.ROOT).length() + ":" + large.repeat(2).length());
    }

    static void decimals() {
        for (String value : new String[] {"0", "-0.00", "0012.3400", "1e7", "0.000001", "0.0000001",
                "-9223372036854775808", "9223372036854775808", "1.25", "\u0661\u0662.\u0663"}) {
            BigDecimal decimal = new BigDecimal(value);
            System.gc();
            System.out.println("decimal:" + decimal + ":" + decimal.scale() + ":" + decimal.precision()
                    + ":" + decimal.signum() + ":" + decimal.longValue());
            failure("exact", () -> System.out.println(decimal.longValueExact()));
            System.out.println("compare:" + decimal.compareTo(new BigDecimal("12.34")));
        }
        for (String value : new String[] {"", ".", "1e", "1e2147483649", "1e-2147483648", "1.2.3", null})
            failure("decimal-invalid", () -> new BigDecimal(value));
        BigDecimal large = new BigDecimal("1" + "0".repeat(4097) + ".000");
        System.out.println("decimal-large:" + large.precision() + ":" + large.toString().length()
                + ":" + large.longValue() + ":" + large.compareTo(new BigDecimal("1e4097")));
        CheckedDecimal derived = new CheckedDecimal("-0012.3400");
        System.out.println("decimal-constructor:" + derived.observed + ":" + derived);
    }

    static class CheckedDecimal extends BigDecimal {
        final String observed;
        CheckedDecimal(String value) {
            super(value);
            System.gc();
            observed = scale() + ":" + precision() + ":" + signum();
        }
    }

    static void builders() {
        StringBuilder builder = new StringBuilder(1).append("seed").append("ab".repeat(1025));
        builder.append(builder, 1, builder.length());
        builder.insert(3, "insert");
        System.gc();
        System.out.println("builder:" + builder.length() + ":" + builder.substring(2, 19));
        builder.setLength(2);
        builder.setLength(1027);
        System.out.println("extend:" + builder.length() + ":" + (int)builder.charAt(1026));
        CharSequence sequence = new CharSequence() {
            public int length() { return 2051; }
            public char charAt(int index) { System.gc(); return (char)('a' + index % 3); }
            public CharSequence subSequence(int start, int end) { throw new UnsupportedOperationException(); }
        };
        builder.append(sequence, 0, sequence.length());
        System.out.println("sequence:" + builder.length() + ":" + builder.substring(1027, 1036));
        StringBuffer buffer = new StringBuffer("buffer").append("x".repeat(2051));
        System.gc();
        System.out.println("buffer:" + buffer.length() + ":" + buffer.subSequence(2, 9));
        StringBuilder empty = new StringBuilder(0);
        final int[] lengths = {0};
        CharSequence emptySequence = new CharSequence() {
            public int length() { lengths[0]++; System.gc(); return 2; }
            public char charAt(int index) { throw new IllegalStateException("Empty append called charAt"); }
            public CharSequence subSequence(int start, int end) { throw new UnsupportedOperationException(); }
        };
        if(empty.append(emptySequence, 1, 1) != empty) throw new AssertionError();
        empty.append((CharSequence)"text", 2, 2).append((CharSequence)empty, 0, 0)
                .append((CharSequence)null, 4, 4);
        System.out.println("empty-append:" + empty.length() + ":" + empty.capacity() + ":" + lengths[0]);
        failure("empty-range-negative", () -> empty.append(emptySequence, -1, -1));
        failure("empty-range-large", () -> empty.append(emptySequence, 3, 3));
        failure("empty-range-null", () -> empty.append((CharSequence)null, 5, 5));
    }

    static class ProbeNumber extends Number {
        public int intValue() { trace.append('I'); System.gc(); return 15; }
        public long longValue() { trace.append('L'); System.gc(); return 17; }
        public float floatValue() { return 1.5f; }
        public double doubleValue() { trace.append('D'); System.gc(); return 1.5; }
    }

    static class ZeroDecimal extends BigDecimal {
        int calls;
        ZeroDecimal() { super("99.25"); }
        public int signum() { calls++; System.gc(); return 0; }
    }

    static class ExactDecimal extends BigDecimal {
        ExactDecimal() { super("0"); }
        public long longValueExact() { System.gc(); return 19; }
    }

    static void callbacks() {
        StringBuilder builder = new StringBuilder("s");
        CharSequence reentrant = new CharSequence() {
            public int length() { return 3; }
            public char charAt(int index) { builder.append('!'); System.gc(); return (char)('a' + index); }
            public CharSequence subSequence(int start, int end) { throw new UnsupportedOperationException(); }
        };
        builder.append(reentrant, 0, 3);
        System.out.println("reentrant:" + builder);
        StringBuilder partial = new StringBuilder("s");
        CharSequence throwing = new CharSequence() {
            public int length() { return 4; }
            public char charAt(int index) {
                System.gc();
                if (index == 2) throw new IllegalStateException();
                return (char)('a' + index);
            }
            public CharSequence subSequence(int start, int end) { throw new UnsupportedOperationException(); }
        };
        try { partial.append(throwing, 0, 4); } catch (IllegalStateException expected) { }
        System.out.println("partial:" + partial);
        ProbeNumber number = new ProbeNumber();
        System.out.println("number:" + String.format(Locale.ROOT, "%d %x %.2f", number, number, number) + ":" + trace);
        ZeroDecimal zero = new ZeroDecimal();
        System.out.println("decimal-zero:" + zero.longValue() + ":" + zero.calls);
        System.out.println("decimal-exact:" + new ExactDecimal().intValueExact());
        failure("decimal-exponent-limit", () -> new BigDecimal("1e2147483648"));
        failure("decode-null", () -> Integer.decode(null));
    }
}
