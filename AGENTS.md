# Project conventions

## Plans

- Save all project plans as Markdown files in the repository-root `.plan/` folder.
- Use descriptive filenames and update the relevant plan when its scope or
  implementation decisions change.

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
