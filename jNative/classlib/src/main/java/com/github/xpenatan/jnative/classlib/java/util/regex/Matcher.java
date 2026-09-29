package com.github.xpenatan.jnative.classlib.java.util.regex;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.regex.*;

@SubstituteClass("java.util.regex.Matcher")
public final class Matcher {
    private final Pattern pattern;
    private final String input;
    private int[] groups;
    private int next, append;

    Matcher(Pattern pattern, String input) {
        this.pattern = pattern;
        this.input = input;
    }

    public boolean find() {
        groups =
                next > input.length()
                        ? null
                        : NativeRegex.find(pattern.compiled, input, next, false);
        if(groups == null) {
            next = input.length() + 1;
            return false;
        }
        next = groups[1] == groups[0] ? groups[1] + 1 : groups[1];
        return true;
    }

    public boolean matches() {
        groups = NativeRegex.find(pattern.compiled, input, 0, true);
        if(groups != null) next = groups[1] == groups[0] ? groups[1] + 1 : groups[1];
        return groups != null;
    }

    public Matcher reset() {
        groups = null;
        next = append = 0;
        return this;
    }

    private void check(int group) {
        if(groups == null) throw new IllegalStateException("No match");
        if(group < 0 || group >= groups.length / 2) throw new IndexOutOfBoundsException();
    }

    public int start() {
        return start(0);
    }

    public int end() {
        return end(0);
    }

    public int start(int group) {
        check(group);
        return groups[group * 2];
    }

    public int end(int group) {
        check(group);
        return groups[group * 2 + 1];
    }

    public String group() {
        return group(0);
    }

    public String group(int group) {
        check(group);
        return start(group) < 0 ? null : input.substring(start(group), end(group));
    }

    public Matcher appendReplacement(StringBuffer output, String replacement) {
        NativeRegex.appendReplacement(this, output, replacement);
        return this;
    }

    public StringBuffer appendTail(StringBuffer output) {
        return output.append(input.substring(append));
    }

    public String replaceAll(String replacement) {
        reset();
        StringBuffer result = new StringBuffer();
        NativeRegex.replace(this, result, replacement, false);
        return result.toString();
    }

    public String replaceFirst(String replacement) {
        reset();
        StringBuffer result = new StringBuffer();
        NativeRegex.replace(this, result, replacement, true);
        return result.toString();
    }
}
