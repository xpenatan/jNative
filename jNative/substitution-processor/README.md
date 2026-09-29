# jnative-substitution-processor

The Java 17-compatible aggregating processor scans substitution declarations and
writes a sorted `META-INF/jnative/substitutions.json` index to class output.
Configure it through an annotation processor dependency and the required option
`-Ajnative.substitutionProvider=<provider-id>`.

The descriptor contains schema version `1`, the provider ID, and binary names of
classes declaring substitution metadata. The compiler reads the annotations from
the final bytecode and validates them again during generation. The processor
reports duplicate target definitions, invalid exact descriptors, nonstatic
helpers, and aliases that are not static native declarations.

Gradle processor metadata declares this processor aggregating so incremental
builds reprocess the provider's declarations together. A manually authored index
uses the same format and does not require this processor. Use one producer for
the descriptor.

See [the substitution guide](../../docs/substitutions.md) for configuration and
manual-index examples.
