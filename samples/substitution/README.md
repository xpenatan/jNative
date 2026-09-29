# Explicit substitution provider

The `library` module produces an ordinary dependency with `vendor.Counter` and
`vendor.Message`. The application compiles against this unchanged API. The
separate `provider` JAR contains a whole-class donor in `example.portable` and a
partial method donor in `example.replacements`, plus a generated provider index.

`CounterMethods.add` calls the original method once, then updates the same private
field through bodyless native `TargetField` GET/SET aliases. The provider changes
`add` to accumulate twice the amount, retaining the original object's state and
the untouched `unchanged()` method. `PortableMessage` supplies the entire Message
class under its logical original name. The builder explicitly registers the
provider JAR; adding that JAR to a classpath would not activate it.

From the repository root:

```text
gradlew :samples:substitution:run_jvm
gradlew :samples:substitution:generate_native
gradlew :samples:substitution:run_native
```

The JVM prints `3`, `5`, `42`, and `original library`. The native program prints
`6`, `10`, `42`, and `selected native provider`. These deliberately different
results demonstrate selection. `generate_native` writes C++ without invoking a
native toolchain; `run_native` requires the configured CMake/C++ toolchain.

All outputs stay in their owning module's `build` directory. Set
`-PnativeBuildType=RELEASE` for a Release native build. See the
[substitution guide](../../docs/substitutions.md) for manual indexes, programmatic
registrations, CLI options, and compatibility limits.
