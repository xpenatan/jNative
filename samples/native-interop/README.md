# Calling C and C++ from Java

This sample translates Java into C++ and links handwritten .c and .cpp files into
the same native executable. It demonstrates:

- A C function adding Java ints with Java-compatible overflow.
- A C function reading a copied Java byte array as unsigned bytes.
- C++ arithmetic and a C++ exception caught as a Java RuntimeException.
- Java String → copied UTF-8 → std::string → a new managed Java String.
- Java byte[] → native copy → reversed managed byte[], preserving the input.

Run these commands from the repository root (JDK 25, CMake and C++ compiler):

~~~powershell
.\gradlew.bat :samples:native-interop:run_jvm
.\gradlew.bat :samples:native-interop:run_native
~~~

On Linux use ./gradlew instead. run_jvm uses the Java reference bodies.
run_native generates C++, compiles the C/C++ sources and runs the executable.
Its first line changes to "Execution: native C/C++"; the marker is implemented in
C and confirms that the native import replaces its Java reference body.

For generation and compilation without running:

~~~powershell
.\gradlew.bat :samples:native-interop:generate_native
.\gradlew.bat :samples:native-interop:build_native
~~~

The default Windows result is samples/native-interop/build/native/debug/native-interop.exe
from the repository root. Generated sources, CMake build files and the executable
are all under this module's build/native directory; rebuild or export that native
project using the normal jNative workflow. Gradle runs the launcher from this module.

Add -PnativeBuildType=RELEASE to build_native or run_native to build and use
build/native/release/native-interop.exe. Debug and Release outputs stay separate.

Edit Java under src/main/java and handwritten C/C++ under src/main/native, then
rerun build_native or run_native. Generation copies the native files into the
output project's user/ directory. If you edit those generated copies directly,
compile the existing project or export it; regeneration protects your edits.

## Follow one call

NativeFunctions.java declares:

Its declaring class carries `@NativeInclude("sample_native.h")`. Every native
import requires this include annotation; generation fails if either binding
annotation is missing or the included header is not supplied. For externally
implemented C functions, `@NativeInclude("jnative_imports.h")` can instead select
the declarations generated from their Java signatures.

~~~java
@NativeImport("sample_cpp_greeting")
public static String greeting(String name) {
    return "Hello, " + name + " — from C++";
}
~~~

On the JVM that Java body runs. In generated code the call enters
sample_cpp_greeting in src/main/native/sample_cpp.cpp. Its argument is a borrowed
jn_handle, not a char pointer or a JNI object. The function copies the text using
jn_string_copy_utf8, builds a std::string, creates a new managed string using
jn_string_from_utf8, and returns its owned handle to Java. The RAII Buffer releases
the native copy. Explicit byte lengths preserve Unicode and embedded zero bytes.

sample_native.h uses extern "C" when compiled as C++, giving both languages the
same symbol names. BuildNativeInterop.java adds that header and both implementation
files using NativeBuilder.nativeFile. It also demonstrates generate followed by
compile as separate API operations.

Do not free borrowed input handles or use raw pointers into the Java heap.
Returned handles transfer ownership to generated Java.
The string/array examples use non-null inputs.

## Expected native output

~~~text
Execution: native C/C++
C addition: 42
C integer wrap: -2147483648
C++ weighted average: 12.5
Hello, Java 🌎 — from C++
C unsigned-byte checksum: 258
Original bytes: 0,1,2,-1
C++ reversed copy: -1,2,1,0
Copy survives GC: true
Caught C++ error: weight must be between 0 and 1
~~~

The same output is stored in expected-output.txt and checked against both execution
modes by NativeSamplesTest. Native verification also forces collection at every
allocation.
