package com.github.xpenatan.jnative.backend.cpp;

import com.github.xpenatan.jnative.CompilerException;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.ReadabilityFallback;
import com.github.xpenatan.jnative.compiler.*;
import com.github.xpenatan.jnative.compiler.Program.MethodId;
import com.github.xpenatan.jnative.internal.Json;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/**
 * Emits editable class methods, with an explicit low-level fallback for complex bytecode.
 */
public final class CppEmitter implements Opcodes {
    private static final int SUPPORT_SOURCE_LIMIT = 1024 * 1024;

    private static final class SupportSources {
        private final Map<String, String> sources;
        private final String preamble;
        private int index;

        private SupportSources(Map<String, String> sources, String preamble) {
            this.sources = sources;
            this.preamble = preamble;
        }

        // Called only after a complete function or a group with shared helpers.
        // A single group may exceed the limit; it must never be split internally.
        private StringBuilder boundary(StringBuilder out, boolean generatedNamespace) {
            if(out.length() < SUPPORT_SOURCE_LIMIT) return out;
            if(generatedNamespace) out.append("}\n\n");
            finish(out);
            return new StringBuilder(preamble)
                    .append(generatedNamespace ? "\nnamespace generated {\n" : "");
        }

        private void finish(StringBuilder out) {
            String path = index == 0 ? "runtime_support.cpp"
                    : String.format(Locale.ROOT, "runtime_support_%03d.cpp", index);
            sources.put(path, out.toString());
            index++;
        }
    }

    private Program program;
    private NativeBuildRequest request;
    private StringBuilder out = new StringBuilder();
    private Program.Method method;
    private int index;
    private CppNames names;
    private MethodEffects methodEffects;
    private CallTargets callTargets;
    private OwnedArrays ownedArrays;
    private final Map<String, Integer> classIds = new HashMap<>();
    private final Map<String, Integer> stringLiterals = new LinkedHashMap<>();
    private final Map<String, Integer> classLiterals = new LinkedHashMap<>();
    private final Set<MethodId> boundedRuntimeCalls = new HashSet<>();
    private final Map<MethodId, Boolean> boundedRuntimeEffects = new HashMap<>();
    private boolean readableLibraryCalls;
    private final List<ReadabilityFallback> readabilityFallbacks = new ArrayList<>();
    private static final MethodId TO_STRING =
            new MethodId("java/lang/Object", "toString", "()Ljava/lang/String;");
    private static String contentHash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch(NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public Map<String, String> emit(Program program, NativeBuildRequest request) {
        this.program = program;
        this.request = request;
        var substitutionOrigins = program.substitutions() == null
                ? Map.<MethodId, SubstitutionRegistry.MethodOrigin>of() : program.substitutions().methodOrigins();
        this.names = new CppNames(program, request.sourceLayout());
        this.callTargets = new CallTargets(program);
        this.ownedArrays = new OwnedArrays(program);
        classIds.clear();
        stringLiterals.clear();
        classLiterals.clear();
        boundedRuntimeCalls.clear();
        boundedRuntimeEffects.clear();
        for(String owner : new TreeSet<>(program.classes().keySet()))
            classIds.put(owner, classIds.size() + 1);
        this.out = new StringBuilder();
        readableLibraryCalls = false;
        readabilityFallbacks.clear();
        var sources = new LinkedHashMap<String, String>();
        sources.put("array-ownership.tsv", ownedArrays.report());
        sources.put("field-storage.tsv", ownedArrays.fieldReport());
        StringBuilder bindingReport = new StringBuilder("java-target\thelper\tsymbol\tinclude\tretained\n");
        for(PlatformBindings.Target target : program.bindings().targets().values())
            bindingReport.append(target.api()).append('\t').append(target.helper()).append('\t')
                    .append(target.binding() == null ? "java" : target.binding().symbol()).append('\t')
                    .append(target.binding() == null ? "" : target.binding().include()).append('\t')
                    .append(program.methods().containsKey(target.helper())).append('\n');
        for(PlatformBindings.FieldTarget target : program.bindings().fields().values())
            bindingReport.append(target.field().owner()).append('.').append(target.field().name()).append(':')
                    .append(target.field().descriptor()).append('\t').append(target.helper()).append('\t')
                    .append(target.binding().symbol()).append('\t').append(target.binding().include()).append('\t')
                    .append(program.methods().containsKey(target.helper())).append('\n');
        sources.put("platform-bindings.tsv", bindingReport.toString());
        if(program.substitutions() != null) {
            sources.put("substitutions.json", program.substitutions().reportJson(program.classes().keySet(), program.methods().keySet()));
            sources.put("substitutions.tsv", program.substitutions().reportTsv(program.classes().keySet(), program.methods().keySet()));
        }
        StringBuilder header =
                new StringBuilder(
                        "#pragma once\n#include \"jn_runtime.hpp\"\n\nnamespace generated {\n");
        StringBuilder exports =
                new StringBuilder(
                        "#ifndef JNATIVE_EXPORTS_H\n"
                                + "#define JNATIVE_EXPORTS_H\n"
                                + "#include \"jn_abi.h\"\n"
                                + "#ifdef __cplusplus\n"
                                + "extern \"C\" {\n"
                                + "#endif\n");
        for(Program.Method method : program.methods().values())
            if((method.bytecode().access & ACC_ABSTRACT) == 0)
                header.append(signature(method)).append(";\n");
        emitClasses(header, sources);
        emitTypeChecks(header, sources);
        header.append("void initialize_literals();\n");
        header.append("void initialize_class_literals();\n");
        header.append("void initialize_reflection();\n");
        line("#include \"application.hpp\"");
        line("#include \"java_api.hpp\"");
        line("#include \"jn_abi.hpp\"");
        line("#include \"jn_console.hpp\"");
        line("#include \"jnative_exports.h\"");
        line("#include <iostream>");
        line("#include <cmath>");
        line("#include <chrono>");
        line("#include <cstdlib>");
        var included = new LinkedHashSet<String>();
        for(ClassNode owner : program.classes().values()) {
            String include = Annotations.value(Annotations.all(owner.visibleAnnotations, owner.invisibleAnnotations), "NativeInclude");
            if(include != null) {
                if(!include.matches("[A-Za-z0-9_./-]+")
                        || include.startsWith("/")
                        || include.contains(".."))
                    throw new CompilerException("JN2001 Invalid native include: " + include);
                if(included.add(include)) line("#include \"" + include + "\"");
            }
        }
        StringBuilder imports = new StringBuilder(
                "#ifndef JNATIVE_IMPORTS_H\n#define JNATIVE_IMPORTS_H\n#include \"jn_abi.h\"\n#include <stdint.h>\n"
                        + "#ifdef __cplusplus\nextern \"C\" {\n#endif\n");
        for(Program.Method method : program.methods().values()) {
            if(method.nativeBinding() == null || method.nativeBinding().managed()) continue;
            String declaration = abiType(Type.getReturnType(method.id().descriptor()))
                    + " " + method.nativeSymbol() + "("
                    + String.join(", ", Arrays.stream(Type.getArgumentTypes(method.id().descriptor()))
                    .map(CppEmitter::abiType).toList()) + ");";
            line("extern \"C\" " + declaration);
            imports.append(declaration).append('\n');
        }
        imports.append("#ifdef __cplusplus\n}\n#endif\n#endif\n");
        sources.put("jnative_imports.h", imports.toString());
        String preamble = out.toString();
        SupportSources support = new SupportSources(sources, preamble);
        StringBuilder mappings =
                new StringBuilder("java-method\tcpp-symbol\tnative-file\tjava-source\n");
        StringBuilder readability = new StringBuilder("java-method\tform\tdetail\n");
        Map<MethodId, Map<String, Object>> methodMappings = new LinkedHashMap<>();
        line("\nnamespace generated {");
        header.append("void initialize_program();\n");
        int registeredTypes = 0;
        for(ClassNode owner : program.classes().values()) {
            if(registeredTypes % 64 == 0) {
                if(registeredTypes != 0) line("}");
                line("static void register_types_" + registeredTypes / 64 + "() {");
            }
            line(
                    "    ::jnative::register_type("
                            + quote(owner.name)
                            + ", "
                            + quote(owner.superName == null ? "" : owner.superName)
                            + ", {"
                            + String.join(
                            ", ", owner.interfaces.stream().map(CppEmitter::quote).toList())
                            + "}, "
                            + classIds.get(owner.name)
                            + ");");
            registeredTypes++;
        }
        if(registeredTypes != 0) line("}");
        emitRuntimeSubstitutionPolicy();
        line("void initialize_program() {");
        line("    static ::jnative::ClassInitialization state;");
        line("    state.run([] {");
        line("        ::jnative::initialize_runtime();");
        for(int batch = 0; batch * 64 < registeredTypes; batch++)
            line("        register_types_" + batch + "();");
        line("        initialize_type_checks();");
        line("        configure_substitutions();");
        line("        initialize_literals();");
        line("        initialize_reflection();");
        line("        initialize_class_literals();");
        line("    });");
        line("}");
        out = support.boundary(out, true);
        emitInitializers(support);
        emitDispatchers(header, support);
        emitMethodAdapters(support);
        emitClassSupport(header, support);
        line("}\n");
        methodEffects = new MethodEffects(program, this::boundedIntrinsic);
        // A body that falls back gains an entry poll. Prove the actual lowering
        // before any caller is allowed to omit roots based on its summary.
        Set<MethodId> fallbackEffects = new HashSet<>();
        Set<MethodId> tailLowerings = methodEffects.tailLowerings();
        for(Program.Method candidate : program.methods().values()) {
            if(!methodEffects.bounded(candidate.id())
                    && !methodEffects.boundedAfterInitialization(candidate.id())
                    && !tailLowerings.contains(candidate.id())) continue;
            if(new ReadableMethodEmitter(this, program, names, request, candidate).emit().source()
                    == null) fallbackEffects.add(candidate.id());
        }
        methodEffects.fallback(fallbackEffects);
        sources.put("method-effects.tsv", methodEffects.report());
        emitRuntimeAdapters(sources, support);
        support.finish(out);
        for(ClassNode owner : program.classes().values()) {
            String path = names.path(owner.name) + ".cpp";
            out =
                    new StringBuilder(sources.getOrDefault(path, preamble))
                            .append('\n')
                            .append(openNamespace(names.namespace(owner.name)));
            for(FieldNode field : owner.fields) {
                if((field.access & ACC_STATIC) == 0) continue;
                line(
                        fieldStorage(owner.name, field)
                                + " "
                                + names.definitionName(owner.name)
                                + "::"
                                + fieldName(owner.name, field.name, field.desc)
                                + "{"
                                + fieldValue(field)
                                + "};");
            }
            int sourceOffset = 0;
            int sourceLine = 1;
            for(Program.Method current : program.methods().values()) {
                if(!current.id().owner().equals(owner.name)
                        || (current.bytecode().access & ACC_ABSTRACT) != 0) continue;
                method = current;
                while(sourceOffset < out.length())
                    if(out.charAt(sourceOffset++) == '\n') sourceLine++;
                int firstLine = sourceLine;
                if(current.nativeSymbol() != null) {
                    emitMethod();
                    readability
                            .append(tableText(current.id().toString()))
                            .append(current.nativeBinding().managed() ? "\tnative-adapter\tmanaged C++ import\n" : "\tnative-adapter\tC ABI import\n");
                }
                else {
                    ReadableMethodEmitter.Result readable =
                            new ReadableMethodEmitter(this, program, names, request, current)
                                    .emit();
                    if(readable.source() != null) {
                        out.append(readable.source());
                        readability
                                .append(tableText(current.id().toString()))
                                .append("\tstructured\t\n");
                    }
                    else {
                        line("// Low-level fallback: " + comment(readable.reason()));
                        emitMethod();
                        readability
                                .append(tableText(current.id().toString()))
                                .append("\tlow-level\t")
                                .append(tableText(readable.reason()))
                                .append('\n');
                        readabilityFallbacks.add(
                                new ReadabilityFallback(
                                        current.id().toString(),
                                        request.generatedSourcesDirectory().resolve(path),
                                        readable.reason()));
                    }
                }
                while(sourceOffset < out.length())
                    if(out.charAt(sourceOffset++) == '\n') sourceLine++;
                methodMappings.put(
                        current.id(),
                        Map.of(
                                "javaMethod",
                                current.id().toString(),
                                "cppMethod",
                                names.method(current.id()),
                                "cppLineStart",
                                firstLine,
                                "cppLineEnd",
                                sourceLine - 1));
                if(program.substitutions() != null) {
                    var provenance = substitutionOrigins.get(current.id());
                    if(provenance != null) {
                        var mapping = new LinkedHashMap<>(methodMappings.get(current.id()));
                        mapping.put("substitution", Map.of("provider", provenance.providerId(), "artifact", provenance.artifact(),
                                "donor", provenance.donor().toString(), "classSha256", provenance.sha256(),
                                "previousImplementation", provenance.previousImplementation()));
                        methodMappings.put(current.id(), mapping);
                    }
                }
                mappings.append(tableText(current.id().toString()))
                        .append('\t')
                        .append(className(owner.name))
                        .append("::")
                        .append(memberName(current.id()))
                        .append('\t')
                        .append(path)
                        .append('\t')
                        .append(tableText(owner.sourceFile == null ? "" : owner.sourceFile))
                        .append('\n');
            }
            line(closeNamespace(names.namespace(owner.name)));
            sources.put(path, out.toString());
        }
        out = new StringBuilder(preamble);
        header.append("void invoke_main(::jnative::Object* arguments);\n");
        int literalSlots = Math.max(1, stringLiterals.size());
        header.append("extern ::jnative::StringLiteral string_literals[")
                .append(literalSlots)
                .append("];\n");
        sources.put("string_literals.cpp", emitStringLiterals(literalSlots));
        int classSlots = Math.max(1, classLiterals.size());
        header.append("extern ::jnative::ClassLiteral class_literals[")
                .append(classSlots)
                .append("];\n");
        sources.put("class_literals.cpp", emitClassLiterals(classSlots));
        header.append("}\n");
        line("void generated::invoke_main(::jnative::Object* arguments) {");
        line("    initialize_program();");
        line("    " + symbol(program.entry()) + "(arguments);");
        line("}\n");
        for(Program.Method current : program.methods().values()) {
            String exported =
                    Annotations.value(current.bytecode().invisibleAnnotations, "NativeExport");
            if(exported == null) continue;
            String signature = exportSignature(current, exported);
            exports.append("JNATIVE_PUBLIC ").append(signature).append(";\n");
            emitExport(current, signature);
        }
        exports.append(
                        "\n"
                                + "/* Host lifecycle. main receives only Java arguments, without a"
                                + " program name. */\n")
                .append("JNATIVE_PUBLIC jn_status jn_app_initialize(void);\n")
                .append(
                        "JNATIVE_PUBLIC jn_status jn_app_main(int count, const char* const*"
                                + " arguments);\n")
                .append("JNATIVE_PUBLIC jn_status jn_app_shutdown(void);\n")
                .append("#ifdef __cplusplus\n}\n#endif\n#endif\n");
        line("extern \"C\" jn_status jn_app_initialize(void) {");
        line("    return ::jnative::abi_call([] { generated::initialize_program(); });");
        line("}");
        line("extern \"C\" jn_status jn_app_main(int count, const char* const* arguments) {");
        line("    return ::jnative::abi_call([&] {");
        line(
                "        if (count < 0 || (count && !arguments)) throw"
                        + " std::invalid_argument(\"Invalid application arguments\");");
        line("        generated::initialize_program();");
        line(
                "        ::jnative::LocalRoot<>"
                        + " values(::jnative::new_array(\"[Ljava/lang/String;\", count));");
        line("        for (int index = 0; index < count; ++index) {");
        line(
                "            if (!arguments[index]) throw std::invalid_argument(\"Null application"
                        + " argument\");");
        line(
                "            ::jnative::LocalRoot<>"
                        + " value(::jnative::allocate<::jnative::String>(::jnative::utf16(arguments[index])));");
        line("            ::jnative::reference_set(values.get(), index, value.get());");
        line("        }");
        line("        generated::invoke_main(values.get());");
        line("    });");
        line("}");
        line("extern \"C\" jn_status jn_app_shutdown(void) { return jn_try_shutdown(); }");
        sources.put("application.cpp", out.toString());
        out = new StringBuilder(preamble);
        line("#ifdef _WIN32");
        line("int wmain(int argc, wchar_t** argv) {");
        line("#else");
        line("int main(int argc, char** argv) {");
        line("#endif");
        line("    ::jnative::DiagnosticSession diagnostics; ");
        line("    ::jnative::diagnostics_thread_name(\"main\");");
        line("    ::jnative::ThreadAttachment main_thread;");
        line("    int status = 0;");
        line("    try {");
        line("        generated::initialize_program();");
        line("        ::jnative::LocalRoot<> main_arguments(::jnative::arguments(argc, argv));");
        line("        generated::invoke_main(main_arguments.get());");
        line("    } catch (const ::jnative::Thrown& error) {");
        line("        ::jnative::print_stack_trace(error.object());");
        line("        status = 1;");
        line("    } catch (const std::exception& error) {");
        line("        ::jnative::report_native_exception(error, std::cerr);");
        line("        status = 1;");
        line("    }");
        line("    bool remaining_daemons = ::jnative::finish_java_main();");
        line("    jn_shutdown();");
        line("    ::jnative::Heap::instance().report_statistics();");
        line("#if JNATIVE_PAUSE_ON_EXIT");
        line("    ::jnative::pause_console_on_exit();");
        line("#endif");
        line("    if (remaining_daemons) ::jnative::exit_process(status);");
        line("    return status;");
        line("}");
        sources.put("application.hpp", header.toString());
        sources.put("reflection.cpp", new ReflectionEmitter(program, names).emit());
        sources.put("jnative_exports.h", exports.toString());
        sources.put("launcher.cpp", out.toString());
        sources.put("java-symbols.tsv", mappings.toString());
        sources.put("source-readability.tsv", readability.toString());
        sources.put("call-targets.tsv", callTargets.report());
        StringBuilder registrations = new StringBuilder("kind\tclass\tmember\tdescriptor\n");
        for(var registration : request.reflection())
            registrations
                    .append(registration.kind())
                    .append('\t')
                    .append(tableText(registration.className()))
                    .append('\t')
                    .append(tableText(registration.name()))
                    .append('\t')
                    .append(tableText(registration.descriptor()))
                    .append('\n');
        sources.put("reflection.tsv", registrations.toString());
        var sourceMap = new ArrayList<Map<String, Object>>();
        for(ClassNode owner : program.classes().values()) {
            String path = names.path(owner.name);
            var entry = new LinkedHashMap<String, Object>();
            entry.put("javaClass", owner.name.replace('/', '.'));
            entry.put("javaInternalName", owner.name);
            var origin = program.origins().get(owner.name);
            if(origin != null)
                entry.put(
                        "input",
                        Map.of(
                                "artifact",
                                origin.artifact(),
                                "member",
                                origin.member(),
                                "classSha256",
                                origin.classSha256(),
                                "generatedFrom",
                                origin.generatedFrom(),
                                "substitutionDonor",
                                origin.substitutionDonor()));
            entry.put(
                    "filenameTransformation",
                    !names.sourceOwner(owner.name).equals(owner.name)
                            ? "nested lambda; shares enclosing class source"
                            : owner.name.equals(path.substring("classes/".length()).replace('.', '/'))
                            ? "package-layout"
                            : "filesystem-safe identifier; see javaInternalName");
            entry.put(
                    "javaSource",
                    owner.sourceFile == null
                            ? ""
                            : (origin != null && origin.member().contains("/")
                                    ? origin.member().substring(0, origin.member().lastIndexOf('/') + 1)
                                    : owner.name.substring(0, owner.name.lastIndexOf('/') + 1))
                            + owner.sourceFile);
            entry.put(
                    "javaSourceKind", owner.sourceFile == null ? "unavailable" : "package-derived");
            entry.put("cppClass", names.className(owner.name));
            entry.put("cppFile", path + ".cpp");
            entry.put("headerFile", path + ".hpp");
            entry.put("sha256", contentHash(sources.get(path + ".cpp")));
            entry.put(
                    "methods",
                    program.methods().keySet().stream()
                            .filter(id -> id.owner().equals(owner.name))
                            .map(
                                    id ->
                                            methodMappings.containsKey(id)
                                                    ? methodMappings.get(id)
                                                    : Map.of(
                                                    "javaMethod",
                                                    id.toString(),
                                                    "cppMethod",
                                                    names.method(id)))
                            .toList());
            sourceMap.add(entry);
        }
        sources.put(
                "source-map.json",
                Json.write(
                        Map.of(
                                "schema",
                                1,
                                "layout",
                                request.sourceLayout().name(),
                                "classes",
                                sourceMap))
                        + "\n");
        return sources;
    }

    public List<ReadabilityFallback> readabilityFallbacks() {
        return List.copyOf(readabilityFallbacks);
    }

    private void emitMethod() {
        line("// " + comment(method.id().toString()));
        line(memberSignature(method, true) + " {");
        if((method.bytecode().access & ACC_STATIC) == 0 && !isInterface(method.id().owner()))
            line("    auto* self = this;");
        String sourceFile = program.classes().get(method.id().owner()).sourceFile;
        if(request.stackTraces().javaFrames())
            line(
                    "    ::jnative::JavaFrame java_frame("
                            + quote(
                            method.id().owner().replace('/', '.')
                                    + "."
                                    + method.id().name())
                            + ", "
                            + quote(
                            request.javaSourceLocations() && sourceFile != null
                                    ? sourceFile
                                    : "Unknown Source")
                            + ");");
        if(synchronizedReferenceReturn()) line("    ::jnative::LocalRoot<> synchronized_return;");
        if(method.nativeBinding() != null && method.nativeBinding().managed()) {
            emitManagedImport();
            line("}\n");
            return;
        }
        if(method.nativeSymbol() != null) {
            boolean leaf = method.nativeBinding().leaf();
            var args = new ArrayList<String>();
            var borrowed = new ArrayList<String>();
            Type[] parameters = Type.getArgumentTypes(method.id().descriptor());
            for(int i = 0; i < parameters.length; ++i) {
                if(reference(parameters[i])) {
                    line("    ::jnative::BorrowedHandle handle" + i + "(arg" + i + ");");
                    args.add("handle" + i + ".id()");
                    borrowed.add("handle" + i + ".id()");
                }
                else args.add("arg" + i);
            }
            line("    " + className(method.id().owner()) + "::ensure_initialized();");
            if((method.bytecode().access & ACC_SYNCHRONIZED) != 0)
                line(
                        "    ::jnative::MonitorGuard synchronized_method(::jnative::class_object("
                                + quote(method.id().owner())
                                + "));");
            Type result = Type.getReturnType(method.id().descriptor());
            boolean returns = !result.equals(Type.VOID_TYPE);
            if(returns) line("    " + abiType(result) + " native_result{};");
            if(leaf) line("    ::jnative::safepoint();");
            else line("    ::jnative::NativeCallError native_error;");
            line("    try {");
            line(
                    leaf
                            ? "        ::jnative::JavaLeafNativeRegion native_call;"
                            : "        ::jnative::JavaNativeRegion native_call;");
            line(
                    "        "
                            + (returns ? "native_result = " : "")
                            + "::"
                            + method.nativeSymbol()
                            + "("
                            + String.join(", ", args)
                            + ");");
            line("    } catch (const ::jnative::Thrown&) { throw; }");
            line("      catch (const std::exception& error) { ::jnative::raise_native(error); }");
            line(
                    "      catch (...) { ::jnative::raise(\"java/lang/RuntimeException\", \"Unknown"
                            + " native exception\"); }");
            if(reference(result))
                emitReturn(
                        (leaf ? "::jnative::" : "native_error.")
                                + "import_reference(native_result, "
                                + quote(result.getInternalName())
                                + ", {"
                                + String.join(", ", borrowed)
                                + "})");
            else {
                if(!leaf) line("    native_error.check();");
                if(returns) line("    return native_result;");
            }
            line("}\n");
            return;
        }
        declareSlots();
        int local = 0;
        if((method.bytecode().access & ACC_STATIC) == 0)
            assign("local", local++, Type.getType(Object.class), "self");
        Type[] arguments = Type.getArgumentTypes(method.id().descriptor());
        for(int arg = 0; arg < arguments.length; ++arg) {
            assign("local", local, arguments[arg], "arg" + arg);
            local += arguments[arg].getSize();
        }
        if((method.bytecode().access & ACC_STATIC) != 0 && !method.id().name().equals("<clinit>"))
            line("    " + initializer(method.id().owner()) + "();");
        if((method.bytecode().access & ACC_SYNCHRONIZED) != 0)
            line(
                    "    ::jnative::MonitorGuard synchronized_method("
                            + ((method.bytecode().access & ACC_STATIC) == 0
                            ? "self"
                            : "::jnative::class_object(" + quote(method.id().owner()) + ")")
                            + ");");
        boolean handlers = !method.bytecode().tryCatchBlocks.isEmpty();
        if(handlers) {
            line("    int instruction = -1;");
            line("    int resume_handler = -1;");
            line("    while (true) {");
            line("    try {");
            line("        switch (resume_handler) {");
            var targets = new TreeSet<Integer>();
            for(TryCatchBlockNode handler : method.bytecode().tryCatchBlocks)
                targets.add(method.bytecode().instructions.indexOf(handler.handler));
            for(int target : targets)
                line("            case " + target + ": goto block_" + target + ";");
            line("            default: break;");
            line("        }");
        }
        for(index = 0; index < method.bytecode().instructions.size(); ++index) {
            AbstractInsnNode instruction = method.bytecode().instructions.get(index);
            if(method.frames()[index] == null) continue;
            if(instruction instanceof LabelNode) line("block_" + index + ":;");
            if(instruction instanceof LineNumberNode source && request.javaSourceLocations()) {
                line(
                        "    // "
                                + comment(sourceFile == null ? "Unknown Source" : sourceFile)
                                + ":"
                                + source.line);
                line("    java_frame.line = " + source.line + ";");
            }
            if(instruction.getOpcode() >= 0) {
                if(handlers) line("    instruction = " + index + ";");
                emitInstruction(instruction, method.frames()[index]);
                clearConsumedRoots(instruction, method.frames()[index]);
            }
        }
        if(handlers) {
            line("    } catch (const ::jnative::Thrown& thrown) {");
            for(TryCatchBlockNode handler : method.bytecode().tryCatchBlocks) {
                int start = method.bytecode().instructions.indexOf(handler.start),
                        end = method.bytecode().instructions.indexOf(handler.end);
                int target = method.bytecode().instructions.indexOf(handler.handler);
                line(
                        "        if (instruction >= "
                                + start
                                + " && instruction < "
                                + end
                                + (handler.type == null
                                ? ""
                                : " && ::jnative::instance_of(thrown.object(), "
                                + typeCheckArgument(handler.type)
                                + ")")
                                + ") {");
                push(0, Type.getType(Object.class), "thrown.object()");
                line("            resume_handler = " + target + ";");
                line("            continue;");
                line("        }");
            }
            line("        throw;");
            line("    }");
            line("    }");
        }
        if(!Type.getReturnType(method.id().descriptor()).equals(Type.VOID_TYPE))
            line("    throw std::logic_error(\"Invalid fallthrough in generated method\");");
        line("}\n");
    }

    private void declareSlots() {
        // Slot types are independent: javac can reuse one local slot for unrelated types.
        for(String category : List.of("local", "stack")) {
            int count =
                    category.equals("local")
                            ? method.bytecode().maxLocals
                            : method.bytecode().maxStack;
            for(int slot = 0; slot < count; ++slot) {
                var types = new LinkedHashSet<Type>();
                for(Frame<BasicValue> frame : method.frames())
                    if(frame != null) {
                        if(category.equals("local")
                                && slot < frame.getLocals()
                                && frame.getLocal(slot).getType() != null)
                            types.add(normalize(frame.getLocal(slot).getType()));
                        if(category.equals("stack")
                                && slot < frame.getStackSize()
                                && frame.getStack(slot).getType() != null)
                            types.add(normalize(frame.getStack(slot).getType()));
                    }
                for(Type t : types)
                    line(
                            "    "
                                    + (reference(t) ? "::jnative::LocalRoot<>" : type(t))
                                    + " "
                                    + slot(category, slot, t)
                                    + "{};");
            }
        }
    }

    private void clearConsumedRoots(AbstractInsnNode instruction, Frame<BasicValue> before) {
        var after = new Frame<>(before);
        try {
            after.execute(instruction, new BasicInterpreter());
        } catch(AnalyzerException error) {
            throw new CompilerException(
                    "JN1004 Invalid frame transition at " + method.id() + ":" + index, error);
        }
        for(int i = 0; i < before.getStackSize(); ++i) {
            Type t = before.getStack(i).getType();
            if(t != null
                    && reference(t)
                    && (i >= after.getStackSize()
                    || after.getStack(i).getType() == null
                    || !reference(after.getStack(i).getType())))
                assign("stack", i, t, "nullptr");
        }
        for(int i = 0; i < before.getLocals(); ++i) {
            Type t = before.getLocal(i).getType();
            if(t != null
                    && reference(t)
                    && (after.getLocal(i).getType() == null
                    || !reference(after.getLocal(i).getType())))
                assign("local", i, t, "nullptr");
        }
    }

    private void emitInstruction(AbstractInsnNode instruction, Frame<BasicValue> frame) {
        int opcode = instruction.getOpcode(), size = frame.getStackSize();
        if(opcode == NOP) return;
        if(opcode >= ICONST_M1 && opcode <= ICONST_5) {
            push(size, Type.INT_TYPE, Integer.toString(opcode - ICONST_0));
            return;
        }
        if(opcode >= LCONST_0 && opcode <= LCONST_1) {
            push(size, Type.LONG_TYPE, "std::int64_t(" + (opcode - LCONST_0) + ")");
            return;
        }
        if(opcode >= FCONST_0 && opcode <= FCONST_2) {
            push(size, Type.FLOAT_TYPE, (opcode - FCONST_0) + ".0f");
            return;
        }
        if(opcode >= DCONST_0 && opcode <= DCONST_1) {
            push(size, Type.DOUBLE_TYPE, (opcode - DCONST_0) + ".0");
            return;
        }
        if(opcode == ACONST_NULL) {
            push(size, Type.getType(Object.class), "nullptr");
            return;
        }
        if(instruction instanceof IntInsnNode value && (opcode == BIPUSH || opcode == SIPUSH)) {
            push(size, Type.INT_TYPE, Integer.toString(value.operand));
            return;
        }
        if(instruction instanceof IntInsnNode value && opcode == NEWARRAY) {
            String descriptor =
                    switch(value.operand) {
                        case T_BOOLEAN -> "[Z";
                        case T_CHAR -> "[C";
                        case T_BYTE -> "[B";
                        case T_SHORT -> "[S";
                        case T_INT -> "[I";
                        case T_LONG -> "[J";
                        case T_FLOAT -> "[F";
                        case T_DOUBLE -> "[D";
                        default -> throw new CompilerException("JN1004 Invalid newarray type");
                    };
            push(
                    size - 1,
                    Type.getType(Object.class),
                    (ownedArrays.instructions(method.id()).contains(instruction)
                            ? "::jnative::new_confined_array<"
                            + primitiveArrayStorage(descriptor)
                            + ">("
                            : "::jnative::new_array(")
                            + quote(descriptor)
                            + ", "
                            + read("stack", size - 1, Type.INT_TYPE)
                            + ")");
            return;
        }
        if(instruction instanceof TypeInsnNode type) {
            if(opcode == NEW) {
                if(!program.platform(type.desc)) {
                    line("    " + initializer(type.desc) + "();");
                    push(
                            size,
                            Type.getType(Object.class),
                            "::jnative::allocate<" + className(type.desc) + ">()");
                }
                else if(RuntimeLibrary.throwable(type.desc))
                    push(
                            size,
                            Type.getType(Object.class),
                            "::jnative::allocate<::jnative::Throwable>(" + quote(type.desc) + ")");
                else if(type.desc.equals("java/lang/Object"))
                    push(
                            size,
                            Type.getType(Object.class),
                            "::jnative::allocate<::jnative::Object>()");
                else if(type.desc.equals("java/lang/String"))
                    push(
                            size,
                            Type.getType(Object.class),
                            "::jnative::allocate<::jnative::String>(std::u16string{})");
                else if(nativeBase(type.desc) != null)
                    push(
                            size,
                            Type.getType(Object.class),
                            "::jnative::allocate<" + nativeBase(type.desc) + ">()");
                else unsupported("allocation of " + type.desc);
                return;
            }
            if(opcode == ANEWARRAY) {
                String descriptor =
                        type.desc.startsWith("[") ? "[" + type.desc : "[L" + type.desc + ";";
                push(
                        size - 1,
                        Type.getType(Object.class),
                        "::jnative::new_array("
                                + quote(descriptor)
                                + ", "
                                + read("stack", size - 1, Type.INT_TYPE)
                                + ")");
                return;
            }
            if(opcode == CHECKCAST) {
                push(
                        size - 1,
                        Type.getType(Object.class),
                        "::jnative::check_cast("
                                + read("stack", size - 1, Type.getType(Object.class))
                                + ", "
                                + typeCheckArgument(type.desc)
                                + ")");
                return;
            }
            if(opcode == INSTANCEOF) {
                push(
                        size - 1,
                        Type.INT_TYPE,
                        "::jnative::instance_of("
                                + read("stack", size - 1, Type.getType(Object.class))
                                + ", "
                                + typeCheckArgument(type.desc)
                                + ")");
                return;
            }
        }
        if(instruction instanceof MultiANewArrayInsnNode array) {
            var dimensions = new ArrayList<String>();
            for(int i = 0; i < array.dims; ++i)
                dimensions.add(read("stack", size - array.dims + i, Type.INT_TYPE));
            push(
                    size - array.dims,
                    Type.getType(Object.class),
                    "::jnative::multi_array("
                            + quote(array.desc)
                            + ", {"
                            + String.join(", ", dimensions)
                            + "})");
            return;
        }
        if(opcode == ARRAYLENGTH) {
            push(
                    size - 1,
                    Type.INT_TYPE,
                    "::jnative::array_length("
                            + read("stack", size - 1, Type.getType(Object.class))
                            + ")");
            return;
        }
        if(opcode >= IALOAD && opcode <= SALOAD) {
            String array = read("stack", size - 2, Type.getType(Object.class)),
                    at = read("stack", size - 1, Type.INT_TYPE);
            Type t = arrayStackType(opcode - IALOAD);
            String access =
                    switch(opcode) {
                        case AALOAD -> "::jnative::reference_get";
                        case BALOAD -> "::jnative::byte_get";
                        default -> "::jnative::"
                                + (ownedArrays
                                .instructions(method.id())
                                .contains(instruction)
                                ? "confined_array_get<"
                                : "array_get<")
                                + arrayElementType(opcode - IALOAD)
                                + ">";
                    };
            push(size - 2, t, access + "(" + array + ", " + at + ")");
            return;
        }
        if(opcode >= IASTORE && opcode <= SASTORE) {
            String array = read("stack", size - 3, Type.getType(Object.class)),
                    at = read("stack", size - 2, Type.INT_TYPE);
            String value = read("stack", size - 1, arrayStackType(opcode - IASTORE));
            String access =
                    switch(opcode) {
                        case AASTORE -> "::jnative::reference_set";
                        case BASTORE -> "::jnative::byte_set";
                        default -> "::jnative::"
                                + (ownedArrays
                                .instructions(method.id())
                                .contains(instruction)
                                ? "confined_array_set<"
                                : "array_set<")
                                + arrayElementType(opcode - IASTORE)
                                + ">";
                    };
            if(opcode == SASTORE)
                value = "std::int16_t(((" + value + " & 65535) ^ 32768) - 32768)";
            line("    " + access + "(" + array + ", " + at + ", " + value + ");");
            return;
        }
        if(instruction instanceof LdcInsnNode constant) {
            Object value = constant.cst;
            if(value instanceof Integer i)
                push(
                        size,
                        Type.INT_TYPE,
                        "::jnative::signed32(UINT32_C(" + Integer.toUnsignedString(i) + "))");
            else if(value instanceof Long l)
                push(
                        size,
                        Type.LONG_TYPE,
                        "::jnative::signed64(UINT64_C(" + Long.toUnsignedString(l) + "))");
            else if(value instanceof Float f)
                push(size, Type.FLOAT_TYPE, floating(f.doubleValue(), true));
            else if(value instanceof Double d) push(size, Type.DOUBLE_TYPE, floating(d, false));
            else if(value instanceof String s)
                push(size, Type.getType(Object.class), stringLiteral(s));
            else if(value instanceof Type t)
                push(size, Type.getType(Object.class), classLiteral(t));
            else unsupported("constant " + value);
            return;
        }
        if(instruction instanceof VarInsnNode variable) {
            if(opcode >= ILOAD && opcode <= ALOAD) {
                Type t = frame.getLocal(variable.var).getType();
                push(size, t, read("local", variable.var, t));
                return;
            }
            if(opcode >= ISTORE && opcode <= ASTORE) {
                Type t = frame.getStack(size - 1).getType();
                assign("local", variable.var, t, read("stack", size - 1, t));
                return;
            }
        }
        if(instruction instanceof IincInsnNode increment) {
            String local = slot("local", increment.var, Type.INT_TYPE);
            line(
                    "    "
                            + local
                            + " = ::jnative::add("
                            + local
                            + ", std::int32_t("
                            + increment.incr
                            + "));");
            return;
        }
        if(opcode >= IADD && opcode <= DREM) {
            Type t = frame.getStack(size - 1).getType();
            String a = read("stack", size - 2, t), b = read("stack", size - 1, t);
            int operation = (opcode - IADD) / 4;
            String value;
            if(t.equals(Type.INT_TYPE) || t.equals(Type.LONG_TYPE)) {
                value =
                        "::jnative::"
                                + List.of("add", "sub", "mul", "divide", "remainder").get(operation)
                                + "("
                                + a
                                + ", "
                                + b
                                + ")";
            }
            else
                value =
                        operation == 4
                                ? "std::fmod(" + a + ", " + b + ")"
                                : a + " " + List.of("+", "-", "*", "/").get(operation) + " " + b;
            push(size - 2, t, value);
            return;
        }
        if(opcode >= INEG && opcode <= DNEG) {
            Type t = frame.getStack(size - 1).getType();
            String value = read("stack", size - 1, t);
            push(
                    size - 1,
                    t,
                    t.equals(Type.INT_TYPE) || t.equals(Type.LONG_TYPE)
                            ? "::jnative::sub(" + type(t) + "(0), " + value + ")"
                            : "-" + value);
            return;
        }
        if(opcode >= ISHL && opcode <= LUSHR) {
            Type t = frame.getStack(size - 2).getType();
            push(
                    size - 2,
                    t,
                    "::jnative::"
                            + List.of("shift_left", "shift_right", "unsigned_shift")
                            .get((opcode - ISHL) / 2)
                            + "("
                            + read("stack", size - 2, t)
                            + ", "
                            + read("stack", size - 1, Type.INT_TYPE)
                            + ")");
            return;
        }
        if(opcode >= IAND && opcode <= LXOR) {
            Type t = frame.getStack(size - 1).getType();
            push(
                    size - 2,
                    t,
                    read("stack", size - 2, t)
                            + " "
                            + List.of("&", "|", "^").get((opcode - IAND) / 2)
                            + " "
                            + read("stack", size - 1, t));
            return;
        }
        if(opcode >= I2L && opcode <= I2S) {
            Type source = frame.getStack(size - 1).getType();
            Type target =
                    switch(opcode) {
                        case I2L, F2L, D2L -> Type.LONG_TYPE;
                        case I2F, L2F, D2F -> Type.FLOAT_TYPE;
                        case I2D, L2D, F2D -> Type.DOUBLE_TYPE;
                        default -> Type.INT_TYPE;
                    };
            String value = read("stack", size - 1, source);
            if(opcode == L2I)
                value = "::jnative::signed32(static_cast<std::uint32_t>(" + value + "))";
            else if(opcode == I2B || opcode == I2S) {
                int mask = opcode == I2B ? 255 : 65535, sign = opcode == I2B ? 128 : 32768;
                value = "((" + value + " & " + mask + ") ^ " + sign + ") - " + sign;
            }
            else if(opcode == I2C) value = value + " & 65535";
            else if((source.equals(Type.FLOAT_TYPE) || source.equals(Type.DOUBLE_TYPE))
                    && (target.equals(Type.INT_TYPE) || target.equals(Type.LONG_TYPE)))
                value = "::jnative::float_to_integer<" + type(target) + ">(" + value + ")";
            else value = "static_cast<" + type(target) + ">(" + value + ")";
            push(size - 1, target, value);
            return;
        }
        if(opcode >= LCMP && opcode <= DCMPG) {
            Type t = frame.getStack(size - 1).getType();
            String a = read("stack", size - 2, t), b = read("stack", size - 1, t);
            String expression = "(" + a + " > " + b + " ? 1 : (" + a + " == " + b + " ? 0 : -1))";
            if(opcode == FCMPG || opcode == DCMPG)
                expression =
                        "(std::isnan(" + a + ") || std::isnan(" + b + ") ? 1 : " + expression + ")";
            push(size - 2, Type.INT_TYPE, expression);
            return;
        }
        if(instruction instanceof JumpInsnNode jump) {
            String destination = "block_" + method.bytecode().instructions.indexOf(jump.label);
            String condition = null;
            if(opcode >= IFEQ && opcode <= IFLE)
                condition =
                        read("stack", size - 1, Type.INT_TYPE)
                                + " "
                                + comparison(opcode - IFEQ)
                                + " 0";
            else if(opcode >= IF_ICMPEQ && opcode <= IF_ICMPLE)
                condition =
                        read("stack", size - 2, Type.INT_TYPE)
                                + " "
                                + comparison(opcode - IF_ICMPEQ)
                                + " "
                                + read("stack", size - 1, Type.INT_TYPE);
            else if(opcode == IFNULL || opcode == IFNONNULL)
                condition =
                        read("stack", size - 1, Type.getType(Object.class))
                                + (opcode == IFNULL ? " == " : " != ")
                                + "nullptr";
            else if(opcode == IF_ACMPEQ || opcode == IF_ACMPNE)
                condition =
                        read("stack", size - 2, Type.getType(Object.class))
                                + (opcode == IF_ACMPEQ ? " == " : " != ")
                                + read("stack", size - 1, Type.getType(Object.class));
            else if(opcode != GOTO) unsupported("jump opcode " + opcode);
            if(method.bytecode().instructions.indexOf(jump.label) <= index)
                line("    ::jnative::safepoint();");
            line(
                    "    "
                            + (condition == null ? "" : "if (" + condition + ") ")
                            + "goto "
                            + destination
                            + ";");
            return;
        }
        if(instruction instanceof TableSwitchInsnNode table) {
            if(method.bytecode().instructions.indexOf(table.dflt) <= index
                    || table.labels.stream()
                    .anyMatch(
                            label ->
                                    method.bytecode().instructions.indexOf(label) <= index))
                line("    ::jnative::safepoint();");
            line("    switch (" + read("stack", size - 1, Type.INT_TYPE) + ") {");
            for(int i = 0; i < table.labels.size(); ++i)
                line(
                        "        case "
                                + (table.min + i)
                                + ": goto block_"
                                + method.bytecode().instructions.indexOf(table.labels.get(i))
                                + ";");
            line(
                    "        default: goto block_"
                            + method.bytecode().instructions.indexOf(table.dflt)
                            + ";");
            line("    }");
            return;
        }
        if(instruction instanceof LookupSwitchInsnNode table) {
            if(method.bytecode().instructions.indexOf(table.dflt) <= index
                    || table.labels.stream()
                    .anyMatch(
                            label ->
                                    method.bytecode().instructions.indexOf(label) <= index))
                line("    ::jnative::safepoint();");
            line("    switch (" + read("stack", size - 1, Type.INT_TYPE) + ") {");
            for(int i = 0; i < table.labels.size(); ++i)
                line(
                        "        case "
                                + table.keys.get(i)
                                + ": goto block_"
                                + method.bytecode().instructions.indexOf(table.labels.get(i))
                                + ";");
            line(
                    "        default: goto block_"
                            + method.bytecode().instructions.indexOf(table.dflt)
                            + ";");
            line("    }");
            return;
        }
        if(opcode == POP || opcode == POP2) return;
        if(opcode >= DUP && opcode <= SWAP) {
            stackPermutation(opcode, frame);
            return;
        }
        if(opcode == RETURN) {
            line("    return;");
            return;
        }
        if(opcode == MONITORENTER || opcode == MONITOREXIT) {
            line(
                    "    ::jnative::"
                            + (opcode == MONITORENTER ? "monitor_enter" : "monitor_exit")
                            + "("
                            + read("stack", size - 1, Type.getType(Object.class))
                            + ");");
            return;
        }
        if(opcode == ATHROW) {
            line(
                    "    ::jnative::throw_object("
                            + read("stack", size - 1, Type.getType(Object.class))
                            + ");");
            return;
        }
        if(opcode >= IRETURN && opcode <= ARETURN) {
            emitReturn(read("stack", size - 1, frame.getStack(size - 1).getType()));
            return;
        }
        if(instruction instanceof FieldInsnNode field && opcode == GETSTATIC
                && program.bindings().field(field.owner, field.name, field.desc) != null) {
            push(size, Type.getType(field.desc), platformFieldCall(field));
            return;
        }
        if(instruction instanceof FieldInsnNode field
                && opcode == GETSTATIC
                && program.primitiveClassField(field.owner, field.name, field.desc)) {
            push(
                    size,
                    Type.getType(Object.class),
                    classLiteral(Type.getType(RuntimeLibrary.primitive(field.owner))));
            return;
        }
        if(instruction instanceof FieldInsnNode field) {
            String owner = fieldOwner(field.owner, field.name, field.desc);
            Type t = Type.getType(field.desc);
            boolean isStatic = opcode == GETSTATIC || opcode == PUTSTATIC;
            boolean put = opcode == PUTFIELD || opcode == PUTSTATIC;
            if(isStatic) line("    " + initializer(owner) + "();");
            String access =
                    isStatic
                            ? className(owner) + "::"
                            : "static_cast<"
                            + className(owner)
                            + "*>(::jnative::require_non_null("
                            + read(
                            "stack",
                            size - (put ? 2 : 1),
                            Type.getType(Object.class))
                            + "))->";
            access += fieldName(owner, field.name, field.desc);
            if(put) line("    " + access + ".set(" + narrow(t, read("stack", size - 1, t)) + ");");
            else push(size - (isStatic ? 0 : 1), t, access + ".get()");
            return;
        }
        if(instruction instanceof MethodInsnNode call) {
            call(call, frame);
            return;
        }
        if(instruction instanceof InvokeDynamicInsnNode dynamic) {
            concatenate(dynamic, frame);
            return;
        }
        unsupported("opcode " + opcode);
    }

    private void call(MethodInsnNode call, Frame<BasicValue> frame) {
        MethodId declaration = resolve(call.owner, call.name, call.desc);
        if(program.platform(declaration.owner()))
            call =
                    new MethodInsnNode(
                            call.getOpcode(), declaration.owner(), call.name, call.desc, call.itf);
        Type[] types = Type.getArgumentTypes(call.desc);
        boolean isStatic = call.getOpcode() == INVOKESTATIC;
        int start = frame.getStackSize() - types.length - (isStatic ? 0 : 1);
        if(!isStatic)
            line(
                    "    ::jnative::require_non_null("
                            + read("stack", start, Type.getType(Object.class))
                            + ");");
        var args = new ArrayList<String>();
        for(int i = 0; i < types.length; ++i)
            args.add(read("stack", start + (isStatic ? 0 : 1) + i, types[i]));
        String expression;
        if(call.owner.equals("java/lang/Object") && call.name.equals("<init>")) {
            line(
                    "    ::jnative::require_non_null("
                            + read("stack", start, Type.getType(Object.class))
                            + ");");
            return;
        }
        else if(!hasBinding(call) && RuntimeLibrary.intrinsic(call.owner, call.name, call.desc)) {
            expression = libraryCall(call, null, args);
        }
        else if(program.platform(call.owner)) {
            if(!isStatic
                    && !call.name.equals("<init>")
                    && !call.owner.equals("java/lang/String")
                    && !boundedRuntimeCall(call)
                    && call.getOpcode() != INVOKESPECIAL) {
                args.addFirst(read("stack", start, Type.getType(Object.class)));
                expression =
                        dispatchName(new MethodId(call.owner, call.name, call.desc))
                                + "("
                                + String.join(", ", args)
                                + ")";
            }
            else
                expression =
                        libraryCall(
                                call,
                                isStatic ? null : read("stack", start, Type.getType(Object.class)),
                                args);
        }
        else {
            MethodId id = resolve(call.owner, call.name, call.desc);
            MethodId direct = directTarget(call);
            if(direct != null) id = direct;
            if(!isStatic) args.addFirst(read("stack", start, Type.getType(Object.class)));
            boolean virtual =
                    direct == null
                            && (call.getOpcode() == INVOKEVIRTUAL
                            || call.getOpcode() == INVOKEINTERFACE);
            if((program.methods().get(id).bytecode().access & (ACC_PRIVATE | ACC_FINAL)) != 0)
                virtual = false;
            expression =
                    (virtual
                            ? dispatchName(new MethodId(call.owner, call.name, call.desc))
                            : symbol(id))
                            + "("
                            + String.join(", ", args)
                            + ")";
        }
        Type result = Type.getReturnType(call.desc);
        if(result.equals(Type.VOID_TYPE)) line("    " + expression + ";");
        else push(start, result, expression);
    }

    boolean hasBinding(MethodInsnNode call) {
        if(program.platform(call.owner)) return false;
        MethodId id = MethodResolver.resolve(call.owner, call.name, call.desc, program.classes()::get, program::platform, program.bindings());
        Program.Method target = program.methods().get(id);
        return target != null && target.nativeBinding() != null;
    }

    private void emitManagedImport() {
        NativeBinding binding = method.nativeBinding();
        boolean nativeRoots = binding.managesRoots() && (method.bytecode().access & ACC_SYNCHRONIZED) == 0
                && trivialManagedImportInitialization(method.id().owner(), new HashSet<>());
        Type[] parameters = Type.getArgumentTypes(method.id().descriptor());
        var args = new ArrayList<String>();
        for(int i = 0; i < parameters.length; ++i) {
            if(reference(parameters[i]) && !nativeRoots) {
                line("    ::jnative::LocalRoot<> native_arg" + i + "(arg" + i + ");");
                args.add("native_arg" + i + ".get()");
            }
            else args.add("arg" + i);
        }
        // Publish arguments before initialization or a collecting operation.
        if(!nativeRoots) line("    " + className(method.id().owner()) + "::ensure_initialized();");
        if((method.bytecode().access & ACC_SYNCHRONIZED) != 0)
            line("    ::jnative::MonitorGuard synchronized_method(::jnative::class_object(" + quote(method.id().owner()) + "));");
        if(!nativeRoots && !binding.bounded()) line("    ::jnative::safepoint();");
        for(NativeBinding.Callback callback : binding.callbacks()) args.add(callbackPointer(callback));
        for(NativeBinding.Field field : binding.fields()) {
            String owner = className(field.owner());
            String member = fieldName(field.owner(), field.name(), field.descriptor());
            args.add("::jnative::NativeFieldAccess<" + owner + ", decltype(" + owner + "::" + member + ")>(&" + owner + "::" + member + ")");
        }
        Type result = Type.getReturnType(method.id().descriptor());
        line("    try {");
        line("        " + (result.equals(Type.VOID_TYPE) ? "" : "return ") + "::" + binding.symbol()
                + "(" + String.join(", ", args) + ");");
        line("    } catch (const ::jnative::Thrown&) { throw; }");
        line("    catch (const std::bad_alloc&) { ::jnative::raise(\"java/lang/OutOfMemoryError\"); }");
        line("    catch (const std::exception& error) { ::jnative::raise_native(error); }");
    }

    private String callbackPointer(NativeBinding.Callback callback) {
        MethodId id = callback.method();
        var parameterTypes = new ArrayList<String>();
        var declarations = new ArrayList<String>();
        var args = new ArrayList<String>();
        boolean instance = callback.invocation() != NativeBinding.Invocation.STATIC;
        if(instance) {
            parameterTypes.add("::jnative::Object*");
            declarations.add("::jnative::Object* receiver");
        }
        Type[] types = Type.getArgumentTypes(id.descriptor());
        for(int i = 0; i < types.length; ++i) {
            parameterTypes.add(type(types[i]));
            declarations.add(type(types[i]) + " arg" + i);
            args.add("arg" + i);
        }
        String result = type(Type.getReturnType(id.descriptor()));
        MethodId target = resolve(id.owner(), id.name(), id.descriptor());
        String call;
        if(callback.invocation() == NativeBinding.Invocation.VIRTUAL || callback.invocation() == NativeBinding.Invocation.INTERFACE) {
            Program.Method declaration = program.methods().get(target);
            if(callback.invocation() == NativeBinding.Invocation.VIRTUAL
                    && !program.platform(id.owner()) && !isInterface(id.owner())
                    && !program.platform(target.owner()) && !isInterface(target.owner())
                    && declaration != null && (declaration.bytecode().access & (ACC_STATIC | ACC_PRIVATE)) == 0) {
                // The native driver already roots live callback arguments. Use the generated
                // virtual member so source-class overrides dispatch without another root frame.
                call = "static_cast<" + className(target.owner()) + "*>(::jnative::require_non_null(receiver))->"
                        + memberName(target) + "(" + String.join(", ", args) + ")";
            } else
                return "static_cast<" + result + " (*)(" + String.join(", ", parameterTypes) + ")>(" + names.call(id) + ")";
        } else if(program.platform(target.owner()) || !program.methods().containsKey(target))
            call = libraryCall(new MethodInsnNode(instance ? INVOKESPECIAL : INVOKESTATIC,
                    target.owner(), target.name(), target.descriptor(), false), instance ? "receiver" : null, args);
        else {
            if(instance) args.addFirst("receiver");
            call = symbol(target) + "(" + String.join(", ", args) + ")";
        }
        return "+[](" + String.join(", ", declarations) + ") -> " + result + " { "
                + (result.equals("void") ? "" : "return ") + call + "; }";
    }

    private void stackPermutation(int opcode, Frame<BasicValue> frame) {
        int n = frame.getStackSize();
        var values = new ArrayList<Integer>();
        for(int i = 0; i < n; ++i) values.add(i);
        int a = values.removeLast();
        switch(opcode) {
            case DUP -> {
                values.add(a);
                values.add(a);
            }
            case SWAP -> {
                int b = values.removeLast();
                values.add(a);
                values.add(b);
            }
            case DUP_X1 -> {
                int b = values.removeLast();
                values.addAll(List.of(a, b, a));
            }
            case DUP_X2 -> {
                int b = values.removeLast();
                if(frame.getStack(b).getSize() == 2) values.addAll(List.of(a, b, a));
                else {
                    int c = values.removeLast();
                    values.addAll(List.of(a, c, b, a));
                }
            }
            case DUP2 -> {
                if(frame.getStack(a).getSize() == 2) values.addAll(List.of(a, a));
                else {
                    int b = values.removeLast();
                    values.addAll(List.of(b, a, b, a));
                }
            }
            case DUP2_X1, DUP2_X2 -> {
                var top = new ArrayList<Integer>();
                if(frame.getStack(a).getSize() == 1) top.add(values.removeLast());
                top.add(a);
                int b = values.removeLast();
                var below = new ArrayList<Integer>();
                if(opcode == DUP2_X2 && frame.getStack(b).getSize() == 1)
                    below.add(values.removeLast());
                below.add(b);
                values.addAll(top);
                values.addAll(below);
                values.addAll(top);
            }
            default -> unsupported("stack permutation " + opcode);
        }
        line("    {");
        for(int i = 0; i < n; ++i)
            line(
                    "        auto temporary_"
                            + i
                            + " = "
                            + read("stack", i, frame.getStack(i).getType())
                            + ";");
        for(int i = 0; i < values.size(); ++i)
            push(i, frame.getStack(values.get(i)).getType(), "temporary_" + values.get(i));
        line("    }");
    }

    MethodId resolve(String owner, String name, String descriptor) {
        MethodId id = MethodResolver.resolve(owner, name, descriptor, program.classes()::get, program::platform, program.bindings());
        if(program.methods().containsKey(id)
                || RuntimeLibrary.intrinsic(id.owner(), name, descriptor)
                || (program.platform(id.owner())
                && program.bindings().find(id) != null)) return id;
        throw new CompilerException("JN1002 Unresolved method " + owner + "." + name + descriptor);
    }

    String typeCheckArgument(String owner) {
        return quote(owner)
                + (classIds.containsKey(owner) ? ", " + className(owner) + "::class_id" : "");
    }

    private void emitTypeChecks(StringBuilder header, Map<String, String> sources) {
        int count = classIds.size();
        int stride = (count + 64) / 64;
        StringBuilder table =
                new StringBuilder("#include \"jn_runtime.hpp\"\n\nnamespace generated {\n");
        table.append("namespace {\nconst std::uint64_t type_ancestry[] = {\n");
        table.append("    ")
                .append(String.join(", ", Collections.nCopies(stride, "0")))
                .append(",\n");
        for(String owner : new TreeSet<>(classIds.keySet())) {
            var ancestors = new HashSet<String>();
            collectAncestors(owner, ancestors);
            long[] row = new long[stride];
            for(String ancestor : ancestors) {
                Integer id = classIds.get(ancestor);
                if(id != null) row[id / 64] |= 1L << (id % 64);
            }
            table.append("    // ").append(comment(owner)).append('\n').append("    ");
            for(long word : row)
                table.append("UINT64_C(0x").append(Long.toHexString(word)).append("), ");
            table.append('\n');
        }
        table.append("};\n}\nvoid initialize_type_checks() {\n")
                .append("    ::jnative::register_type_checks(type_ancestry, ")
                .append(count)
                .append(", ")
                .append(stride)
                .append(");\n}\n}\n");
        header.append("void initialize_type_checks();\n");
        sources.put("type_checks.cpp", table.toString());
    }

    private void collectAncestors(String owner, Set<String> result) {
        if(owner == null || !result.add(owner)) return;
        ClassNode type = program.classes().get(owner);
        if(type == null) {
            collectAncestors(RuntimeLibrary.parent(owner), result);
            return;
        }
        collectAncestors(type.superName, result);
        for(String implemented : type.interfaces) collectAncestors(implemented, result);
    }

    private void emitClasses(StringBuilder header, Map<String, String> sources) {
        var ordered = new ArrayList<ClassNode>();
        var seen = new HashSet<String>();
        for(ClassNode node : program.classes().values()) orderClass(node, seen, ordered);
        StringBuilder includes = new StringBuilder();
        for(ClassNode node : ordered) {
            header.append("inline void ")
                    .append(initializer(node.name))
                    .append("() { ")
                    .append(className(node.name))
                    .append("::ensure_initialized(); }\n");
            if(program.platform(node.superName == null ? "" : node.superName)
                    && nativeBase(node.superName) == null)
                throw new CompilerException("JN1003 Unsupported superclass " + node.superName);
            String base =
                    node.superName == null
                            ? "::jnative::Object"
                            : nativeBase(node.superName) != null
                            ? nativeBase(node.superName)
                            : className(node.superName);
            StringBuilder declaration =
                    new StringBuilder(sources.containsKey(names.path(node.name) + ".hpp")
                            ? "" : "#pragma once\n#include \"jn_runtime.hpp\"\n");
            if(program.classes().containsKey(node.superName)
                    && !names.path(node.name).equals(names.path(node.superName)))
                declaration
                        .append("#include \"")
                        .append(names.path(node.superName))
                        .append(".hpp\"\n");
            declaration.append('\n').append(openNamespace(names.namespace(node.name)));
            declaration
                    .append("struct ")
                    .append(names.definitionName(node.name))
                    .append((node.access & ACC_FINAL) != 0 ? " final" : "")
                    .append(" : ")
                    .append(base)
                    .append(" {\n");
            for(String nested : names.nestedClasses(node.name))
                declaration.append("    struct ").append(names.simpleName(nested)).append(";\n");
            declaration
                    .append("    static constexpr std::uint32_t class_id = ")
                    .append(classIds.get(node.name))
                    .append(";\n")
                    .append("    ")
                    .append(names.simpleName(node.name))
                    .append("() { ::jnative::Object::set_type_id(class_id); }\n");
            for(FieldNode field : node.fields) {
                boolean isStatic = (field.access & ACC_STATIC) != 0;
                declaration.append("    ");
                if(isStatic) declaration.append("static ");
                declaration
                        .append(fieldStorage(node.name, field))
                        .append(' ')
                        .append(fieldName(node.name, field.name, field.desc));
                if(!isStatic) declaration.append('{').append(fieldValue(field)).append('}');
                declaration.append(";\n");
            }
            declaration.append('\n');
            var ownMembers = new HashSet<String>();
            for(Program.Method m : program.methods().values())
                if(m.id().owner().equals(node.name) && !m.id().name().startsWith("<"))
                    ownMembers.add(memberName(m.id()));
            var inheritedOverloads = new TreeSet<String>();
            for(String parent = node.superName;
                program.classes().containsKey(parent);
                parent = program.classes().get(parent).superName)
                for(Program.Method m : program.methods().values())
                    if(m.id().owner().equals(parent)
                            && !m.id().name().startsWith("<")
                            && ownMembers.contains(memberName(m.id())))
                        inheritedOverloads.add(memberName(m.id()));
            for(String inherited : inheritedOverloads)
                declaration
                        .append("    using ")
                        .append(base)
                        .append("::")
                        .append(inherited)
                        .append(";\n");
            for(Program.Method m : program.methods().values()) {
                if(!m.id().owner().equals(node.name)) continue;
                boolean abstractMethod = (m.bytecode().access & ACC_ABSTRACT) != 0;
                if(isInterface(node.name) && abstractMethod) continue;
                declaration.append("    ");
                if((m.bytecode().access & ACC_STATIC) != 0 || isInterface(node.name))
                    declaration.append("static ");
                else if(!m.id().name().startsWith("<") && (m.bytecode().access & ACC_PRIVATE) == 0)
                    declaration.append("virtual ");
                declaration
                        .append(type(Type.getReturnType(m.id().descriptor())))
                        .append(' ')
                        .append(memberName(m.id()))
                        .append('(')
                        .append(sourceParameters(m))
                        .append(')')
                        .append(abstractMethod ? " = 0;\n" : ";\n");
                if(m.id().name().equals("<init>")
                        && (node.access & (ACC_ABSTRACT | ACC_INTERFACE)) == 0)
                    declaration
                            .append("    static ")
                            .append(names.simpleName(node.name))
                            .append("* ")
                            .append(factoryMember(m.id()))
                            .append('(')
                            .append(sourceParameters(m))
                            .append(");\n");
            }
            declaration
                    .append(
                            "\n"
                                    + "    static std::atomic<bool> initialization_complete;\n"
                                    + "    static JNATIVE_NOINLINE void initialize_slow();\n"
                                    + "    static void ensure_initialized() {\n"
                                    + "        if"
                                    + " (!initialization_complete.load(std::memory_order_acquire))"
                                    + " initialize_slow();\n"
                                    + "    }\n"
                                    + "    const char* type_name() const override;\n"
                                    + "    void trace(::jnative::Tracer& tracer) override;\n"
                                    + "};\n")
                    .append(closeNamespace(names.namespace(node.name)));
            String path = names.path(node.name) + ".hpp";
            sources.merge(path, declaration.toString(), String::concat);
            if(names.sourceOwner(node.name).equals(node.name))
                includes.append("#include \"").append(path).append("\"\n");
        }
        header.insert(header.indexOf("namespace generated"), includes.append('\n'));
    }

    private void emitMethodAdapters(SupportSources support) {
        for(Program.Method current : program.methods().values()) {
            if((current.bytecode().access & ACC_ABSTRACT) != 0) continue;
            var args = new ArrayList<String>();
            for(int i = 0; i < Type.getArgumentTypes(current.id().descriptor()).length; ++i)
                args.add("arg" + i);
            String target = className(current.id().owner()) + "::" + memberName(current.id());
            if((current.bytecode().access & ACC_STATIC) == 0) {
                if(isInterface(current.id().owner())) args.addFirst("self");
                else
                    target =
                            "static_cast<"
                                    + className(current.id().owner())
                                    + "*>(::jnative::require_non_null(self))->"
                                    + names.simpleName(current.id().owner())
                                    + "::"
                                    + memberName(current.id());
            }
            line(signature(current) + " {");
            line(
                    "    "
                            + (Type.getReturnType(current.id().descriptor()).equals(Type.VOID_TYPE)
                            ? ""
                            : "return ")
                            + target
                            + "("
                            + String.join(", ", args)
                            + ");");
            line("}\n");
            out = support.boundary(out, true);
        }
    }

    private void emitClassSupport(StringBuilder header, SupportSources support) {
        for(ClassNode node : program.classes().values()) {
            String qualified = className(node.name).substring("::generated::".length());
            String base =
                    program.classes().containsKey(node.superName)
                            ? className(node.superName)
                            : nativeBase(
                            node.superName == null ? "java/lang/Object" : node.superName);
            line("std::atomic<bool> " + qualified + "::initialization_complete{false};");
            line(
                    "const char* "
                            + qualified
                            + "::type_name() const { return "
                            + quote(node.name)
                            + "; }");
            line("void " + qualified + "::trace(::jnative::Tracer& tracer) {");
            line("    " + base + "::trace(tracer);");
            for(FieldNode field : node.fields)
                if((field.access & ACC_STATIC) == 0 && reference(Type.getType(field.desc)))
                    line(
                            "    tracer.visit("
                                    + fieldName(node.name, field.name, field.desc)
                                    + ".get());");
            line("}\n");
            for(Program.Method current : program.methods().values()) {
                if(!current.id().owner().equals(node.name)
                        || !current.id().name().equals("<init>")
                        || (node.access & (ACC_ABSTRACT | ACC_INTERFACE)) != 0) continue;
                emitFactory(current, header);
                var args = new ArrayList<String>();
                for(int i = 0; i < Type.getArgumentTypes(current.id().descriptor()).length; ++i)
                    args.add("arg" + i);
                line(
                        className(node.name)
                                + "* "
                                + qualified
                                + "::"
                                + factoryMember(current.id())
                                + "("
                                + parameters(current, false)
                                + ") {");
                line(
                        "    return make_"
                                + symbol(current.id())
                                + "("
                                + String.join(", ", args)
                                + ");");
                line("}\n");
            }
            out = support.boundary(out, true);
        }
    }

    boolean isInterface(String owner) {
        return (program.classes().get(owner).access & ACC_INTERFACE) != 0;
    }

    private String sourceParameters(Program.Method current) {
        var parameters = new ArrayList<String>();
        var used = new HashSet<String>();
        int slot = 0;
        if((current.bytecode().access & ACC_STATIC) == 0) {
            ++slot;
            if(isInterface(current.id().owner())) {
                parameters.add("::jnative::Object* self");
                used.add("self");
            }
        }
        Type[] arguments = Type.getArgumentTypes(current.id().descriptor());
        for(int i = 0; i < arguments.length; ++i) {
            String name = "arg" + i;
            if(current.bytecode().localVariables != null)
                for(LocalVariableNode local : current.bytecode().localVariables)
                    if(local.index == slot) {
                        name = CppNames.identifier(local.name);
                        break;
                    }
            parameters.add(type(arguments[i]) + " " + CppNames.unique(name, used));
            slot += arguments[i].getSize();
        }
        return String.join(", ", parameters);
    }

    private String factoryMember(MethodId constructor) {
        return memberName(constructor).replaceFirst("initialize", "create");
    }

    private String parameters(Program.Method current, boolean interfaceReceiver) {
        var parameters = new ArrayList<String>();
        if(interfaceReceiver
                && isInterface(current.id().owner())
                && (current.bytecode().access & ACC_STATIC) == 0)
            parameters.add("::jnative::Object* self");
        Type[] arguments = Type.getArgumentTypes(current.id().descriptor());
        for(int i = 0; i < arguments.length; ++i) parameters.add(type(arguments[i]) + " arg" + i);
        return String.join(", ", parameters);
    }

    private String memberSignature(Program.Method current, boolean definition) {
        return type(Type.getReturnType(current.id().descriptor()))
                + " "
                + (definition ? names.definitionName(current.id().owner()) + "::" : "")
                + memberName(current.id())
                + "("
                + parameters(current, true)
                + ")";
    }

    private void orderClass(ClassNode node, Set<String> seen, List<ClassNode> result) {
        if(!seen.add(node.name)) return;
        String sourceOwner = names.sourceOwner(node.name);
        if(!sourceOwner.equals(node.name))
            orderClass(program.classes().get(sourceOwner), seen, result);
        if(program.classes().containsKey(node.superName))
            orderClass(program.classes().get(node.superName), seen, result);
        result.add(node);
    }

    private void emitInitializers(SupportSources support) {
        for(ClassNode node : program.classes().values()) {
            line(
                    "JNATIVE_NOINLINE void "
                            + className(node.name).substring("::generated::".length())
                            + "::initialize_slow() {");
            line("    static ::jnative::ClassInitialization initialization;");
            line("    initialization.run([] {");
            for(FieldNode field : node.fields)
                if(field.value instanceof String text)
                    line(
                            "        "
                                    + className(node.name)
                                    + "::"
                                    + fieldName(node.name, field.name, field.desc)
                                    + ".set("
                                    + stringLiteral(text)
                                    + ");");
            if(node.superName != null && program.classes().containsKey(node.superName))
                line("        " + initializer(node.superName) + "();");
            for(ClassNode face :
                    InitializationOrder.defaultInterfaces(node, program.classes()::get, program::platform))
                line("        " + initializer(face.name) + "();");
            MethodId clinit = new MethodId(node.name, "<clinit>", "()V");
            if(program.methods().containsKey(clinit)) line("        " + symbol(clinit) + "();");
            line("    });");
            // Recursive initialization by the owning thread returns before it completes.
            // Only the completed state may publish a fast path to other threads.
            line(
                    "    if (initialization.completed()) initialization_complete.store(true,"
                            + " std::memory_order_release);");
            line("}\n");
            out = support.boundary(out, true);
        }
    }

    private boolean substituted(String owner, String name, String descriptor) {
        if(program.substitutions() == null) return false;
        var full = program.substitutions().classReplacement(owner);
        if(full != null && !full.builtin()) return true;
        var rule = program.substitutions().methodRules().get(new MethodId(owner, name, descriptor));
        return rule != null && !rule.builtin();
    }

    private void emitRuntimeSubstitutionPolicy() {
        line("static ::jnative::Object* construct_native_exception(const char* type, ::jnative::Object* message) {");
        line("    ::jnative::LocalRoot<> text(message);");
        for(var entry : new TreeMap<>(RuntimeLibrary.generatedExceptionConstructors()).entrySet()) {
            String owner = entry.getKey();
            MethodId constructor = new MethodId(owner, "<init>", entry.getValue());
            if(program.platform(owner) || !program.methods().containsKey(constructor)) continue;
            line("    if (std::strcmp(type, " + quote(owner) + ") == 0) {");
            line("        " + initializer(owner) + "();");
            line("        ::jnative::LocalRoot<> result(::jnative::allocate<" + className(owner) + ">());");
            line("        " + symbol(constructor) + "(result.get(), "
                    + (entry.getValue().equals("(I)V") ? "1" : "text.get()") + ");");
            line("        return result.get();");
            line("    }");
        }
        line("    return nullptr;");
        line("}");
        line("static void configure_substitutions() {");
        line("    ::jnative::RuntimeSubstitutions policy;");
        line("    policy.exception_factory = &construct_native_exception;");
        for(String signature : List.of("hashCode()I", "equals(Ljava/lang/Object;)Z", "toString()Ljava/lang/String;")) {
            int split = signature.indexOf('(');
            String name = signature.substring(0, split), desc = signature.substring(split);
            if(!substituted("java/lang/String", name, desc)) continue;
            var target = program.bindings().find(new MethodId("java/lang/String", name, desc));
            String field = name.equals("hashCode") ? "string_hash" : name.equals("equals") ? "string_equals" : "string_text";
            line("    policy." + field + " = &" + symbol(target.helper()) + ";");
        }
        if(substituted("java/io/FileInputStream", "read", "([BII)I"))
            line("    policy.file_input_fast_path = false;");
        if(substituted("java/util/zip/CRC32", "update", "(I)V"))
            line("    policy.crc32_fast_path = false;");
        line("    ::jnative::configure_runtime_substitutions(policy);");
        line("}");
    }

    private void emitDispatchers(StringBuilder header, SupportSources support) {
        var calls = new LinkedHashSet<MethodId>();
        for(MethodId member : program.reflection().methods()) {
            if(member.name().equals("<init>")) continue;
            if(program.platform(member.owner())
                    || (program.methods().get(member).bytecode().access & ACC_STATIC) == 0)
                calls.add(member);
        }
        for(Program.Method method : program.methods().values()) {
            if(method.nativeBinding() != null) {
                for(NativeBinding.Callback callback : method.nativeBinding().callbacks())
                    if(callback.invocation() == NativeBinding.Invocation.VIRTUAL
                            || callback.invocation() == NativeBinding.Invocation.INTERFACE) calls.add(callback.method());
                continue;
            }
            for(AbstractInsnNode instruction : method.bytecode().instructions) {
                if(instruction instanceof InvokeDynamicInsnNode dynamic
                        && dynamic.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")) calls.add(TO_STRING);
                if(instruction instanceof MethodInsnNode call
                        && (!program.platform(call.owner) || !call.owner.equals("java/lang/String"))
                        && (call.getOpcode() == INVOKEINTERFACE
                        || call.getOpcode() == INVOKEVIRTUAL)) {
                    MethodId declaration = resolve(call.owner, call.name, call.desc);
                    if(!program.platform(declaration.owner())
                            && (program.methods().get(declaration).bytecode().access
                            & (ACC_PRIVATE | ACC_FINAL))
                            != 0) continue;
                    calls.add(
                            program.platform(declaration.owner())
                                    ? declaration
                                    : new MethodId(call.owner, call.name, call.desc));
                }
            }
        }
        var implementations = new ArrayList<>(program.instantiatedClasses());
        names.registerCalls(calls);
        implementations.sort(Comparator.comparingInt(this::depth).reversed().thenComparing(s -> s));
        for(MethodId call : calls) {
            var parameters = new ArrayList<>(List.of("::jnative::Object* receiver"));
            for(Type t : Type.getArgumentTypes(call.descriptor())) parameters.add(type(t));
            header.append(type(Type.getReturnType(call.descriptor())))
                    .append(' ')
                    .append(dispatchName(call))
                    .append('(')
                    .append(String.join(", ", parameters))
                    .append(");\n");
        }
        for(MethodId call : calls) {
            Type[] arguments = Type.getArgumentTypes(call.descriptor());
            var parameters = new ArrayList<>(List.of("::jnative::Object* receiver"));
            var args = new ArrayList<String>();
            for(int i = 0; i < arguments.length; ++i) {
                parameters.add(type(arguments[i]) + " arg" + i);
                args.add("arg" + i);
            }
            line(
                    type(Type.getReturnType(call.descriptor()))
                            + " "
                            + dispatchName(call)
                            + "("
                            + String.join(", ", parameters)
                            + ") {");
            line("    ::jnative::require_non_null(receiver);");
            for(MethodId bridge : program.bindings().interfaceImplementations(call)) {
                String condition = bridge.owner().equals("java/lang/String")
                        ? "receiver->runtime_kind == ::jnative::RuntimeKind::string"
                        : "dynamic_cast<::jnative::FilePath*>(receiver)";
                var bridgeArgs = new ArrayList<>(args);
                if(call.owner().equals("java/lang/Comparable"))
                    bridgeArgs.set(0, "::jnative::check_cast(arg0, \"" + bridge.owner() + "\")");
                String expression = libraryCall(new MethodInsnNode(INVOKEVIRTUAL, bridge.owner(),
                        bridge.name(), bridge.descriptor(), false), "receiver", bridgeArgs);
                line("    if (" + condition + ") return " + expression + ";");
            }
            var targets = new LinkedHashMap<MethodId, List<String>>();
            for(String implementation : implementations) {
                if(!subtype(implementation, call.owner())) continue;
                MethodId target;
                try {
                    target = resolve(implementation, call.name(), call.descriptor());
                } catch(CompilerException missing) {
                    if(program.platform(call.owner())) continue;
                    throw missing;
                }
                if(program.platform(target.owner())) continue;
                if(!program.methods().containsKey(target)
                        || (program.methods().get(target).bytecode().access & ACC_ABSTRACT) != 0)
                    continue;
                targets.computeIfAbsent(target, ignored -> new ArrayList<>()).add(implementation);
            }
            // Runtime-only calls have no generated override to select. Even an
            // empty switch would otherwise evaluate the virtual metadata accessor.
            if(targets.isEmpty() && boundedRuntimeIntrinsic(call)) boundedRuntimeCalls.add(call);
            if(!targets.isEmpty()) {
                line("    switch (receiver->type_id()) {");
                for(var entry : targets.entrySet()) {
                    MethodId target = entry.getKey();
                    for(String implementation : entry.getValue())
                        line("        case " + className(implementation) + "::class_id:");
                    String invoke =
                            (program.classes().get(target.owner()).access & ACC_INTERFACE) != 0
                                    ? symbol(target)
                                    + "(receiver"
                                    + (args.isEmpty() ? "" : ", " + String.join(", ", args))
                                    + ")"
                                    : "static_cast<"
                                    + className(target.owner())
                                    + "*>(receiver)->"
                                    + names.simpleName(target.owner())
                                    + "::"
                                    + memberName(target)
                                    + "("
                                    + String.join(", ", args)
                                    + ")";
                    line(
                            "            "
                                    + (Type.getReturnType(call.descriptor()).equals(Type.VOID_TYPE)
                                    ? invoke + "; return;"
                                    : "return " + invoke + ";"));
                }
                line("        default: break;");
                line("    }");
            }
            if(program.platform(call.owner())) {
                String fallback =
                        libraryCall(
                                new MethodInsnNode(
                                        INVOKESPECIAL,
                                        call.owner(),
                                        call.name(),
                                        call.descriptor(),
                                        false),
                                "receiver",
                                args);
                line(
                        "    "
                                + (Type.getReturnType(call.descriptor()).equals(Type.VOID_TYPE)
                                ? ""
                                : "return ")
                                + fallback
                                + ";");
            }
            else line("    ::jnative::raise(\"java/lang/AbstractMethodError\");");
            line("}\n");
            out = support.boundary(out, true);
        }
    }

    private void emitRuntimeAdapters(Map<String, String> sources, SupportSources support) {
        StringBuilder api = new StringBuilder("#pragma once\n#include \"jn_runtime.hpp\"\n\n");
        api.append(
                        "// Managed calls for generated and handwritten C++. Attach the calling"
                                + " thread\n")
                .append("// and keep object arguments rooted while evaluating other arguments.\n");
        StringBuilder mapping = new StringBuilder("java-method\tcpp-call\n");
        for(MethodId call : names.calls()) {
            boolean bounded =
                    boundedRuntimeCalls.contains(call) || methodEffects.boundedDispatch(call);
            String name = names.call(call);
            int separator = name.lastIndexOf("::");
            String namespace = name.substring(2, separator), member = name.substring(separator + 2);
            Type[] arguments = Type.getArgumentTypes(call.descriptor());
            var parameters = new ArrayList<>(List.of("::jnative::Object* receiver"));
            var values = new ArrayList<>(List.of(bounded ? "receiver" : "receiver_root.get()"));
            for(int i = 0; i < arguments.length; ++i) {
                parameters.add(type(arguments[i]) + " arg" + i);
                values.add("arg" + i + (!bounded && reference(arguments[i]) ? "_root.get()" : ""));
            }
            String signature =
                    type(Type.getReturnType(call.descriptor()))
                            + " "
                            + member
                            + "("
                            + String.join(", ", parameters)
                            + ")";
            api.append(openNamespace(namespace))
                    .append("    ")
                    .append(signature)
                    .append(";\n")
                    .append(closeNamespace(namespace));
            line(openNamespace(namespace));
            line(signature + " {");
            if(bounded) {
                // No managed allocation or poll on the successful path. Failure raises
                // without using the receiver again; callers retain their existing roots.
                line("    // All successful targets are bounded and noncollecting.");
            }
            else {
                line("    ::jnative::LocalRoot<> receiver_root(receiver);");
                for(int i = 0; i < arguments.length; ++i)
                    if(reference(arguments[i]))
                        line("    ::jnative::LocalRoot<> arg" + i + "_root(arg" + i + ");");
            }
            line(
                    "    return ::generated::"
                            + dispatchName(call)
                            + "("
                            + String.join(", ", values)
                            + ");");
            line("}\n" + closeNamespace(namespace));
            out = support.boundary(out, false);
            mapping.append(tableText(call.toString())).append('\t').append(name).append('\n');
        }
        sources.put("java_api.hpp", api.toString());
        sources.put("java-api.tsv", mapping.toString());
    }

    MethodId directTarget(MethodInsnNode call) {
        return callTargets.direct(call);
    }

    boolean boundedRuntimeCall(MethodInsnNode call) {
        if(call.getOpcode() != INVOKEVIRTUAL) return false;
        MethodId id = new MethodId(call.owner, call.name, call.desc);
        if(!boundedRuntimeIntrinsic(id)) return false;
        return boundedRuntimeEffects.computeIfAbsent(
                id,
                ignored -> {
                    for(String implementation : program.instantiatedClasses()) {
                        if(!subtype(implementation, id.owner())) continue;
                        try {
                            MethodId target = resolve(implementation, id.name(), id.descriptor());
                            if(!program.platform(target.owner())) return false;
                        } catch(CompilerException unresolved) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    private boolean boundedRuntimeIntrinsic(MethodId call) {
        PlatformBindings.Target target = program.bindings().find(call);
        if(target == null) return false;
        Program.Method linked = program.methods().get(target.helper());
        if(linked == null || linked.nativeBinding() == null || !linked.nativeBinding().boundedAccess()) return false;
        ClassNode helper = program.classes().get(target.helper().owner());
        return NativeBinding.trivialInitialization(helper);
    }

    boolean boundedMethod(MethodId id) {
        return methodEffects != null && methodEffects.bounded(id);
    }

    String stringLiteral(String text) {
        int slot = stringLiterals.computeIfAbsent(text, ignored -> stringLiterals.size());
        return "::generated::string_literals[" + slot + "].get(" + utf16(text) + ")";
    }

    String classLiteral(Type type) {
        return classLiteral(
                type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY
                        ? type.getInternalName()
                        : type.getClassName());
    }

    private String classLiteral(String name) {
        int slot = classLiterals.computeIfAbsent(name, ignored -> classLiterals.size());
        return "::generated::class_literals[" + slot + "].get(" + quote(name) + ")";
    }

    private String emitClassLiterals(int slots) {
        StringBuilder result =
                new StringBuilder("#include \"application.hpp\"\nnamespace generated {\n")
                        .append("::jnative::ClassLiteral class_literals[")
                        .append(slots)
                        .append("]{};\n");
        int index = 0;
        for(String name : classLiterals.keySet()) {
            if(index % 64 == 0) {
                if(index != 0) result.append("}\n");
                result.append("static void initialize_class_literals_")
                        .append(index / 64)
                        .append("() {\n");
            }
            result.append("    ").append(classLiteral(name)).append(";\n");
            index++;
        }
        if(index != 0) result.append("}\n");
        result.append("void initialize_class_literals() {\n");
        for(int batch = 0; batch * 64 < index; batch++)
            result.append("    initialize_class_literals_").append(batch).append("();\n");
        return result.append("}\n}\n").toString();
    }

    private String emitStringLiterals(int slots) {
        StringBuilder result =
                new StringBuilder("#include \"application.hpp\"\nnamespace generated {\n")
                        .append("::jnative::StringLiteral string_literals[")
                        .append(slots)
                        .append("]{};\n");
        int index = 0;
        for(String text : stringLiterals.keySet()) {
            if(index % 64 == 0) {
                if(index != 0) result.append("}\n");
                result.append("static void initialize_literals_")
                        .append(index / 64)
                        .append("() {\n");
            }
            result.append("    ").append(stringLiteral(text)).append(";\n");
            index++;
        }
        if(index != 0) result.append("}\n");
        result.append("void initialize_literals() {\n");
        for(int batch = 0; batch * 64 < index; batch++)
            result.append("    initialize_literals_").append(batch).append("();\n");
        return result.append("}\n}\n").toString();
    }

    boolean boundedCall(MethodId caller, MethodInsnNode call) {
        return methodEffects != null && methodEffects.boundedCall(caller, call);
    }

    boolean boundedReturn(MethodId method) {
        return methodEffects != null && methodEffects.boundedReturn(method);
    }

    boolean boundedAfterInitialization(MethodId method) {
        return methodEffects != null && methodEffects.boundedAfterInitialization(method);
    }

    boolean boundedOutsideRegions(MethodId method, Set<AbstractInsnNode> scoped) {
        return methodEffects != null && methodEffects.boundedOutsideRegions(method, scoped);
    }

    boolean delegatesRoots(MethodId method) {
        return methodEffects != null && methodEffects.delegatesRoots(method);
    }

    Set<String> initializationDependencies(MethodId method) {
        return methodEffects.initializationDependencies(method);
    }

    boolean returnsThrough(MethodId method, AbstractInsnNode instruction) {
        return methodEffects.returnsThrough(method, instruction);
    }

    Set<AbstractInsnNode> ownedArrayInstructions(MethodId method) {
        return ownedArrays.instructions(method);
    }

    private String primitiveArrayStorage(String descriptor) {
        return switch(descriptor) {
            case "[C" -> "std::uint16_t";
            case "[S" -> "std::int16_t";
            default -> type(Type.getType(descriptor.substring(1)));
        };
    }

    boolean boundedIntrinsic(MethodInsnNode call) {
        if(boundedRuntimeCall(call) || call.getOpcode() == INVOKESTATIC
                && boundedRuntimeIntrinsic(new MethodId(call.owner, call.name, call.desc))) return true;
        if(call.getOpcode() == INVOKESTATIC && !program.platform(call.owner)) {
            MethodId id = resolve(call.owner, call.name, call.desc);
            Program.Method helper = program.methods().get(id);
            if(helper != null && helper.nativeBinding() != null && helper.nativeBinding().bounded()
                    && trivialSourceInitialization(id.owner(), new HashSet<>())) return true;
        }
        return boundedArraycopy(call);
    }

    private boolean trivialSourceInitialization(String owner, Set<String> seen) {
        if(program.platform(owner)) return true;
        ClassNode type = program.classes().get(owner);
        if(type == null) return false;
        if(!seen.add(owner)) return true;
        if(type.methods.stream().anyMatch(member -> member.name.equals("<clinit>"))) return false;
        if(type.superName != null && !trivialSourceInitialization(type.superName, seen)) return false;
        return type.interfaces.stream().allMatch(face -> trivialSourceInitialization(face, seen));
    }

    private boolean trivialManagedImportInitialization(String owner, Set<String> seen) {
        if(owner.equals("java/lang/Object")) return true;
        // Runtime marker interfaces have neither members nor class initialization.
        if(program.platform(owner)
                && (owner.equals("java/lang/Cloneable") || owner.equals("java/io/Serializable"))) return true;
        // Unknown platform ancestors are not a proof that initialization cannot collect.
        ClassNode type = program.classes().get(owner);
        if(type == null) return false;
        if(!seen.add(owner)) return true;
        if(type.methods.stream().anyMatch(member -> member.name.equals("<clinit>"))) return false;
        if(type.superName != null && !trivialManagedImportInitialization(type.superName, seen)) return false;
        return type.interfaces.stream().allMatch(face -> trivialManagedImportInitialization(face, seen));
    }

    private boolean boundedArraycopy(MethodInsnNode call) {
        return verifiedArraycopy(call) && smallConstantCopy(call);
    }

    private boolean verifiedArraycopy(MethodInsnNode call) {
        if(call.getOpcode() != INVOKESTATIC || !call.owner.equals("java/lang/System")
                || !call.name.equals("arraycopy") || !call.desc.equals("(Ljava/lang/Object;ILjava/lang/Object;II)V")) return false;
        PlatformBindings.Target target = program.bindings().find(new MethodId(call.owner, call.name, call.desc));
        Program.Method helper = target == null ? null : program.methods().get(target.helper());
        return helper != null && target.binding() != null
                && target.binding().symbol().equals("jnative::platform_System_arraycopy_7b15f0890b")
                && target.binding().equals(helper.nativeBinding())
                && NativeBinding.trivialInitialization(program.classes().get(target.helper().owner()));
    }

    private static boolean smallConstantCopy(MethodInsnNode call) {
        AbstractInsnNode count = call.getPrevious();
        while(count instanceof LineNumberNode || count instanceof FrameNode)
            count = count.getPrevious();
        // A label may merge a different operand-stack value from another path.
        // Do not infer the count merely from the last fallthrough instruction.
        if(count == null || count instanceof LabelNode) return false;
        int value = -1;
        if(count.getOpcode() >= ICONST_0 && count.getOpcode() <= ICONST_5)
            value = count.getOpcode() - ICONST_0;
        else if(count instanceof IntInsnNode constant
                && (constant.getOpcode() == BIPUSH || constant.getOpcode() == SIPUSH))
            value = constant.operand;
        else if(count instanceof LdcInsnNode constant && constant.cst instanceof Integer integer)
            value = integer;
        return value >= 0 && value <= 64;
    }

    private static String openNamespace(String namespace) {
        return "namespace " + namespace.replace("::", " { namespace ") + " {\n";
    }

    private static String closeNamespace(String namespace) {
        return "} ".repeat(namespace.split("::").length) + "\n";
    }

    private String fieldStorage(String owner, FieldNode field) {
        Type valueType = Type.getType(field.desc);
        if((field.access & ACC_STATIC) != 0 && reference(valueType)) {
            return "::jnative::StaticReference";
        }
        String wrapper =
                (field.access & ACC_VOLATILE) != 0
                        ? "ManagedField"
                        : ownedArrays.plainField(owner, field) ? "OwnedField" : "OrdinaryField";
        return "::jnative::" + wrapper + "<" + type(valueType) + ">";
    }

    private static String fieldValue(FieldNode field) {
        if(field.value instanceof Integer value) return integer(value);
        if(field.value instanceof Long value) return longInteger(value);
        if(field.value instanceof Float value) return floating(value.doubleValue(), true);
        if(field.value instanceof Double value) return floating(value, false);
        return "";
    }

    private int depth(String name) {
        ClassNode node = program.classes().get(name);
        return node == null || node.superName == null ? 0 : depth(node.superName) + 1;
    }

    private boolean subtype(String name, String parent) {
        if(name.equals(parent) || parent.equals("java/lang/Object")) return true;
        if(program.platform(name)) {
            String base = RuntimeLibrary.parent(name);
            return base != null && subtype(base, parent);
        }
        ClassNode node = program.classes().get(name);
        return node != null
                && ((node.superName != null && subtype(node.superName, parent))
                || node.interfaces.stream().anyMatch(i -> subtype(i, parent)));
    }

    String fieldOwner(String owner, String name, String descriptor) {
        for(String current = owner; current != null; ) {
            ClassNode node = program.classes().get(current);
            if(node == null) break;
            if(node.fields.stream()
                    .anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)))
                return current;
            current = node.superName;
        }
        throw new CompilerException("JN1002 Unresolved field " + owner + "." + name + descriptor);
    }

    String platformFieldCall(FieldInsnNode field) {
        return platformFieldCall(field, false);
    }

    String platformFieldCall(FieldInsnNode field, boolean readable) {
        PlatformBindings.FieldTarget target = program.bindings().field(field.owner, field.name, field.desc);
        if(target == null || !program.methods().containsKey(target.helper()))
            throw new CompilerException("JN2001 Missing retained annotated platform field getter: "
                    + field.owner + "." + field.name + ":" + field.desc);
        return (readable ? className(target.helper().owner()) + "::" + memberName(target.helper())
                : symbol(target.helper())) + "()";
    }

    String libraryCall(MethodInsnNode call, String receiver, List<String> args) {
        return libraryCall(call, receiver, args, List.of());
    }

    private String libraryCall(MethodInsnNode call, String receiver, List<String> args, List<Type> argumentTypes) {
        MethodId api = new MethodId(call.owner, call.name, call.desc);
        PlatformBindings.Target target = program.bindings().find(api);
        if(target == null)
            throw new CompilerException("JN2001 Missing annotated platform binding: " + api);
        if(!program.methods().containsKey(target.helper()))
            throw new CompilerException("JN2001 Platform helper was not retained: " + target.helper() + " for " + api);
        var parameters = new ArrayList<String>(args);
        if(target.instance()) parameters.addFirst(receiver);
        boolean boundedCopy = boundedArraycopy(call);
        NativeBinding binding = program.methods().get(target.helper()).nativeBinding();
        if(boundedRuntimeIntrinsic(api))
            return "::" + binding.symbol() + "(" + String.join(", ", parameters) + ")";
        String adapter = (readableLibraryCalls ? className(target.helper().owner()) + "::" + memberName(target.helper())
                : symbol(target.helper())) + "(" + String.join(", ", parameters) + ")";
        if(verifiedArraycopy(call)) {
            // The caller remains may-collect: only the bounded branch skips the
            // adapter, while negative/large counts retain its roots and safepoint.
            // Readable lowering materializes effectful operands before this guard;
            // fallback lowering already holds evaluated values in stack slots.
            String count = parameters.getLast();
            String direct = "::" + binding.symbol() + "(" + String.join(", ", parameters) + ")";
            if(argumentTypes.size() == 5) {
                Type source = argumentTypes.getFirst();
                if(source != null && source.equals(argumentTypes.get(2))
                        && source.getSort() == Type.ARRAY && source.getDimensions() == 1) {
                    String element = switch(source.getElementType().getSort()) {
                        case Type.BOOLEAN -> "std::uint8_t";
                        case Type.BYTE -> "std::int8_t";
                        case Type.CHAR -> "std::uint16_t";
                        case Type.SHORT -> "std::int16_t";
                        case Type.INT -> "std::int32_t";
                        case Type.LONG -> "std::int64_t";
                        case Type.FLOAT -> "float";
                        case Type.DOUBLE -> "double";
                        default -> null;
                    };
                    if(element != null)
                        direct = "::jnative::bounded_primitive_array_copy<" + element + ">(" + String.join(", ", parameters) + ")";
                }
            }
            if(boundedCopy) return direct;
            return "((" + count + " >= 0 && " + count + " <= 64) ? " + direct + " : " + adapter + ")";
        }
        return adapter;
    }

    private void concatenate(InvokeDynamicInsnNode dynamic, Frame<BasicValue> frame) {
        if(!dynamic.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")
                || !Set.of("makeConcatWithConstants", "makeConcat").contains(dynamic.bsm.getName()))
            unsupported(
                    "invokedynamic bootstrap "
                            + dynamic.bsm.getOwner()
                            + "."
                            + dynamic.bsm.getName());
        Type[] arguments = Type.getArgumentTypes(dynamic.desc);
        int start = frame.getStackSize() - arguments.length;
        String recipe =
                dynamic.bsm.getName().equals("makeConcat")
                        ? "\u0001".repeat(arguments.length)
                        : (String)dynamic.bsmArgs[0];
        int parameter = 0, constant = 1;
        var parts = new ArrayList<String>();
        StringBuilder text = new StringBuilder();
        for(int i = 0; i < recipe.length(); ++i) {
            char c = recipe.charAt(i);
            if(c != 1 && c != 2) {
                text.append(c);
                continue;
            }
            if(!text.isEmpty()) {
                parts.add(utf16(text.toString()));
                text.setLength(0);
            }
            if(c == 1) {
                parts.add(
                        textExpression(
                                arguments[parameter],
                                read("stack", start + parameter, arguments[parameter])));
                ++parameter;
            }
            else parts.add(utf16(String.valueOf(dynamic.bsmArgs[constant++])));
        }
        if(!text.isEmpty()) parts.add(utf16(text.toString()));
        push(
                start,
                Type.getType(Object.class),
                "::jnative::concatenate({" + String.join(", ", parts) + "})");
    }

    static String textExpression(Type t, String expression) {
        return textExpression(t, expression, dispatchName(TO_STRING));
    }

    String readableTextExpression(Type t, String expression) {
        return textExpression(t, expression, names.call(TO_STRING));
    }

    private static String textExpression(Type t, String expression, String toString) {
        if(t.equals(Type.BOOLEAN_TYPE))
            return "(" + expression + " ? std::u16string(u\"true\") : std::u16string(u\"false\"))";
        if(t.equals(Type.CHAR_TYPE)) return "std::u16string(1, char16_t(" + expression + "))";
        if(reference(t) && !t.equals(Type.getType(String.class)))
            return "::jnative::to_text("
                    + expression
                    + " ? "
                    + toString
                    + "("
                    + expression
                    + ") : nullptr)";
        return "::jnative::to_text(" + expression + ")";
    }

    static String narrow(Type t, String value) {
        return switch(t.getSort()) {
            case Type.BOOLEAN -> "(" + value + " & 1)";
            case Type.BYTE -> "(((" + value + " & 255) ^ 128) - 128)";
            case Type.SHORT -> "(((" + value + " & 65535) ^ 32768) - 32768)";
            case Type.CHAR -> "(" + value + " & 65535)";
            default -> value;
        };
    }

    private static Type arrayStackType(int opcode) {
        return switch(opcode) {
            case 1 -> Type.LONG_TYPE;
            case 2 -> Type.FLOAT_TYPE;
            case 3 -> Type.DOUBLE_TYPE;
            case 4 -> Type.getType(Object.class);
            default -> Type.INT_TYPE;
        };
    }

    private static String arrayElementType(int opcode) {
        return switch(opcode) {
            case 5 -> "std::int8_t";
            case 6 -> "std::uint16_t";
            case 7 -> "std::int16_t";
            default -> type(arrayStackType(opcode));
        };
    }

    String className(String name) {
        return names.className(name);
    }

    String nativeBase(String name) {
        if(name != null && !program.platform(name)) return null;
        if(RuntimeLibrary.throwable(name)) return "::jnative::Throwable";
        return switch(name) {
            case "java/lang/Object" -> "::jnative::Object";
            case "java/lang/Number" -> "::jnative::Object";
            case "java/lang/Thread" -> "::jnative::Thread";
            case "java/lang/ThreadLocal" -> "::jnative::ThreadLocal";
            case "java/util/concurrent/atomic/AtomicInteger" -> "::jnative::AtomicInteger";
            case "java/util/concurrent/atomic/AtomicLong" -> "::jnative::AtomicLong";
            default -> null;
        };
    }





    static String initializer(String name) {
        return "initialize_" + identifier(name) + "_" + digest(name);
    }

    String fieldName(String owner, String name, String descriptor) {
        return names.field(owner, name, descriptor);
    }

    String memberName(MethodId id) {
        return names.method(id);
    }

    static String dispatchName(MethodId id) {
        return "dispatch_" + symbol(id);
    }

    static String quote(String text) {
        var value = new StringBuilder("\"");
        for(int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if(c == '\\' || c == '"') value.append('\\').append(c);
            else if(c < 32 || c == 127)
                value.append(String.format(Locale.ROOT, "\\%03o", (int)c));
            else value.append(c);
        }
        return value.append('"').toString();
    }

    static String comment(String text) {
        return text.replaceAll("[\\p{Cntrl}\\\\]", " ");
    }

    private static String tableText(String text) {
        return text.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String identifier(String text) {
        String value = text.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return value.isEmpty() ? "unnamed" : value;
    }

    private String signature(Program.Method method) {
        var parameters = new ArrayList<String>();
        if((method.bytecode().access & ACC_STATIC) == 0) parameters.add("::jnative::Object* self");
        Type[] arguments = Type.getArgumentTypes(method.id().descriptor());
        for(int i = 0; i < arguments.length; ++i) parameters.add(type(arguments[i]) + " arg" + i);
        return type(Type.getReturnType(method.id().descriptor()))
                + " "
                + symbol(method.id())
                + "("
                + String.join(", ", parameters)
                + ")";
    }

    private static String parameterTypes(String descriptor) {
        return String.join(
                ", ",
                Arrays.stream(Type.getArgumentTypes(descriptor)).map(CppEmitter::type).toList());
    }

    private static String abiType(Type type) {
        return reference(type) ? "jn_handle" : type(type).replace("std::", "");
    }

    private void emitFactory(Program.Method constructor, StringBuilder header) {
        Type[] types = Type.getArgumentTypes(constructor.id().descriptor());
        var parameters = new ArrayList<String>();
        var args = new ArrayList<String>();
        for(int i = 0; i < types.length; ++i) {
            parameters.add(type(types[i]) + " arg" + i);
            args.add(reference(types[i]) ? "root" + i + ".get()" : "arg" + i);
        }
        String shell = className(constructor.id().owner());
        String signature =
                shell
                        + "* make_"
                        + symbol(constructor.id())
                        + "("
                        + String.join(", ", parameters)
                        + ")";
        header.append(signature).append(";\n");
        line("// Managed factory: callers must root the returned object before a safepoint.");
        line(signature + " {");
        for(int i = 0; i < types.length; ++i)
            if(reference(types[i]))
                line("    ::jnative::LocalRoot<> root" + i + "(arg" + i + ");");
        line("    initialize_program();");
        line("    " + initializer(constructor.id().owner()) + "();");
        line(
                "    ::jnative::LocalRoot<"
                        + shell
                        + "> object(::jnative::allocate<"
                        + shell
                        + ">());");
        line(
                "    "
                        + symbol(constructor.id())
                        + "(object.get()"
                        + (args.isEmpty() ? "" : ", " + String.join(", ", args))
                        + ");");
        line("    return object.get();");
        line("}\n");
    }

    private static String exportSignature(Program.Method method, String symbol) {
        var parameters = new ArrayList<String>();
        Type[] types = Type.getArgumentTypes(method.id().descriptor());
        for(int i = 0; i < types.length; ++i) parameters.add(abiType(types[i]) + " arg" + i);
        Type result = Type.getReturnType(method.id().descriptor());
        if(!result.equals(Type.VOID_TYPE)) parameters.add(abiType(result) + "* result");
        return "jn_status "
                + symbol
                + "("
                + (parameters.isEmpty() ? "void" : String.join(", ", parameters))
                + ")";
    }

    private void emitExport(Program.Method current, String signature) {
        line("extern \"C\" " + signature + " {");
        line("    return ::jnative::abi_call([&] {");
        Type result = Type.getReturnType(current.id().descriptor());
        boolean returns = !result.equals(Type.VOID_TYPE);
        if(returns) line("        ::jnative::require_output(result);");
        // Resolve roots before initialization, which can allocate and collect.
        var args = new ArrayList<String>();
        Type[] parameters = Type.getArgumentTypes(current.id().descriptor());
        for(int i = 0; i < parameters.length; ++i) {
            if(reference(parameters[i])) {
                line(
                        "        ::jnative::LocalRoot<> root"
                                + i
                                + "(::jnative::Heap::instance().resolve(arg"
                                + i
                                + "));");
                args.add(
                        "::jnative::check_cast(root"
                                + i
                                + ".get(), "
                                + quote(parameters[i].getInternalName())
                                + ")");
            }
            else args.add("arg" + i);
        }
        line("        generated::initialize_program();");
        String call = "generated::" + symbol(current.id()) + "(" + String.join(", ", args) + ")";
        line(
                "        "
                        + (returns ? "*result = " : "")
                        + (reference(result)
                        ? "::jnative::Heap::instance().retain(" + call + ", true)"
                        : call)
                        + ";");
        line("    });");
        line("}\n");
    }

    static String symbol(MethodId id) {
        return "j_"
                + identifier(id.owner())
                + "_"
                + identifier(id.name())
                + "_"
                + digest(id.toString());
    }

    private static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 12);
        } catch(NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    public static String type(Type type) {
        return switch(type.getSort()) {
            case Type.VOID -> "void";
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> "std::int32_t";
            case Type.LONG -> "std::int64_t";
            case Type.FLOAT -> "float";
            case Type.DOUBLE -> "double";
            default -> "::jnative::Object*";
        };
    }

    private static Type normalize(Type t) {
        if(reference(t)) return Type.getType(Object.class);
        return switch(t.getSort()) {
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT -> Type.INT_TYPE;
            default -> t;
        };
    }

    static boolean reference(Type t) {
        return t.getSort() == Type.OBJECT || t.getSort() == Type.ARRAY;
    }

    private String slot(String category, int position, Type t) {
        String name = category + position;
        if(category.equals("local") && method.bytecode().localVariables != null) {
            for(LocalVariableNode local : method.bytecode().localVariables)
                if(local.index == position) {
                    name += "_" + identifier(local.name);
                    break;
                }
        }
        return name
                + "_"
                + (reference(t) ? "ref" : normalize(t).getDescriptor().toLowerCase(Locale.ROOT));
    }

    private String read(String category, int position, Type t) {
        return slot(category, position, t) + (reference(t) ? ".get()" : "");
    }

    private void assign(String category, int position, Type t, String expression) {
        line(
                "    "
                        + slot(category, position, t)
                        + (reference(t) ? ".set(" + expression + ");" : " = " + expression + ";"));
    }

    private void push(int position, Type t, String expression) {
        assign("stack", position, t, expression);
    }

    private static String comparison(int n) {
        return List.of("==", "!=", "<", ">=", ">", "<=").get(n);
    }

    private void unsupported(String detail) {
        throw new CompilerException(
                "JN1005 Unsupported "
                        + detail
                        + " at "
                        + method.id()
                        + " instruction "
                        + index
                        + "\nReachable through "
                        + method.reachabilityPath());
    }

    private boolean synchronizedReferenceReturn() {
        return (method.bytecode().access & ACC_SYNCHRONIZED) != 0
                && reference(Type.getReturnType(method.id().descriptor()));
    }

    private void emitReturn(String expression) {
        if(synchronizedReferenceReturn()) {
            // Releasing a monitor can park for collection. Keep the result rooted until afterward.
            line("    synchronized_return.set(" + expression + ");");
            line("    return synchronized_return.get();");
        }
        else {
            line("    return " + expression + ";");
        }
    }

    private void line(String text) {
        out.append(text).append('\n');
    }

    static String floating(double value, boolean single) {
        String t = single ? "float" : "double";
        if(Double.isNaN(value)) return "std::numeric_limits<" + t + ">::quiet_NaN()";
        if(Double.isInfinite(value))
            return (value < 0 ? "-" : "") + "std::numeric_limits<" + t + ">::infinity()";
        return (single ? Float.toString((float)value) : Double.toString(value))
                + (single ? "f" : "");
    }

    static String integer(int value) {
        return value == Integer.MIN_VALUE
                ? "std::numeric_limits<std::int32_t>::min()"
                : Integer.toString(value);
    }

    static String longInteger(long value) {
        return value == Long.MIN_VALUE
                ? "std::numeric_limits<std::int64_t>::min()"
                : "std::int64_t(" + value + "LL)";
    }

    static String utf16(String text) {
        var result = new StringBuilder("u\"");
        for(int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            switch(c) {
                case '\\', '"' -> result.append('\\').append(c);
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if(c >= 32 && c < 127) result.append(c);
                    else if(c < 256) result.append(String.format(Locale.ROOT, "\\%03o", (int)c));
                    else if(Character.isHighSurrogate(c)
                            && i + 1 < text.length()
                            && Character.isLowSurrogate(text.charAt(i + 1))) {
                        result.append(
                                String.format(
                                        Locale.ROOT,
                                        "\\U%08x",
                                        Character.toCodePoint(c, text.charAt(++i))));
                    }
                    else if(Character.isSurrogate(c)) {
                        // C++ universal character names cannot represent isolated UTF-16
                        // surrogates.
                        var units = new ArrayList<String>();
                        for(int j = 0; j < text.length(); ++j)
                            units.add("char16_t(0x" + Integer.toHexString(text.charAt(j)) + ")");
                        return "std::u16string{" + String.join(", ", units) + "}";
                    }
                    else result.append(String.format(Locale.ROOT, "\\u%04x", (int)c));
                }
            }
        }
        result.append('"');
        // An explicit length preserves embedded NULs.
        return text.indexOf('\0') >= 0
                ? "std::u16string(" + result + ", " + text.length() + ")"
                : result.toString();
    }

    record LibraryExpression(List<String> statements, String expression) {
    }

    private String runtimeCall(MethodId id) {
        return readableLibraryCalls ? names.call(id) : dispatchName(id);
    }

    LibraryExpression lowerLibraryCall(
            MethodInsnNode call, String receiver, List<String> arguments) {
        return lowerLibraryCall(call, receiver, arguments, List.of());
    }

    LibraryExpression lowerLibraryCall(
            MethodInsnNode call, String receiver, List<String> arguments, List<Type> argumentTypes) {
        StringBuilder saved = out;
        boolean previous = readableLibraryCalls;
        readableLibraryCalls = true;
        out = new StringBuilder();
        try {
            String expression = libraryCall(call, receiver, arguments, argumentTypes);
            return new LibraryExpression(
                    out.toString().lines().map(String::strip).toList(), expression);
        } finally {
            out = saved;
            readableLibraryCalls = previous;
        }
    }
}
