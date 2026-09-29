package com.github.xpenatan.jnative;

import com.github.xpenatan.jnative.internal.Json;
import com.github.xpenatan.jnative.internal.NativePaths;
import com.github.xpenatan.jnative.internal.ProjectFiles;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Exact-build symbol archives, local report export, and offline native address decoding.
 */
public final class NativeDiagnostics {
    private NativeDiagnostics() {
    }

    private static Path child(Path root, String relative) {
        Path path = root.resolve(relative).normalize();
        if(Path.of(relative).isAbsolute() || !path.startsWith(root) || path.equals(root))
            throw new CompilerException("JN4100 Diagnostic path escapes its bundle: " + relative);
        return path;
    }

    private static Map<String, Object> read(Path path) throws IOException {
        if(Files.size(path) > 32 * 1024 * 1024)
            throw new CompilerException("JN4100 Diagnostic document exceeds 32 MiB");
        return Json.object(Json.read(Files.readString(path)));
    }

    /**
     * Verifies every archived file and returns its immutable descriptor.
     */
    public static NativeDiagnosticsBundle openBundle(Path directory) {
        Path root = directory.toAbsolutePath().normalize();
        try {
            var manifest = read(root.resolve("manifest.json"));
            String id = Objects.toString(manifest.get("buildId"), "");
            if(!id.matches("[0-9a-f]{32}")
                    || !"1".equals(Objects.toString(manifest.get("schema"))))
                throw new CompilerException("JN4100 Unsupported diagnostic bundle");
            var files = Json.object(manifest.get("files"));
            if(files.isEmpty() || files.size() > 100000)
                throw new CompilerException("JN4100 Invalid artifact inventory");
            for(var file : files.entrySet()) {
                Path path = child(root, file.getKey());
                if(!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !path.toRealPath().startsWith(root.toRealPath())
                        || !hash(path).equals(file.getValue()))
                    throw new CompilerException(
                            "JN4101 Diagnostic artifact checksum mismatch: " + file.getKey());
            }
            for(String required : List.of("sources.zip", "source-hashes.json"))
                if(!files.containsKey(required))
                    throw new CompilerException(
                            "JN4100 Missing archive inventory entry: " + required);
            String executable = Objects.toString(manifest.get("executable"), "");
            if(executable.isEmpty()
                    || executable.contains("/")
                    || executable.contains("\\")
                    || !files.containsKey("linked/" + executable))
                throw new CompilerException("JN4100 Unverified primary executable");
            var names = new HashSet<String>();
            for(Object item : Json.array(manifest.get("modules"))) {
                var module = Json.object(item);
                if(!names.add(Objects.toString(module.get("name"), ""))
                        || !Objects.toString(module.get("sha256"), "").matches("[0-9a-f]{64}"))
                    throw new CompilerException("JN4100 Invalid module inventory");
                for(String key : List.of("image", "symbols"))
                    if(!files.containsKey(module.get(key)))
                        throw new CompilerException(
                                "JN4100 Unverified module artifact: " + module.get(key));
                if(!module.get("sha256").equals(files.get(module.get("image"))))
                    throw new CompilerException("JN4101 Module image checksum mismatch");
            }
            if(Files.exists(root.resolve("source-map.json"))
                    && !files.containsKey("source-map.json"))
                throw new CompilerException("JN4100 Unverified source map");
            return new NativeDiagnosticsBundle(
                    id, root, root.resolve("manifest.json"), root.resolve("sources.zip"));
        } catch(IOException | IllegalArgumentException e) {
            throw new CompilerException("JN4100 Cannot read diagnostic bundle: " + root, e);
        }
    }

    /**
     * Selects the last completed archive for this project and native configuration.
     */
    public static NativeDiagnosticsBundle latest(Path project, BuildType type) {
        Path root = project.toAbsolutePath().normalize();
        try {
            String id =
                    Files.readString(
                                    root.resolve(
                                            "diagnostics/latest-"
                                                    + (type == BuildType.DEBUG
                                                    ? "Debug"
                                                    : "Release")
                                                    + ".txt"))
                            .strip();
            if(!id.matches("[0-9a-f]{32}"))
                throw new CompilerException("JN4100 Invalid build identity");
            return openBundle(root.resolve("diagnostics").resolve(id));
        } catch(IOException e) {
            throw new CompilerException("JN4100 No completed diagnostic bundle for " + root, e);
        }
    }

    /**
     * Copies a verified bundle to an immutable private store outside disposable build output.
     */
    public static NativeDiagnosticsBundle archive(Path project, BuildType type, Path store) {
        return archiveBuild(project, latest(project, type), store);
    }

    static NativeDiagnosticsBundle archiveBuild(
            Path project, NativeDiagnosticsBundle selected, Path store) {
        NativeDiagnosticsBundle source = openBundle(selected.directory());
        if(!source.buildId().equals(selected.buildId()))
            throw new CompilerException("JN4102 Diagnostic build identity changed");
        Path root = store.toAbsolutePath().normalize(),
                nativeRoot = project.toAbsolutePath().normalize();
        Path staging = null;
        try {
            boolean editable =
                    "editable"
                            .equals(
                                    ProjectFiles.read(nativeRoot.resolve(ProjectFiles.METADATA))
                                            .getProperty("mode"));
            Path disposable = editable ? nativeRoot : nativeRoot.getParent();
            if(root.startsWith(disposable))
                throw new CompilerException(
                        "JN4102 Archive store must be outside disposable build output: " + root);
            Files.createDirectories(root);
            if(root.toRealPath().startsWith(disposable.toRealPath()))
                throw new CompilerException("JN4102 Archive store resolves inside build output");
            Path destination = root.resolve(source.buildId());
            if(Files.exists(destination)) {
                var existing = openBundle(destination);
                if(!Files.readString(existing.manifest())
                        .equals(Files.readString(source.manifest())))
                    throw new CompilerException(
                            "JN4102 Build identity already has a different archive");
                return existing;
            }
            staging = Files.createTempDirectory(root, ".jnative-stage-");
            var inventory = Json.object(read(source.manifest()).get("files"));
            for(String relative : inventory.keySet()) {
                NativePaths.validate(
                        child(destination, relative));
                Path target = child(staging, relative);
                Files.createDirectories(target.getParent());
                Files.copy(child(source.directory(), relative), target);
            }
            Files.copy(source.manifest(), staging.resolve("manifest.json"));
            openBundle(staging);
            Files.move(staging, destination);
            staging = null;
            return openBundle(destination);
        } catch(IOException e) {
            throw new CompilerException("JN4102 Cannot retain release archive", e);
        } finally {
            if(staging != null)
                try {
                    ProjectFiles.removeStaging(staging);
                } catch(IOException ignored) {
                }
        }
    }

    /**
     * Extracts the verified source snapshot to a new directory, with portable path checks.
     */
    public static Path extractSources(NativeDiagnosticsBundle archive, Path destination) {
        var bundle = openBundle(archive.directory());
        Path target = destination.toAbsolutePath().normalize(), staging = null;
        try(var zip = new ZipFile(bundle.sources().toFile())) {
            if(Files.exists(target))
                throw new CompilerException("JN4105 Source extraction requires a new directory");
            var expected = read(bundle.directory().resolve("source-hashes.json"));
            if(expected.size() > 100000)
                throw new CompilerException("JN4105 Too many source files");
            var names = new HashSet<String>();
            var entries = zip.stream().filter(entry -> !entry.isDirectory()).toList();
            for(var entry : entries) {
                String name = entry.getName().replaceFirst("^\\./", "");
                if(!names.add(name) || !expected.containsKey(name))
                    throw new CompilerException("JN4105 Invalid source archive entry: " + name);
                NativePaths.validate(child(target, name));
                if(entry.getSize() < 0 || entry.getSize() > 64L * 1024 * 1024)
                    throw new CompilerException("JN4105 Source file exceeds 64 MiB");
            }
            if(!names.equals(expected.keySet()))
                throw new CompilerException("JN4105 Source inventory does not match ZIP entries");
            Files.createDirectories(target.getParent());
            staging = Files.createTempDirectory(target.getParent(), ".jnative-stage-source-");
            long total = 0;
            for(var entry : entries) {
                String name = entry.getName().replaceFirst("^\\./", "");
                total += entry.getSize();
                if(total > 1024L * 1024 * 1024)
                    throw new CompilerException("JN4105 Sources exceed 1 GiB");
                Path file = child(staging, name);
                Files.createDirectories(file.getParent());
                try(var input = zip.getInputStream(entry)) {
                    Files.copy(input, file);
                }
                if(!hash(file).equals(expected.get(name)))
                    throw new CompilerException(
                            "JN4105 Extracted source checksum mismatch: " + name);
            }
            Files.move(staging, target);
            staging = null;
            return target;
        } catch(IOException | IllegalArgumentException e) {
            throw new CompilerException("JN4105 Cannot extract native sources", e);
        } finally {
            if(staging != null)
                try {
                    ProjectFiles.removeStaging(staging);
                } catch(IOException ignored) {
                }
        }
    }

    /**
     * Lists saved nonempty report documents. No reports are uploaded or removed.
     */
    public static List<Path> pendingReports(Path directory) {
        if(!Files.isDirectory(directory)) return List.of();
        try(var files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(
                            p -> {
                                try {
                                    return Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                                            && Files.size(p) > 0;
                                } catch(IOException e) {
                                    return false;
                                }
                            })
                    .sorted(
                            Comparator.comparingLong(NativeDiagnostics::reportTime)
                                    .thenComparing(Path::toString))
                    .toList();
        } catch(IOException e) {
            throw new CompilerException("JN4103 Cannot list crash reports", e);
        }
    }

    private static long reportTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch(IOException missing) {
            return 0;
        }
    }

    /**
     * Exports one report and its dump, if present, into a new directory for manual submission.
     */
    public static Path exportReport(Path report, Path destination) {
        return exportReport(report, destination, true);
    }

    /**
     * Exports a report, optionally omitting its memory dump for a smaller attachment.
     */
    public static Path exportReport(Path report, Path destination, boolean includeDump) {
        Path target = destination.toAbsolutePath().normalize(),
                source = report.toAbsolutePath().normalize(),
                staging = null;
        try {
            var data = read(source);
            validateReport(data);
            if(Files.exists(target))
                throw new CompilerException("JN4103 Report export requires a new directory");
            String dump = Objects.toString(data.get("dump"), "");
            Path dumpPath = dump.isEmpty() || !includeDump ? null : child(source.getParent(), dump);
            if(dumpPath != null
                    && (!dumpPath.toRealPath().startsWith(source.getParent().toRealPath())
                    || !Files.isRegularFile(dumpPath, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(dumpPath) > 1024L * 1024 * 1024))
                throw new CompilerException(
                        "JN4103 Invalid report attachment or dump exceeds 1 GiB");
            Files.createDirectories(target.getParent());
            staging = Files.createTempDirectory(target.getParent(), ".jnative-stage-report-");
            if(!includeDump) {
                data.put("dump", "");
                data.put("dumpIncluded", false);
            }
            else if(dumpPath != null) data.put("dump", dumpPath.getFileName().toString());
            Files.writeString(staging.resolve(source.getFileName()), Json.write(data) + "\n");
            if(dumpPath != null) Files.copy(dumpPath, staging.resolve(dumpPath.getFileName()));
            Files.move(staging, target);
            staging = null;
            return target.resolve(source.getFileName());
        } catch(IOException | IllegalArgumentException e) {
            throw new CompilerException("JN4103 Cannot export report", e);
        } finally {
            if(staging != null)
                try {
                    ProjectFiles.removeStaging(staging);
                } catch(IOException ignored) {
                }
        }
    }

    private static void validateReport(Map<String, Object> report) {
        if(!"1".equals(Objects.toString(report.get("schema")))
                || !Objects.toString(report.get("buildId"), "").matches("[0-9a-f]{32}"))
            throw new CompilerException("JN4104 Unsupported report schema or build identity");
        if(Json.array(report.getOrDefault("threads", List.of())).size() > 128
                || Json.array(report.getOrDefault("causes", List.of())).size() > 16)
            throw new CompilerException("JN4104 Report exceeds thread/cause limit");
    }

    /**
     * Decoded text and JSON refer only to the verified, exact-build source snapshot.
     */
    public record DecodedReport(String buildId, String text, String json) {
    }

    public static DecodedReport decode(Path report, Path symbolStore) {
        return decode(report, symbolStore, "addr2line");
    }

    /**
     * Decodes the saved Windows minidump using the verified archived helper.
     */
    public static DecodedReport decodeDump(Path report, Path symbolStore, String symbolizer) {
        try {
            Path reportPath = report.toAbsolutePath().normalize();
            var document = read(reportPath);
            validateReport(document);
            String id = Objects.toString(document.get("buildId"), "");
            if(!id.matches("[0-9a-f]{32}"))
                throw new CompilerException("JN4104 Invalid dump build identity");
            Path store = symbolStore.toAbsolutePath().normalize();
            var bundle =
                    openBundle(
                            Files.isRegularFile(store.resolve("manifest.json"))
                                    ? store
                                    : store.resolve(id));
            if(!bundle.buildId().equals(id))
                throw new CompilerException("JN4104 Dump build identity mismatch");
            Path dump = child(reportPath.getParent(), Objects.toString(document.get("dump"), ""));
            if(!Json.object(read(bundle.manifest()).get("files"))
                    .containsKey("images/jnative-diagnostics.exe"))
                throw new CompilerException("JN4104 Missing verified dump helper");
            if(!Files.isRegularFile(dump, LinkOption.NOFOLLOW_LINKS)
                    || !dump.toRealPath().startsWith(reportPath.getParent().toRealPath()))
                throw new CompilerException("JN4104 Invalid minidump attachment");
            Path helper = bundle.directory().resolve("images/jnative-diagnostics.exe");
            if(!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"))
                throw new CompilerException(
                        "JN4104 Windows minidump unwinding requires the Windows native helper");
            var unwound =
                    Json.object(
                            Json.read(
                                    run(
                                            List.of(
                                                    helper.toString(),
                                                    "--dump",
                                                    dump.toString(),
                                                    bundle.directory()
                                                            .resolve("images")
                                                            .toString()),
                                            bundle.directory(),
                                            Duration.ofSeconds(30))));
            if(!id.equals(Json.object(unwound.get("metadata")).get("buildId")))
                throw new CompilerException("JN4104 Minidump metadata identity mismatch");
            document.put("frames", unwound.get("frames"));
            document.put("threads", unwound.getOrDefault("threads", List.of()));
            document.put(
                    "unwindStatus",
                    "partial when required module images or unwind data are unavailable");
            Path temporary = Files.createTempFile("jnative-dump-", ".json");
            try {
                Files.writeString(temporary, Json.write(document));
                return decode(temporary, bundle.directory(), symbolizer);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch(IOException | IllegalArgumentException e) {
            throw new CompilerException("JN4104 Cannot decode minidump", e);
        }
    }

    /**
     * Decodes a Linux x64 ELF core using GDB with Python support, on the developer machine.
     */
    public static DecodedReport decodeCore(
            Path report, Path core, Path symbolStore, String debugger) {
        Path work = null;
        try {
            var document = read(report.toAbsolutePath().normalize());
            validateReport(document);
            String id = Objects.toString(document.get("buildId"), "");
            if(!id.matches("[0-9a-f]{32}"))
                throw new CompilerException("JN4104 Invalid core build identity");
            Path store = symbolStore.toAbsolutePath().normalize();
            var bundle =
                    openBundle(
                            Files.isRegularFile(store.resolve("manifest.json"))
                                    ? store
                                    : store.resolve(id));
            if(!bundle.buildId().equals(id))
                throw new CompilerException("JN4104 Core build identity mismatch");
            if(!"Linux".equals(read(bundle.manifest()).get("JN_SYSTEM")))
                throw new CompilerException("JN4104 Linux archive required for core decoding");
            work = Files.createTempDirectory(".jnative-stage-core-");
            Path script = work.resolve("decode.py"),
                    request = work.resolve("request.json"),
                    result = work.resolve("result.json");
            try(var input =
                        NativeDiagnostics.class.getResourceAsStream("/cpp/jn_core_decode.py")) {
                if(input == null)
                    throw new CompilerException("JN4104 Missing core decoder resource");
                Files.copy(input, script);
            }
            Files.writeString(
                    request,
                    Json.write(
                            Map.of(
                                    "archive",
                                    linuxPath(bundle.directory()),
                                    "core",
                                    linuxPath(core),
                                    "buildId",
                                    id,
                                    "output",
                                    linuxPath(result))));
            var command =
                    new ArrayList<>(
                            List.of(
                                    debugger,
                                    "-nx",
                                    "-nh",
                                    "-batch",
                                    "-iex",
                                    "set auto-load off",
                                    "-iex",
                                    "set debuginfod enabled off",
                                    "-x",
                                    linuxPath(script)));
            if(windowsHost())
                command.addAll(
                        0,
                        List.of(
                                "wsl",
                                "--exec",
                                "env",
                                "JNATIVE_CORE_REQUEST=" + linuxPath(request)));
            run(
                    command,
                    bundle.directory(),
                    Duration.ofSeconds(60),
                    Map.of("JNATIVE_CORE_REQUEST", linuxPath(request)));
            var output = read(result);
            var sourceMap =
                    Files.isRegularFile(bundle.directory().resolve("source-map.json"))
                            ? read(bundle.directory().resolve("source-map.json"))
                            : Map.<String, Object>of();
            var sourceHashes = read(bundle.directory().resolve("source-hashes.json"));
            StringBuilder text = new StringBuilder("Build: ").append(id).append("  Event: core\n");
            for(Object item : Json.array(output.get("threads"))) {
                var thread = Json.object(item);
                text.append("Thread: ")
                        .append(thread.get("thread"))
                        .append(' ')
                        .append(thread.get("threadName"))
                        .append('\n');
                int index = 0;
                for(Object entry : Json.array(thread.get("decodedFrames"))) {
                    var frame = Json.object(entry);
                    annotateLocation(frame, sourceMap, sourceHashes);
                    text.append('#')
                            .append(index++)
                            .append(' ')
                            .append(frame.get("function"))
                            .append(" at ")
                            .append(
                                    Objects.toString(
                                            frame.get("location"),
                                            Objects.toString(frame.get("pc"))))
                            .append(
                                    Boolean.TRUE.equals(frame.get("inline"))
                                            ? " [inlined]\n"
                                            : "\n");
                }
            }
            return new DecodedReport(id, text.toString(), Json.write(output) + "\n");
        } catch(IOException | IllegalArgumentException e) {
            throw new CompilerException("JN4104 Cannot decode Linux core", e);
        } finally {
            if(work != null)
                try {
                    ProjectFiles.removeStaging(work);
                } catch(IOException ignored) {
                }
        }
    }

    /**
     * Uses a GNU-compatible target addr2line; the player never needs this tool.
     */
    public static DecodedReport decode(Path report, Path symbolStore, String symbolizer) {
        try {
            var raw = read(report.toAbsolutePath().normalize());
            validateReport(raw);
            String id = Objects.toString(raw.get("buildId"), "");
            if(!id.matches("[0-9a-f]{32}"))
                throw new CompilerException("JN4104 Report has no supported build identity");
            Path store = symbolStore.toAbsolutePath().normalize();
            var bundle =
                    openBundle(
                            Files.isRegularFile(store.resolve("manifest.json"))
                                    ? store
                                    : store.resolve(id));
            if(!bundle.buildId().equals(id))
                throw new CompilerException("JN4104 Report and symbol build identities differ");
            var manifest = read(bundle.manifest());
            var modules = new HashMap<String, Map<String, Object>>();
            for(Object item : Json.array(manifest.get("modules"))) {
                var module = Json.object(item);
                modules.put(module.get("sha256").toString(), module);
            }
            var sourceMap =
                    Files.isRegularFile(bundle.directory().resolve("source-map.json"))
                            ? read(bundle.directory().resolve("source-map.json"))
                            : Map.<String, Object>of();
            var sourceHashes = read(bundle.directory().resolve("source-hashes.json"));
            var output = new LinkedHashMap<String, Object>(raw);
            var text =
                    new StringBuilder("Build: ")
                            .append(id)
                            .append("  Event: ")
                            .append(raw.getOrDefault("event", "unknown"))
                            .append("  Thread: ")
                            .append(raw.getOrDefault("thread", "unknown"))
                            .append(' ')
                            .append(raw.getOrDefault("threadName", ""))
                            .append("  [")
                            .append(raw.getOrDefault("captureSite", "unknown"))
                            .append("]\n")
                            .append("Capture: ")
                            .append(raw.getOrDefault("status", "unknown"))
                            .append('\n');
            var cache = new HashMap<String, List<Map<String, Object>>>();
            List<Object> frames = Json.array(raw.getOrDefault("frames", List.of()));
            output.put(
                    "decodedFrames",
                    decodeFrames(
                            frames,
                            bundle,
                            modules,
                            sourceMap,
                            sourceHashes,
                            symbolizer,
                            cache,
                            text));
            for(Object item : Json.array(raw.getOrDefault("threads", List.of()))) {
                var thread = Json.object(item);
                text.append("Thread: ").append(thread.get("thread")).append('\n');
                thread.put(
                        "decodedFrames",
                        decodeFrames(
                                Json.array(thread.get("frames")),
                                bundle,
                                modules,
                                sourceMap,
                                sourceHashes,
                                symbolizer,
                                cache,
                                text));
            }
            for(Object item : Json.array(raw.getOrDefault("causes", List.of()))) {
                var cause = Json.object(item);
                text.append("Caused by: ").append(cause.get("message")).append('\n');
                cause.put(
                        "decodedFrames",
                        decodeFrames(
                                Json.array(cause.get("frames")),
                                bundle,
                                modules,
                                sourceMap,
                                sourceHashes,
                                symbolizer,
                                cache,
                                text));
            }
            output.put("sourceArchive", bundle.sources().toString());
            return new DecodedReport(id, text.toString(), Json.write(output) + "\n");
        } catch(IOException | IllegalArgumentException e) {
            throw new CompilerException("JN4104 Cannot decode report", e);
        }
    }

    private static List<Object> decodeFrames(
            List<Object> frames,
            NativeDiagnosticsBundle bundle,
            Map<String, Map<String, Object>> modules,
            Map<String, Object> sourceMap,
            Map<String, Object> sourceHashes,
            String symbolizer,
            Map<String, List<Map<String, Object>>> cache,
            StringBuilder text)
            throws IOException {
        if(frames.size() > 256)
            throw new CompilerException("JN4104 Too many frames in one thread");
        var decoded = new ArrayList<Object>();
        int index = 0;
        for(Object item : frames) {
            var frame = Json.object(item);
            var module = Json.object(frame.getOrDefault("module", Map.of()));
            String sha = Objects.toString(module.get("sha256"), ""),
                    name = Objects.toString(module.get("name"), "unknown");
            String pc = Objects.toString(frame.get("pc"), "0x0");
            var matching = modules.get(sha);
            if(matching == null || "unavailable".equals(matching.get("symbolStatus"))) {
                for(var archived : modules.values())
                    if(archived.get("name").equals(name)
                            && !sha.isEmpty()
                            && !archived.get("sha256").equals(sha))
                        throw new CompilerException(
                                "JN4104 Module checksum does not match archive: " + name);
                text.append('#')
                        .append(index++)
                        .append(' ')
                        .append(name)
                        .append(' ')
                        .append(pc)
                        .append(" [symbols unavailable]\n");
                decoded.add(Map.of("module", name, "pc", pc, "status", "symbols-unavailable"));
                continue;
            }
            long address =
                    number(pc) - number(module.get("base")) + number(module.get("preferredBase"));
            if("return".equals(frame.get("kind"))) address--;
            String key = sha + ":" + Long.toUnsignedString(address, 16);
            var locations = cache.get(key);
            if(locations == null) {
                Path symbols = child(bundle.directory(), matching.get("symbols").toString());
                boolean linux =
                        windowsHost() && "Linux".equals(read(bundle.manifest()).get("JN_SYSTEM"));
                var command =
                        new ArrayList<>(
                                List.of(
                                        symbolizer,
                                        "-f",
                                        "-C",
                                        "-i",
                                        "-e",
                                        linux ? linuxPath(symbols) : symbols.toString(),
                                        "0x" + Long.toUnsignedString(address, 16)));
                if(linux) command.addAll(0, List.of("wsl", "--exec"));
                String result = run(command, bundle.directory(), Duration.ofSeconds(15));
                var lines = result.replace("\r", "").strip().split("\n");
                locations = new ArrayList<>();
                for(int i = 0; i + 1 < lines.length; i += 2) {
                    var location = new LinkedHashMap<String, Object>();
                    location.put("function", lines[i]);
                    location.put("location", lines[i + 1]);
                    // GNU addr2line lists the innermost inline scope first and
                    // its enclosing non-inlined function last.
                    location.put("inline", i + 3 < lines.length);
                    annotateLocation(location, sourceMap, sourceHashes);
                    locations.add(location);
                }
                cache.put(key, locations);
            }
            text.append('#').append(index++).append(' ');
            boolean first = true;
            for(var location : locations) {
                if(!first) text.append("    inlined by: ");
                first = false;
                text.append(location.get("function"))
                        .append(" at ")
                        .append(location.get("location"))
                        .append('\n');
            }
            decoded.add(
                    Map.of(
                            "module",
                            name,
                            "pc",
                            pc,
                            "status",
                            locations.stream()
                                    .noneMatch(
                                            location ->
                                                    !location.get("location")
                                                            .toString()
                                                            .startsWith("??"))
                                    ? "unresolved"
                                    : "decoded",
                            "locations",
                            locations));
        }
        return decoded;
    }

    private static void annotateLocation(
            Map<String, Object> location,
            Map<String, Object> sourceMap,
            Map<String, Object> sourceHashes) {
        location.put("sourceStatus", "not-in-source-archive");
        if(location.get("location") == null) return;
        String normalized =
                location.get("location").toString().replace('\\', '/').replaceFirst("^(\\./)+", "");
        location.put("location", normalized);
        String projectLocation = normalized;
        // Some MinGW compilation units prepend a relative compilation directory
        // to a separately prefix-mapped ./source path (b/release/./user/file.cpp).
        int relativeSource = normalized.lastIndexOf("/./");
        if(relativeSource > 0
                && !normalized.startsWith("/")
                && !normalized.startsWith("../")
                && !normalized.matches("^[A-Za-z]:.*"))
            projectLocation = normalized.substring(relativeSource + 3);
        // Project source paths are made relative by the compiler prefix map.
        // Do not match an unrelated SDK file merely because its suffix agrees.
        for(String source : sourceHashes.keySet()) {
            if(!projectLocation.startsWith(source + ":")) continue;
            location.put("location", projectLocation);
            location.put("sourceStatus", "archived");
            location.put("sourceFile", source);
            location.put("sourceSha256", sourceHashes.get(source));
            var candidates = new ArrayList<Map<String, Object>>();
            for(Object mapping : Json.array(sourceMap.getOrDefault("classes", List.of()))) {
                var entry = Json.object(mapping);
                if(source.equals("src/" + entry.get("cppFile"))) candidates.add(entry);
            }
            if(!candidates.isEmpty()) {
                boolean verified = candidates.stream().allMatch(entry ->
                        Objects.equals(entry.get("sha256"), sourceHashes.get(source)));
                location.put(
                        "javaMappingStatus",
                        verified ? "verified" : "stale-after-native-edit");
                String position = projectLocation.substring(source.length() + 1);
                Map<String, Object> selected = candidates.size() == 1 ? candidates.getFirst() : null;
                if(verified && selected == null)
                    for(var candidate : candidates)
                        if(generatedMethod(candidate, position) != null) {
                            selected = candidate;
                            break;
                        }
                if(selected != null) {
                    location.put("javaClass", selected.get("javaClass"));
                    restoreGeneratedFunction(location, selected, position);
                }
            }
            break;
        }
    }

    /**
     * Recover an omitted inline name only from the exact generated C++ source region.
     */
    private static void restoreGeneratedFunction(
            Map<String, Object> location, Map<String, Object> mapping, String sourcePosition) {
        if(!"??".equals(location.get("function"))
                || !"verified".equals(location.get("javaMappingStatus"))) return;
        var method = generatedMethod(mapping, sourcePosition);
        if(method == null) return;
        location.put("function", mapping.get("cppClass") + "::" + method.get("cppMethod"));
        location.put("functionSource", "verified-generated-cpp-range");
    }

    private static Map<String, Object> generatedMethod(
            Map<String, Object> mapping, String sourcePosition) {
        var position = Pattern.compile("^(\\d+)(?:\\D.*)?$").matcher(sourcePosition);
        if(!position.matches()) return null;
        long line;
        try {
            line = Long.parseLong(position.group(1));
        } catch(NumberFormatException invalid) {
            return null;
        }
        for(Object value : Json.array(mapping.getOrDefault("methods", List.of()))) {
            var method = Json.object(value);
            if(!(method.get("cppLineStart") instanceof Number start)
                    || !(method.get("cppLineEnd") instanceof Number end)
                    || line < start.longValue() || line > end.longValue()) continue;
            return method;
        }
        return null;
    }

    private static boolean windowsHost() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static String linuxPath(Path path) {
        String value = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        if(!windowsHost()) return value;
        if(value.length() < 3 || value.charAt(1) != ':')
            throw new CompilerException("JN4104 WSL decoding requires a local Windows drive path");
        return "/mnt/" + Character.toLowerCase(value.charAt(0)) + value.substring(2);
    }

    private static long number(Object value) {
        String text = Objects.toString(value, "0x0");
        if(!text.matches("0x[0-9a-fA-F]{1,16}"))
            throw new CompilerException("JN4104 Invalid native address");
        return Long.parseUnsignedLong(text.substring(2), 16);
    }

    static String hash(Path file) throws IOException {
        try(var input = Files.newInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int read;
            while((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch(NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String run(List<String> command, Path directory, Duration timeout)
            throws IOException {
        return run(command, directory, timeout, Map.of());
    }

    private static String run(
            List<String> command, Path directory, Duration timeout, Map<String, String> environment)
            throws IOException {
        Path log = Files.createTempFile("jnative-decode-", ".log");
        Process process = null;
        try {
            var builder =
                    new ProcessBuilder(command)
                            .directory(directory.toFile())
                            .redirectErrorStream(true)
                            .redirectOutput(log.toFile());
            builder.environment().putAll(environment);
            process = builder.start();
            if(!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS))
                throw new CompilerException("JN4104 Symbol decoder timed out");
            if(Files.size(log) > 32 * 1024 * 1024)
                throw new CompilerException("JN4104 Symbol decoder output exceeds 32 MiB");
            String output = Files.readString(log);
            if(process.exitValue() != 0)
                throw new CompilerException("JN4104 Symbol decoder failed: " + output);
            return output;
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompilerException("JN4104 Decoding interrupted", e);
        } finally {
            if(process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }
}
