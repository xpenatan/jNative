package com.github.xpenatan.jnative;

/** An exact declared target method; target staticness is inferred by the compiler. */
public record MethodReference(String owner, String name, String descriptor) {
    public MethodReference {
        owner = SubstitutionNames.binaryName(owner, "owner");
        name = SubstitutionNames.memberName(name);
        descriptor = SubstitutionNames.methodDescriptor(descriptor);
    }
}
