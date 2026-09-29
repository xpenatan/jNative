# Release diagnostics example

This sample generates a native player and a small local report flow. The player
runs without Java. Its C++ native method can deliberately fail on the main or
render thread; the following launch discovers saved reports and can export an
attachment for a support request.

Build Release and retain its private archive outside build output:

~~~powershell
.\gradlew.bat :samples:diagnostics:prepare_release -PnativeArchiveStore=D:/Private/GameSymbols
~~~

The task uses the module's configured build directory. Ship the contents of
build/native/release, including jnative-diagnostics.exe and any dependent DLLs.
The task prints the permanent archive directory. Do not ship that archive.

For development, build_native defaults to Debug and writes to build/native/debug.
Use -PnativeBuildType=RELEASE with build_native for a Release build without permanent
archiving. prepare_release always builds Release. The two output directories coexist.

Run the sample from the repository root:

~~~powershell
.\samples\diagnostics\build\native\release\diagnostics-demo.exe reports
.\samples\diagnostics\build\native\release\diagnostics-demo.exe exception
.\samples\diagnostics\build\native\release\diagnostics-demo.exe worker
.\samples\diagnostics\build\native\release\diagnostics-demo.exe reports
.\samples\diagnostics\build\native\release\diagnostics-demo.exe export
~~~

exception constructs an owned native exception and prints it after crossing the
Java native boundary. worker deliberately crashes a render thread. crash performs
the same fault on the main thread. Export copies the most recently written report
and its dump, when present, into a fresh attachment directory and prints its path.
The sample sends nothing over the network. A real launcher can ask the player to
submit that attachment using its own support flow.

Reports default to %LOCALAPPDATA%/jNative/diagnostics-demo/crashes on Windows.
Set JNATIVE_REPORT_DIR to choose another writable location. JNATIVE_INCLUDE_DUMP=0
keeps address reports without memory dumps. The helper also supports
--export <report> <destination> --without-dump.

Decode the submitted report with the developer CLI:

~~~powershell
.\gradlew.bat :jNative:cli:run --args="decode --report D:/Submitted/report.json --symbols D:/Private/GameSymbols --from-dump"
~~~

Omit --from-dump to use the raw stacks saved by the helper. Use --format json for
structured output. The expected application fault resolves to user/demo.cpp,
including the native call and generated render-thread callback when available.

Extract the exact archived sources:

~~~powershell
.\gradlew.bat :jNative:cli:run --args="extract-sources --symbols D:/Private/GameSymbols/BUILD_ID --destination D:/CrashSources/BUILD_ID"
~~~

The default PACKAGE_DIRECTORIES layout preserves Java package folders. The
PACKAGE_FILENAME alternative is configured with the builder and keeps the full
package in the filename. Both are covered by conformance tests.

Linux builds use the same native project with CMake. Linux reports contain a
minimal signal/PC record; use the system's configured core collection for complete
thread unwinding. Decode a collected core with --core <file> and Linux GDB.
No core configuration is changed by this sample.

The example intentionally uses console pause on normal exit, including Release.
Redirected input/output skips the pause; a fatal crash terminates rather than
entering normal shutdown.
