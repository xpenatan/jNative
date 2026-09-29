package com.github.xpenatan.jnative.classlib.java.util.logging;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.logging.*;

@SubstituteClass("java.util.logging.Level")
public final class Level {
    public static final Level WARNING = new Level("WARNING", 900);
    public static final Level SEVERE = new Level("SEVERE", 1000);
    public static final Level INFO = new Level("INFO", 800);
    public static final Level FINE = new Level("FINE", 500);
    private final String name;
    private final int value;

    private Level(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int intValue() {
        return value;
    }

    public String toString() {
        return name;
    }
}
