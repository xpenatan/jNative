# Class and method substitutions

jNative reads explicitly registered provider JARs or compiled directories during
native generation. Application bytecode continues to reference the original API.
The generated program uses the selected implementation under the original class
and member identity. Providers are bytecode inputs: discovering them does not
execute their constructors or static initializers on the compiler JVM.

Use ordinary `src/main/java` sources and ordinary JAR entries. The annotation
artifact `com.github.xpenatan.jNative:jnative-substitution` supports Java 17 and has
no compiler dependency. The separate `jnative-substitution-processor` artifact
generates the provider descriptor.

## Author a provider

```java
package example.nativeimpl;

import com.github.xpenatan.jnative.substitution.SubstituteClass;

@SubstituteClass("vendor.Counter")
public class PortableCounter {
    private int value;

    public int add(int amount) {
        value += amount * 2;
        return value;
    }
}
```

The donor can have a different package and name. It supplies the entire selected
class, including fields, constructors, initializers, hierarchy, and reachable
members. It must satisfy the original application's binary contracts. Missing
members do not fall back to the displaced class. Public nested API classes need
explicit mappings; private implementation companions retain their donor names.

For a partial replacement, provide a static helper:

```java
package example.nativeimpl;

import com.github.xpenatan.jnative.substitution.*;
import vendor.Counter;

public class CounterMethods {
    @SubstituteMethod(owner = "vendor.Counter", name = "add", descriptor = "(I)I")
    public static int add(Counter self, int amount) {
        return previous(self, amount * 2);
    }

    @OriginalMethod(owner = "vendor.Counter", name = "add", descriptor = "(I)I")
    public static native int previous(Counter self, int amount);

    @TargetField(owner = "vendor.Counter", name = "value", descriptor = "I",
            access = FieldAccess.GET)
    public static native int readValue(Counter self);

    @TargetField(owner = "vendor.Counter", name = "value", descriptor = "I",
            access = FieldAccess.SET)
    public static native void writeValue(Counter self, int value);
}
```

The compiler infers staticness from the exact selected target declaration or
supported runtime contract. Instance targets include the receiver as the helper's
first parameter; static targets do not add one. Target descriptors exclude that
receiver. Binary class names use dots and preserve `$`; JVM descriptors use
slashes. Each overload and bridge is a distinct slot. Repeat `SubstituteMethod`
to map compatible targets to one helper. Helpers must be static and must not be
synchronized. The logical target retains its synchronization and member metadata.
When original class metadata is supplied, replacements retain its class kind,
required hierarchy and public visibility. They cannot add final or abstract
restrictions or narrow the original permitted-subclass set.
Target owners and descriptor types are normalized through the declared class
aliases before method conflicts and preferences are resolved. A method helper
inside a shadowed full-class donor is rejected: put that helper in a separate
class so its executable identity remains distinct from the winning class.
Preferences select a provider; they cannot disambiguate two competing rules
within that provider.

`TargetField` and `OriginalMethod` aliases must be static native declarations
without Java bodies. The compiler supplies their implementation. A winning method
patch from the same provider must authorize the alias. Field aliases access the
effective receiver's existing field; writes to final fields are rejected.
Eligible aliases are validated before reachability, including aliases unused by
the selected method body. Aliases in providers with no eligible winning patch
remain dormant and fail if reached.
`OriginalMethod` invokes the implementation selected before that patch. An
ordinary call to the target invokes the patch again. On an ordinary JVM these
native aliases have no implementation supplied by jNative.

Actual C++ imports still require the existing `NativeImport` and `NativeInclude`
contract. `SubstituteMethod` alone never binds an otherwise unimplemented native
declaration.

## Generate or author an index

Configure the processor with `-Ajnative.substitutionProvider=example.counter`.
It writes sorted binary declaration names to
`META-INF/jnative/substitutions.json`. Gradle can configure the processor as an
aggregating annotation processor:

```kotlin
dependencies {
    compileOnly("com.github.xpenatan.jNative:jnative-substitution:<version>")
    annotationProcessor("com.github.xpenatan.jNative:jnative-substitution-processor:<version>")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Ajnative.substitutionProvider=example.counter")
}
```

A manual index is equivalent; the annotations remain authoritative:

```json
{
  "schemaVersion": 1,
  "providerId": "example.counter",
  "declarations": ["example.nativeimpl.CounterMethods"]
}
```

The processor or manual index lists declarations containing substitution
annotations. Helpers referenced by those declarations are read from the same
artifact or explicitly registered dependency artifacts. Do not use a manual index
and the processor to produce the same output file.

## Register providers

```java
NativeBuilder.create()
    .classpath(applicationClasses)
    .classpath(originalLibraryJar)
    .substitutionPath(providerJar)
    .substitutionDependencies(helperJar)
    .preferClass("vendor.Counter", "example.counter")
    .preferMethod(new MethodReference("vendor.Counter", "add", "(I)I"), "example.counter")
    .mainClass("example.Main")
    .buildRoot(output)
    .generate();
```

Classpath membership alone does not activate an embedded provider index.
`substitutionDependencies` supplies helper bytecode without activating providers.
The bundled provider is enabled by default. `useBuiltinSubstitutions(false)`
removes its substitutions; fundamental native representation restrictions remain.

For an unannotated donor, `substitutions(new SubstitutionProvider(id, artifact,
classes, methods))` registers `ClassSubstitution` and `MethodSubstitution` records
directly. A method donor uses `StaticMethodReference`. An index is unnecessary for
these explicit registrations. All request options snapshot their collections and
normalize paths without loading Java classes.

Equivalent CLI options may repeat:

```text
--substitution-path provider.jar
--substitution-dependency helpers.jar
--no-builtin-substitutions
--prefer-class-substitution vendor.Counter=example.counter
--prefer-method-substitution vendor.Counter#add(I)I=example.counter
```

Pass each preference as one argument; quote it when the invoking shell requires
quoting for parentheses or other punctuation.

## Selection and compatibility

An external whole-class rule outranks the built-in rule. External method patches
apply to the selected class; built-in method patches do not overwrite an external
whole-class donor. Multiple external winners require an exact provider preference,
and ineffective or misspelled preferences fail. Provider order never selects a
winner. Separate builds have separate effective declarations and binding tables.

When original class bytes are supplied, generation also checks class kind,
hierarchy, visibility, and final/sealed restrictions. Matching public or protected
members cannot narrow visibility, change staticness, make an overridable method
final or abstract, or make a writable field final. A whole-class donor owns its
private state. Missing members fail when reached; unused portions of the original
API need not be implemented. If original bytes are absent, the selection report
records that original-ABI comparison was unavailable, and reachable uses still
undergo validation.

Constructors and static initializers require whole-class replacement; ordinary
method patches cannot target `<init>` or `<clinit>`. Arrays and fixed native
payload types reject whole-class replacement with a representation-specific
diagnostic. These include `Object`, `String`, `Class`, native thread/atomic/buffer,
path, reflection, and supported exception payloads. Generated types such as
`ArrayList` and `CharBuffer` use the ordinary class rules. Exact supported ordinary
methods on fixed types can be patched where a runtime declaration contract exists.

Generation writes `substitutions.json` beside generated C++ sources with selected
and shadowed declarations, unused rules, hashes, and runtime restrictions. Raw
original and donor hashes identify inputs; effective class and method hashes
identify cached bytecode after remapping, patching and lambda lowering. The
compact `substitutions.tsv` lists the same rule statuses for inspection. See the
[diagnostic codes](diagnostic-codes.md) for configuration errors. Native-only rebuilds use the exported C++ project;
regeneration needs the Java inputs and provider artifacts.

Long provider package names keep their Java identities and C++ namespaces. When
a generated source path exceeds 48 characters beneath `classes/`, its physical
filename uses `longnames`, a readable class name, and a stable identity hash to
avoid exceeding portable native build path limits. The source map records the
actual file path in either source layout.

Native runtime callbacks honor supported method patches. This includes String
hash/equality/text conversion in native collections, and native factories for
the generated `NumberFormatException` and `MalformedInputException` classes.
The FileInputStream and CRC32 shortcuts are disabled when their assumed method
or whole-class implementation is replaced. Fixed native payload layouts remain
explicitly restricted; a Java donor cannot change those layouts.
