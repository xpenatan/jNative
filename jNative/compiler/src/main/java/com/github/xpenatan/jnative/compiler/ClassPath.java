package com.github.xpenatan.jnative.compiler;

import com.github.xpenatan.jnative.CompilerException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * Reads class files without defining classes or running Java initializers.
 */
final class ClassPath {
    private final List<Path> entries;
    private final Map<String, ClassNode> cache = new LinkedHashMap<>();

    private final Map<String, Program.InputOrigin> origins = new LinkedHashMap<>();

    Map<String, Program.InputOrigin> origins() {
        return Map.copyOf(origins);
    }

    ClassPath(List<Path> entries) {
        // Like the JVM, ignore absent optional classpath entries (Gradle resource
        // directories commonly do not exist when a project has no resources).
        this.entries = entries.stream().filter(Files::exists).toList();
        if(this.entries.isEmpty())
            throw new CompilerException("JN1001 Classpath entry does not exist: " + entries);
    }

    ClassNode read(String name) {
        if(cache.containsKey(name)) return cache.get(name);
        if(ClassLibrary.contains(name))
            return decode(name, ClassLibrary.read(name), "jnative-classlib", name + ".class");
        if(name.isEmpty()
                || name.contains("..")
                || name.startsWith("/")
                || name.endsWith("/")
                || name.contains("//")
                || name.chars().anyMatch(c -> c < 32 || "\\:.[".indexOf(c) >= 0))
            throw new CompilerException("JN1001 Invalid class name: " + name);
        for(Path entry : entries) {
            try {
                byte[] bytes = null;
                String memberName = name + ".class";
                if(Files.isDirectory(entry)) {
                    Path file = entry.resolve(name + ".class");
                    if(Files.isRegularFile(file)) {
                        if(!file.toRealPath().startsWith(entry.toRealPath()))
                            throw new CompilerException(
                                    "JN1001 Class file escapes classpath directory: " + name);
                        bytes = Files.readAllBytes(file);
                    }
                }
                else {
                    try(var jar =
                                new JarFile(
                                        entry.toFile(),
                                        false,
                                        ZipFile.OPEN_READ,
                                        Runtime.Version.parse("25"))) {
                        var member = jar.getJarEntry(name + ".class");
                        if(member != null)
                            try(var stream = jar.getInputStream(member)) {
                                memberName = member.getRealName();
                                bytes = stream.readAllBytes();
                            }
                    }
                }
                if(bytes == null) continue;
                return decode(
                        name, bytes, entry.toAbsolutePath().normalize().toString(), memberName);
            } catch(IOException | IllegalArgumentException error) {
                throw new CompilerException("JN1001 Cannot read " + name + " from " + entry, error);
            }
        }
        throw new CompilerException("JN1001 Class not found: " + name.replace('/', '.'));
    }

    private ClassNode decode(String name, byte[] bytes, String artifact, String member) {
        try {
            String hash =
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(bytes));
            origins.put(name, new Program.InputOrigin(artifact, member, hash, ""));
            return decodeChecked(name, bytes);
        } catch(java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        } catch(CompilerException error) {
            throw error;
        } catch(RuntimeException error) {
            throw new CompilerException(
                    "JN1004 Malformed classfile: " + name + ": " + error.getMessage(), error);
        }
    }

    private ClassNode decodeChecked(String name, byte[] bytes) {
        if(bytes.length < 10 || java.nio.ByteBuffer.wrap(bytes).getInt() != 0xcafebabe)
            throw new CompilerException("JN1004 Invalid classfile header: " + name);
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        int major = node.version & 0xffff;
        if(major < 52 || major > 69 || (node.version >>> 16) == 65535)
            throw new CompilerException(
                    "JN1001 Unsupported classfile version " + major + ": " + name);
        if(!name.equals(node.name))
            throw new CompilerException("JN1001 Class name/path mismatch: " + name);
        cache.put(name, node);
        for(var generated : LambdaLowering.lower(node).entrySet()) {
            LibraryLowering.lower(generated.getValue());
            var origin = origins.get(name);
            origins.put(
                    generated.getKey(),
                    new Program.InputOrigin(
                            origin.artifact(), origin.member(), origin.classSha256(), name));
            if(cache.putIfAbsent(generated.getKey(), generated.getValue()) != null)
                throw new CompilerException(
                        "JN1004 Synthetic lambda class name collision: " + generated.getKey());
        }
        LibraryLowering.lower(node);
        return node;
    }
}
