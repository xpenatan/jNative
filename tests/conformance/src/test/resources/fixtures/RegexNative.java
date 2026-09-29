import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexNative {
    static void failure(String label, Runnable action) {
        try { action.run(); System.out.println(label + ":ok"); }
        catch (RuntimeException error) { System.out.println(label + ":" + error.getClass().getName()); }
    }

    public static void main(String[] args) {
        if (args.length != 0) { partial(); return; }
        for (String pattern : new String[] {"", ",", "(?=a)", "^", "a", "z"}) {
            for (String input : new String[] {"", ",", ",a,,b,", "aba"}) {
                for (int limit : new int[] {-1, 0, 1, 2, 3}) {
                    System.out.println("split:" + Arrays.toString(Pattern.compile(pattern).split(input, limit)));
                }
            }
        }
        for (String replacement : new String[] {"X", "$0", "$1", "$2", "$12", "$01", "$00", "\\$\\\\"}) {
            Pattern pattern = Pattern.compile("(a)(b)?");
            System.out.println("all:" + pattern.matcher("zayabq").replaceAll(replacement));
            System.out.println("first:" + pattern.matcher("zayabq").replaceFirst(replacement));
        }
        for (String pattern : new String[] {"", "(?=a)", "a*"}) {
            System.out.println("zero:" + Pattern.compile(pattern).matcher("aba").replaceAll("X"));
        }
        Matcher matcher = Pattern.compile("(a)(b)?").matcher("zayabq");
        StringBuffer output = new StringBuffer("seed:");
        while (matcher.find()) matcher.appendReplacement(output, "[$1/$2]");
        System.out.println("append:" + matcher.appendTail(output));
        System.out.println("reset:" + matcher.replaceAll("$0"));
        failure("no-match", () -> Pattern.compile("a").matcher("a").appendReplacement((StringBuffer)null, null));
        failure("null-replacement", () -> Pattern.compile("a").matcher("a").replaceAll((String)null));
        failure("null-output", () -> {
            Matcher found = Pattern.compile("a").matcher("a"); found.find(); found.appendReplacement((StringBuffer)null, "x");
        });
        failure("split-null", () -> Pattern.compile("a").split(null));
        for (String replacement : new String[] {"$9", "$", "x\\", "${missing}"}) {
            failure("replacement-error", () -> Pattern.compile("(a)").matcher("a").replaceAll(replacement));
        }
        System.out.println("null-unused:" + Pattern.compile("z").matcher("abc").replaceAll((String)null));
        System.out.println("literal:" + Arrays.toString(Pattern.compile(".", Pattern.LITERAL).split("a.b..", -1)));
        System.out.println("flags:" + Pattern.compile("a.", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher("A\n").replaceAll("x"));
        for (String literal : new String[] {"", "abc", "\u03a9", "a.b", "\\[]$", "\u0000"}) {
            Matcher match = Pattern.compile(literal, Pattern.LITERAL).matcher("_" + literal + "_" + literal);
            System.gc();
            while (match.find()) {
                System.out.println("literal-group:" + match.start() + ":" + match.end()
                        + ":" + match.group().length());
                failure("literal-extra-group", () -> match.group(1));
            }
            System.out.println("literal-whole:" + Pattern.compile(literal, Pattern.LITERAL).matcher(literal).matches());
            System.out.println("literal-miss:" + Pattern.compile(literal, Pattern.LITERAL).matcher("_" + literal).matches());
        }
        System.out.println("literal-icase:" + Pattern.compile("a.b", Pattern.LITERAL | Pattern.CASE_INSENSITIVE)
                .matcher("A.B").matches());
        String large = "a,".repeat(1025);
        String[] parts = Pattern.compile(",").split(large, -1);
        System.out.println("large-split:" + parts.length + ":" + parts[1024] + ":" + parts[1025].length());
        String replacement = "x".repeat(4097) + "$1";
        String result = Pattern.compile("(a)").matcher("zaq").replaceAll(replacement);
        System.out.println("large-replace:" + result.length() + ":" + result.substring(4096));
    }

    // The supported facade commits replacement characters incrementally, whereas
    // the host JDK stages a replacement before writing it to the output buffer.
    static void partial() {
        Matcher matcher = Pattern.compile("(a)").matcher("zaq");
        matcher.find();
        StringBuffer output = new StringBuffer("seed");
        failure("group", () -> matcher.appendReplacement(output, "$9"));
        System.out.println("prefix:" + output);
        matcher.appendReplacement(output, "a").appendTail(output);
        System.out.println("retry:" + output);
        Matcher escaped = Pattern.compile("a").matcher("zaq");
        escaped.find();
        StringBuffer partial = new StringBuffer();
        failure("escape", () -> escaped.appendReplacement(partial, "X\\"));
        System.out.println("partial:" + partial);
        failure("null", () -> escaped.appendReplacement(partial, null));
        System.out.println("unchanged:" + partial);
        final int[] calls = {0};
        CharSequence sequence = new CharSequence() {
            public int length() { throw new AssertionError(); }
            public char charAt(int index) { throw new AssertionError(); }
            public CharSequence subSequence(int start, int end) { throw new AssertionError(); }
            public String toString() { calls[0]++; System.gc(); return "a,b,"; }
        };
        System.out.println("sequence:" + Arrays.toString(Pattern.compile(",").split(sequence, -1)) + ":" + calls[0]);
    }
}
