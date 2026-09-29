package com.github.xpenatan.jnative.substitution;

import java.lang.annotation.*;

/** Compiler-readable substitution metadata; does not activate a provider by itself. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface SubstituteClass {
    String value();
}
