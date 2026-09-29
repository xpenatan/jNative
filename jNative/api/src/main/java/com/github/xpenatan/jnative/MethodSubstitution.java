package com.github.xpenatan.jnative;

import java.util.Objects;

/** An exact method replacement whose implementation is a static helper. */
public record MethodSubstitution(MethodReference target, StaticMethodReference implementation) {
    public MethodSubstitution {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(implementation, "implementation");
    }
}
