# Substitution diagnostic codes

Configuration records report malformed public names, descriptors, or paths with
`IllegalArgumentException` before generation. Compiler diagnostics include the
provider, artifact, target, and donor wherever available.

| Code | Meaning | Action |
| --- | --- | --- |
| JN4020 | Invalid/missing provider index, unsupported schema, malformed declaration, or unreadable provider artifact | Check schema version 1, binary names, exact JVM descriptors, and artifact contents. |
| JN4021 | Missing donor class/method, exact declared target member, or aliased field | Supply the correct artifact and target owner, name, and descriptor; inherited methods must name their declaring owner. |
| JN4022 | Conflicting providers, donor ownership, helper bytes, duplicate definitions, or hidden implementation identities | Remove conflicting definitions or select an exact provider preference; distinct helper bytes with the same class name need distinct namespaces. |
| JN4023 | Unknown, stale, or ineffective provider preference | Match the exact registered class/method target and an eligible provider ID. |
| JN4024 | Incompatible class kind/hierarchy/visibility, invocation kind, staticness, receiver, or exact helper descriptor | Preserve the original binary contract; instance helpers take the target receiver first, and static helpers do not add one. |
| JN4025 | Class or method substitution cycle, self-substitution, or ambiguous chained donor identities | Use distinct donors and an acyclic implementation selection. |
| JN4026 | Invalid or inactive native alias, multiple executable bindings, unavailable previous body, runtime-state field access, or a final-field write | Use one active bodyless static native alias owned by the winning provider and an exact supported field or previous implementation. |
| JN4027 | Forbidden whole-class replacement of an array or fixed native representation | Supply a supported exact method patch, or retain that representation's original native layout. |

Ordinary missing bytecode and unbound native declarations retain their existing
compiler diagnostics. See [the substitution guide](substitutions.md) for provider
authoring and selection.
