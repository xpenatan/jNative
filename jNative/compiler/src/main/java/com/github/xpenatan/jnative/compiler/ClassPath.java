package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import com.github.xpenatan.jnative.SubstitutionOptions;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/** Reads raw artifact bytecode separately from one build's effective declarations. */
final class ClassPath {
    private final List<SubstitutionArtifact> entries;
    private final Map<String, ClassNode> cache = new LinkedHashMap<>();
    private final Map<String, Program.InputOrigin> origins = new LinkedHashMap<>();
    private final Map<String, SubstitutionRegistry.ProviderBytes> originals = new LinkedHashMap<>();
    private final Map<String, String> effectiveClassHashes = new LinkedHashMap<>();
    private final Map<Program.MethodId, String> effectiveMethodHashes = new LinkedHashMap<>();
    private final SubstitutionRegistry registry;

    ClassPath(List<Path> entries) { this(entries, SubstitutionOptions.defaults()); }

    ClassPath(List<Path> entries, SubstitutionOptions options) {
        this.entries = entries.stream().filter(Files::exists).map(SubstitutionArtifact::new).toList();
        if(this.entries.isEmpty()) throw new CompilerException("JN1001 Classpath entry does not exist: " + entries);
        registry = new SubstitutionRegistry(this, options);
        registry.validate();
    }

    SubstitutionRegistry registry() { return registry; }
    Map<String, Program.InputOrigin> origins() { return Map.copyOf(origins); }
    String effectiveClassHash(String owner) { return effectiveClassHashes.get(owner); }
    String effectiveMethodHash(Program.MethodId method) { return effectiveMethodHashes.get(method); }

    /** Fresh original declaration, before donor aliases and method patches; null when omitted. */
    ClassNode raw(String owner) {
        var original = original(owner);
        return original == null ? null : decodeOriginal(owner, original.bytes().content());
    }

    boolean hasOriginal(String owner) { return original(owner) != null; }

    Program.InputOrigin originalOrigin(String owner) {
        var original = original(owner);
        return original == null ? null : new Program.InputOrigin(original.artifact().path().toString(),
                original.bytes().member(), original.bytes().sha256(), "");
    }

    private SubstitutionRegistry.ProviderBytes original(String owner) {
        // Every cached key, including misses, has already passed owner validation.
        if(originals.containsKey(owner)) return originals.get(owner);
        validateOriginalOwner(owner);
        for(var entry : entries) {
            var bytes = originalBytes(entry, owner);
            if(bytes != null) {
                var original = new SubstitutionRegistry.ProviderBytes(entry, owner, bytes);
                originals.put(owner, original);
                return original;
            }
        }
        originals.put(owner, null);
        return null;
    }

    ClassNode read(String requested) {
        String name = registry.canonical(requested);
        validateOriginalOwner(name);
        if(cache.containsKey(name)) return cache.get(name);
        var replacement = registry.replacementBytes(name);
        if(replacement != null) return effective(name, replacement.artifact(), replacement.owner(), replacement.bytes());
        for(var entry : entries) {
            var bytes = originalBytes(entry, name);
            if(bytes != null) {
                var helper = registry.helperBytes(name);
                if(helper != null && !helper.bytes().sha256().equals(bytes.sha256()))
                    throw new CompilerException("JN4022 Conflicting application/provider helper bytes: " + name + " in " + entry.path() + " and " + helper.artifact().path());
                return effective(name, entry, name, bytes);
            }
        }
        var helper = registry.helperBytes(name);
        if(helper != null) return effective(name, helper.artifact(), helper.owner(), helper.bytes());
        throw new CompilerException("JN1001 Class not found: " + name.replace('/', '.'));
    }

    private static SubstitutionArtifact.Bytes originalBytes(SubstitutionArtifact entry, String owner) {
        try { return entry.classBytes(owner); }
        catch(CompilerException failure) {
            throw new CompilerException("JN1001 Cannot read " + owner + " from " + entry.path(), failure);
        }
    }

    private static void validateOriginalOwner(String owner) {
        try { SubstitutionArtifact.validateOwner(owner); }
        catch(CompilerException failure) { throw new CompilerException("JN1001 Invalid class name: " + owner, failure); }
    }

    private static ClassNode decodeOriginal(String owner, byte[] bytes) {
        try {
            if(bytes.length < 10 || ByteBuffer.wrap(bytes).getInt() != 0xcafebabe)
                throw new CompilerException("JN1004 Invalid classfile header: " + owner);
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
            int major = node.version & 0xffff;
            if(major < 52 || major > 69 || node.version >>> 16 == 65535)
                throw new CompilerException("JN1001 Unsupported classfile version " + major + ": " + owner);
            if(!node.name.equals(owner)) throw new CompilerException("JN1001 Class name/path mismatch: " + owner);
            return node;
        } catch(CompilerException failure) { throw failure; }
        catch(RuntimeException failure) {
            throw new CompilerException("JN1004 Malformed classfile: " + owner + ": " + failure.getMessage(), failure);
        }
    }

    private ClassNode effective(String name, SubstitutionArtifact artifact, String donor, SubstitutionArtifact.Bytes bytes) {
        ClassNode raw = registry.classReplacement(name) == null && entries.contains(artifact)
                ? decodeOriginal(donor, bytes.content()) : SubstitutionArtifact.decode(donor, bytes.content());
        ClassNode node = registry.normalize(raw);
        if(!node.name.equals(name)) throw new CompilerException("JN4020 Effective class identity mismatch: " + name + " donor " + donor);
        origins.put(name, new Program.InputOrigin(artifact.path().toString(), bytes.member(), bytes.sha256(), "", donor.equals(name) ? "" : donor));
        cache.put(name, node);
        try {
            registry.apply(node);
            for(var generated : LambdaLowering.lower(node).entrySet()) {
                origins.put(generated.getKey(), new Program.InputOrigin(artifact.path().toString(), bytes.member(), bytes.sha256(), name, donor.equals(name) ? "" : donor));
                if(cache.putIfAbsent(generated.getKey(), generated.getValue()) != null)
                    throw new CompilerException("JN1004 Synthetic lambda class name collision: " + generated.getKey());
                recordEffectiveHashes(generated.getValue());
            }
            recordEffectiveHashes(node);
            return node;
        } catch(RuntimeException failure) {
            cache.remove(name);
            origins.remove(name);
            effectiveClassHashes.remove(name);
            effectiveMethodHashes.keySet().removeIf(method -> method.owner().equals(name));
            throw failure;
        }
    }

    private void recordEffectiveHashes(ClassNode node) {
        var writer = new ClassWriter(0);
        node.accept(writer);
        effectiveClassHashes.put(node.name, sha256(writer.toByteArray()));
        for(var method : node.methods) {
            var methodWriter = new ClassWriter(0);
            methodWriter.visit(node.version, node.access, node.name, node.signature, node.superName, node.interfaces.toArray(String[]::new));
            method.accept(methodWriter);
            methodWriter.visitEnd();
            effectiveMethodHashes.put(new Program.MethodId(node.name, method.name, method.desc), sha256(methodWriter.toByteArray()));
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }
}
