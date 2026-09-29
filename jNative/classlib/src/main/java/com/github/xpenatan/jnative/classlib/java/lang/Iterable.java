package com.github.xpenatan.jnative.classlib.java.lang;

import com.github.xpenatan.jnative.classlib.java.util.Iterator;
import com.github.xpenatan.jnative.classlib.java.util.NativeCollections;
import com.github.xpenatan.jnative.classlib.java.util.function.Consumer;
import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("java.lang.Iterable")
public interface Iterable<T> {
    Iterator<T> iterator();

    default void forEach(Consumer<? super T> action) {
        if(action == null) throw new NullPointerException();
        NativeCollections.forEach(iterator(), action);
    }
}
