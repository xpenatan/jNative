package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.internal.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** One compilation's frozen provider choices and effective executable policy. */
public final class SubstitutionRegistry {
    private static final String ANNOTATIONS = "Lcom/github/xpenatan/jnative/substitution/";
    public record ClassRule(String providerId, Path artifact, String target, String implementation, boolean builtin) {}
    public record MethodRule(String providerId, Path artifact, Program.MethodId target, Program.MethodId implementation, boolean builtin) {}
    public record MethodOrigin(String providerId, String artifact, Program.MethodId donor, String sha256, String previousImplementation) {}
    private record Provider(String id, SubstitutionArtifact artifact, boolean builtin, String hash) {}
    private record Alias(Provider provider, Program.MethodId declaration, String owner, String name, String descriptor, String access, boolean original) {}
    private final ClassPath classPath;
    private final List<Provider> providers = new ArrayList<>();
    private final List<SubstitutionArtifact> dependencies = new ArrayList<>();
    private final Map<String, ClassRule> classRules = new TreeMap<>();
    private final Map<Program.MethodId, MethodRule> methodRules = new TreeMap<>(Comparator.comparing(Program.MethodId::toString));
    private final Map<Program.MethodId, MethodRule> builtinMethods = new HashMap<>();
    private final Map<Program.MethodId, Boolean> runtimeContracts = new HashMap<>();
    private final Map<String, String> aliases = new TreeMap<>();
    private final Map<Program.MethodId, Alias> nativeAliases = new HashMap<>();
    private final Map<Program.MethodId, Program.MethodId> previousMethods = new HashMap<>();
    private final Map<Program.MethodId, MethodOrigin> methodOrigins = new LinkedHashMap<>();
    private final Map<Program.MethodId, PlatformBindings.Target> baselineRoutes = new LinkedHashMap<>();
    private final Map<PlatformBindings.StaticField, PlatformBindings.FieldTarget> baselineFields = new LinkedHashMap<>();
    private final List<Map<String, Object>> report = new ArrayList<>();
    private final Map<String, List<String>> indexedDeclarations = new HashMap<>();
    private final SubstitutionRemapper remapper;
    private record DonorKey(Path artifact, String owner) {}
    private final Map<DonorKey, ClassNode> normalizedDonors = new HashMap<>();
    private record ExecutableKey(Path artifact, Program.MethodId method) {}
    private final Map<ExecutableKey, NativeBinding> donorBindings = new HashMap<>();
    private PlatformBindings.Table bindings;

    SubstitutionRegistry(ClassPath classPath, SubstitutionOptions options) {
        this.classPath = classPath;
        var classes = new TreeMap<String, List<ClassRule>>();
        var methods = new TreeMap<Program.MethodId, List<MethodRule>>(Comparator.comparing(Program.MethodId::toString));
        var descriptors = new ArrayList<SubstitutionProviderIndex>();
        if(options.useBuiltinSubstitutions()) descriptors.add(SubstitutionProviderIndex.builtin());
        for(Path path : options.providerPaths()) descriptors.add(SubstitutionProviderIndex.read(new SubstitutionArtifact(path)));
        for(var index : descriptors) {
            var provider = provider(index.id(), index.artifact(), index.id().equals("jnative.builtin") && options.useBuiltinSubstitutions(), index.hash());
            indexedDeclarations.put(provider.id(), index.declarations());
            for(String declaration : index.declarations()) scan(provider, declaration, classes, methods);
        }
        for(SubstitutionProvider definition : options.providers()) {
            var artifact = new SubstitutionArtifact(definition.artifact());
            var provider = provider(definition.id(), artifact, false, "programmatic");
            for(var rule : definition.classes()) addClass(provider, internal(rule.target()), internal(rule.implementation()), classes);
            for(var rule : definition.methods()) addMethod(provider, id(rule.target()),
                    new Program.MethodId(internal(rule.implementation().owner()), rule.implementation().name(), rule.implementation().descriptor()), methods);
            var donors = new TreeSet<String>();
            for(var rule : definition.classes()) donors.add(internal(rule.implementation()));
            for(var rule : definition.methods()) donors.add(internal(rule.implementation().owner()));
            for(String donor : donors) scanAliases(provider, artifact.classNode(donor));
        }
        for(Path path : options.dependencyPaths()) dependencies.add(new SubstitutionArtifact(path));
        validateAliases();
        remapper = new SubstitutionRemapper(aliases);
        var normalizedMethods = new TreeMap<Program.MethodId, List<MethodRule>>(Comparator.comparing(Program.MethodId::toString));
        for(var rules : methods.values()) for(MethodRule rule : rules) {
            var target = normalized(rule.target());
            var normalizedRule = new MethodRule(rule.providerId(), rule.artifact(), target, rule.implementation(), rule.builtin());
            var candidates = normalizedMethods.computeIfAbsent(target, ignored -> new ArrayList<>());
            if(!candidates.contains(normalizedRule)) candidates.add(normalizedRule);
        }
        methods = normalizedMethods;
        var preferences = new HashMap<Program.MethodId, String>();
        for(var preference : options.methodPreferences().entrySet()) {
            var target = normalized(id(preference.getKey()));
            String previous = preferences.putIfAbsent(target, preference.getValue());
            if(previous != null && !previous.equals(preference.getValue()))
                throw error("JN4023", "Conflicting normalized method preferences: " + target);
        }
        for(var entry : classes.entrySet()) {
            String preference = options.classPreferences().get(entry.getKey().replace('/', '.'));
            ClassRule winner = chooseClass(entry.getKey(), entry.getValue(), preference);
            classRules.put(entry.getKey(), winner);
            for(var rule : entry.getValue()) report(rule.providerId(), "class", rule.target(), rule.implementation(), rule.equals(winner));
        }
        for(String preference : options.classPreferences().keySet())
            if(!classes.containsKey(internal(preference))) throw error("JN4023", "Stale class preference: " + preference);
        for(var entry : methods.entrySet()) {
            for(MethodRule rule : entry.getValue()) if(rule.builtin()) {
                if(builtinMethods.putIfAbsent(entry.getKey(), rule) != null)
                    throw error("JN4022", "Multiple builtin method contracts: " + entry.getKey());
            }
            var candidates = entry.getValue();
            ClassRule selectedClass = classRules.get(entry.getKey().owner());
            if(selectedClass != null && !selectedClass.builtin()) candidates = candidates.stream().filter(rule -> !rule.builtin()).toList();
            MethodReference reference = new MethodReference(entry.getKey().owner(), entry.getKey().name(), entry.getKey().descriptor());
            String preference = preferences.get(entry.getKey());
            if(candidates.isEmpty()) {
                if(preference != null) throw error("JN4023", "Ineffective method preference: " + reference);
                for(var rule : entry.getValue()) report(rule.providerId(), "method", rule.target().toString(), rule.implementation().toString(), false);
                continue;
            }
            MethodRule winner = chooseMethod(entry.getKey(), candidates, preference);
            methodRules.put(entry.getKey(), winner);
            for(var rule : entry.getValue()) report(rule.providerId(), "method", rule.target().toString(), rule.implementation().toString(), rule.equals(winner));
        }
        for(var preference : preferences.keySet())
            if(!methodRules.containsKey(preference)) throw error("JN4023", "Stale method preference: " + preference);
        for(MethodRule rule : methodRules.values()) {
            ClassRule selected = classRules.get(canonical(rule.implementation().owner()));
            if(aliases.containsKey(rule.implementation().owner()) && selected != null
                    && (!selected.providerId().equals(rule.providerId()) || !selected.artifact().equals(rule.artifact())
                    || !selected.implementation().equals(rule.implementation().owner())))
                throw error("JN4022", "Method donor belongs to a shadowed full-class implementation: provider " + rule.providerId()
                        + " artifact " + rule.artifact() + " target " + rule.target() + " donor " + rule.implementation()
                        + "; declare the static method helper in a separate class");
        }
        readRuntimeContracts();
        validateMethodCycles();
    }

    private Program.MethodId normalized(Program.MethodId method) {
        return new Program.MethodId(canonical(method.owner()), method.name(), canonicalDescriptor(method.descriptor()));
    }

    private void validateMethodCycles() {
        for(MethodRule start : methodRules.values()) {
            var visited = new LinkedHashSet<Program.MethodId>();
            MethodRule current = start;
            while(current != null) {
                if(!visited.add(current.target())) throw error("JN4025", "Method substitution cycle: " + visited);
                var helper = new Program.MethodId(canonical(current.implementation().owner()), current.implementation().name(),
                        canonicalDescriptor(current.implementation().descriptor()));
                current = methodRules.get(helper);
            }
        }
    }

    private void readRuntimeContracts() {
        for(String line : runtimeResource("runtime-methods.tsv").lines().toList()) {
            if(line.isBlank() || line.startsWith("#")) continue;
            String[] values = line.split("\t", -1);
            if(values.length != 4 || !Set.of("STATIC", "INSTANCE").contains(values[3]) || !NativeBinding.validMethodDescriptor(values[2]))
                throw error("JN4020", "Malformed trusted runtime method contract: " + line);
            var target = new Program.MethodId(values[0], values[1], values[2]);
            if(runtimeContracts.putIfAbsent(target, values[3].equals("INSTANCE")) != null)
                throw error("JN4022", "Duplicate trusted runtime method contract: " + target);
        }
    }

    private static String runtimeResource(String name) {
        String member = "/META-INF/jnative/" + name;
        try(var stream = SubstitutionRegistry.class.getResourceAsStream(member)) {
            return stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch(IOException failure) { throw new CompilerException("JN4020 Cannot read trusted runtime contract " + member, failure); }
    }

    record ProviderBytes(SubstitutionArtifact artifact, String owner, SubstitutionArtifact.Bytes bytes) {}

    ProviderBytes replacementBytes(String logicalOwner) {
        ClassRule rule = classRules.get(logicalOwner);
        if(rule == null) return null;
        var artifact = artifact(rule.artifact());
        return new ProviderBytes(artifact, rule.implementation(), artifact.classBytes(rule.implementation()));
    }

    ProviderBytes helperBytes(String owner) {
        ProviderBytes selected = null;
        var artifacts = new ArrayList<SubstitutionArtifact>();
        for(var provider : providers) artifacts.add(provider.artifact());
        artifacts.addAll(dependencies);
        for(var artifact : artifacts) {
            var bytes = artifact.classBytes(owner);
            if(bytes == null) continue;
            if(selected != null && !selected.bytes().sha256().equals(bytes.sha256()))
                throw error("JN4022", "Conflicting helper bytecode " + owner + " in " + selected.artifact().path() + " and " + artifact.path());
            if(selected == null) selected = new ProviderBytes(artifact, owner, bytes);
        }
        return selected;
    }

    ClassNode normalize(ClassNode node) { return remapper.remap(node); }

    private ClassNode donor(Path path, String owner) {
        return normalizedDonors.computeIfAbsent(new DonorKey(path, owner), key -> normalize(artifact(key.artifact()).classNode(key.owner())));
    }

    private SubstitutionArtifact artifact(Path path) {
        return providers.stream().map(Provider::artifact).filter(artifact -> artifact.path().equals(path)).findFirst().orElseThrow();
    }

    public PlatformBindings.Table platformBindings() {
        if(bindings != null) return bindings;
        // Runtime target metadata is validated once from explicitly activated builtin declarations.
        for(Provider provider : providers) {
            if(!provider.builtin()) continue;
            for(String declaration : indexedDeclarations.getOrDefault(provider.id(), List.of())) {
                ClassNode owner = donor(provider.artifact().path(), declaration);
                for(MethodNode method : owner.methods) {
                    for(var target : PlatformBindings.read(owner, method)) {
                        if(baselineRoutes.putIfAbsent(target.api(), target) != null)
                            throw error("JN4022", "Duplicate builtin runtime contract: " + target.api());
                    }
                    for(var field : PlatformBindings.readStaticFields(owner, method)) {
                        if(baselineFields.putIfAbsent(field.field(), field) != null)
                            throw error("JN4022", "Duplicate builtin static field contract: " + field.field());
                    }
                }
            }
        }
        var selected = new LinkedHashMap<Program.MethodId, PlatformBindings.Target>(baselineRoutes);
        for(String line : runtimeResource("runtime-constructors.tsv").lines().toList()) {
            if(line.isBlank() || line.startsWith("#")) continue;
            String[] values = line.split("\t", -1);
            if(values.length != 6 || !values[1].equals("<init>") || !NativeBinding.validMethodDescriptor(values[2]) || !NativeBinding.validMethodDescriptor(values[5]))
                throw error("JN4020", "Malformed trusted runtime construction contract: " + line);
            boolean builtins = providers.stream().anyMatch(Provider::builtin);
            if(!builtins) continue;
            var target = new Program.MethodId(values[0], values[1], values[2]);
            var helper = new Program.MethodId(values[3], values[4], values[5]);
            selected.put(target, new PlatformBindings.Target(target, helper, true, null));
        }
        selected.entrySet().removeIf(entry -> classRules.containsKey(entry.getKey().owner()) && !classRules.get(entry.getKey().owner()).builtin());
        for(MethodRule rule : methodRules.values()) {
            if(!platform(rule.target().owner())) continue;
            var previous = baselineRoutes.get(rule.target());
            Boolean instance = previous == null ? runtimeContracts.get(rule.target()) : previous.instance();
            if(instance == null)
                throw error("JN4024", "Target staticness/declaration unavailable for runtime method: " + rule.providerId() + " " + rule.target());
            ClassNode owner = donor(rule.artifact(), rule.implementation().owner());
            MethodNode helper = find(owner, rule.implementation().name(), canonicalDescriptor(rule.implementation().descriptor()));
            NativeBinding binding = validateHelper(rule, owner, helper, instance);
            var helperId = new Program.MethodId(owner.name, helper.name, helper.desc);
            selected.put(rule.target(), new PlatformBindings.Target(rule.target(), helperId, instance, binding));
            Program.MethodId previousHelper = previous == null ? null : previous.helper();
            MethodRule builtin = builtinMethods.get(rule.target());
            if(!rule.builtin() && builtin != null) previousHelper = new Program.MethodId(canonical(builtin.implementation().owner()),
                    builtin.implementation().name(), canonicalDescriptor(builtin.implementation().descriptor()));
            if(previousHelper != null) previousMethods.put(rule.target(), previousHelper);
            methodOrigin(rule, previousHelper == null ? "unavailable" : previousHelper.toString());
        }
        var selectedFields = new LinkedHashMap<PlatformBindings.StaticField, PlatformBindings.FieldTarget>(baselineFields);
        selectedFields.entrySet().removeIf(entry -> classRules.containsKey(entry.getKey().owner()) && !classRules.get(entry.getKey().owner()).builtin());
        bindings = new PlatformBindings.Table(selected, selectedFields);
        return bindings;
    }

    /** Applies method replacements and active aliases to one already normalized effective declaration. */
    public void apply(ClassNode owner) {
        platformBindings();
        for(MethodRule rule : methodRules.values()) {
            if(!rule.target().owner().equals(owner.name) || platform(owner.name)) continue;
            MethodNode target = find(owner, rule.target().name(), canonicalDescriptor(rule.target().descriptor()));
            if(target == null) throw error("JN4021", "Target must be an exact declared method: " + rule.providerId() + " " + rule.target());
            ClassNode donor = donor(rule.artifact(), rule.implementation().owner());
            MethodNode helper = find(donor, rule.implementation().name(), canonicalDescriptor(rule.implementation().descriptor()));
            boolean instance = (target.access & Opcodes.ACC_STATIC) == 0;
            validateHelper(rule, donor, helper, instance);
            String hidden = "$jnative$previous$" + SubstitutionArtifact.hash(rule.target().toString().getBytes(StandardCharsets.UTF_8)).substring(0, 16);
            if(find(owner, hidden, target.desc) != null) throw error("JN4022", "Hidden previous-method identity collision: " + rule.target());
            var previous = new MethodNode(target.access, hidden, target.desc, target.signature, target.exceptions.toArray(String[]::new));
            target.accept(previous);
            previous.name = hidden;
            previous.access = (previous.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE;
            removeAnnotation(previous, "Lcom/github/xpenatan/jnative/interop/NativeExport;");
            owner.methods.add(previous);
            previousMethods.put(rule.target(), new Program.MethodId(owner.name, hidden, previous.desc));
            methodOrigin(rule, owner.name + "." + hidden + target.desc);
            target.access &= ~(Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT);
            removeAnnotation(target, "Lcom/github/xpenatan/jnative/interop/NativeImport;");
            target.instructions = new InsnList();
            target.tryCatchBlocks.clear();
            target.localVariables = null;
            target.visibleLocalVariableAnnotations = null;
            target.invisibleLocalVariableAnnotations = null;
            int local = loadArguments(target, instance);
            target.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, donor.name, helper.name, helper.desc, (donor.access & Opcodes.ACC_INTERFACE) != 0));
            target.instructions.add(new InsnNode(Type.getReturnType(target.desc).getOpcode(Opcodes.IRETURN)));
            target.maxLocals = local;
            target.maxStack = local + 2;
        }
        for(MethodNode method : List.copyOf(owner.methods)) {
            var logical = new Program.MethodId(owner.name, method.name, method.desc);
            Alias alias = nativeAliases.get(logical);
            if(alias == null) {
                for(var candidate : nativeAliases.values()) {
                    if(canonical(candidate.declaration().owner()).equals(owner.name) && candidate.declaration().name().equals(method.name)
                            && canonicalDescriptor(candidate.declaration().descriptor()).equals(method.desc)) { alias = candidate; break; }
                }
            }
            if(alias != null && active(alias)) lowerAlias(owner, method, alias);
        }
    }

    private NativeBinding validateHelper(MethodRule rule, ClassNode owner, MethodNode helper, boolean instance) {
        if(helper == null) throw error("JN4021", "Normalized donor method missing: " + rule);
        Type[] arguments = Type.getArgumentTypes(canonicalDescriptor(rule.target().descriptor()));
        var expected = new ArrayList<Type>();
        if(instance) expected.add(Type.getObjectType(canonical(rule.target().owner())));
        expected.addAll(List.of(arguments));
        String descriptor = Type.getMethodDescriptor(Type.getReturnType(canonicalDescriptor(rule.target().descriptor())), expected.toArray(Type[]::new));
        var key = new ExecutableKey(rule.artifact(), new Program.MethodId(owner.name, helper.name, helper.desc));
        NativeBinding binding;
        if(donorBindings.containsKey(key)) binding = donorBindings.get(key);
        else {
            binding = NativeBinding.read(owner, helper);
            donorBindings.put(key, binding);
        }
        if(!descriptor.equals(helper.desc)) {
            if(binding == null || !binding.managed() || !compatibleDescriptor(descriptor, helper.desc))
                throw error("JN4024", "Helper receiver/descriptor mismatch: provider " + rule.providerId() + " artifact " + rule.artifact()
                        + " target " + rule.target() + " donor " + rule.implementation() + ", expected " + descriptor + ", got " + helper.desc);
        }
        return binding;
    }

    private static boolean compatibleDescriptor(String expected, String actual) {
        Type[] left = Type.getArgumentTypes(expected), right = Type.getArgumentTypes(actual);
        if(left.length != right.length) return false;
        for(int i = 0; i < left.length; i++) if(!compatible(left[i], right[i])) return false;
        return compatible(Type.getReturnType(expected), Type.getReturnType(actual));
    }
    private static boolean compatible(Type expected, Type actual) {
        return expected.equals(actual) || (expected.getSort() == Type.OBJECT || expected.getSort() == Type.ARRAY) && actual.equals(Type.getType(Object.class));
    }

    private void methodOrigin(MethodRule rule, String previous) {
        var bytes = artifact(rule.artifact()).classBytes(rule.implementation().owner());
        methodOrigins.put(rule.target(), new MethodOrigin(rule.providerId(), rule.artifact().toString(), rule.implementation(), bytes.sha256(), previous));
    }

    private boolean active(Alias alias) {
        if(alias.original()) {
            MethodRule rule = methodRules.get(new Program.MethodId(canonical(alias.owner()), alias.name(), canonicalDescriptor(alias.descriptor())));
            return rule != null && rule.providerId().equals(alias.provider().id());
        }
        for(MethodRule rule : methodRules.values()) {
            if(!rule.providerId().equals(alias.provider().id())) continue;
            var pending = new ArrayDeque<String>();
            pending.add(rule.target().owner());
            var visited = new HashSet<String>();
            while(!pending.isEmpty()) {
                String target = pending.removeFirst();
                if(!visited.add(target)) continue;
                if(target.equals(canonical(alias.owner()))) return true;
                if(platform(target)) continue;
                ClassNode declaration = classPath.read(target);
                if(declaration.superName != null) pending.addLast(declaration.superName);
                pending.addAll(declaration.interfaces);
            }
        }
        return false;
    }

    private void lowerAlias(ClassNode owner, MethodNode method, Alias alias) {
        int required = Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE;
        if((method.access & required) != required || method.instructions.size() != 0)
            throw error("JN4026", "Active aliases require static native declarations without bodies: " + alias.declaration());
        if(Annotations.value(annotations(method.visibleAnnotations, method.invisibleAnnotations), "NativeImport") != null)
            throw error("JN4026", "Alias has multiple executable bindings: " + alias.declaration());
        String targetOwner = canonical(alias.owner());
        String targetDescriptor = canonicalDescriptor(alias.descriptor());
        var body = new InsnList();
        if(alias.original()) {
            var target = new Program.MethodId(targetOwner, alias.name(), targetDescriptor);
            if(!platform(targetOwner)) classPath.read(targetOwner);
            Program.MethodId previous = previousMethods.get(target);
            if(previous == null) throw error("JN4026", "Previous implementation unavailable: " + alias.declaration() + " target " + target);
            ClassNode previousOwner = classPath.read(previous.owner());
            MethodNode previousMethod = find(previousOwner, previous.name(), previous.descriptor());
            if(previousMethod == null || (previousMethod.access & Opcodes.ACC_ABSTRACT) != 0)
                throw error("JN4026", "Previous implementation is abstract or missing: " + target);
            NativeBinding.read(previousOwner, previousMethod);
            boolean instance = (previousMethod.access & Opcodes.ACC_STATIC) == 0;
            String expected = platform(targetOwner)
                    ? Boolean.TRUE.equals(runtimeContracts.get(target)) || baselineRoutes.get(target) != null && baselineRoutes.get(target).instance()
                        ? withReceiver(targetDescriptor, targetOwner) : targetDescriptor
                    : instance ? withReceiver(previous.descriptor(), previous.owner()) : previous.descriptor();
            if(!method.desc.equals(expected)) throw error("JN4024", "OriginalMethod alias descriptor mismatch: " + alias.declaration() + ", expected " + expected);
            load(body, Type.getArgumentTypes(method.desc));
            body.add(new MethodInsnNode(instance ? Opcodes.INVOKESPECIAL : Opcodes.INVOKESTATIC, previous.owner(), previous.name(), previous.descriptor(), (previousOwner.access & Opcodes.ACC_INTERFACE) != 0));
        }
        else {
            if(platform(targetOwner)) throw error("JN4026", "TargetField cannot expose runtime representation state: " + alias.declaration());
            ClassNode target = classPath.read(targetOwner);
            FieldNode field = null;
            var visited = new HashSet<String>();
            while(target != null && visited.add(target.name)) {
                for(FieldNode candidate : target.fields) if(candidate.name.equals(alias.name()) && candidate.desc.equals(targetDescriptor)) field = candidate;
                if(field != null) break;
                target = target.superName == null || platform(target.superName) ? null : classPath.read(target.superName);
            }
            if(field == null) throw error("JN4021", "TargetField missing exact field: " + alias.declaration() + " target " + targetOwner + "." + alias.name() + ":" + targetDescriptor);
            boolean instance = (field.access & Opcodes.ACC_STATIC) == 0;
            boolean set = alias.access().equals("SET");
            if(set && (field.access & Opcodes.ACC_FINAL) != 0) throw error("JN4026", "TargetField cannot write final field: " + alias.declaration());
            var arguments = new ArrayList<Type>();
            if(instance) arguments.add(Type.getObjectType(targetOwner));
            if(set) arguments.add(Type.getType(targetDescriptor));
            String expected = Type.getMethodDescriptor(set ? Type.VOID_TYPE : Type.getType(targetDescriptor), arguments.toArray(Type[]::new));
            if(!method.desc.equals(expected)) throw error("JN4024", "TargetField alias descriptor mismatch: " + alias.declaration() + ", expected " + expected);
            load(body, Type.getArgumentTypes(method.desc));
            body.add(new FieldInsnNode(instance ? set ? Opcodes.PUTFIELD : Opcodes.GETFIELD : set ? Opcodes.PUTSTATIC : Opcodes.GETSTATIC,
                    target.name, field.name, field.desc));
        }
        body.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));
        method.access &= ~Opcodes.ACC_NATIVE;
        method.instructions = body;
        method.maxLocals = Arrays.stream(Type.getArgumentTypes(method.desc)).mapToInt(Type::getSize).sum();
        method.maxStack = method.maxLocals + 2;
        removeAnnotation(method, ANNOTATIONS + (alias.original() ? "OriginalMethod;" : "TargetField;"));
    }

    private static String withReceiver(String descriptor, String owner) { return "(L" + owner + ";" + descriptor.substring(1); }
    private static int loadArguments(MethodNode method, boolean instance) {
        int local = 0;
        if(instance) method.instructions.add(new VarInsnNode(Opcodes.ALOAD, local++));
        for(Type argument : Type.getArgumentTypes(method.desc)) {
            method.instructions.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
            local += argument.getSize();
        }
        return local;
    }
    private static void load(InsnList body, Type[] arguments) {
        int local = 0;
        for(Type argument : arguments) {
            body.add(new VarInsnNode(argument.getOpcode(Opcodes.ILOAD), local));
            local += argument.getSize();
        }
    }
    private static void removeAnnotation(MethodNode method, String descriptor) {
        if(method.visibleAnnotations != null) method.visibleAnnotations.removeIf(annotation -> annotation.desc.equals(descriptor));
        if(method.invisibleAnnotations != null) method.invisibleAnnotations.removeIf(annotation -> annotation.desc.equals(descriptor));
    }

    public String canonical(String owner) { return remapper.owner(owner.replace('.', '/')); }
    public String canonicalDescriptor(String descriptor) { return remapper.descriptor(descriptor); }
    public ClassRule classReplacement(String owner) { return classRules.get(canonical(owner)); }
    public boolean hasGeneratedClass(String owner) {
        String canonical = canonical(owner);
        if(classRules.containsKey(canonical)) return true;
        int lambda = canonical.indexOf("$jNativeLambda$");
        if(lambda >= 0 && classRules.containsKey(canonical.substring(0, lambda))) return true;
        if(!canonical.startsWith("[") && classPath.hasOriginal(canonical)) return true;
        // An unavailable JDK API stays in the runtime diagnostic path so missing
        // members can be reported together. This fallback does not classify its
        // layout as fixed: selected donors and supplied originals already won.
        return !canonical.startsWith("[") && !RuntimeLibrary.runtimeOwner(canonical)
                && !canonical.startsWith("java/") && !canonical.startsWith("javax/") && !canonical.startsWith("jdk/");
    }
    public boolean platform(String owner) { return !hasGeneratedClass(owner); }

    void validate() {
        platformBindings();
        for(ClassRule rule : classRules.values()) {
            ClassNode original = classPath.raw(rule.target());
            if(original == null) continue;
            ClassNode donor = donor(rule.artifact(), rule.implementation());
            int kind = Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM | Opcodes.ACC_RECORD | Opcodes.ACC_ANNOTATION;
            if((original.access & kind) != (donor.access & kind))
                throw error("JN4024", "Class kind mismatch: provider " + rule.providerId() + " target " + rule.target() + " donor " + rule.implementation());
            if((original.access & Opcodes.ACC_PUBLIC) != 0 && (donor.access & Opcodes.ACC_PUBLIC) == 0)
                throw error("JN4024", "Class replacement narrows public visibility: " + rule);
            if((original.access & Opcodes.ACC_FINAL) == 0 && (donor.access & Opcodes.ACC_FINAL) != 0)
                throw error("JN4024", "Class replacement adds final restriction: " + rule);
            if((original.access & Opcodes.ACC_ABSTRACT) == 0 && (donor.access & Opcodes.ACC_ABSTRACT) != 0)
                throw error("JN4024", "Class replacement adds abstract restriction: " + rule);
            if(donor.permittedSubclasses != null && !donor.permittedSubclasses.isEmpty()
                    && (original.permittedSubclasses == null || original.permittedSubclasses.isEmpty()
                    || !donor.permittedSubclasses.containsAll(original.permittedSubclasses)))
                throw error("JN4024", "Class replacement narrows permitted subclasses: " + rule);
            if(!Objects.equals(original.superName, donor.superName) || !donor.interfaces.containsAll(original.interfaces))
                throw error("JN4024", "Class replacement changes required hierarchy: " + rule);
            for(MethodNode method : original.methods) {
                if(!externallyAccessible(method.access)) continue;
                MethodNode replacement = find(donor, method.name, method.desc);
                if(replacement == null) continue;
                String member = "method " + rule.target() + "." + method.name + method.desc;
                validateMemberAccess(rule, member, method.access, replacement.access);
                if((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_FINAL)) == 0
                        && (replacement.access & Opcodes.ACC_FINAL) != 0 && !method.name.startsWith("<"))
                    throw error("JN4024", "Class replacement adds final restriction to " + member + ": " + rule);
                if((method.access & Opcodes.ACC_ABSTRACT) == 0 && (replacement.access & Opcodes.ACC_ABSTRACT) != 0)
                    throw error("JN4024", "Class replacement makes concrete " + member + " abstract: " + rule);
            }
            for(FieldNode field : original.fields) {
                if(!externallyAccessible(field.access)) continue;
                FieldNode replacement = donor.fields.stream().filter(candidate -> candidate.name.equals(field.name)
                        && candidate.desc.equals(field.desc)).findFirst().orElse(null);
                if(replacement == null) continue;
                String member = "field " + rule.target() + "." + field.name + ":" + field.desc;
                validateMemberAccess(rule, member, field.access, replacement.access);
                if((field.access & Opcodes.ACC_FINAL) == 0 && (replacement.access & Opcodes.ACC_FINAL) != 0)
                    throw error("JN4024", "Class replacement adds final restriction to " + member + ": " + rule);
            }
        }
        for(MethodRule rule : methodRules.values()) if(!platform(rule.target().owner())) classPath.read(rule.target().owner());
        for(Alias alias : nativeAliases.values()) if(active(alias)) classPath.read(canonical(alias.declaration().owner()));
    }

    private static boolean externallyAccessible(int access) {
        return (access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
    }

    private static void validateMemberAccess(ClassRule rule, String member, int original, int replacement) {
        boolean narrows = (original & Opcodes.ACC_PUBLIC) != 0 ? (replacement & Opcodes.ACC_PUBLIC) == 0
                : !externallyAccessible(replacement);
        if(narrows) throw error("JN4024", "Class replacement narrows visibility of " + member + ": " + rule);
        if((original & Opcodes.ACC_STATIC) != (replacement & Opcodes.ACC_STATIC))
            throw error("JN4024", "Class replacement changes staticness of " + member + ": " + rule);
    }
    public Map<String, ClassRule> classRules() { return Collections.unmodifiableMap(classRules); }
    public Map<Program.MethodId, MethodRule> methodRules() { return Collections.unmodifiableMap(methodRules); }
    public Map<Program.MethodId, MethodOrigin> methodOrigins() { return Map.copyOf(methodOrigins); }
    public String reportJson() {
        return reportJson(null, null);
    }

    public String reportJson(Set<String> reachableClasses, Set<Program.MethodId> reachableMethods) {
        var declarations = new ArrayList<Map<String, Object>>();
        for(var declaration : report) {
            var item = new TreeMap<String, Object>(declaration);
            String target = (String)item.get("target");
            String kind = (String)item.get("kind");
            item.put("status", declarationStatus(item, reachableClasses, reachableMethods));
            String targetOwner = kind.equals("class") ? target : encodedOwner(target);
            String effectiveHash = classPath.effectiveClassHash(targetOwner);
            if(effectiveHash != null) item.put("effectiveClassSha256", effectiveHash);
            if(kind.equals("method")) {
                var targetMethod = encodedMethod(target);
                String methodHash = classPath.effectiveMethodHash(targetMethod);
                MethodRule rule = methodRules.get(targetMethod);
                if(methodHash == null && rule != null && platform(targetMethod.owner()))
                    methodHash = classPath.effectiveMethodHash(normalized(rule.implementation()));
                if(methodHash != null) item.put("effectiveMethodSha256", methodHash);
            }
            ClassNode original = classPath.raw(targetOwner);
            if(original != null) {
                var bytes = classPath.originalOrigin(targetOwner);
                item.put("originalArtifact", bytes.artifact());
                item.put("originalMember", bytes.member());
                item.put("originalClassSha256", bytes.classSha256());
            }
            declarations.add(item);
        }
        declarations.sort(Comparator.comparing(item -> item.get("target") + ":" + item.get("kind") + ":" + item.get("providerId")));
        var providerReports = new ArrayList<Map<String, Object>>();
        for(Provider provider : providers) {
            var item = new TreeMap<String, Object>();
            item.put("providerId", provider.id());
            item.put("artifact", provider.artifact().path().toString());
            item.put("builtin", provider.builtin());
            item.put("indexSha256", provider.hash().equals("programmatic") ? null : provider.hash());
            item.put("artifactSha256", artifactHash(provider.artifact()));
            providerReports.add(item);
        }
        providerReports.sort(Comparator.comparing(item -> item.get("providerId").toString()));
        var inputs = new ArrayList<Map<String, Object>>();
        for(var entry : new TreeMap<>(classPath.origins()).entrySet()) {
            var origin = entry.getValue();
            var item = new TreeMap<String, Object>();
            item.put("owner", entry.getKey());
            item.put("artifact", origin.artifact());
            item.put("member", origin.member());
            item.put("classSha256", origin.classSha256());
            String effectiveHash = classPath.effectiveClassHash(entry.getKey());
            if(effectiveHash != null) item.put("effectiveClassSha256", effectiveHash);
            inputs.add(item);
        }
        var restrictions = new TreeMap<String, String>();
        for(String owner : RuntimeLibrary.runtimeTypes()) {
            String restriction = RuntimeLibrary.forbiddenClassReplacementReason(owner);
            if(restriction != null) restrictions.put(owner, restriction);
        }
        restrictions.put("<arrays>", RuntimeLibrary.forbiddenClassReplacementReason("["));
        var origins = new ArrayList<Map<String, Object>>();
        methodOrigins.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(Program.MethodId::toString))).forEach(entry -> {
            var item = new TreeMap<String, Object>();
            item.put("target", entry.getKey().toString());
            item.put("providerId", entry.getValue().providerId());
            item.put("artifact", entry.getValue().artifact());
            item.put("donor", entry.getValue().donor().toString());
            item.put("donorClassSha256", entry.getValue().sha256());
            item.put("previousImplementation", entry.getValue().previousImplementation());
            var donor = entry.getValue().donor();
            String donorHash = classPath.effectiveMethodHash(normalized(donor));
            String targetHash = classPath.effectiveMethodHash(entry.getKey());
            if(targetHash == null && platform(entry.getKey().owner())) targetHash = donorHash;
            if(targetHash != null) item.put("effectiveMethodSha256", targetHash);
            if(donorHash != null) item.put("effectiveDonorMethodSha256", donorHash);
            origins.add(item);
        });
        var document = new TreeMap<String, Object>();
        document.put("schemaVersion", 1);
        document.put("providers", providerReports);
        var dependencyReports = new ArrayList<Map<String, Object>>();
        for(var dependency : dependencies) {
            var item = new TreeMap<String, Object>();
            item.put("artifact", dependency.path().toString());
            item.put("artifactSha256", artifactHash(dependency));
            dependencyReports.add(item);
        }
        dependencyReports.sort(Comparator.comparing(item -> item.get("artifact").toString()));
        document.put("dependencies", dependencyReports);
        document.put("declarations", declarations);
        document.put("inputs", inputs);
        document.put("methods", origins);
        document.put("runtimeRestrictions", restrictions);
        return Json.write(document);
    }

    public String reportTsv(Set<String> reachableClasses, Set<Program.MethodId> reachableMethods) {
        var output = new StringBuilder("status\tkind\ttarget\tprovider\tdonor\tartifact\n");
        for(var item : report.stream().sorted(Comparator.comparing(row -> row.get("target") + ":" + row.get("kind") + ":" + row.get("providerId"))).toList()) {
            output.append(declarationStatus(item, reachableClasses, reachableMethods));
            for(String column : List.of("kind", "target", "providerId", "donor", "artifact"))
                output.append('\t').append(item.get(column).toString().replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n"));
            output.append('\n');
        }
        return output.toString();
    }

    private String declarationStatus(Map<String, Object> item, Set<String> classes, Set<Program.MethodId> methods) {
        String status = (String)item.get("status");
        if(!status.equals("selected") || classes == null || methods == null) return status;
        String target = (String)item.get("target");
        if(item.get("kind").equals("class")) return classes.contains(target) ? status : "unused";
        int descriptor = target.indexOf('(');
        int separator = target.lastIndexOf('.', descriptor);
        var id = new Program.MethodId(encodedOwner(target), target.substring(separator + 1, descriptor), target.substring(descriptor));
        if(methods.contains(id)) return status;
        MethodRule rule = methodRules.get(id);
        if(rule != null && methods.contains(new Program.MethodId(canonical(rule.implementation().owner()),
                rule.implementation().name(), canonicalDescriptor(rule.implementation().descriptor())))) return status;
        return "unused";
    }

    private static String encodedOwner(String member) {
        int descriptor = member.indexOf('(');
        int separator = member.lastIndexOf('.', descriptor);
        return internal(member.substring(0, separator));
    }

    private static Program.MethodId encodedMethod(String member) {
        int descriptor = member.indexOf('(');
        int separator = member.lastIndexOf('.', descriptor);
        return new Program.MethodId(encodedOwner(member), member.substring(separator + 1, descriptor), member.substring(descriptor));
    }

    private static String artifactHash(SubstitutionArtifact artifact) {
        try {
            if(Files.isRegularFile(artifact.path())) return SubstitutionArtifact.hash(Files.readAllBytes(artifact.path()));
            var fingerprints = new StringBuilder();
            try(var walk = Files.walk(artifact.path())) {
                for(Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                    String member = artifact.path().relativize(file).toString().replace('\\', '/');
                    fingerprints.append(member).append('\0').append(artifact.read(member).sha256()).append('\n');
                }
            }
            return SubstitutionArtifact.hash(fingerprints.toString().getBytes(StandardCharsets.UTF_8));
        } catch(IOException failure) { throw new CompilerException("JN4020 Cannot fingerprint provider " + artifact.path(), failure); }
    }

    private Provider provider(String id, SubstitutionArtifact artifact, boolean builtin, String hash) {
        for(Provider previous : providers) {
            if(previous.id().equals(id)) {
                if(!previous.artifact().path().equals(artifact.path()) || !previous.hash().equals(hash))
                    throw error("JN4022", "Duplicate provider id with different artifacts/definitions: " + id);
                return previous;
            }
        }
        var provider = new Provider(id, artifact, builtin, hash);
        providers.add(provider);
        return provider;
    }

    private void scan(Provider provider, String declaration, Map<String, List<ClassRule>> classes,
            Map<Program.MethodId, List<MethodRule>> methods) {
        ClassNode node = provider.artifact().classNode(declaration);
        for(var annotation : annotations(node.visibleAnnotations, node.invisibleAnnotations)) {
            if(annotation.desc.equals(ANNOTATIONS + "SubstituteClass;")) {
                Object target = property(annotation, "value");
                if(target instanceof String name && name.startsWith("[")) throw error("JN4027", "Cannot replace array representation: provider "
                        + provider.id() + " artifact " + provider.artifact().path() + " target " + name + " donor " + node.name);
                addClass(provider, binary(property(annotation, "value")), node.name, classes);
            }
        }
        for(MethodNode method : node.methods) {
            var implementation = new Program.MethodId(node.name, method.name, method.desc);
            for(var annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if(annotation.desc.equals(ANNOTATIONS + "SubstituteMethod;")) addAnnotatedMethod(provider, annotation, implementation, methods);
                else if(annotation.desc.equals(ANNOTATIONS + "SubstituteMethods;")) {
                    Object contents = property(annotation, "value");
                    if(!(contents instanceof List<?> list)) throw error("JN4020", "Malformed repeatable declarations: " + implementation);
                    for(Object value : list) {
                        if(!(value instanceof AnnotationNode child) || !child.desc.equals(ANNOTATIONS + "SubstituteMethod;"))
                            throw error("JN4020", "Malformed repeatable declaration: " + implementation);
                        addAnnotatedMethod(provider, child, implementation, methods);
                    }
                }
            }
        }
        scanAliases(provider, node);
    }

    private void addAnnotatedMethod(Provider provider, AnnotationNode annotation, Program.MethodId implementation,
            Map<Program.MethodId, List<MethodRule>> methods) {
        try {
            var reference = new MethodReference((String)property(annotation, "owner"), (String)property(annotation, "name"), (String)property(annotation, "descriptor"));
            addMethod(provider, id(reference), implementation, methods);
        } catch(IllegalArgumentException | NullPointerException failure) {
            throw error("JN4020", "Malformed method rule in provider " + provider.id() + " donor " + implementation + ": " + failure.getMessage());
        }
    }

    private void addClass(Provider provider, String target, String donor, Map<String, List<ClassRule>> classes) {
        try { new ClassSubstitution(target, donor); }
        catch(IllegalArgumentException failure) { throw error("JN4020", provider.id() + ": " + failure.getMessage()); }
        String restriction = RuntimeLibrary.forbiddenClassReplacementReason(target);
        if(restriction != null) throw error("JN4027", "Cannot replace runtime representation: provider " + provider.id()
                + " artifact " + provider.artifact().path() + " target " + target + " donor " + donor + ": " + restriction);
        String previous = aliases.putIfAbsent(donor, target);
        if(previous != null && !previous.equals(target)) throw error("JN4022", "Donor maps to multiple targets: " + donor);
        provider.artifact().validateClass(donor);
        var rule = new ClassRule(provider.id(), provider.artifact().path(), target, donor, provider.builtin());
        var values = classes.computeIfAbsent(target, ignored -> new ArrayList<>());
        if(!values.contains(rule)) values.add(rule);
    }

    private void addMethod(Provider provider, Program.MethodId target, Program.MethodId donor,
            Map<Program.MethodId, List<MethodRule>> methods) {
        if(target.equals(donor)) throw error("JN4025", "A method cannot substitute itself: " + provider.id() + " " + target);
        Integer access = provider.artifact().methodAccess(donor.owner(), donor.name(), donor.descriptor());
        if(access == null) throw error("JN4021", "Missing donor method: " + provider.id() + " " + provider.artifact().path() + " " + donor);
        if((access & Opcodes.ACC_STATIC) == 0 || (access & Opcodes.ACC_SYNCHRONIZED) != 0)
            throw error("JN4024", "Method helper must be static and not synchronized: " + provider.id() + " " + donor);
        var rule = new MethodRule(provider.id(), provider.artifact().path(), target, donor, provider.builtin());
        var values = methods.computeIfAbsent(target, ignored -> new ArrayList<>());
        if(!values.contains(rule)) values.add(rule);
    }

    private void scanAliases(Provider provider, ClassNode node) {
        for(MethodNode method : node.methods) {
            long aliasCount = annotations(method.visibleAnnotations, method.invisibleAnnotations).stream()
                    .filter(annotation -> annotation.desc.equals(ANNOTATIONS + "OriginalMethod;")
                            || annotation.desc.equals(ANNOTATIONS + "TargetField;")).count();
            if(aliasCount > 1) throw error("JN4026", "Alias has multiple executable bindings: provider " + provider.id()
                    + " " + new Program.MethodId(node.name, method.name, method.desc));
            for(var annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                boolean original = annotation.desc.equals(ANNOTATIONS + "OriginalMethod;");
                if(!original && !annotation.desc.equals(ANNOTATIONS + "TargetField;")) continue;
                var declaration = new Program.MethodId(node.name, method.name, method.desc);
                if((method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE)) != (Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE)
                        || method.instructions.size() != 0)
                    throw error("JN4026", "Alias declarations must be static native without Java bodies: provider " + provider.id() + " " + declaration);
                if(Annotations.value(annotations(method.visibleAnnotations, method.invisibleAnnotations), "NativeImport") != null)
                    throw error("JN4026", "Alias has multiple executable bindings: provider " + provider.id() + " " + declaration);
                String owner = binary(property(annotation, "owner"));
                Object name = property(annotation, "name"), descriptor = property(annotation, "descriptor");
                if(!(name instanceof String) || !(descriptor instanceof String)) throw error("JN4020", "Invalid alias: " + declaration);
                if(original) {
                    try { new MethodReference(owner, (String)name, (String)descriptor); }
                    catch(IllegalArgumentException failure) { throw error("JN4020", "Invalid OriginalMethod alias " + declaration + ": " + failure.getMessage()); }
                }
                else if(!NativeBinding.validValueDescriptor((String)descriptor) || ((String)name).isBlank()
                        || ((String)name).matches(".*[.;/\\[<>\\s].*"))
                    throw error("JN4020", "Invalid TargetField alias: " + declaration);
                String access = "";
                if(!original) {
                    Object operation = property(annotation, "access");
                    if(!(operation instanceof String[] enumeration) || enumeration.length != 2
                            || !enumeration[0].equals(ANNOTATIONS + "FieldAccess;") || !Set.of("GET", "SET").contains(enumeration[1]))
                        throw error("JN4020", "Invalid field access operation: " + declaration);
                    access = enumeration[1];
                }
                var alias = new Alias(provider, declaration, owner, (String)name, (String)descriptor, access, original);
                var previous = nativeAliases.putIfAbsent(declaration, alias);
                if(previous != null && !previous.equals(alias)) throw error("JN4022", "Multiple alias bindings: " + declaration);
            }
        }
    }

    private void validateAliases() {
        for(String donor : aliases.keySet()) {
            var visited = new HashSet<String>();
            String target = donor;
            while(aliases.containsKey(target)) {
                if(!visited.add(target)) throw error("JN4025", "Class substitution cycle: " + visited);
                target = aliases.get(target);
            }
            if(!target.equals(aliases.get(donor))) throw error("JN4025", "Chained donor/target aliases are ambiguous: " + donor);
        }
    }

    private static ClassRule chooseClass(String target, List<ClassRule> rules, String preference) {
        var external = rules.stream().filter(rule -> !rule.builtin()).toList();
        var candidates = external.isEmpty() ? rules : external;
        if(preference != null) {
            candidates = candidates.stream().filter(rule -> rule.providerId().equals(preference)).toList();
            if(candidates.isEmpty()) throw error("JN4023", "Unknown/ineffective class provider preference " + preference + " for " + target);
        }
        if(candidates.size() != 1) throw error("JN4022", "Multiple class substitution winners for " + target + ": " + candidates);
        return candidates.getFirst();
    }

    private static MethodRule chooseMethod(Program.MethodId target, List<MethodRule> rules, String preference) {
        var external = rules.stream().filter(rule -> !rule.builtin()).toList();
        var candidates = external.isEmpty() ? rules : external;
        if(preference != null) {
            candidates = candidates.stream().filter(rule -> rule.providerId().equals(preference)).toList();
            if(candidates.isEmpty()) throw error("JN4023", "Unknown/ineffective method provider preference " + preference + " for " + target);
        }
        if(candidates.size() != 1) throw error("JN4022", "Multiple method substitution winners for " + target + ": " + candidates);
        return candidates.getFirst();
    }

    private void report(String provider, String kind, String target, String donor, boolean selected) {
        var item = new TreeMap<String, Object>();
        item.put("providerId", provider);
        item.put("kind", kind);
        item.put("target", target);
        item.put("donor", donor);
        item.put("status", selected ? "selected" : "shadowed");
        Provider definition = providers.stream().filter(value -> value.id().equals(provider)).findFirst().orElseThrow();
        item.put("artifact", definition.artifact().path().toString());
        String owner = kind.equals("class") ? donor : encodedOwner(donor);
        item.put("donorClassSha256", definition.artifact().classBytes(owner).sha256());
        report.add(item);
    }

    private static String internal(String binary) { return binary.replace('.', '/'); }
    private static String binary(Object value) {
        if(!(value instanceof String name)) throw error("JN4020", "Missing/invalid binary name in substitution metadata");
        try { return internal(new MethodReference(name, "$validate", "()V").owner()); }
        catch(IllegalArgumentException failure) { throw error("JN4020", "Invalid binary name: " + value); }
    }
    private static Program.MethodId id(MethodReference method) { return new Program.MethodId(internal(method.owner()), method.name(), method.descriptor()); }
    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) { return Annotations.all(visible, invisible); }
    private static Object property(AnnotationNode annotation, String key) {
        if(annotation.values != null) for(int i = 0; i < annotation.values.size(); i += 2)
            if(annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }
    private static MethodNode find(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst().orElse(null);
    }
    private static CompilerException error(String code, String message) { return new CompilerException(code + " " + message); }
}
