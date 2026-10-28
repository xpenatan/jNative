# jNative runtime

This artifact supplies C++ sources and CMake configuration used by generated native
projects. It has no Java API. See the headers in the `cpp/` directory of the main
or sources JAR for runtime interfaces and platform integration.

Managed allocations up to roughly 4 MiB use nonmoving, thread-owned size-class
pages. Shells up to 512 bytes use dense 16-byte classes; array allocations use
geometric classes. Up to 64 KiB, a page holds multiple allocations. Larger classes
use one block per page to avoid reserving several unused large payloads. Requests
above the reuse range use the platform allocator directly. Array
constructors initialize every element once, including reused storage; default
construction of the atomic fields already supplies Java's zero values.
Live pooled objects carry one page pointer with ownership/deallocation flags;
free-list links occupy the payload only after destruction. The heap owns arenas beyond thread detach;
foreign frees are queued until the owner or stopped-world collector can reclaim
them. Collection retains at most 8 MiB of completely empty pages across the heap,
dividing that budget among attached arenas. Partly occupied pages remain until
their last object dies. Detach retains no empty pages. This trades some size-class
rounding and page fragmentation for fewer calls to the system allocator. Reusable
pages retain their free-slot chains after pending remote frees have drained.

Collection traces both existing and newly allocated objects. Dense small slots
track ownership in the arena, avoiding a second pointer registration per object.
Larger allocations retain pointer queues and a survivor list; walking their
widely spaced arena headers was slower in the isolated array measurements.
Unadopted native objects remain outside GC ownership. Proven standard deallocators
allow the collector to release an arena slot directly after virtual destruction;
custom native delete overloads keep ordinary deletion.
The marking work buffer is reused between collections. Object and array allocation
use the attached thread's heap. Automatic collection considers both object count
and managed bytes: initially 4,096 objects or 4 MiB of new allocation; after a
collection the count budget grows to at least four times the surviving object
count, while the byte budget grows to at least the surviving managed-byte size.
This prevents a few large discarded arrays from bypassing collection while
avoiding repeated whole-heap scans when most storage survives. These are pacing
budgets, not hard heap limits. Managed-byte counts include allocator headers and
size-class rounding, but exclude page slack and separately allocated native
containers such as string storage. The object-count budget remains a backstop.

Collection-pressure accounting merges after 64 objects or 64 KiB per mutator
instead of issuing global atomic increments for every object. Ownership and
statistics are published immediately; the already reported prefixes avoid
incrementing separate partial counters. A single mutator includes its partial batch and keeps
the exact explicit object threshold; `JNATIVE_GC_INTERVAL` disables byte-based
pacing, and intervals below 64 retain per-object global accounting.
With multiple mutators, collection pressure can lag by at most 63 allocations
and less than 64 KiB per other attached thread. Collection and detach account for
every partial batch.
`JNATIVE_STATS=1` reports
collection timings, `allocated_bytes`, `managed_bytes`, `peak_managed_bytes`, and
`pooled_bytes` (reserved page storage, including empty cached pages). These are
runtime storage diagnostics, not process RSS measurements.

Array views borrow immutable length and storage metadata; they do not retain an
array or its elements. The generated caller must keep the array alive across
collection. Constructing a view does not throw on a null input: `length()` or an
element access reports the failure when Java first uses it. The compiler can
snapshot a stable local array's length at the first loop-head test and use that
limit in its range proof. Proven ranges use unchecked element access between
collection checks at most 64 iterations apart; reference and shared primitive
payload loads remain atomic. Other paths keep checked access and original
exception ordering.

## Small performance probes

Run one layer with `./gradlew :jNative:runtime:benchmarkIsolation
-PisolationRun=adopt-before -PisolationCases=adopt` (use `gradlew.bat` on Windows).
Each run name must be new. The task builds a Release executable and saves raw
samples, process-median ranges, binary hashes and command lines under
`jNative/runtime/build/isolation/<run>/`. It uses six processes, three warmups and
five samples per process; Windows runs pin the measured process to CPU affinity 1.
Set `-PisolationBaseline=<previous runtime_isolation_benchmark executable>` to
interleave previous/current measurements. `-PisolationGenerator=<CMake generator>`
overrides the platform default.

Select `pool` for shell allocation, `adopt` for publication of already-created
shells, `allocate` for the complete allocation path without collection during
timing, `allocate-gc` for allocation with automatic collection, `roots` for root
registration, or `poll` for the pre-allocation check. Filters are regular expressions.
Setup and final cleanup are outside the timer. The no-GC probes use larger live
working sets than the GC probe; their times must not be added/subtracted as an
exact cost breakdown. Root/poll results also include loop and probe overhead.
These are runtime diagnostics, not equivalent C++/GraalVM/TeaVM application scores.
`array-calloc` and `array-new` isolate zero initialization of eight atomic reference
payloads; their release is untimed. These probes do not relax Java initialization
requirements or establish an application speedup.

The allocation contracts are separate CTest cases: `allocation_context_shells`,
`allocation_context_arrays`, `allocation_context_failures`,
`allocation_context_pressure`, `allocation_context_concurrent_pressure`,
`allocation_context_automatic_pressure`,
`allocation_context_byte_pressure`, `allocation_context_byte_detach`,
`allocation_context_byte_survivors`, `allocation_context_cache_budget`, and
`allocation_context_arena_ownership`.
These cover automatic survivor-budget bounds, pressure across thread detach, and
recovery when marking throws before an arena-owned graph is fully traced.
Native allocation contracts also cover ordinary custom `operator new` overloads,
including hidden/inherited placement forms and cleanup after construction fails.

`./gradlew :jNative:runtime:check` runs Debug and Release contracts. On Windows,
`gradlew.bat :jNative:runtime:check "-PruntimeGenerator=Visual Studio 18 2026"`
checks MSVC in separate module build directories; the default remains MinGW.
