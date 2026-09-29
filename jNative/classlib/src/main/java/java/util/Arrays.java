package java.util;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

@NativeInclude("jn_array_algorithms.hpp")
public final class Arrays {

    public static <T> List<T> asList(T... values) {
        Objects.requireNonNull(values);
        return new AbstractList<T>() {
            public int size() {
                return values.length;
            }

            public T get(int index) {
                return values[index];
            }

            public T set(int index, T value) {
                T old = values[index];
                values[index] = value;
                return old;
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static <T, U> T[] copyOf(U[] values, int length, Class<? extends T[]> type) {
        T[] result = (T[])java.lang.reflect.Array.newInstance(type.getComponentType(), length);
        System.arraycopy(values, 0, result, 0, Math.min(values.length, length));
        return result;
    }

    public static void sort(long[] values) {
        sort(values, 0, values.length);
    }

    @NativeImport(value = "jnative::arrays_sort_j", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void sort(long[] values, int from, int to);

    @NativeImport(value = "jnative::arrays_equal_object", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/lang/Object.equals(Ljava/lang/Object;)Z"},
            callbackReceivers = {-1},
            callbackKinds = {NativeImport.Invocation.VIRTUAL})
    public static native boolean equals(Object[] first, Object[] second);

    @NativeImport(value = "jnative::arrays_hash_object", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/lang/Object.hashCode()I"},
            callbackReceivers = {-1},
            callbackKinds = {NativeImport.Invocation.VIRTUAL})
    public static native int hashCode(Object[] values);

    @NativeImport(value = "jnative::arrays_string_object", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/lang/Object.toString()Ljava/lang/String;"},
            callbackReceivers = {-1},
            callbackKinds = {NativeImport.Invocation.VIRTUAL})
    public static native String toString(Object[] values);

    @NativeImport(value = "jnative::arrays_equals_b", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean equals(byte[] first, byte[] second);

    @NativeImport(value = "jnative::arrays_hash_b", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int hashCode(byte[] values);

    @NativeImport(value = "jnative::arrays_string_b", managed = true, runtimeOnly = true, managesRoots = true)
    public static native String toString(byte[] values);

    @NativeImport(value = "jnative::arrays_copy_range", managed = true, runtimeOnly = true, managesRoots = true)
    public static native byte[] copyOfRange(byte[] values, int from, int to);

    @NativeImport(value = "jnative::arrays_equals_c", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean equals(char[] first, char[] second);

    @NativeImport(value = "jnative::arrays_hash_c", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int hashCode(char[] values);

    @NativeImport(value = "jnative::arrays_string_c", managed = true, runtimeOnly = true, managesRoots = true)
    public static native String toString(char[] values);

    @NativeImport(value = "jnative::arrays_copy_range", managed = true, runtimeOnly = true, managesRoots = true)
    public static native char[] copyOfRange(char[] values, int from, int to);

    @NativeImport(value = "jnative::arrays_equals_s", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean equals(short[] first, short[] second);

    @NativeImport(value = "jnative::arrays_hash_s", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int hashCode(short[] values);

    @NativeImport(value = "jnative::arrays_string_s", managed = true, runtimeOnly = true, managesRoots = true)
    public static native String toString(short[] values);

    @NativeImport(value = "jnative::arrays_copy_range", managed = true, runtimeOnly = true, managesRoots = true)
    public static native short[] copyOfRange(short[] values, int from, int to);

    @NativeImport(value = "jnative::arrays_equals_i", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean equals(int[] first, int[] second);

    @NativeImport(value = "jnative::arrays_hash_i", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int hashCode(int[] values);

    @NativeImport(value = "jnative::arrays_string_i", managed = true, runtimeOnly = true, managesRoots = true)
    public static native String toString(int[] values);

    @NativeImport(value = "jnative::arrays_copy_range", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int[] copyOfRange(int[] values, int from, int to);

    @NativeImport(value = "jnative::arrays_equals_j", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean equals(long[] first, long[] second);

    @NativeImport(value = "jnative::arrays_hash_j", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int hashCode(long[] values);

    @NativeImport(value = "jnative::arrays_string_j", managed = true, runtimeOnly = true, managesRoots = true)
    public static native String toString(long[] values);

    @NativeImport(value = "jnative::arrays_copy_range", managed = true, runtimeOnly = true, managesRoots = true)
    public static native long[] copyOfRange(long[] values, int from, int to);

    @NativeImport(value = "jnative::arrays_equals_f", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean equals(float[] first, float[] second);

    @NativeImport(value = "jnative::arrays_hash_f", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int hashCode(float[] values);

    @NativeImport(value = "jnative::arrays_string_f", managed = true, runtimeOnly = true, managesRoots = true)
    public static native String toString(float[] values);

    @NativeImport(value = "jnative::arrays_copy_range", managed = true, runtimeOnly = true, managesRoots = true)
    public static native float[] copyOfRange(float[] values, int from, int to);

    @NativeImport(value = "jnative::arrays_equals_d", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean equals(double[] first, double[] second);

    @NativeImport(value = "jnative::arrays_hash_d", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int hashCode(double[] values);

    @NativeImport(value = "jnative::arrays_string_d", managed = true, runtimeOnly = true, managesRoots = true)
    public static native String toString(double[] values);

    @NativeImport(value = "jnative::arrays_copy_range", managed = true, runtimeOnly = true, managesRoots = true)
    public static native double[] copyOfRange(double[] values, int from, int to);

    @NativeImport(value = "jnative::arrays_equals_z", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean equals(boolean[] first, boolean[] second);

    @NativeImport(value = "jnative::arrays_hash_z", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int hashCode(boolean[] values);

    @NativeImport(value = "jnative::arrays_string_z", managed = true, runtimeOnly = true, managesRoots = true)
    public static native String toString(boolean[] values);

    @NativeImport(value = "jnative::arrays_copy_range", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean[] copyOfRange(boolean[] values, int from, int to);

    private Arrays() {
    }

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native <T> T[] copyOf(T[] source, int length);

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native byte[] copyOf(byte[] source, int length);

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native boolean[] copyOf(boolean[] source, int length);

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native char[] copyOf(char[] source, int length);

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native short[] copyOf(short[] source, int length);

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int[] copyOf(int[] source, int length);

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native long[] copyOf(long[] source, int length);

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native float[] copyOf(float[] source, int length);

    @NativeImport(value = "jnative::array_copy_of", managed = true, runtimeOnly = true, managesRoots = true)
    public static native double[] copyOf(double[] source, int length);

    public static void sort(Object[] values) {
        sort(values, 0, values.length, null);
    }

    public static <T> void sort(T[] values, Comparator<? super T> comparator) {
        sort(values, 0, values.length, comparator);
    }

    @NativeImport(value = "jnative::arrays_sort_object", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/util/Comparator.compare(Ljava/lang/Object;Ljava/lang/Object;)I", "java/lang/Comparable.compareTo(Ljava/lang/Object;)I"},
            callbackReceivers = {3, -1},
            callbackKinds = {NativeImport.Invocation.INTERFACE, NativeImport.Invocation.INTERFACE})
    public static native <T> void sort(T[] values, int from, int to, Comparator<? super T> comparator);

    @NativeImport(value = "jnative::arrays_search_object", managed = true, runtimeOnly = true, managesRoots = true, callbacksSynchronous = true,
            callbacks = {"java/lang/Comparable.compareTo(Ljava/lang/Object;)I"},
            callbackReceivers = {-1},
            callbackKinds = {NativeImport.Invocation.INTERFACE})
    public static native int binarySearch(Object[] values, Object key);

    @NativeImport(value = "jnative::arrays_search_i", managed = true, runtimeOnly = true, managesRoots = true)
    public static native int binarySearch(int[] values, int key);

    @NativeImport(value = "jnative::arrays_sort_i", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void sort(int[] values);

    public static void fill(Object[] values, Object value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_reference", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(Object[] values, int from, int to, Object value);

    public static void fill(int[] values, int value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_i", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(int[] values, int from, int to, int value);

    public static void fill(long[] values, long value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_j", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(long[] values, int from, int to, long value);

    public static void fill(byte[] values, byte value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_b", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(byte[] values, int from, int to, byte value);

    public static void fill(boolean[] values, boolean value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_z", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(boolean[] values, int from, int to, boolean value);

    public static void fill(char[] values, char value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_c", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(char[] values, int from, int to, char value);

    public static void fill(short[] values, short value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_s", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(short[] values, int from, int to, short value);

    public static void fill(float[] values, float value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_f", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(float[] values, int from, int to, float value);

    public static void fill(double[] values, double value) {
        fill(values, 0, values.length, value);
    }

    @NativeImport(value = "jnative::arrays_fill_d", managed = true, runtimeOnly = true, managesRoots = true)
    public static native void fill(double[] values, int from, int to, double value);
}
