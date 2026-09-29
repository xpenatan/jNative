# jNative class library

This artifact is the default `jnative.builtin` substitution provider. Implementations
live in `com.github.xpenatan.jnative.classlib.java.*` at ordinary JAR paths and use
the [public substitution API](../../docs/substitutions.md). Native generation maps
their typed references to the original Java API names; the host JVM does not load
or initialize these implementations during discovery.

Java sources use the standard `src/main/java` directory and main source set,
with compile-only dependencies on `jNative:interop` and `jNative:substitution`. Reload the Gradle
project in the IDE after the layout change so annotation imports such as
`NativeImport` and `NativeInclude` resolve to their interop source declarations.
Ordinary Java compilation generates `META-INF/jnative/substitutions.json` with the
annotation processor. The binary and sources JARs use normal package paths.

The Java sources retain API signatures, managed state, synchronization, scalar
operations and virtual dispatch. Traversal, sorting, parsing, encoding, formatting
and collection maintenance run in the C++ runtime. Native drivers call managed
overrides through declared callbacks, so subclass behavior remains part of the
library contract.

Every C++ binding requires `@NativeImport` and an owning `@NativeInclude`.
Reachable unannotated native methods cause a `CompilerException` during generation;
there is no name-based native fallback. `TargetField` and `OriginalMethod` are
separate bodyless compiler bindings, validated only within active replacements.
For example:

```java
@NativeInclude("jn_classlib_numbers.hpp")
final class NativeNumbers {
    @NativeImport(value = "jnative::integer_parse", managed = true,
            types = {"java/lang/NumberFormatException"})
    static native int parse(String text, int radix);
}
```

`managed=true` calls C++ with managed object pointers. The generated boundary roots
reference arguments; the implementation must root temporary references across
allocations, callbacks and collection polls. Ordinary external imports retain the
default C ABI with opaque handles. A Java facade that calls an annotated helper
does not need a second import annotation.

`managesRoots=true` delegates boundary rooting to an audited managed implementation:
it roots every live reference before allocating, polling, blocking or calling Java,
and polls cooperatively during input-dependent work. The compiler omits argument
roots, initialization and the entry poll only for unsynchronized helpers whose
entire initialization hierarchy is proven trivial. Otherwise it retains the normal
boundary. Caller allocation and collection effects remain conservative; this
contract does not imply `bounded`.
The initialization proof recognizes the runtime's `Cloneable` and `Serializable`
marker interfaces; other unmodeled platform ancestors retain the normal boundary.

Managed import metadata declares callback signatures and dispatch kinds, typed
instance-field accessors, and types created or thrown only by native code.
Source-class virtual callbacks use generated C++ virtual members, preserving
overrides without a second dispatch bridge. Interface and platform callbacks
retain their general dispatch adapters. Native drivers keep callback arguments
and results rooted, using one root frame per operation where possible.
`NativePlatform`, `NativeBuffers` and `NativeReflection` declare exact method and
static-field routes for APIs implemented by runtime objects rather than replacement
Java classes. Compiler mechanics such as object allocation, class literals,
arithmetic and `invokedynamic` string construction remain compiler operations.

Effect metadata is a correctness promise, not a hint to remove checks. `bounded`
is restricted to primitive scalar helpers. `boundedAccess` describes a bounded
successful platform access; exceptional execution can allocate, and the compiler
must prove dispatch, initialization and exception-visible reference safety before
inlining it. `runtimeOnly` limits access to runtime representations, declared
fields and declared callbacks, allowing the ownership analysis to stay precise.
General native imports remain conservative.

`registeredReflection` limits generated-field access and generated behavior to
emitted reflection registrations, alongside runtime representations and declared
callbacks/adapters. Registered fields, constructors, methods and their dependencies
remain conservatively exposed or traversed; unrelated classes keep their ownership
proofs. This managed-only promise cannot combine with bounded effect promises.

`callbacksSynchronous` promises that declared callbacks run only on the calling
managed thread before the native call returns. Collection, text and I/O drivers
use this contract; asynchronous thread/executor bindings do not. Ownership
analysis still follows synchronous callbacks when their caller runs on a worker.

`ClasslibInventoryTest` writes the compiled method/source and platform binding
inventories under `jNative/compiler/build/reports`. It validates native bindings
and rejects Java control-flow loops or direct helper recursion outside the fixed
boxing-cache initializers. Conformance tests additionally exercise dynamic
callbacks, reentrancy, partial failures and concurrent collection. The full
migration plan and measured results are in `.plan/classlib-native-kernels.md`.

This remains the supported emulation profile, not a complete JDK implementation.
The migration preserves existing API and data-structure limits. CharsetDecoder's
REPORT, REPLACE and IGNORE policies now preserve tested input positions on errors.
