package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Artifact-scoped immutable byte reader; never consults or defines host JVM classes. */
final class SubstitutionArtifact {
    static final String INDEX = "META-INF/jnative/substitutions.json";
    private final Path path;
    private final Map<String, Bytes> cache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, ClassNode> rawClasses = Collections.synchronizedMap(new HashMap<>());

    SubstitutionArtifact(Path path) {
        this.path = path.toAbsolutePath().normalize();
        if(!Files.isDirectory(this.path) && !Files.isRegularFile(this.path))
            throw new CompilerException("JN4020 Substitution artifact does not exist: " + this.path);
    }

    Path path() { return path; }

    Set<String> classNames() {
        var names = new TreeSet<String>();
        try {
            if(Files.isDirectory(path)) {
                try(var walk = Files.walk(path)) {
                    for(Path file : walk.filter(Files::isRegularFile).toList()) {
                        String member = path.relativize(file).toString().replace('\\', '/');
                        if(member.endsWith(".class") && !member.startsWith("META-INF/"))
                            names.add(member.substring(0, member.length() - 6));
                    }
                }
            }
            else {
                try(var jar = new JarFile(path.toFile(), false, ZipFile.OPEN_READ, Runtime.Version.parse("25"))) {
                    for(var entries = jar.entries(); entries.hasMoreElements();) {
                        String member = entries.nextElement().getName();
                        if(member.startsWith("META-INF/versions/")) {
                            int separator = member.indexOf('/', "META-INF/versions/".length());
                            if(separator < 0 || !jar.isMultiRelease()) continue;
                            int version = Integer.parseInt(member.substring("META-INF/versions/".length(), separator));
                            if(version > 25 || version < 9) continue;
                            member = member.substring(separator + 1);
                        }
                        if(member.endsWith(".class") && !member.startsWith("META-INF/")) names.add(member.substring(0, member.length() - 6));
                    }
                }
            }
        } catch(IOException | IllegalArgumentException failure) {
            throw new CompilerException("JN4020 Cannot inventory substitution artifact: " + path, failure);
        }
        names.remove("module-info");
        names.forEach(SubstitutionArtifact::validateOwner);
        return Collections.unmodifiableSet(names);
    }

    record Bytes(byte[] content, String member, String sha256) {
        Bytes { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }

    Bytes read(String member) {
        if(member.isEmpty() || member.startsWith("/") || member.endsWith("/")
                || member.contains("..") || member.contains("//")
                || member.chars().anyMatch(c -> c < 32 || "\\:".indexOf(c) >= 0))
            throw new CompilerException("JN4020 Invalid substitution artifact member: " + member);
        if(cache.containsKey(member)) return cache.get(member);
        try {
            byte[] content;
            String physical = member;
            if(Files.isDirectory(path)) {
                Path file = path.resolve(member);
                if(!Files.isRegularFile(file)) { cache.put(member, null); return null; }
                if(!file.toRealPath().startsWith(path.toRealPath()))
                    throw new CompilerException("JN4020 Substitution entry escapes artifact: " + path + "!" + member);
                content = Files.readAllBytes(file);
            }
            else {
                try(var jar = new JarFile(path.toFile(), false, ZipFile.OPEN_READ, Runtime.Version.parse("25"))) {
                    var entry = jar.getJarEntry(member);
                    if(entry == null) { cache.put(member, null); return null; }
                    physical = entry.getRealName();
                    try(var stream = jar.getInputStream(entry)) { content = stream.readAllBytes(); }
                }
            }
            var bytes = new Bytes(content, physical, hash(content));
            cache.put(member, bytes);
            return bytes;
        } catch(IOException | IllegalArgumentException failure) {
            throw new CompilerException("JN4020 Cannot read substitution artifact " + path + "!" + member, failure);
        }
    }

    Bytes classBytes(String owner) {
        validateOwner(owner);
        return read(owner + ".class");
    }

    ClassNode classNode(String owner) {
        ClassNode raw = rawClass(owner);
        var snapshot = new ClassNode();
        raw.accept(snapshot);
        return snapshot;
    }

    void validateClass(String owner) { rawClass(owner); }

    Integer methodAccess(String owner, String name, String descriptor) {
        return rawClass(owner).methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .map(method -> method.access).findFirst().orElse(null);
    }

    private ClassNode rawClass(String owner) {
        return rawClasses.computeIfAbsent(owner, name -> {
            Bytes bytes = classBytes(name);
            if(bytes == null) throw new CompilerException("JN4021 Substitution donor missing: " + path + "!" + name);
            return decode(name, bytes.content());
        });
    }

    static ClassNode decode(String owner, byte[] bytes) {
        try {
            if(bytes.length < 10 || ByteBuffer.wrap(bytes).getInt() != 0xcafebabe)
                throw new IllegalArgumentException("Invalid classfile header");
            var node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
            int major = node.version & 0xffff;
            if(major < 52 || major > 69 || node.version >>> 16 == 65535)
                throw new IllegalArgumentException("Unsupported classfile version " + major);
            if(!node.name.equals(owner)) throw new IllegalArgumentException("Class name/path mismatch");
            return node;
        } catch(RuntimeException failure) {
            throw new CompilerException("JN4020 Malformed substitution class " + owner + ": " + failure.getMessage(), failure);
        }
    }

    static void validateOwner(String owner) {
        if(owner.isEmpty() || owner.contains("..") || owner.startsWith("/") || owner.endsWith("/")
                || owner.contains("//") || owner.chars().anyMatch(c -> c < 32 || "\\:.[;".indexOf(c) >= 0))
            throw new CompilerException("JN4020 Invalid class name: " + owner);
    }

    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
