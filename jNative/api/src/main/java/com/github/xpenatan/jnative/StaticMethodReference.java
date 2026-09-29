package com.github.xpenatan.jnative;

/** The exact static helper declaration in a provider artifact. */
public record StaticMethodReference(String owner, String name, String descriptor) {
    public StaticMethodReference {
        owner = SubstitutionNames.binaryName(owner, "owner");
        name = SubstitutionNames.memberName(name);
        descriptor = SubstitutionNames.methodDescriptor(descriptor);
    }
}
