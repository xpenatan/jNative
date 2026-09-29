# Project conventions

## Plans

- Save all project plans as Markdown files in the repository-root `.plan/` folder.
- Use descriptive filenames and update the relevant plan when its scope or
  implementation decisions change.

## Gradle

- Declare tasks explicitly with readable, stable names.
- Do not use `for`, `forEach`, `while`, or `repeat` loops in Gradle files.
- Use multiline configuration blocks and one statement per line.
- Keep generated sources and native build outputs inside the owning module's
  configured build directory. Use that module's layout.buildDirectory in Gradle.
