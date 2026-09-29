package java.util.regex;

import java.util.Objects;

public final class Pattern {
    public static final int UNIX_LINES = 1,
            CASE_INSENSITIVE = 2,
            COMMENTS = 4,
            MULTILINE = 8,
            LITERAL = 16,
            DOTALL = 32,
            UNICODE_CASE = 64,
            CANON_EQ = 128,
            UNICODE_CHARACTER_CLASS = 256;
    private final String expression;
    private final int flags;
    final Object compiled;

    private Pattern(String expression, int flags) {
        this.expression = Objects.requireNonNull(expression);
        this.flags = flags;
        if((flags & ~(CASE_INSENSITIVE | LITERAL | DOTALL)) != 0)
            throw new UnsupportedOperationException(
                    "Regex flags are not supported by this runtime profile: " + flags);
        try {
            compiled = NativeRegex.compile(expression, flags);
        } catch(IllegalArgumentException error) {
            throw new PatternSyntaxException(error.getMessage(), expression, -1);
        }
    }

    public static Pattern compile(String expression) {
        return compile(expression, 0);
    }

    public static Pattern compile(String expression, int flags) {
        return new Pattern(expression, flags);
    }

    public Matcher matcher(CharSequence input) {
        return new Matcher(this, Objects.requireNonNull(input).toString());
    }

    public String pattern() {
        return expression;
    }

    public int flags() {
        return flags;
    }

    public String toString() {
        return expression;
    }

    public static boolean matches(String expression, CharSequence input) {
        return compile(expression).matcher(input).matches();
    }

    public String[] split(CharSequence input) {
        return split(input, 0);
    }

    public String[] split(CharSequence input, int limit) {
        String text = input.toString();
        return NativeRegex.split(matcher(text), text, limit);
    }
}
