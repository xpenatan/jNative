package java.util.regex;

public class PatternSyntaxException extends IllegalArgumentException {
    private final String description, pattern;
    private final int index;

    public PatternSyntaxException(String description, String pattern, int index) {
        super(description + " near index " + index + ": " + pattern);
        this.description = description;
        this.pattern = pattern;
        this.index = index;
    }

    public String getDescription() {
        return description;
    }

    public String getPattern() {
        return pattern;
    }

    public int getIndex() {
        return index;
    }
}
