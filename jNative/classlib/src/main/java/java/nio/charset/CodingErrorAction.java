package java.nio.charset;

public final class CodingErrorAction {
    public static final CodingErrorAction REPORT = new CodingErrorAction("REPORT");
    public static final CodingErrorAction REPLACE = new CodingErrorAction("REPLACE");
    public static final CodingErrorAction IGNORE = new CodingErrorAction("IGNORE");
    private final String name;

    private CodingErrorAction(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }
}
