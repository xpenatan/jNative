package com.github.xpenatan.jnative;

/** A whole-class replacement with an explicitly named donor class. */
public record ClassSubstitution(String target, String implementation) {
    public ClassSubstitution {
        if(target != null && target.strip().startsWith("["))
            throw new IllegalArgumentException("Array representations cannot be replaced: " + target);
        target = SubstitutionNames.binaryName(target, "target");
        implementation = SubstitutionNames.binaryName(implementation, "implementation");
        if(target.equals(implementation)) throw new IllegalArgumentException("A class cannot substitute itself: " + target);
    }
}
