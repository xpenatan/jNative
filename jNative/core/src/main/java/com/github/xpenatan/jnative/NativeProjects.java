package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.BuildPaths;
import com.github.xpenatan.jnative.internal.NativePaths;
import com.github.xpenatan.jnative.internal.ProjectFiles;
import com.github.xpenatan.jnative.toolchain.cmake.CMakeToolchain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Opens or exports self-contained native projects without loading Java inputs.
 */
public final class NativeProjects {
    private NativeProjects() {
    }

    /**
     * Reads versioned project metadata from an existing generated or editable project.
     */
    public static NativeProject open(Path directory) {
        Path root = BuildPaths.absolute(directory, "directory");
        try {
            var values = ProjectFiles.read(root.resolve(ProjectFiles.METADATA));
            if(!Set.of("1", "2", "3").contains(values.getProperty("schema", ""))
                    || !Set.of("0.1.0-abi1", ProjectFiles.RUNTIME_VERSION)
                    .contains(values.getProperty("runtime", "")))
                throw new CompilerException(
                        "JN4003 Unsupported or missing native project/runtime metadata: " + root);
            if(!Set.of("generated", "editable").contains(values.getProperty("mode", "")))
                throw new CompilerException(
                        "JN4003 Invalid native project ownership mode: " + root);
            return new NativeProject(
                    root,
                    values.getProperty("target"),
                    values.getProperty("mode").equals("editable"));
        } catch(IOException | IllegalArgumentException error) {
            throw new CompilerException("JN4003 Cannot open native project: " + root, error);
        }
    }

    /**
     * Copies the current source snapshot, including local application edits and user
     * files, into a new developer-owned directory. The destination must not exist.
     * Java inputs and native build products are not copied.
     */
    public static NativeProject export(NativeGenerationResult generation, Path destination) {
        Objects.requireNonNull(generation, "generation");
        Path origin = generation.request().buildRoot().resolve("native");
        var files = new TreeSet<Path>();
        files.addAll(generation.generatedFiles());
        files.addAll(generation.projectFiles());
        return export(
                open(origin), generation.request().generatedSourcesDirectory(), files, destination);
    }

    /**
     * Exports a self-contained generated project using its on-disk ownership manifest.
     */
    public static NativeProject export(Path directory, Path destination) {
        NativeProject project = open(directory);
        try {
            var metadata = ProjectFiles.read(project.directory().resolve(ProjectFiles.METADATA));
            Path sources =
                    project.directory().resolve(metadata.getProperty("sources", "src")).normalize();
            var files = new TreeSet<Path>();
            for(String name :
                    ProjectFiles.read(project.directory().resolve("jnative.manifest"))
                            .stringPropertyNames()) {
                Path file = project.directory().resolve(name).normalize();
                if(!file.startsWith(project.directory()))
                    throw new CompilerException(
                            "JN4003 External generated files need exportProject(generation, destination): "
                                    + file);
                files.add(file);
            }
            if(project.editable() || files.isEmpty() || !sources.startsWith(project.directory()))
                throw new CompilerException(
                        "JN4003 Expected a self-contained generated project with an ownership manifest");
            return export(project, sources, files, destination);
        } catch(IOException error) {
            throw new CompilerException("JN4003 Cannot read export manifest", error);
        }
    }

    private static NativeProject export(
            NativeProject project, Path sourceRoot, Set<Path> inputs, Path destination) {
        Path target = BuildPaths.absolute(destination, "destination");
        Path origin = project.directory();
        if(Files.exists(target) || target.startsWith(origin))
            throw new CompilerException(
                    "JN4002 Export needs a new directory outside the generated project: " + target);
        Path staging = null;
        try {
            Files.createDirectories(target.getParent());
            staging = Files.createTempDirectory(target.getParent(), ".jnative-stage-");
            var files = new TreeSet<>(inputs);
            Path user = origin.resolve("user");
            if(Files.isDirectory(user))
                try(var walk = Files.walk(user)) {
                    walk.filter(Files::isRegularFile).forEach(files::add);
                }
            NativePaths.validateProject(
                    target,
                    target.resolve("src"),
                    files.stream()
                            .map(
                                    file ->
                                            target.resolve(
                                                    file.startsWith(sourceRoot)
                                                            ? Path.of("src")
                                                            .resolve(
                                                                    sourceRoot.relativize(
                                                                            file))
                                                            : origin.relativize(file)))
                            .toList(),
                    project.targetFileName());
            var hashes = new TreeMap<String, String>();
            for(Path file : files) {
                if(file.equals(origin.resolve("jnative.manifest"))
                        || file.equals(origin.resolve(ProjectFiles.METADATA))) continue;
                Path relative =
                        file.startsWith(sourceRoot)
                                ? Path.of("src").resolve(sourceRoot.relativize(file))
                                : origin.relativize(file);
                NativePaths.validate(target.resolve(relative));
                Path copied = staging.resolve(relative).normalize();
                Path allowed = file.startsWith(sourceRoot) ? sourceRoot : origin;
                if(!copied.startsWith(staging)
                        || Files.isSymbolicLink(file)
                        || !file.toRealPath().startsWith(allowed.toRealPath()))
                    throw new CompilerException(
                            "JN4003 Unsupported external file or link in export: " + file);
                byte[] content = Files.readAllBytes(file);
                if(Set.of("CMakeLists.txt", "jnative-sources.cmake", "jnative-sources.json")
                        .contains(relative.toString())) {
                    String cmake = new String(content, StandardCharsets.UTF_8);
                    for(Path source : inputs)
                        if(source.startsWith(sourceRoot))
                            cmake =
                                    cmake.replace(
                                            CMakeToolchain.quoted(
                                                    origin.relativize(source).toString()),
                                            CMakeToolchain.quoted(
                                                    Path.of("src")
                                                            .resolve(sourceRoot.relativize(source))
                                                            .toString()));
                    cmake =
                            cmake.replace(
                                    CMakeToolchain.quoted(origin.relativize(sourceRoot).toString()),
                                    "\"src\"");
                    content = cmake.getBytes(StandardCharsets.UTF_8);
                }
                Files.createDirectories(copied.getParent());
                Files.write(copied, content);
                hashes.put(relative.toString().replace('\\', '/'), ProjectFiles.hash(content));
            }
            var metadata = ProjectFiles.read(origin.resolve(ProjectFiles.METADATA));
            var exported = new TreeMap<String, String>();
            metadata.forEach((key, value) -> exported.put(key.toString(), value.toString()));
            exported.put("mode", "editable");
            exported.put("sources", "src");
            Files.write(staging.resolve(ProjectFiles.METADATA), ProjectFiles.properties(exported));
            Files.write(
                    staging.resolve("export-snapshot.properties"), ProjectFiles.properties(hashes));
            Files.move(
                    staging,
                    target); // Fresh-directory publication; never replace an existing snapshot.
            staging = null;
            return new NativeProject(target, project.targetFileName(), true);
        } catch(IOException error) {
            throw new CompilerException("JN4001 Cannot export native project: " + target, error);
        } finally {
            if(staging != null)
                try {
                    ProjectFiles.removeStaging(staging);
                } catch(IOException cleanup) {
                    /* Preserve the original failure; staging is never a successful export. */
                }
        }
    }
}
