package com.github.xpenatan.jnative.backend.cpp;

/** Java-visible operations carried by the readable IR independently of C++ spelling. */
record JavaEffects(int mask) {
    enum Kind {
        HEAP_READ, HEAP_WRITE, THROW, SAFEPOINT, CLASS_INITIALIZATION, ALLOCATION, INVOCATION, MONITOR;

        int bit() {
            return 1 << ordinal();
        }
    }

    static final JavaEffects PURE = new JavaEffects(0);
    static final JavaEffects CHECK = of(Kind.THROW);
    static final JavaEffects RUNTIME_CHECK = of(Kind.THROW, Kind.SAFEPOINT);
    static final JavaEffects READ = of(Kind.HEAP_READ, Kind.THROW);
    static final JavaEffects WRITE = of(Kind.HEAP_WRITE, Kind.THROW);
    static final JavaEffects REFERENCE_WRITE = of(Kind.HEAP_WRITE, Kind.THROW, Kind.SAFEPOINT);
    static final JavaEffects ALLOCATE = of(Kind.ALLOCATION, Kind.THROW, Kind.SAFEPOINT);
    static final JavaEffects INITIALIZE = of(Kind.CLASS_INITIALIZATION, Kind.THROW, Kind.SAFEPOINT);
    static final JavaEffects CALL = of(Kind.INVOCATION, Kind.THROW, Kind.SAFEPOINT);
    static final JavaEffects MONITOR = of(Kind.MONITOR, Kind.THROW, Kind.SAFEPOINT);

    private static JavaEffects of(Kind... kinds) {
        int bits = 0;
        for (Kind kind : kinds) bits |= kind.bit();
        return new JavaEffects(bits);
    }

    boolean has(Kind kind) {
        return (mask & kind.bit()) != 0;
    }

    boolean pure() {
        return mask == 0;
    }

    JavaEffects withoutSafepoint() {
        return new JavaEffects(mask & ~Kind.SAFEPOINT.bit());
    }
}
