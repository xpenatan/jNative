package com.github.xpenatan.jnative.classlib.java.util;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

import java.util.*;

@SubstituteClass("java.util.Locale")
public final class Locale {
    public static final Locale ROOT = new Locale("");
    public static final Locale ENGLISH = new Locale("en");
    public static final Locale US = new Locale("en", "US");
    private static Locale defaultLocale = ENGLISH;
    private final String language;
    private final String country;

    public Locale(String language) {
        this(language, "");
    }

    public Locale(String language, String country) {
        this.language = Objects.requireNonNull(language);
        this.country = Objects.requireNonNull(country);
    }

    public static Locale getDefault() {
        return defaultLocale;
    }

    public static void setDefault(Locale locale) {
        defaultLocale = Objects.requireNonNull(locale);
    }

    public String getLanguage() {
        return language;
    }

    public String getCountry() {
        return country;
    }

    public boolean equals(Object other) {
        return other instanceof Locale
                && language.equals(((Locale)other).language)
                && country.equals(((Locale)other).country);
    }

    public int hashCode() {
        return language.hashCode() * 31 + country.hashCode();
    }

    public String toString() {
        return country.isEmpty() ? language : language + "_" + country;
    }
}
