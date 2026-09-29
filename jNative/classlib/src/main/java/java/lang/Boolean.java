package java.lang;

public final class Boolean implements Comparable<Boolean>, java.io.Serializable {
    public static final Boolean TRUE = new Boolean(true), FALSE = new Boolean(false);
    private final boolean value;

    public static boolean parseBoolean(String value) {
        return value != null && value.equalsIgnoreCase("true");
    }

    public static boolean getBoolean(String name) {
        try {
            return parseBoolean(System.getProperty(name));
        } catch(IllegalArgumentException | NullPointerException ignored) {
            return false;
        }
    }

    public Boolean(boolean value) {
        this.value = value;
    }

    public static Boolean valueOf(boolean value) {
        return value ? TRUE : FALSE;
    }

    public boolean booleanValue() {
        return value;
    }

    public boolean equals(Object other) {
        return other instanceof Boolean && ((Boolean)other).value == value;
    }

    public int hashCode() {
        return hashCode(value);
    }

    public static int hashCode(boolean value) {
        return value ? 1231 : 1237;
    }

    public int compareTo(Boolean other) {
        return compare(value, other.value);
    }

    public static int compare(boolean first, boolean second) {
        return first == second ? 0 : first ? 1 : -1;
    }

    public String toString() {
        return value ? "true" : "false";
    }

    public static String toString(boolean value) {
        return value ? "true" : "false";
    }
}
