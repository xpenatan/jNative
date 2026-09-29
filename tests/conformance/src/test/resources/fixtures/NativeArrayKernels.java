import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class NativeArrayKernels {
    static volatile long sink;

    public static void main(String[] args) throws Exception {
        if (args.length != 0 && args[0].equals("bench")) {
            benchmark();
            return;
        }
        conformance();
    }

    static void conformance() throws Exception {
        primitiveBoolean();
        primitiveByte();
        primitiveChar();
        primitiveShort();
        primitiveInt();
        primitiveLong();
        primitiveFloat();
        primitiveDouble();
        floatingEquality();
        references();
        lists();
        collectionStress();
    }

    static void attempt(String label, Runnable operation) {
        try {
            operation.run();
            System.out.println(label + ":ok");
        } catch (RuntimeException error) {
            System.gc();
            System.out.println(label + ":" + error.getClass().getName());
        }
    }

    static void primitiveBoolean() {
        boolean[] pattern = {true, false};
        for (int size : new int[] {0, 1, 2, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 4099}) {
            boolean[] first = new boolean[size];
            for (int i = 0; i < size; i++) first[i] = pattern[i % pattern.length];
            boolean[] second = first.clone();
            System.out.println("boolean:" + size + ":" + Arrays.hashCode(first)
                    + ":" + Arrays.equals(first, second) + ":" + Arrays.equals(first, first)
                    + ":" + Arrays.equals(first, (boolean[]) null)
                    + ":" + Arrays.equals((boolean[]) null, first)
                    + ":" + Arrays.equals((boolean[]) null, (boolean[]) null)
                    + ":" + Arrays.hashCode((boolean[]) null)
                    + ":" + Arrays.equals(first, new boolean[size + 1]));
            if (size > 0) {
                for (int position : new int[] {0, size / 2, size - 1}) {
                    boolean saved = first[position];
                    first[position] = !first[position];
                    System.out.println(Arrays.equals(first, second) + ":" + Arrays.hashCode(first));
                    first[position] = saved;
                }
            }
            Arrays.fill(first, true);
            System.out.println("boolean-full:" + rawBoolean(first));
            Arrays.fill(first, 0, size, pattern[0]);
            Arrays.fill(first, size / 3, size - size / 3, true);
            System.gc();
            System.out.println("boolean-range:" + rawBoolean(first));
            for (int[] range : new int[][] {{2, 1}, {-1, 0}, {-1, -2}, {0, size + 1},
                    {size + 1, size + 1}, {0, 0}, {size, size},
                    {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
                attempt("boolean-bounds", () -> Arrays.fill(first, range[0], range[1], true));
            }
            System.out.println("boolean-after-bounds:" + rawBoolean(first));
        }
        attempt("boolean-null-full", () -> Arrays.fill((boolean[]) null, true));
        attempt("boolean-null-reversed", () -> Arrays.fill((boolean[]) null, 2, 1, true));
        attempt("boolean-null-negative", () -> Arrays.fill((boolean[]) null, -1, 0, true));
        attempt("boolean-null-empty", () -> Arrays.fill((boolean[]) null, 0, 0, true));
    }

    static long rawBoolean(boolean[] array) {
        long checksum = 1;
        for (boolean value : array) checksum = checksum * 31 + (value ? 1231 : 1237);
        return checksum;
    }

    static void primitiveByte() {
        byte[] pattern = {Byte.MIN_VALUE, Byte.MAX_VALUE, 0, -1, 1};
        for (int size : new int[] {0, 1, 2, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 4099}) {
            byte[] first = new byte[size];
            for (int i = 0; i < size; i++) first[i] = pattern[i % pattern.length];
            byte[] second = first.clone();
            System.out.println("byte:" + size + ":" + Arrays.hashCode(first)
                    + ":" + Arrays.equals(first, second) + ":" + Arrays.equals(first, first)
                    + ":" + Arrays.equals(first, (byte[]) null)
                    + ":" + Arrays.equals((byte[]) null, first)
                    + ":" + Arrays.equals((byte[]) null, (byte[]) null)
                    + ":" + Arrays.hashCode((byte[]) null)
                    + ":" + Arrays.equals(first, new byte[size + 1]));
            if (size > 0) {
                for (int position : new int[] {0, size / 2, size - 1}) {
                    byte saved = first[position];
                    first[position] = (byte)27;
                    System.out.println(Arrays.equals(first, second) + ":" + Arrays.hashCode(first));
                    first[position] = saved;
                }
            }
            Arrays.fill(first, (byte)-37);
            System.out.println("byte-full:" + rawByte(first));
            Arrays.fill(first, 0, size, pattern[0]);
            Arrays.fill(first, size / 3, size - size / 3, (byte)-37);
            System.gc();
            System.out.println("byte-range:" + rawByte(first));
            for (int[] range : new int[][] {{2, 1}, {-1, 0}, {-1, -2}, {0, size + 1},
                    {size + 1, size + 1}, {0, 0}, {size, size},
                    {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
                attempt("byte-bounds", () -> Arrays.fill(first, range[0], range[1], (byte)-37));
            }
            System.out.println("byte-after-bounds:" + rawByte(first));
        }
        attempt("byte-null-full", () -> Arrays.fill((byte[]) null, (byte)-37));
        attempt("byte-null-reversed", () -> Arrays.fill((byte[]) null, 2, 1, (byte)-37));
        attempt("byte-null-negative", () -> Arrays.fill((byte[]) null, -1, 0, (byte)-37));
        attempt("byte-null-empty", () -> Arrays.fill((byte[]) null, 0, 0, (byte)-37));
    }

    static long rawByte(byte[] array) {
        long checksum = 1;
        for (byte value : array) checksum = checksum * 31 + (value);
        return checksum;
    }

    static void primitiveChar() {
        char[] pattern = {0, 65535, 32768, 1, 'x'};
        for (int size : new int[] {0, 1, 2, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 4099}) {
            char[] first = new char[size];
            for (int i = 0; i < size; i++) first[i] = pattern[i % pattern.length];
            char[] second = first.clone();
            System.out.println("char:" + size + ":" + Arrays.hashCode(first)
                    + ":" + Arrays.equals(first, second) + ":" + Arrays.equals(first, first)
                    + ":" + Arrays.equals(first, (char[]) null)
                    + ":" + Arrays.equals((char[]) null, first)
                    + ":" + Arrays.equals((char[]) null, (char[]) null)
                    + ":" + Arrays.hashCode((char[]) null)
                    + ":" + Arrays.equals(first, new char[size + 1]));
            if (size > 0) {
                for (int position : new int[] {0, size / 2, size - 1}) {
                    char saved = first[position];
                    first[position] = (char)27;
                    System.out.println(Arrays.equals(first, second) + ":" + Arrays.hashCode(first));
                    first[position] = saved;
                }
            }
            Arrays.fill(first, (char)65534);
            System.out.println("char-full:" + rawChar(first));
            Arrays.fill(first, 0, size, pattern[0]);
            Arrays.fill(first, size / 3, size - size / 3, (char)65534);
            System.gc();
            System.out.println("char-range:" + rawChar(first));
            for (int[] range : new int[][] {{2, 1}, {-1, 0}, {-1, -2}, {0, size + 1},
                    {size + 1, size + 1}, {0, 0}, {size, size},
                    {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
                attempt("char-bounds", () -> Arrays.fill(first, range[0], range[1], (char)65534));
            }
            System.out.println("char-after-bounds:" + rawChar(first));
        }
        attempt("char-null-full", () -> Arrays.fill((char[]) null, (char)65534));
        attempt("char-null-reversed", () -> Arrays.fill((char[]) null, 2, 1, (char)65534));
        attempt("char-null-negative", () -> Arrays.fill((char[]) null, -1, 0, (char)65534));
        attempt("char-null-empty", () -> Arrays.fill((char[]) null, 0, 0, (char)65534));
    }

    static long rawChar(char[] array) {
        long checksum = 1;
        for (char value : array) checksum = checksum * 31 + (value);
        return checksum;
    }

    static void primitiveShort() {
        short[] pattern = {Short.MIN_VALUE, Short.MAX_VALUE, 0, -1, 1};
        for (int size : new int[] {0, 1, 2, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 4099}) {
            short[] first = new short[size];
            for (int i = 0; i < size; i++) first[i] = pattern[i % pattern.length];
            short[] second = first.clone();
            System.out.println("short:" + size + ":" + Arrays.hashCode(first)
                    + ":" + Arrays.equals(first, second) + ":" + Arrays.equals(first, first)
                    + ":" + Arrays.equals(first, (short[]) null)
                    + ":" + Arrays.equals((short[]) null, first)
                    + ":" + Arrays.equals((short[]) null, (short[]) null)
                    + ":" + Arrays.hashCode((short[]) null)
                    + ":" + Arrays.equals(first, new short[size + 1]));
            if (size > 0) {
                for (int position : new int[] {0, size / 2, size - 1}) {
                    short saved = first[position];
                    first[position] = (short)27;
                    System.out.println(Arrays.equals(first, second) + ":" + Arrays.hashCode(first));
                    first[position] = saved;
                }
            }
            Arrays.fill(first, (short)-30001);
            System.out.println("short-full:" + rawShort(first));
            Arrays.fill(first, 0, size, pattern[0]);
            Arrays.fill(first, size / 3, size - size / 3, (short)-30001);
            System.gc();
            System.out.println("short-range:" + rawShort(first));
            for (int[] range : new int[][] {{2, 1}, {-1, 0}, {-1, -2}, {0, size + 1},
                    {size + 1, size + 1}, {0, 0}, {size, size},
                    {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
                attempt("short-bounds", () -> Arrays.fill(first, range[0], range[1], (short)-30001));
            }
            System.out.println("short-after-bounds:" + rawShort(first));
        }
        attempt("short-null-full", () -> Arrays.fill((short[]) null, (short)-30001));
        attempt("short-null-reversed", () -> Arrays.fill((short[]) null, 2, 1, (short)-30001));
        attempt("short-null-negative", () -> Arrays.fill((short[]) null, -1, 0, (short)-30001));
        attempt("short-null-empty", () -> Arrays.fill((short[]) null, 0, 0, (short)-30001));
    }

    static long rawShort(short[] array) {
        long checksum = 1;
        for (short value : array) checksum = checksum * 31 + (value);
        return checksum;
    }

    static void primitiveInt() {
        int[] pattern = {Integer.MIN_VALUE, Integer.MAX_VALUE, 0, -1, 1, 0x12345678};
        for (int size : new int[] {0, 1, 2, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 4099}) {
            int[] first = new int[size];
            for (int i = 0; i < size; i++) first[i] = pattern[i % pattern.length];
            int[] second = first.clone();
            System.out.println("int:" + size + ":" + Arrays.hashCode(first)
                    + ":" + Arrays.equals(first, second) + ":" + Arrays.equals(first, first)
                    + ":" + Arrays.equals(first, (int[]) null)
                    + ":" + Arrays.equals((int[]) null, first)
                    + ":" + Arrays.equals((int[]) null, (int[]) null)
                    + ":" + Arrays.hashCode((int[]) null)
                    + ":" + Arrays.equals(first, new int[size + 1]));
            if (size > 0) {
                for (int position : new int[] {0, size / 2, size - 1}) {
                    int saved = first[position];
                    first[position] = 27;
                    System.out.println(Arrays.equals(first, second) + ":" + Arrays.hashCode(first));
                    first[position] = saved;
                }
            }
            Arrays.fill(first, Integer.MIN_VALUE + 7);
            System.out.println("int-full:" + rawInt(first));
            Arrays.fill(first, 0, size, pattern[0]);
            Arrays.fill(first, size / 3, size - size / 3, Integer.MIN_VALUE + 7);
            System.gc();
            System.out.println("int-range:" + rawInt(first));
            for (int[] range : new int[][] {{2, 1}, {-1, 0}, {-1, -2}, {0, size + 1},
                    {size + 1, size + 1}, {0, 0}, {size, size},
                    {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
                attempt("int-bounds", () -> Arrays.fill(first, range[0], range[1], Integer.MIN_VALUE + 7));
            }
            System.out.println("int-after-bounds:" + rawInt(first));
        }
        attempt("int-null-full", () -> Arrays.fill((int[]) null, Integer.MIN_VALUE + 7));
        attempt("int-null-reversed", () -> Arrays.fill((int[]) null, 2, 1, Integer.MIN_VALUE + 7));
        attempt("int-null-negative", () -> Arrays.fill((int[]) null, -1, 0, Integer.MIN_VALUE + 7));
        attempt("int-null-empty", () -> Arrays.fill((int[]) null, 0, 0, Integer.MIN_VALUE + 7));
    }

    static long rawInt(int[] array) {
        long checksum = 1;
        for (int value : array) checksum = checksum * 31 + (value);
        return checksum;
    }

    static void primitiveLong() {
        long[] pattern = {Long.MIN_VALUE, Long.MAX_VALUE, 0, -1, 1, 0x123456789abcdefL};
        for (int size : new int[] {0, 1, 2, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 4099}) {
            long[] first = new long[size];
            for (int i = 0; i < size; i++) first[i] = pattern[i % pattern.length];
            long[] second = first.clone();
            System.out.println("long:" + size + ":" + Arrays.hashCode(first)
                    + ":" + Arrays.equals(first, second) + ":" + Arrays.equals(first, first)
                    + ":" + Arrays.equals(first, (long[]) null)
                    + ":" + Arrays.equals((long[]) null, first)
                    + ":" + Arrays.equals((long[]) null, (long[]) null)
                    + ":" + Arrays.hashCode((long[]) null)
                    + ":" + Arrays.equals(first, new long[size + 1]));
            if (size > 0) {
                for (int position : new int[] {0, size / 2, size - 1}) {
                    long saved = first[position];
                    first[position] = 27L;
                    System.out.println(Arrays.equals(first, second) + ":" + Arrays.hashCode(first));
                    first[position] = saved;
                }
            }
            Arrays.fill(first, Long.MIN_VALUE + 7);
            System.out.println("long-full:" + rawLong(first));
            Arrays.fill(first, 0, size, pattern[0]);
            Arrays.fill(first, size / 3, size - size / 3, Long.MIN_VALUE + 7);
            System.gc();
            System.out.println("long-range:" + rawLong(first));
            for (int[] range : new int[][] {{2, 1}, {-1, 0}, {-1, -2}, {0, size + 1},
                    {size + 1, size + 1}, {0, 0}, {size, size},
                    {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
                attempt("long-bounds", () -> Arrays.fill(first, range[0], range[1], Long.MIN_VALUE + 7));
            }
            System.out.println("long-after-bounds:" + rawLong(first));
        }
        attempt("long-null-full", () -> Arrays.fill((long[]) null, Long.MIN_VALUE + 7));
        attempt("long-null-reversed", () -> Arrays.fill((long[]) null, 2, 1, Long.MIN_VALUE + 7));
        attempt("long-null-negative", () -> Arrays.fill((long[]) null, -1, 0, Long.MIN_VALUE + 7));
        attempt("long-null-empty", () -> Arrays.fill((long[]) null, 0, 0, Long.MIN_VALUE + 7));
    }

    static long rawLong(long[] array) {
        long checksum = 1;
        for (long value : array) checksum = checksum * 31 + (value);
        return checksum;
    }

    static void primitiveFloat() {
        float[] pattern = {Float.intBitsToFloat(0x7fc12345), Float.intBitsToFloat(0xffc54321), -0.0f, 0.0f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.MIN_VALUE, Float.MAX_VALUE};
        for (int size : new int[] {0, 1, 2, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 4099}) {
            float[] first = new float[size];
            for (int i = 0; i < size; i++) first[i] = pattern[i % pattern.length];
            float[] second = first.clone();
            System.out.println("float:" + size + ":" + Arrays.hashCode(first)
                    + ":" + Arrays.equals(first, second) + ":" + Arrays.equals(first, first)
                    + ":" + Arrays.equals(first, (float[]) null)
                    + ":" + Arrays.equals((float[]) null, first)
                    + ":" + Arrays.equals((float[]) null, (float[]) null)
                    + ":" + Arrays.hashCode((float[]) null)
                    + ":" + Arrays.equals(first, new float[size + 1]));
            if (size > 0) {
                for (int position : new int[] {0, size / 2, size - 1}) {
                    float saved = first[position];
                    first[position] = 27.0f;
                    System.out.println(Arrays.equals(first, second) + ":" + Arrays.hashCode(first));
                    first[position] = saved;
                }
            }
            Arrays.fill(first, Float.intBitsToFloat(0xffc98765));
            System.out.println("float-full:" + rawFloat(first));
            Arrays.fill(first, 0, size, pattern[0]);
            Arrays.fill(first, size / 3, size - size / 3, Float.intBitsToFloat(0xffc98765));
            System.gc();
            System.out.println("float-range:" + rawFloat(first));
            for (int[] range : new int[][] {{2, 1}, {-1, 0}, {-1, -2}, {0, size + 1},
                    {size + 1, size + 1}, {0, 0}, {size, size},
                    {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
                attempt("float-bounds", () -> Arrays.fill(first, range[0], range[1], Float.intBitsToFloat(0xffc98765)));
            }
            System.out.println("float-after-bounds:" + rawFloat(first));
        }
        attempt("float-null-full", () -> Arrays.fill((float[]) null, Float.intBitsToFloat(0xffc98765)));
        attempt("float-null-reversed", () -> Arrays.fill((float[]) null, 2, 1, Float.intBitsToFloat(0xffc98765)));
        attempt("float-null-negative", () -> Arrays.fill((float[]) null, -1, 0, Float.intBitsToFloat(0xffc98765)));
        attempt("float-null-empty", () -> Arrays.fill((float[]) null, 0, 0, Float.intBitsToFloat(0xffc98765)));
    }

    static long rawFloat(float[] array) {
        long checksum = 1;
        for (float value : array) checksum = checksum * 31 + (Float.floatToRawIntBits(value));
        return checksum;
    }

    static void primitiveDouble() {
        double[] pattern = {Double.longBitsToDouble(0x7ff8123456789abcL), Double.longBitsToDouble(0xfff8543212345678L), -0.0, 0.0, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.MIN_VALUE, Double.MAX_VALUE};
        for (int size : new int[] {0, 1, 2, 31, 32, 33, 63, 64, 65, 1023, 1024, 1025, 4099}) {
            double[] first = new double[size];
            for (int i = 0; i < size; i++) first[i] = pattern[i % pattern.length];
            double[] second = first.clone();
            System.out.println("double:" + size + ":" + Arrays.hashCode(first)
                    + ":" + Arrays.equals(first, second) + ":" + Arrays.equals(first, first)
                    + ":" + Arrays.equals(first, (double[]) null)
                    + ":" + Arrays.equals((double[]) null, first)
                    + ":" + Arrays.equals((double[]) null, (double[]) null)
                    + ":" + Arrays.hashCode((double[]) null)
                    + ":" + Arrays.equals(first, new double[size + 1]));
            if (size > 0) {
                for (int position : new int[] {0, size / 2, size - 1}) {
                    double saved = first[position];
                    first[position] = 27.0;
                    System.out.println(Arrays.equals(first, second) + ":" + Arrays.hashCode(first));
                    first[position] = saved;
                }
            }
            Arrays.fill(first, Double.longBitsToDouble(0xfff8987654321234L));
            System.out.println("double-full:" + rawDouble(first));
            Arrays.fill(first, 0, size, pattern[0]);
            Arrays.fill(first, size / 3, size - size / 3, Double.longBitsToDouble(0xfff8987654321234L));
            System.gc();
            System.out.println("double-range:" + rawDouble(first));
            for (int[] range : new int[][] {{2, 1}, {-1, 0}, {-1, -2}, {0, size + 1},
                    {size + 1, size + 1}, {0, 0}, {size, size},
                    {Integer.MAX_VALUE, Integer.MIN_VALUE}, {Integer.MAX_VALUE, Integer.MAX_VALUE}}) {
                attempt("double-bounds", () -> Arrays.fill(first, range[0], range[1], Double.longBitsToDouble(0xfff8987654321234L)));
            }
            System.out.println("double-after-bounds:" + rawDouble(first));
        }
        attempt("double-null-full", () -> Arrays.fill((double[]) null, Double.longBitsToDouble(0xfff8987654321234L)));
        attempt("double-null-reversed", () -> Arrays.fill((double[]) null, 2, 1, Double.longBitsToDouble(0xfff8987654321234L)));
        attempt("double-null-negative", () -> Arrays.fill((double[]) null, -1, 0, Double.longBitsToDouble(0xfff8987654321234L)));
        attempt("double-null-empty", () -> Arrays.fill((double[]) null, 0, 0, Double.longBitsToDouble(0xfff8987654321234L)));
    }

    static long rawDouble(double[] array) {
        long checksum = 1;
        for (double value : array) checksum = checksum * 31 + (Double.doubleToRawLongBits(value));
        return checksum;
    }

    static void floatingEquality() {
        float[] floats = {Float.intBitsToFloat(0x7fc00001), -0.0f};
        float[] otherFloats = {Float.intBitsToFloat(0xffc12345), -0.0f};
        double[] doubles = {Double.longBitsToDouble(0x7ff8000000000001L), -0.0};
        double[] otherDoubles = {Double.longBitsToDouble(0xfff8123456789abcL), -0.0};
        System.out.println("nan-float:" + Arrays.equals(floats, otherFloats)
                + ":" + Arrays.hashCode(floats) + ":" + Arrays.hashCode(otherFloats));
        System.out.println("nan-double:" + Arrays.equals(doubles, otherDoubles)
                + ":" + Arrays.hashCode(doubles) + ":" + Arrays.hashCode(otherDoubles));
        otherFloats[1] = 0.0f;
        otherDoubles[1] = 0.0;
        System.out.println("zero-float:" + Arrays.equals(floats, otherFloats)
                + ":" + Arrays.hashCode(otherFloats));
        System.out.println("zero-double:" + Arrays.equals(doubles, otherDoubles)
                + ":" + Arrays.hashCode(otherDoubles));
        Arrays.fill(otherFloats, 0, otherFloats.length, -0.0f);
        Arrays.fill(otherDoubles, 0, otherDoubles.length, -0.0);
        System.out.println("fill-zero-bits:" + rawFloat(otherFloats) + ":" + rawDouble(otherDoubles));
    }

    static void references() {
        Object incompatible = new Object();
        Object[] narrow = new String[] {"a", "b", "c", "d"};
        attempt("reference-empty-incompatible", () -> Arrays.fill(narrow, 2, 2, incompatible));
        attempt("reference-incompatible", () -> Arrays.fill(narrow, 1, 3, incompatible));
        System.out.println(Arrays.toString(narrow));
        Arrays.fill(narrow, 1, 3, null);
        System.gc();
        System.out.println(Arrays.toString(narrow));
        Arrays.fill(narrow, null);
        System.gc();
        System.out.println(Arrays.toString(narrow));
        Arrays.fill(narrow, "refilled");
        attempt("reference-reversed", () -> Arrays.fill(narrow, 2, 1, incompatible));
        attempt("reference-negative", () -> Arrays.fill(narrow, -1, 0, incompatible));
        attempt("reference-beyond", () -> Arrays.fill(narrow, 4, 5, incompatible));
        attempt("reference-end-empty", () -> Arrays.fill(narrow, 4, 4, incompatible));
        attempt("reference-null-reversed", () -> Arrays.fill((Object[]) null, 2, 1, incompatible));
        attempt("reference-null-empty", () -> Arrays.fill((Object[]) null, 0, 0, null));
        attempt("reference-null-full", () -> Arrays.fill((Object[]) null, null));
        System.out.println(Arrays.toString(narrow));

        int[] numbers = new int[4099];
        String[] strings = new String[4099];
        String[] labels = {"a", "b", "c", "d", "e"};
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = i * 17 - 31;
            strings[i] = new String(labels[i % labels.length]);
        }
        System.arraycopy(numbers, 0, numbers, 3, 4096);
        System.arraycopy(strings, 0, strings, 3, 4096);
        System.gc();
        System.out.println("copy-right:" + Arrays.hashCode(numbers) + ":" + Arrays.hashCode(strings));
        System.arraycopy(numbers, 3, numbers, 0, 4096);
        System.arraycopy(strings, 3, strings, 0, 4096);
        System.gc();
        System.out.println("copy-left:" + Arrays.hashCode(numbers) + ":" + Arrays.hashCode(strings));

        Object[] source = new Object[3075];
        String[] target = new String[3077];
        Arrays.fill(source, "written");
        Arrays.fill(target, "original");
        source[1537] = incompatible;
        attempt("copy-partial", () -> System.arraycopy(source, 0, target, 1, source.length));
        System.gc();
        int prefix = 0;
        for (String value : target) if ("written".equals(value)) prefix++;
        System.out.println("copy-prefix:" + prefix + ":" + target[0] + ":" + target[1537]
                + ":" + target[1538] + ":" + target[3076] + ":" + Arrays.hashCode(target));
        Object[] wide = new Object[4099];
        System.arraycopy(strings, 0, wide, 0, strings.length);
        System.out.println("copy-covariant:" + Arrays.equals(strings, wide));
        attempt("copy-empty-covariant", () -> System.arraycopy(new Object[0], 0, new String[0], 0, 0));
        Arrays.fill(wide, 1023, 3076, null);
        System.gc();
        System.out.println("reference-large-clear:" + Arrays.hashCode(wide)
                + ":" + wide[1022] + ":" + wide[1023] + ":" + wide[3075] + ":" + wide[3076]);
    }

    static class TraceList extends ArrayList<String> {
        TraceList() { super(); }
        TraceList(int capacity) { super(capacity); }
        int modifications() { return modCount; }
    }

    static void listState(String label, TraceList list) {
        System.out.println(label + ":" + list.size() + ":" + list.modifications()
                + ":" + list.hashCode());
    }

    static void lists() {
        TraceList defaults = new TraceList();
        defaults.ensureCapacity(10);
        listState("default-capacity", defaults);
        defaults.trimToSize();
        defaults.clear();
        defaults.clear();
        listState("empty-trim-clear", defaults);
        TraceList list = new TraceList(0);
        for (int i = 0; i < 4099; i++) list.add(new String(i % 2 == 0 ? "even" : "odd"));
        System.gc();
        listState("grown", list);
        Iterator<String> unchanged = list.iterator();
        list.ensureCapacity(1);
        System.out.println("capacity-noop:" + unchanged.next());
        Iterator<String> enlarged = list.iterator();
        list.ensureCapacity(8192);
        attempt("capacity-iterator", () -> enlarged.next());
        listState("ensure", list);
        Iterator<String> trimmed = list.iterator();
        list.trimToSize();
        attempt("trim-iterator", () -> trimmed.next());
        list.trimToSize();
        listState("trim", list);
        list.add(0, "front");
        list.add(1024, "middle");
        list.add(list.size(), "end");
        System.gc();
        listState("insert", list);
        System.out.println("removed:" + list.remove(0) + ":" + list.remove(1023)
                + ":" + list.remove(list.size() - 1));
        System.gc();
        listState("remove", list);
        Iterator<String> setIterator = list.iterator();
        System.out.println("set:" + list.set(0, "set") + ":" + setIterator.next());
        int before = list.modifications();
        attempt("list-add-negative", () -> list.add(-1, "bad"));
        attempt("list-add-beyond", () -> list.add(list.size() + 1, "bad"));
        attempt("list-remove-negative", () -> list.remove(-1));
        attempt("list-remove-beyond", () -> list.remove(list.size()));
        System.out.println("failed-modification:" + (before == list.modifications()));

        Iterator<String> cursor = list.iterator();
        attempt("iterator-remove-before", () -> cursor.remove());
        System.out.println("iterator-first:" + cursor.next());
        cursor.remove();
        attempt("iterator-remove-twice", () -> cursor.remove());
        System.out.println("iterator-after-remove:" + cursor.next());
        Iterator<String> stale = list.iterator();
        stale.next();
        list.add("late");
        attempt("iterator-concurrent-next", () -> stale.next());
        attempt("iterator-concurrent-remove", () -> stale.remove());
        listState("cursor", list);
        Iterator<String> cleared = list.iterator();
        list.clear();
        System.gc();
        attempt("clear-iterator", () -> cleared.next());
        listState("clear", list);
        list.add(null);
        list.add("after-clear");
        list.trimToSize();
        System.gc();
        System.out.println("reused:" + Arrays.toString(list.toArray()));
        list.clear();
        Iterator<String> exhausted = list.iterator();
        attempt("iterator-empty", () -> exhausted.next());
        list.clear();
        attempt("iterator-empty-clear", () -> exhausted.next());
        listState("final", list);
    }

    static volatile boolean collectorStarted;
    static volatile int collections;

    static void collectionStress() throws Exception {
        int[] first = new int[8193];
        int[] second = new int[8193];
        String[] strings = new String[8193];
        TraceList list = new TraceList(8194);
        Thread collector = new Thread(() -> {
            collectorStarted = true;
            for (int i = 0; i < 64; i++) {
                System.gc();
                collections++;
            }
        });
        collector.start();
        while (!collectorStarted) Thread.yield();
        long checksum = 0;
        for (int round = 0; round < 32; round++) {
            Arrays.fill(first, 1, first.length - 1, round);
            Arrays.fill(second, 1, second.length - 1, round);
            checksum += Arrays.equals(first, second) ? Arrays.hashCode(first) : 17;
            Arrays.fill(strings, new String(new char[] {'k', 'e', 'p', 't'}));
            System.arraycopy(strings, 0, strings, 1, strings.length - 1);
            for (int i = 0; i < strings.length; i++) list.add(strings[i]);
            list.add(0, "front");
            list.remove(0);
            list.trimToSize();
            checksum += list.size() + list.get(4096).length();
            list.clear();
            Arrays.fill(strings, 0, strings.length, null);
        }
        collector.join();
        System.out.println("collector-progress:" + (collections == 64) + ":" + checksum
                + ":" + strings[0] + ":" + strings[strings.length - 1] + ":" + list.size());
    }

    static void row(boolean report, String operation, int size, int iterations,
                    long elapsed, long checksum) {
        sink = checksum;
        if (report) System.out.println("BENCH," + operation + "," + size + ","
                + iterations + "," + elapsed + "," + checksum);
    }

    static void benchmark() {
        System.out.println("BENCH_HEADER,operation,size,iterations,elapsed_ns,checksum");
        for (int size : new int[] {16, 256, 4096}) {
            benchmarkArrays(size, false);
            benchmarkLists(size, false);
            for (int sample = 0; sample < 3; sample++) {
                benchmarkArrays(size, true);
                benchmarkLists(size, true);
            }
        }
    }

    static void benchmarkArrays(int size, boolean report) {
        int rounds = size == 16 ? 100000 : size == 256 ? 20000 : 2000;
        int[] first = new int[size];
        int[] second = new int[size];
        Arrays.fill(first, 7);
        Arrays.fill(second, 7);
        long checksum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            Arrays.fill(first, 0, size, i);
            checksum += first[i % size];
        }
        row(report, "int-fill", size, rounds, System.nanoTime() - start, checksum);
        Arrays.fill(first, 7);
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            // Change both arrays so the equality result cannot be loop invariant.
            first[0] = i;
            second[0] = i;
            checksum += Arrays.equals(first, second) ? 1 : 0;
        }
        row(report, "int-equals", size, rounds, System.nanoTime() - start, checksum);
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            first[0] = i;
            checksum += Arrays.hashCode(first);
        }
        row(report, "int-hash", size, rounds, System.nanoTime() - start, checksum);
    }

    static void benchmarkLists(int size, boolean report) {
        Integer token = Integer.valueOf(7);
        ArrayList<Integer> list = new ArrayList<Integer>(size + 1);
        for (int i = 0; i < size; i++) list.add(token);
        int rounds = size == 16 ? 50000 : size == 256 ? 10000 : 1000;
        long checksum = 0;
        long start = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            list.add(0, token);
            checksum += list.remove(0).intValue();
        }
        row(report, "list-shift", size, rounds, System.nanoTime() - start, checksum);

        // Allocate and populate outside each timed burst. Values are shared tokens.
        ArrayList<ArrayList<Integer>> batch = new ArrayList<ArrayList<Integer>>(128);
        for (int i = 0; i < 128; i++) {
            ArrayList<Integer> entry = new ArrayList<Integer>(size);
            for (int j = 0; j < size; j++) entry.add(token);
            batch.add(entry);
        }
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < batch.size(); i++) {
            ArrayList<Integer> entry = batch.get(i);
            entry.add(token);
            checksum += entry.size();
        }
        row(report, "list-grow", size, batch.size(), System.nanoTime() - start, checksum);
        checksum = 0;
        start = System.nanoTime();
        for (int i = 0; i < batch.size(); i++) {
            ArrayList<Integer> entry = batch.get(i);
            entry.clear();
            checksum += entry.size();
        }
        row(report, "list-clear", size, batch.size(), System.nanoTime() - start, checksum);
    }
}
