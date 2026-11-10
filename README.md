# jNative

jNative converts Java bytecode into C++ and compiles standalone executables
without a JVM. It includes garbage collection, threading, basic reflection and
C/C++ native interop.

The [project-owned comparison suite](tests/conformance/README.md) compares jNative
with GraalVM Native Image and standalone C++ using repository-owned workloads.

The [substitution API](docs/substitutions.md) lets providers replace complete
classes or exact methods while application bytecode keeps its original imports.

Licensed under the [Apache License 2.0](LICENSE).
