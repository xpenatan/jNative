package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.lang.Record")
public abstract class Record {
    protected Record() {
    }

    public abstract boolean equals(Object other);

    public abstract int hashCode();

    public abstract String toString();
}
