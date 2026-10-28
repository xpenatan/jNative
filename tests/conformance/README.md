# Isolated performance comparisons

`LookupIsolation` separates virtual hashing, same-object map hits, equal-but-distinct
key hits, misses, replacement of an existing value, and reference scanning. Keys
have deterministic hashes. Setup creates the keys, values and map before timing;
the measured operations allocate no new entries. Each process runs one operation
and size. These are diagnostic probes, not an overall performance score.

The comparison tooling currently requires Windows, MSVC, CMake and a GraalVM
installation with Native Image. TeaVM 0.16.0 is a declared Gradle dependency.
Create a machine-specific properties file under this module's build directory:

```properties
generator=Visual Studio 18 2026
vcvars=C:/path/to/Visual Studio/VC/Auxiliary/Build/vcvars64.bat
nativeImage=C:/path/to/graalvm/bin/native-image.cmd
```

Build and preserve a baseline before changing jNative:

```text
gradlew.bat :tests:conformance:prepareLookupComparison -PlookupTools=<absolute-properties-path> -PlookupRun=baseline
```

This writes `build/lookup-comparison/baseline/benchmark.properties`, records input
hashes and build logs, and compiles the same Java bytecode with jNative, GraalVM
and TeaVM. A run name cannot overwrite an existing run.

To compare an edited compiler, copy that generated properties file to another
file under `build`, change `lookup.jNative` to `lookup.Baseline`, and choose a new
`runName`. With no `lookup.jNative` property, the timing task builds the current
compiler. Keep the reference bytecode and comparator paths unchanged. Select one
probe with `cases=lookup:get-equal:256`, or all 18 with `cases=lookup:.*`.

```text
gradlew.bat :tests:conformance:benchmarkScores -PbenchmarkConfig=<absolute-properties-path>
```

Despite the legacy task name, results contain raw elapsed times, never normalized
scores. `samples.csv` retains individual nanosecond measurements; `summary.csv`
contains medians and ranges across process medians; `timings.csv` presents
milliseconds for each implementation. The runner verifies fixture hashes and JVM
checksums, uses three warmups and five samples per process, pins each measured
process to CPU affinity 1, and alternates forward/reverse implementation order.
Use `pairs=6` for six processes per implementation. Run without other builds or
benchmarks competing for CPU time.

`profileGc=true` runs separate jNative diagnostic processes and records `gc.csv`.
Those counts include setup and warmups, not just the measured region. Keep unfavorable timings and
verification failures alongside improvements when reporting results.

Build the C++17 counterpart of the six original lookup probes with:

```text
gradlew.bat :tests:conformance:prepareLookupCppComparison -PcppProbeRun=baseline -PconformanceGenerator="Visual Studio 18 2026"
```

Set `lookup.Cpp` to the absolute path of
`build/lookup-cpp-comparison/baseline/native/Release/lookup_cpp.exe`. Its setup and
measured operations match the Java fixture, including separate key/value
allocations, virtual hashing and alternating reference scans. It uses
`std::unordered_map`; bucket policy and Java GC/exception semantics differ.

`MapProbeIsolation` adds typed/erased hashing, stable Object/identity hashing,
cached string hashing, ordinary-object/string lookups, and four/eight-key collision
groups. All key creation and hash priming happen before timing. Identity hashes
are checked against each process's setup values because their numeric values are
implementation-specific. Collision hits visit every key, avoiding a preference
for head-first or tail-first insertion; groups contain at most eight keys and do
not exercise tree bins.

Build its matching comparators with the same tools properties:

```text
gradlew.bat :tests:conformance:prepareMapProbeComparison -PlookupTools=<absolute-properties-path> -PlookupRun=map-probes-baseline
```

Use the generated configuration with `benchmarkScores`. Its suite is `map-probe`;
select one operation using `cases=map-probe:get-collision4-same:256`. Preserve the
generated `map-probe.jNative` binary as `map-probe.Baseline` before changing the
compiler. The original `LookupIsolation` bytecode remains unchanged.

Build the corresponding C++17 probes with an explicit task:

```text
gradlew.bat :tests:conformance:prepareMapCppComparison -PcppProbeRun=baseline -PconformanceGenerator="Visual Studio 18 2026"
```

Set `map-probe.Cpp` to the absolute path of
`build/map-cpp-comparison/baseline/native/Release/map_probe_cpp.exe` in the timing
configuration. This reference uses separately allocated keys and values and
`std::unordered_map`; its bucket policy, identity hashes, memory management and
exception semantics differ from Java. Checksums and measured operations match.

`AllocationIsolation` separates dropped shells, a ring of retained shells, a live
graph created before measurement, and primitive/reference/typed array allocation.
Setup, retained-object verification and final cleanup are untimed. Every allocation
escapes through a volatile reference; ring and graph contents are verified after
measurement. The runner checks both partially filled and wrapped rings against the JVM.

```text
gradlew.bat :tests:conformance:prepareAllocationComparison -PlookupTools=<absolute-properties-path> -PallocationRun=allocation-prior
gradlew.bat :tests:conformance:prepareAllocationCppComparison -PcppProbeRun=allocation-prior
```

The Java builds are saved under `build/allocation-comparison/<run>`. The generated
benchmark properties use the `allocation` suite and `reference.allocationClasses`.
Set `allocation.Cpp` to the absolute path of
`build/allocation-cpp-comparison/<run>/native/Release/allocation_cpp.exe` and select
individual probes with `cases=allocation:reference-array:256`. Preserve
`allocation.jNative` as `allocation.Baseline` before changing runtime/compiler sources.
Omit `allocation.jNative` to build the current sources when timing a candidate.
Override `allocation.sizes` with comma-separated positive element/retention counts
to probe other working sets or allocator boundaries using the same binaries.
Array probes keep the nominal work near two million elements, with at least
eight allocations at very large sizes. Only the shell-ring probe creates a
retention ring; array probes have no unrelated retained array to scan.
`profileGcCases` optionally limits the separate diagnostic processes; their
counts and pauses include setup and warmups, not just the timed sample.

For runtime-only experiments with a revised fixture, an optional `priorRuntime`
directory in the tools properties builds a second jNative executable using that
preserved runtime source snapshot and the new fixture. It records every copied
runtime file's hash in `prior-runtime.csv`. This assumes the compiler and classlib
are unchanged from the prior; it does not reconstruct an older compiler.

Benchmark failures stop a run by default. After recording an unsupported input,
`unavailable.TeaVM`, `unavailable.GraalVM` or `unavailable.Cpp` may explicitly name
a case regex to omit that comparator for those inputs. The verification report
lists every omission and its elapsed-time cells stay empty. An omission is not
a verification pass and must be disclosed alongside the results.

Build the complete C++ general-workload counterpart with
`prepareGeneralCppComparison -PcppProbeRun=<new-name>`. Set `general.Cpp` to its
executable under `build/general-cpp-comparison/<run>/native/Release/general_cpp.exe`
and `general.cppCases=.*` to enable its allocation/growth operations. Without that
property, the runner keeps the supported subset for older C++ executables.

C++ uses normal ownership: replacing an allocation reclaims the old value during
measurement, and final destruction is untimed. General map growth uses separately
allocated integer keys/values with caching for the workload's values 0..127.
Vector growth, unordered-map bucket policy, GC timing, object layout, and Java
exception/type-check contracts differ. These compare observable workloads;
they do not imply identical allocation instructions or memory management.

`MapGrowthIsolation` separates boxing and table growth with four selectable
operations: `boxed-grow`, `boxed-presized`, `preboxed-grow`, and
`preboxed-presized`. Key/value setup and final entry verification are untimed.
Every constructed map escapes, and each implementation performs the same number
of insertions. The probes are independent workloads; subtracting their timings
does not isolate an additive cost because their GC and cache behavior differs.

```text
gradlew.bat :tests:conformance:prepareMapGrowthComparison -PlookupTools=<absolute-properties-path> -PmapGrowthRun=<new-name>
gradlew.bat :tests:conformance:prepareMapGrowthCppComparison -PcppProbeRun=<new-name>
```

Java-tool builds are under `build/map-growth-comparison/<run>`, with configuration
keys `map-growth.*` and `reference.mapGrowthClasses`. Add `map-growth.Cpp` pointing
to `build/map-growth-cpp-comparison/<run>/native/Release/map_growth_cpp.exe`.
Select one probe with `cases=map-growth:preboxed-presized:4096`; preserve the old
`map-growth.jNative` as `map-growth.Baseline` and omit `map-growth.jNative` to build
the current sources. As with the general C++ map reference, this uses
`std::unordered_map` with explicit box ownership, a different bucket policy,
and deterministic reclamation of previous maps. Final destruction is untimed.

`MapIterationIsolation` checks the iteration tradeoff independently of insertion.
It provides `hash-dense`, `hash-sparse`, and `linked-sparse`, with table capacities
of 2 or 64 times the entry count. Construction and final entry checks are outside
timing; each measured round creates and consumes a Java iterator.

```text
gradlew.bat :tests:conformance:prepareMapIterationComparison -PlookupTools=<absolute-properties-path> -PlookupRun=<new-name>
gradlew.bat :tests:conformance:prepareMapIterationCppComparison -PcppRun=<new-name>
```

The Java-tool configuration uses `map-iteration.*` and
`reference.mapIterationClasses` under `build/map-iteration-comparison/<run>`.
Set `map-iteration.Cpp` to the executable under
`build/map-iteration-cpp-comparison/<run>/native/Release/map_iteration_cpp.exe`.
The C++ counterpart uses boxed keys/values, `std::unordered_map` for hash
iteration, and an ordered list alongside its hash index for linked iteration.
Its iterators do not allocate, and its bucket policy differs from the Java tools.
All results remain raw times with checked checksums, not normalized scores.

For a longer confirmation of a very short probe, set
`rounds.general.search-prefix.16=100000` (or
`rounds.<suite>.<operation>.<size>` for another case). The override applies the
same round count to every implementation and is recorded in the configuration
and raw result rows. Keep the default-duration results separately; do not mix
elapsed times from different round counts.
