package com.github.xpenatan.jnative.internal;

import com.github.xpenatan.jnative.CompilerException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/**
 * Deterministic metadata and staged installation with edit protection.
 */
public final class ProjectFiles {
    public static final String METADATA = "jnative-project.properties";
    public static final String RUNTIME_VERSION = "0.2.0-abi1";

    private ProjectFiles() {
    }

    public static Properties read(Path path) throws IOException {
        var values = new Properties();
        if(Files.isRegularFile(path))
            try(var reader = Files.newBufferedReader(path)) {
                values.load(reader);
            }
        return values;
    }

    public static byte[] properties(Map<String, String> values) {
        var text = new StringBuilder("# jNative deterministic metadata\n");
        new TreeMap<>(values)
                .forEach(
                        (key, value) ->
                                text.append(escape(key))
                                        .append('=')
                                        .append(escape(value))
                                        .append('\n'));
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace(" ", "\\ ")
                .replace("=", "\\=")
                .replace(":", "\\:")
                .replace("#", "\\#")
                .replace("!", "\\!");
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch(NoSuchAlgorithmException error) {
            throw new AssertionError(error);
        }
    }

    public static void assertRegenerable(Path project) {
        try {
            if("editable".equals(read(project.resolve(METADATA)).getProperty("mode")))
                throw new CompilerException(
                        "JN4002 This is a developer-owned native export. Generate into a new build root: "
                                + project);
        } catch(IOException error) {
            throw new CompilerException("JN4001 Cannot read project metadata", error);
        }
    }

    public static void installOwned(Path project, Map<Path, byte[]> content) throws IOException {
        assertRegenerable(project);
        Path manifest = project.resolve("jnative.manifest");
        Properties previous = read(manifest);
        var originals = new LinkedHashMap<Path, byte[]>();
        var obsolete = new LinkedHashSet<Path>();
        var hashes = new TreeMap<String, String>();
        for(var file : content.entrySet()) {
            String key = project.relativize(file.getKey()).toString().replace('\\', '/');
            hashes.put(key, hash(file.getValue()));
            if(Files.exists(file.getKey())) {
                byte[] before = Files.readAllBytes(file.getKey());
                originals.put(file.getKey(), before);
                if(!Arrays.equals(before, file.getValue())
                        && !hash(before).equals(previous.getProperty(key)))
                    throw new CompilerException(
                            "JN4002 Refusing to overwrite an edited or unowned file: "
                                    + file.getKey());
            }
        }
        for(String key : previous.stringPropertyNames()) {
            if(hashes.containsKey(key)) continue;
            Path path = project.resolve(key).normalize();
            // An old manifest never grants deletion authority outside the project.
            if(!path.startsWith(project) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                continue;
            if(!path.toRealPath().startsWith(project.toRealPath()))
                throw new CompilerException(
                        "JN4002 Refusing to remove a file reached through an external link: "
                                + path);
            byte[] before = Files.readAllBytes(path);
            if(!hash(before).equals(previous.getProperty(key)))
                throw new CompilerException(
                        "JN4002 Obsolete generated file has local edits: " + path);
            originals.put(path, before);
            obsolete.add(path);
        }
        var files = new LinkedHashMap<>(content);
        files.put(
                manifest,
                properties(hashes)); // Publish ownership only after every output is installed.
        if(Files.exists(manifest)) originals.put(manifest, Files.readAllBytes(manifest));
        Files.createDirectories(project.getParent());
        Path staging = Files.createTempDirectory(project.getParent(), ".jnative-stage-");
        var installed = new ArrayList<Path>();
        try {
            var staged = new LinkedHashMap<Path, Path>();
            int index = 0;
            for(var file : files.entrySet()) {
                // Preserve timestamps so native build systems reuse unchanged translation units.
                if(Arrays.equals(originals.get(file.getKey()), file.getValue())) continue;
                Path path = staging.resolve(Integer.toString(index++));
                Files.write(path, file.getValue());
                staged.put(file.getKey(), path);
            }
            for(var file : staged.entrySet()) {
                if(file.getKey().equals(manifest)) {
                    for(Path path : obsolete) {
                        Files.delete(path);
                        installed.add(path);
                    }
                }
                Files.createDirectories(file.getKey().getParent());
                replace(file.getValue(), file.getKey());
                installed.add(file.getKey());
            }
        } catch(IOException failure) {
            Collections.reverse(installed);
            for(Path path : installed) {
                try {
                    if(originals.containsKey(path)) Files.write(path, originals.get(path));
                    else Files.deleteIfExists(path);
                } catch(IOException rollback) {
                    failure.addSuppressed(rollback);
                }
            }
            throw failure;
        } finally {
            removeStaging(staging);
        }
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch(AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void removeStaging(Path staging) throws IOException {
        Path root = staging.toAbsolutePath().normalize();
        if(!root.getFileName().toString().startsWith(".jnative-stage-"))
            throw new IOException("Not a compiler staging directory: " + root);
        try(var walk = Files.walk(root)) {
            for(Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if(!path.toAbsolutePath().normalize().startsWith(root))
                    throw new IOException("Staging path escaped root");
                Files.deleteIfExists(path);
            }
        }
    }
}
