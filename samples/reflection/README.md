# Public reflection in a native executable

This sample finds Player by name, invokes its constructor, reads and writes fields,
and invokes methods through the standard Java reflection APIs. It also demonstrates
static fields, modifiers, primitive boxing, GC and InvocationTargetException.

From the repository root:

~~~powershell
.\gradlew.bat :samples:reflection:run_jvm
.\gradlew.bat :samples:reflection:run_native
~~~

On Linux use ./gradlew. The Windows executable is
samples/reflection/build/native/debug/reflection.exe by default, from the repository root.
Its output matches the JVM and expected-output.txt. generate_native and build_native
are also available.

Generated sources and CMake build files are also under this module's build/native
directory. Gradle runs the launcher from this module and supplies its build directory.
The sample selects PACKAGE_FILENAME. The selected layout is saved in
build/native/jnative-project.properties; changing it for existing output reports
JN4011. To discard this module's generated output and rebuild with another layout:

~~~powershell
.\gradlew.bat :samples:reflection:clean :samples:reflection:run_native
~~~

Use a new build root instead when existing native source edits need to be retained.

The executable uses ConsoleMode.PAUSE_ON_EXIT. Opening it directly on Windows
keeps the console open with "Press any key to exit..." after printing its results.
Gradle's run_native task captures output, so it finishes without waiting for input.
Pause mode works in both Debug and Release. Build Release with:

~~~powershell
.\gradlew.bat :samples:reflection:build_native -PnativeBuildType=RELEASE
~~~

Use -PnativeBuildType=DEBUG for Debug (the default). Debug writes to
build/native/debug/reflection.exe; Release writes to build/native/release/reflection.exe.
Both executables and their diagnostics helpers remain available after switching
configurations. run_native also accepts -PnativeBuildType=RELEASE and runs the
executable from the selected directory.

BuildReflection registers the class explicitly:

~~~java
NativeBuilder.create()
    .classpathFromCurrentJvm()
    .mainClass(ReflectionExample.class.getName())
    .reflectClass(Player.class.getName())
    .sourceLayout(SourceLayout.PACKAGE_FILENAME)
    .buildRoot(Path.of("build"));
~~~

Player is otherwise referenced only by its string name in the translated
application. Registration preserves its public constructors/methods and field
access, plus inherited public members. No annotations are required.

Generated adapters and metadata live in build/native/src/reflection.cpp. Ordinary
translated Player methods live in
build/native/src/classes/com.github.xpenatan.jnative.samples.reflection.Player.cpp,
with their declarations in the matching .hpp beside it. damage () uses an ordinary C++
condition and named health/amount values; GC and monitor helpers preserve Java
semantics. runtime_support.cpp contains the class's tracing, factories and adapters.
ReflectionExample.cpp now uses C++ try/catch blocks for the example's exception
handlers and a typed value for its class-identity comparison. Runtime reflection
calls use readable names such as jnative::java_api::java::lang::reflect::Method::invoke,
declared in build/native/src/java_api.hpp. java-api.tsv lists their overload names.
source-readability.tsv
records the emission form of every reachable method. To retain only a selected
surface, replace reflectClass with:

~~~java
builder.reflectConstructor(Player.class.getName(), "(Ljava/lang/String;I)V")
       .reflectMethod(Player.class.getName(), "damage", "(I)I")
       .reflectField(Player.class.getName(), "health");
~~~

That smaller registration enables those three operations. Add the other fields
and methods before running the unchanged example. A metadata-only registration
uses reflectMetadata; attempting value access or invocation without the matching
registration reports a missing reflection access registration.
