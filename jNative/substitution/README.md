# jnative-substitution

Java 17-compatible annotations for providers consumed during jNative generation.
This artifact has no dependency on ASM, the compiler, or the backend. Provider
implementations use ordinary packages and `src/main/java` layouts.

All annotations have `CLASS` retention. `SubstituteClass` names a complete target
class. Repeatable `SubstituteMethod` names an exact owner, method name, and JVM
descriptor, implemented by a static helper. `TargetField` (`GET` or `SET`) and
`OriginalMethod` declare static native compiler aliases without Java bodies.
Target staticness is inferred by the compiler; there is no invocation-kind option.

Use `jnative-substitution-processor` with
`-Ajnative.substitutionProvider=<provider-id>` to generate
`META-INF/jnative/substitutions.json`, or author the equivalent index manually.
An index becomes active only through explicit provider registration.

See [the substitution guide](../../docs/substitutions.md) for complete class and
method examples, private-state aliases, programmatic registrations, builder/CLI
usage, C++ binding requirements, and native representation limits.
