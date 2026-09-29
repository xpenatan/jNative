package com.github.xpenatan.jnative.classlib.java.util.regex;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.regex.*;

@SubstituteClass("java.util.regex.PatternSyntaxException")
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
