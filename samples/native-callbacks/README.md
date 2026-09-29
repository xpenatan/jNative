# C++ worker threads calling back into Java

This sample starts three std::thread workers inside an imported C++ function.
Each worker makes four calls to an exported Java method. It demonstrates persistent
foreign-thread attachment, ThreadLocal state, owned reference handles, copied text,
forced GC, joining workers, and an exception traveling Java → C++ → Java.

Run from the repository root:

~~~powershell
.\gradlew.bat :samples:native-callbacks:run_jvm
.\gradlew.bat :samples:native-callbacks:run_native
~~~

On Linux use ./gradlew. The JVM reference uses Java threads; the generated executable
uses the std::thread implementation in src/main/native/callbacks.cpp.

To generate or compile without executing:

~~~powershell
.\gradlew.bat :samples:native-callbacks:generate_native
.\gradlew.bat :samples:native-callbacks:build_native
~~~

The default Windows executable is samples/native-callbacks/build/native/debug/native-callbacks.exe
from the repository root. Generated sources and CMake build files are also under
this module's build/native directory. Gradle runs the launcher from this module.

Add -PnativeBuildType=RELEASE to build_native or run_native to build and use
build/native/release/native-callbacks.exe. Debug and Release outputs stay separate.

Edit src/main/java or src/main/native and rerun build_native to regenerate from
the sample sources. Handwritten native files are copied to the output project's
user/ directory. Compile or export that existing project if you edit its copies
directly, since regeneration protects edited output files.

## Call flow

1. NativeCallbacks.main calls CallbackBridge.runWorkers, annotated NativeImport.
2. C++ retains the borrowed prefix string once for each worker.
3. Each worker calls jn_attach_thread once, then sample_java_process repeatedly.
4. The generated C wrapper enters JavaCallbacks.process. Its ThreadLocal checks
   the task sequence, and System.gc runs while arguments and results remain live.
5. C++ receives an owned string handle, copies its UTF-16 bytes, counts code units,
   then releases both buffer and handle.
6. Workers detach and release their retained inputs. The importing thread joins
   all workers before returning the total to Java.

JavaCallbacks.process is annotated NativeExport ("sample_java_process").
The compiler emits the C declaration in jnative_exports.h:

~~~c
jn_status sample_java_process(jn_handle prefix, int32_t worker, int32_t task,
                             jn_handle* result);
~~~

The return value is an ABI status; the last parameter receives the Java return
value as an owned handle. C++ must check the status and release the result.
BuildNativeCallbacks.java explicitly retains callback exports with exportClass.

The sample's small C++ RAII helpers release resources on failures as well as
success. Worker exceptions are collected and rethrown on the importing thread as
a Java RuntimeException. No exception escapes a std::thread entry function.

## Exception round trip

sample_roundtrip_exception calls the exported JavaCallbacks.reject method.
That method throws IllegalArgumentException. The C export catches it and sets the
thread's pending ABI error. C++ returns immediately, preserving that error; the
Java import wrapper rethrows the original exception, and main catches it.
This synchronous path preserves the original Java exception type.

Do not clear a pending error or invoke another ordinary ABI function before the
import returns if you want Java to receive it.

## Expected native output

~~~text
Execution: native C/C++
Workers: 3
Callbacks completed: 12
Returned UTF-16 units: 132
ThreadLocal order and GC: passed
Caught callback error: rejected by Java callback
Native workers joined and handles released
~~~

Each returned string is "Task 🌎:worker:task", with 11 UTF-16 code units for the
single-digit IDs in this example. Output is printed by main after joining workers,
so scheduling does not change the recorded result. expected-output.txt is checked
by NativeSamplesTest, including collection at every native allocation.
