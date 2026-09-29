package java.util;

public final class Objects {

    public static int hash(Object... values) {
        return Arrays.hashCode(values);
    }

    private Objects() {
    }

    public static boolean equals(Object first, Object second) {
        return first == second || (first != null && first.equals(second));
    }

    public static int hashCode(Object value) {
        return value == null ? 0 : value.hashCode();
    }

    public static <T> T requireNonNull(T value) {
        if(value == null) throw new NullPointerException();
        return value;
    }

    public static <T> T requireNonNull(T value, String message) {
        if(value == null) throw new NullPointerException(message);
        return value;
    }

    public static boolean isNull(Object value) {
        return value == null;
    }

    public static boolean nonNull(Object value) {
        return value != null;
    }

    public static String toString(Object value) {
        return String.valueOf(value);
    }

    public static String toString(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
