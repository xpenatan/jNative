# Project conventions

## Script files and validation

- Never add or generate `.ps1` or `.py` files anywhere in this repository,
  including validation tooling, fixtures, temporary files, and build outputs.
- Use Java/JUnit and explicitly declared Gradle tasks for validation and
  benchmark automation.
- Do not hide PowerShell or Python scripts behind different file extensions.

## Plans

- Save all project plans as Markdown files in the repository-root `.plan/` folder.
- Use descriptive filenames and update the relevant plan when its scope or
  implementation decisions change.

## Performance reports

- Before performance work, read repository-root `.performance/HISTORY.md`.
  It records attempts, retained/reverted changes, results, failures and open
  questions. If missing, report that the local history is unavailable.
- Keep performance investigation history in the Git-ignored
  `.performance/HISTORY.md`, not `docs/`. Maintain this single file.
  Do not create evidence archives, companion reports or copies of raw run
  files unless the user explicitly requests them.
- Record each attempt's hypothesis, change, named baseline, comparator timings,
  verification outcome and keep/revert/open decision, including negative results
  and why an approach should not be repeated. Preserve earlier conclusions
  when recording new results.
- Consult the history before repeating an experiment or restoring a rejected
  approach. Explain the changed hypothesis, inputs or evidence that justifies
  retrying; necessary correctness/regression checks are still expected.
- Always compare jNative with GraalVM and TeaVM, and with C++ when an equivalent
  workload exists. Performance claims in progress updates and final reports must
  include side-by-side raw timings for the other implementations; comparison with
  a previous jNative build is supplementary, not sufficient on its own.
- State the remaining performance gap to the other implementations. If a
  comparator is unavailable or fails verification, say so explicitly and limit
  the conclusion; never present a jNative-only speedup as evidence of parity.
- Report actual elapsed times and units for each benchmark implementation.
- Do not normalize benchmark scores to jNative = 100 or another fixed score.
- Compare changes against a named prior build, record the actual elapsed
  comparator timings in the history, and state verification failures and
  missing comparator workloads explicitly. Raw logs/data need not be retained
  as documentation.
- Investigate costs with small, independently selectable probes that separate
  setup, measured work, and cleanup. Use broader workloads to check whether an
  isolated improvement carries over and introduces regressions.

## Java

- Use imports instead of package-qualified type and annotation references.
  Keep enclosing-type qualification such as `Map.Entry`; keep package names only
  when a genuine name conflict requires them. Apply this to source files,
  examples, and generated Java fixtures; put fixture imports inside the source.

## Gradle

- Declare tasks explicitly with readable, stable names.
- Do not use `for`, `forEach`, `while`, or `repeat` loops in Gradle files.
- Use multiline configuration blocks and one statement per line.
- Keep generated sources and native build outputs inside the owning module's
  configured build directory. Use that module's layout.buildDirectory in Gradle.
