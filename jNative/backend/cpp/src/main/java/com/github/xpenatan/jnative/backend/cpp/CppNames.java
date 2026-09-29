package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.SourceLayout;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.compiler.Program;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.objectweb.asm.Type;

/**
 * Names for the editable C++ surface. Internal ABI symbols have a separate namespace.
 */
final class CppNames {
    private static final Set<String> RESERVED =
            Set.of(
                    "alignas",
                    "alignof",
                    "and",
                    "and_eq",
                    "asm",
                    "auto",
                    "bitand",
                    "bitor",
                    "bool",
                    "break",
                    "case",
                    "catch",
                    "char",
                    "char16_t",
                    "char32_t",
                    "class",
                    "compl",
                    "const",
                    "constexpr",
                    "const_cast",
                    "continue",
                    "decltype",
                    "default",
                    "delete",
                    "do",
                    "double",
                    "dynamic_cast",
                    "else",
                    "enum",
                    "explicit",
                    "export",
                    "extern",
                    "false",
                    "float",
                    "for",
                    "friend",
                    "goto",
                    "if",
                    "inline",
                    "int",
                    "long",
                    "mutable",
                    "namespace",
                    "new",
                    "noexcept",
                    "not",
                    "not_eq",
                    "nullptr",
                    "operator",
                    "or",
                    "or_eq",
                    "private",
                    "protected",
                    "public",
                    "register",
                    "reinterpret_cast",
                    "return",
                    "short",
                    "signed",
                    "sizeof",
                    "static",
                    "static_assert",
                    "static_cast",
                    "struct",
                    "switch",
                    "template",
                    "this",
                    "thread_local",
                    "throw",
                    "true",
                    "try",
                    "typedef",
                    "typeid",
                    "typename",
                    "union",
                    "unsigned",
                    "using",
                    "virtual",
                    "void",
                    "volatile",
                    "wchar_t",
                    "while",
                    "xor",
                    "xor_eq",
                    "requires",
                    "concept",
                    "co_await",
                    "co_return",
                    "co_yield",
                    "char8_t",
                    // Common macros exposed by the runtime's standard/platform headers.
                    "EOF",
                    "NULL",
                    "NAN",
                    "INFINITY",
                    "stdin",
                    "stdout",
                    "stderr",
                    "errno",
                    "assert",
                    "offsetof",
                    "WIN32",
                    "WIN64");
    private static final Set<String> RUNTIME_MEMBERS =
            Set.of(
                    "trace",
                    "type_name",
                    "type_id",
                    "class_id",
                    "array_kind",
                    "monitor",
                    "marked",
                    "initialize",
                    "class_initialize",
                    "initialization_complete",
                    "initialize_slow",
                    "create",
                    "ensure_initialized");
    private final Map<String, String> classes = new LinkedHashMap<>();
    private final Map<String, String> paths = new LinkedHashMap<>();
    private final Map<String, String> sourcePaths = new LinkedHashMap<>();
    private final Map<String, String> methods = new LinkedHashMap<>();
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final Map<MethodId, String> calls = new LinkedHashMap<>();
    private final Map<String, String> enclosing = new LinkedHashMap<>();

    private final SourceLayout layout;

    CppNames(Program program) {
        this(program, SourceLayout.PACKAGE_DIRECTORIES);
    }

    CppNames(Program program, SourceLayout layout) {
        this.layout = layout;
        // Case-insensitive paths must also be distinct on Windows.
        var usedPaths = new HashSet<String>();
        var usedSourcePaths = new HashSet<String>();
        for(String owner : new TreeSet<>(program.classes().keySet())) {
            String path =
                    String.join(
                            "/", Arrays.stream(owner.split("/")).map(CppNames::pathPart).toList());
            String candidate = path;
            for(int suffix = 2; !usedPaths.add(candidate.toLowerCase(Locale.ROOT)); ++suffix)
                candidate = path + "_" + suffix;
            paths.put(owner, candidate);
            classes.put(owner, "::generated::" + candidate.replace("/", "::"));
            String sourcePath = layout == SourceLayout.PACKAGE_FILENAME ? candidate.replace('/', '.') : candidate;
            if(sourcePath.length() > 48) {
                String leaf = candidate.substring(candidate.lastIndexOf('/') + 1);
                if(leaf.length() > 20) leaf = leaf.substring(0, 20);
                String digest = UUID.nameUUIDFromBytes(owner.getBytes(StandardCharsets.UTF_8))
                        .toString().replace("-", "");
                sourcePath = "longnames" + (layout == SourceLayout.PACKAGE_FILENAME ? "." : "/")
                        + leaf + "_" + digest.substring(0, 16);
            }
            String selectedPath = sourcePath;
            for(int suffix = 2; !usedSourcePaths.add(selectedPath.toLowerCase(Locale.ROOT)); ++suffix)
                selectedPath = sourcePath + "_" + suffix;
            sourcePaths.put(owner, selectedPath);
        }
        var fieldNames = new HashSet<>(RUNTIME_MEMBERS);
        for(var node : program.classes().values()) {
            var used = new HashSet<>(RUNTIME_MEMBERS);
            used.add(simpleName(node.name));
            for(var field : node.fields) {
                String name = unique(identifier(field.name), used);
                fields.put(node.name + "." + field.name + field.desc, name);
                fieldNames.add(name);
            }
            fieldNames.add(simpleName(node.name));
        }
        // Allocate method names globally so overrides retain the same C++ name.
        // Java overloads that erase to the same C++ argument types need distinct names.
        var signatures = new HashSet<String>();
        var ids = new TreeSet<String>();
        for(var method : program.methods().values())
            ids.add(method.id().name() + method.id().descriptor());
        for(String key : ids) {
            int descriptor = key.indexOf('(');
            String name = key.substring(0, descriptor), desc = key.substring(descriptor);
            String base =
                    switch(name) {
                        case "<init>" -> "initialize";
                        case "<clinit>" -> "class_initialize";
                        default -> identifier(name);
                    };
            if(!name.startsWith("<") && fieldNames.contains(base)) base += "_method";
            String arguments =
                    Arrays.stream(Type.getArgumentTypes(desc))
                            .map(CppEmitter::type)
                            .toList()
                            .toString();
            String candidate = base;
            for(int suffix = 2; !signatures.add(candidate + arguments); ++suffix)
                candidate = base + "_" + suffix;
            methods.put(key, candidate);
        }
        // Compiler provenance distinguishes generated lambdas from user '$' classes.
        for(String owner : new TreeSet<>(program.classes().keySet())) {
            var origin = program.origins().get(owner);
            if(origin != null && owner.contains("$jNativeLambda$")
                    && !origin.generatedFrom().isEmpty() && program.classes().containsKey(origin.generatedFrom()))
                enclosing.put(owner, origin.generatedFrom());
        }
        var nestedNames = new HashMap<String, Set<String>>();
        for(var entry : enclosing.entrySet()) {
            String owner = entry.getKey(), parent = entry.getValue();
            Set<String> used = nestedNames.computeIfAbsent(parent, ignored -> {
                var reserved = new HashSet<>(RUNTIME_MEMBERS);
                reserved.add(simpleName(parent));
                for(String ancestor = parent; program.classes().containsKey(ancestor);
                    ancestor = program.classes().get(ancestor).superName) {
                    var node = program.classes().get(ancestor);
                    for(var field : node.fields)
                        reserved.add(field(ancestor, field.name, field.desc));
                    for(var method : program.methods().keySet())
                        if(method.owner().equals(ancestor)) reserved.add(method(method));
                    for(var inner : node.innerClasses)
                        if(inner.innerName != null) reserved.add(identifier(inner.innerName));
                }
                return reserved;
            });
            for(var field : program.classes().get(owner).fields)
                used.add(field(owner, field.name, field.desc));
            for(var method : program.methods().keySet())
                if(method.owner().equals(owner)) used.add(method(method));
            String prefix = parent + "$jNativeLambda$";
            if(!owner.startsWith(prefix) || !owner.substring(prefix.length()).matches("[0-9]+"))
                throw new IllegalArgumentException("Unrecognized generated lambda origin: " + owner);
            classes.put(owner, className(parent) + "::"
                    + unique("Lambda" + owner.substring(prefix.length()), used));
        }
    }

    String className(String owner) {
        return Objects.requireNonNull(classes.get(owner), owner);
    }

    String simpleName(String owner) {
        String name = className(owner);
        return name.substring(name.lastIndexOf("::") + 2);
    }

    String namespace(String owner) {
        String name = className(sourceOwner(owner)).substring(2);
        return name.substring(0, name.lastIndexOf("::"));
    }

    String definitionName(String owner) {
        return className(owner).substring(namespace(owner).length() + 4);
    }

    String sourceOwner(String owner) {
        String parent = enclosing.get(owner);
        return parent == null ? owner : sourceOwner(parent);
    }

    List<String> nestedClasses(String owner) {
        return enclosing.entrySet().stream().filter(entry -> entry.getValue().equals(owner))
                .map(Map.Entry::getKey).toList();
    }

    String path(String owner) {
        return "classes/" + sourcePaths.get(sourceOwner(owner));
    }

    String method(MethodId id) {
        return Objects.requireNonNull(methods.get(id.name() + id.descriptor()), id.toString());
    }

    String field(String owner, String name, String descriptor) {
        return fields.get(owner + "." + name + descriptor);
    }

    void registerCalls(Collection<MethodId> methods) {
        calls.clear();
        var childNamespaces = new HashMap<String, Set<String>>();
        for(MethodId method : methods) {
            String[] parts = callOwnerPath(method.owner()).split("/");
            String parent = "";
            for(String part : parts) {
                childNamespaces.computeIfAbsent(parent, ignored -> new HashSet<>()).add(part);
                parent = parent.isEmpty() ? part : parent + "/" + part;
            }
        }
        var signatures = new HashMap<String, Set<String>>();
        for(MethodId method :
                methods.stream().sorted(Comparator.comparing(MethodId::toString)).toList()) {
            String base = identifier(method.name());
            if(childNamespaces
                    .getOrDefault(callOwnerPath(method.owner()), Set.of())
                    .contains(base)) base += "_method";
            String arguments =
                    Arrays.stream(Type.getArgumentTypes(method.descriptor()))
                            .map(CppEmitter::type)
                            .toList()
                            .toString();
            Set<String> used =
                    signatures.computeIfAbsent(method.owner(), ignored -> new HashSet<>());
            String candidate = base;
            for(int suffix = 2; !used.add(candidate + arguments); ++suffix)
                candidate = base + "_" + suffix;
            String owner = callOwnerPath(method.owner()).replace("/", "::");
            calls.put(method, "::jnative::java_api::" + owner + "::" + candidate);
        }
    }

    private String callOwnerPath(String owner) {
        return paths.getOrDefault(
                owner,
                String.join(
                        "/", Arrays.stream(owner.split("/")).map(CppNames::identifier).toList()));
    }

    String call(MethodId id) {
        return Objects.requireNonNull(calls.get(id), id.toString());
    }

    Set<MethodId> calls() {
        return calls.keySet();
    }

    static String unique(String base, Set<String> used) {
        String name = base;
        for(int suffix = 2; !used.add(name); ++suffix) name = base + "_" + suffix;
        return name;
    }

    private static String pathPart(String part) {
        String name = identifier(part.replace('$', '_'));
        if(name.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) name += "_type";
        return name;
    }

    static String identifier(String text) {
        if(text.matches("[A-Za-z][A-Za-z0-9_]*")
                && !text.contains("__")
                && !text.startsWith("java_")
                && !RESERVED.contains(text)) return text;
        // The reserved prefix and exact encoding prevent both collisions and C++
        // implementation-reserved identifiers (leading or doubled underscores).
        return "java_"
                + HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
    }
}
