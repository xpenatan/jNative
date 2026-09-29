package com.github.xpenatan.jnative.interop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a static Java method to a native symbol in the generated program. A non-native Java body
 * may provide the JVM reference implementation. Reference parameters are borrowed opaque handles,
 * valid for the duration of the call. A returned reference must be an owned handle (or zero for
 * null). Retain a borrowed handle before storing it or returning it. This external C ABI is the
 * default. With {@link #managed()}, references are raw managed objects and the symbol may be a
 * qualified C++ identifier. The implementation must root temporary references and returned
 * callback values across safepoints, and preserve Java exception and thread semantics. Callback
 * function pointers are appended in declaration order, followed by typed field accessors.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface NativeImport {
    /**
     * C-linkage function name, or a qualified C++ identifier in managed mode.
     */
    String value();

    /** Direct C++ call using managed references instead of the external handle ABI. */
    boolean managed() default false;

    /**
     * Managed implementation roots every live reference before allocation, collection polls,
     * blocking or Java callbacks, and polls cooperatively during input-dependent work. The
     * compiler may omit boundary roots, initialization and the entry poll only when declaring
     * class initialization is transitively trivial and the method is not synchronized. This
     * promise does not make the call bounded or reduce its allocation and safepoint effects.
     */
    boolean managesRoots() default false;

    /**
     * Managed implementation only inspects runtime representations or declared field adapters.
     * Generated Java behavior, including asynchronous calls, uses the declared callbacks. It
     * cannot inspect other generated fields or expose references to undeclared asynchronous work.
     * This promise governs ownership analysis, independently of allocation and safepoint effects.
     */
    boolean runtimeOnly() default false;

    /**
     * Managed implementation accesses generated fields and behavior only through emitted
     * reflection registrations, runtime representations and declared callbacks/field adapters.
     * Ownership analysis still exposes registered members and follows their dependencies.
     * This promise does not reduce allocation, blocking or collection effects.
     */
    boolean registeredReflection() default false;

    /** Proven bounded scalar helper: no allocation, blocking, references or Java callbacks. */
    boolean bounded() default false;

    /** Exact hidden Java dependencies, encoded as internal/Owner.method(descriptor). */
    String[] callbacks() default {};

    /**
     * Every declared callback executes synchronously on the calling managed thread. No callback
     * is queued, deferred, or dispatched asynchronously. This promise does not make the callback
     * nonallocating or prevent the helper from being invoked on a worker thread.
     */
    boolean callbacksSynchronous() default false;

    /** Java argument containing each receiver; defaults to zero. -1 means a native rooted receiver. */
    int[] callbackReceivers() default {};

    /** Dispatch kinds in callback order; defaults to virtual dispatch. */
    Invocation[] callbackKinds() default {};

    /** Managed instance fields in adapter order, encoded internal/Owner.field:descriptor. */
    String[] fields() default {};

    /** Exact platform Java APIs implemented by this helper: internal/Owner.method(descriptor). */
    String[] targets() default {};

    /** Exact platform static fields read by a zero-argument annotated getter. */
    String[] staticFields() default {};

    /** Targets are instance APIs; this static helper receives their receiver as argument zero. */
    boolean instance() default false;

    /** Managed types created or thrown by native code; retains type metadata without initializing them. */
    String[] types() default {};

    /**
     * Platform scalar access with bounded, noncollecting successful execution. Failure may raise
     * and allocate. Only a lowering that protects exception-visible references and proves helper
     * initialization and dispatch may inline this access without its collecting import wrapper.
     */
    boolean boundedAccess() default false;

    enum Invocation {
        VIRTUAL, INTERFACE, STATIC, SPECIAL
    }

    /**
     * Keeps a short, bounded native call on the managed thread without a blocking transition. All
     * parameters and the return value must be primitive (or void). The native implementation must
     * never block, call Java, use the managed handle/ABI functions, or access managed objects. Use
     * the default for driver calls, I/O, locks and any operation with unbounded execution time.
     * Floating-point state is restored and C++ exceptions are translated on return.
     */
    boolean leaf() default false;
}
