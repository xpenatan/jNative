# Portability

`./gradlew :samples:portability:run_native` builds a standalone executable.
`./gradlew :samples:portability:run_host` builds a native parent that links the
same generated classes using `jNative::Application`. The host retains control
after Java main returns and checks shutdown before unloading.

The sample covers objects, arrays, strings, reflection, threads and a C++ callback
into Java. Add `-PnativeBuildType=RELEASE` for Release. The compiler's default
language mode is retained; `-PcppStandard=20` explicitly selects another mode for
both builds. Custom native settings use `cmakeDefine`, `cmakeArgs`,
`cmakeBuildArgs`, `buildToolArgs`, and `cmakeToolchain` on `NativeBuilder`.
