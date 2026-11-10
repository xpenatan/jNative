# Project-owned implementation comparisons

The workloads and runners belong to this repository. They require no game
framework, renderer, graphics driver, sibling checkout, external application
executable, or another project's build helper. Comparators are jNative,
GraalVM Native Image and equivalent standalone C++.

The default `all` selection runs ten suites: **59 operations and 166 default
size combinations**. The `general` suite covers integer arithmetic, interface dispatch,
reference and typed-array allocation, list/map growth, cold string hashing, and
string searches. String input preparation and output are outside measured
regions; allocation and collection growth remain timed when those are the
operation being measured.

The current runner requires Windows, MSVC, CMake and GraalVM Native Image.
Create `tests/conformance/build/comparison-tools.properties` with installed tools:

```properties
generator=Visual Studio 18 2026
vcvars=C:/path/to/Visual Studio/VC/Auxiliary/Build/vcvars64.bat
nativeImage=C:/path/to/graalvm/bin/native-image.cmd
```

Run from the repository root, choosing a new run name each time:

```text
gradlew.bat :tests:conformance:compareImplementations -PcomparisonTools=<absolute-properties-path> -PcomparisonRun=full-01
```

This task builds the same fixture bytecode with jNative and Native Image, builds
its C++ counterpart, and verifies results before timing. It checks independent
small inputs, fixture hashes, JVM checksums for every measured size/round count,
and every measured sample. A verification failure stops the comparison.

Outputs stay under the owning module's configured build directory:
`compare/<run>/`. In `all` mode, `suite-status.csv` records every selected suite's
case count, PASS/FAIL, elapsed time, result path and failure-log path. Individual
suites live in short numbered subdirectories. A failed suite does not prevent
the others from running; any failed suite makes the overall task fail.
Short build subdirectories keep native paths within Windows limits.
For a single-suite run, `benchmark.properties` records inputs;
`measurements/timings.csv` contains raw milliseconds for each implementation;
`samples.csv` and `summary.csv` in that directory contain samples and median
ranges. `verification.txt` records the checks. These are temporary execution
outputs; the persistent performance history is `.performance/HISTORY.md` at the
repository root.

Full runs also write every selected case to one searchable HTML table and one
combined CSV, including failed suites and duration warnings. Regenerate those
outputs from a completed full run without repeating measurements:

```text
gradlew.bat :tests:conformance:reportComparison -PcomparisonRun=full-01
```

Open `build/compare/full-01/all-cases.html`; the complete raw table is also in
`all-cases.csv`. The report retains original and tuned C++ columns separately,
uses the actual calibrated rounds, and refuses incomplete or duplicate timing
rows from a supposedly successful suite. Failed suites retain every selected
case with blank timings and an explicit status. The report reads a completed
run and does not execute benchmarks or change the measurements.

The case label combines suite, operation and numeric size, for example
`map-probe:get-string:4096`. For map-probe lookups, the report also shows
`LookupCount` (size × measured rounds) and nanoseconds per lookup for each
implementation. Use those units to compare sizes with different work counts.

The default uses three processes per implementation, three warmups and five
samples per process, CPU affinity 1, and alternating implementation order. Each
suite's builds finish before its calibration and measurements. Suites execute
sequentially, so no compilation overlaps measured work. Each sample loops over the selected
operation many times; it is not a single operation timing.

`compareImplementations` first runs separate calibration pilots on every
implementation, including an optional prior jNative control. It chooses one
shared round count per case, targeting at least **100 ms** for the fastest
implementation's median sample. Three pilot samples and the usual warmups run
per pilot process. Pilot samples are never included in the reported results.
The JVM verifies checksums for every changed round count before it is timed.

Calibration uses a **2,000 ms** slowest-implementation pilot budget, at most six
attempts, bounded round growth and a **64 MiB** conservative estimate for
cold-hashing inputs that grow with rounds. These are calibration limits, not
hard bounds on process memory or execution time. If a limit prevents reaching
the target, `timings.csv` explicitly reports `below-target` with its reason.
Measured duration status uses the minimum process-median sample across all
implementations; a noisy run can fall below target even after successful pilots.
Do not treat such a row as having met the requested timing duration.

Configure repetition with:

| Property | Default | Purpose |
| --- | ---: | --- |
| `comparisonMinSampleMillis` | 100 | Target duration for the fastest implementation |
| `comparisonMaxSampleMillis` | 2000 | Pilot budget for the slowest implementation |
| `comparisonMaxSetupMiB` | 64 | Estimated round-scaled cold-hash input budget |
| `comparisonCalibrate` | true | Set false to retain explicitly configured/default rounds |

`measurements/calibration.csv` records pilot counts and timings.
`resolved-configuration.properties` in the same directory records the exact
chosen counts, and `timings.csv` includes `Rounds`, `TargetSampleMs` and
`DurationStatus`. To replay exactly those counts, copy the resolved properties
to a new file, set a new `runName` and `calibration.enabled=false`, then use the
`benchmarkScores -PbenchmarkConfig=<absolute-properties-path>` task. Legacy
benchmark configuration files keep their round counts unless they opt in with
`calibration.enabled=true`.

Select operations with
`-PcomparisonCases=arithmetic:256` or
`-PcomparisonCases=(dispatch|arithmetic):.*`. Set `-PcomparisonProcesses=6` for
a longer comparison. To include a named prior jNative binary, set
`-PcomparisonBaseline=<absolute-executable-path>` using the same fixture and
settings and select a single suite with `-PcomparisonSuite=<suite>`.
Other implementations remain required comparisons.

In Windows PowerShell, preserve regular-expression metacharacters when invoking
the batch wrapper by using its stop-parsing token and double quotes:

```powershell
.\gradlew.bat --% :tests:conformance:compareImplementations -PcomparisonTools=E:/path/to/comparison-tools.properties -PcomparisonRun=repeat-01 "-PcomparisonCases=(arithmetic:256|search-prefix:16)"
```

Select independent probes with `-PcomparisonSuite=<suite>`:

| Suite | Operations | Default cases | Measured work |
| --- | ---: | ---: | --- |
| `general` | 9 | 27 | Arithmetic, dispatch, allocation, collections, strings |
| `numeric` | 6 | 12 | Field updates, table lookups, packing and a numeric pipeline |
| `transfer` | 3 | 9 | Primitive-array and typed-buffer transfers |
| `allocation` | 6 | 18 | Dropped/retained objects, graphs and arrays |
| `lookup` | 6 | 18 | Hashing, map hits/misses/replacement and reference scanning |
| `map-probe` | 13 | 39 | Typed/erased/identity hashing and collision groups |
| `map-growth` | 4 | 12 | Boxing, presizing and table growth |
| `map-iteration` | 3 | 9 | Dense/sparse hash and linked-map iteration |
| `nested-array` | 4 | 12 | Nested versus flat array traversal |
| `runtime-cost` | 5 | 10 | Reference stores and scalar/bulk buffer access |
| Total | 59 | 166 | Each operation may have several sizes |

Use `-PcomparisonSuite=all` (the default) for the full selection or name one suite
for focused work. Case filters apply to each suite's operation and size; suites
with no matching cases are not selected. A suite marked PASS has completed its
checksum-verified matrix; inspect each row's duration flag separately. Current
admission results and unresolved failures belong in `.performance/HISTORY.md`.

List every current case without native builds or measurements:

```text
gradlew.bat :tests:conformance:listComparisonCases
```

The Java examples are executable source files under `src/test/resources/fixtures`.
For example, `GeneralPerformanceBenchmark.java` owns the actual timed loop and
checksum output. Equivalent C++ code lives in `src/test/cpp/general_performance.cpp`.
`ProjectComparison.java` orchestrates builds, and `BenchmarkScores.java` performs
verification and starts measured processes. JUnit tests validate semantics and
the harness; they do not assert that one implementation is faster.

C++ references implement equivalent observable workloads with ordinary ownership
and standard containers. Their memory management, exception/type-check contracts,
iterators, layouts and container growth policies differ from Java. Report these
limits alongside raw times; timings are not correctness assertions or evidence
of general performance parity.

For example, `general:list-grow` compares Java `ArrayList<String>` with MSVC's
`std::vector<const std::u16string*>`. Each round creates and grows a fresh
container. Assigning the new C++ owner destroys the previous vector inside the
timer; Java reclamation follows GC scheduling, including any collections that
occur during the sample. Final C++ destruction is outside the timer. Capacity
growth and allocation policies also differ. Label this result as ArrayList
versus the particular C++ vector implementation when interpreting it.

GraalVM Native Image compiles the Java workload to native machine code, with
its runtime and GC linked into the executable. Both comparator executables
contain native code. Their language of origin does not establish their relative
speed. Separating insertion into preallocated storage, capacity expansion and
allocation/reclamation would require additional probes; the current combined
timing does not attribute the observed gap to any one of these costs.

The focused `compareListGrowth` task builds and measures three native
list candidates while preserving the original `Cpp` vector reference:

```text
gradlew.bat :tests:conformance:compareListGrowth -PcomparisonTools=<absolute-properties-path> -PcomparisonRun=list-native-01
```

- `CppStack` holds vector metadata on the stack, starts with capacity ten and
  subsequently grows normally.
- `CppSmall` holds eight pointers inline, doubles capacity and uses `realloc`
  after moving to heap storage. It uses a native list representation with a
  different growth policy.
- `CppSmall32` uses the same representation with 32 inline slots, trading a
  larger stack-held container for fewer heap allocations on short lists.

Every round appends the requested number of pointers to a fresh list; neither
candidate reserves the final size, shares a pool across rounds or replaces the
work with a checksum formula. Previous storage is reclaimed inside timing and
final destruction is outside. Independent native checks cover element contents,
empty lists, growth boundaries, moves and moved-from reuse. The last measured
list's contents are also checked after stopping the timer. The usual independent
small-input and exact-count JVM checksum checks apply before measurement.

Results retain `CppMs` and add separately labelled `CppStackMs`, `CppSmallMs`
and `CppSmall32Ms`.
Calibration and measured process order include each eligible candidate. These
variants participate only in `general:list-grow`; unrelated controls retain their
ordinary implementations. Use `comparisonCases` to select general controls in
the focused task. `compareImplementations` includes the candidates by default
for general list growth; `comparisonCppTuning=false` selects the older reference
alone for that case.
These are tested optimization candidates; no universal optimum or required
winning order is assumed. Other C++ workloads have not been tuned by this task.

## Individual build and timing tasks

`LookupIsolation` separates virtual hashing, same-object map hits, equal-but-distinct
key hits, misses, replacement of an existing value, and reference scanning. Keys
have deterministic hashes. Setup creates the keys, values and map before timing;
the measured operations allocate no new entries. Each process runs one operation
and size. These are diagnostic probes, not an overall performance score.

The comparison tooling currently requires Windows, MSVC, CMake and a GraalVM
installation with Native Image.
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
hashes and build logs, and compiles the same Java bytecode with jNative and
GraalVM Native Image. A run name cannot overwrite an existing run.

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
`unavailable.GraalVM` or `unavailable.Cpp` may explicitly name
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
