package com.github.xpenatan.jnative.cli;

import com.github.xpenatan.jnative.*;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Thin command-line facade over the same generation, compilation and export APIs.
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(execute(args, System.out, System.err));
    }

    public static int execute(String[] args, PrintStream output, PrintStream errors) {
        if(args.length == 0 || args[0].equals("--help")) {
            output.println(
                    """
                            jnative generate|build --classpath <paths> --main <class> --output <build-root>
                              [--target <name>] [--native <file>] [--export-class <class>]
                              [--reflect-class <class>] [--reflect-metadata <class>]
                              [--release] [--debug-information]
                              [--console-mode NORMAL|PAUSE_ON_EXIT]
                              [--source-layout PACKAGE_DIRECTORIES|PACKAGE_FILENAME]
                              [--native-symbols AUTO|EMBEDDED|SEPARATE|NONE]
                              [--stack-traces NATIVE|JAVA|BOTH|NONE] [--crash-reports LOCAL|OFF]
                            jnative compile --project <native-project> [--release]
                            jnative export --project <generated-project> --destination <new-directory>
                            jnative archive --project <native-project> --destination <private-store> [--release]
                            jnative decode --report <report.json> --symbols <private-store> [--symbolizer <addr2line>] [--format text|json]
                              [--from-dump | --core <core-file> --gdb <gdb>]
                            jnative extract-sources --symbols <build-bundle> --destination <new-directory>
                            jnative reports --directory <report-directory>
                            jnative export-report --report <report.json> --destination <new-directory> [--without-dump]
                            jnative profile
                            Native tool options: --cmake <command> --generator <name> --timeout-seconds <number>
                              --cmake-toolchain <file> --cmake-arg=<argument>
                              --cmake-build-arg=<argument> --build-tool-arg=<argument>
                            Argument options may repeat; each value is one token, without shell quoting.
                            Classpaths use the host path separator. Native/export/reflection options may repeat.
                            """);
            return 0;
        }
        try {
            String command = args[0];
            if(command.equals("profile")) {
                if(args.length != 1)
                    throw new IllegalArgumentException("profile takes no options");
                output.print(NativeCompatibility.libraryInventory());
                return 0;
            }
            if(!Set.of(
                            "generate",
                            "build",
                            "compile",
                            "export",
                            "archive",
                            "decode",
                            "reports",
                            "export-report",
                            "extract-sources")
                    .contains(command))
                throw new IllegalArgumentException("Unknown command: " + command);
            var options = new LinkedHashMap<String, List<String>>();
            var builder =
                    NativeBuilder.create()
                            .log(
                                    (level, text) ->
                                            (level == BuildLog.Level.INFO ? output : errors)
                                                    .println(text));
            for(int i = 1; i < args.length; ++i) {
                String key = args[i];
                String inline = null;
                int equals = key.indexOf('=');
                if(equals > 0) {
                    inline = key.substring(equals + 1);
                    key = key.substring(0, equals);
                }
                if(Set.of("--release", "--debug-information", "--from-dump", "--without-dump")
                        .contains(key)) {
                    if(inline != null)
                        throw new IllegalArgumentException("Flag takes no value: " + key);
                    if(options.putIfAbsent(key, List.of("true")) != null)
                        throw new IllegalArgumentException("Duplicate option: " + key);
                    continue;
                }
                if(!Set.of(
                                "--classpath",
                                "--main",
                                "--output",
                                "--target",
                                "--native",
                                "--export-class",
                                "--reflect-class",
                                "--reflect-metadata",
                                "--cmake-toolchain",
                                "--cmake-arg",
                                "--cmake-build-arg",
                                "--build-tool-arg",
                                "--core",
                                "--gdb",
                                "--symbols",
                                "--report",
                                "--directory",
                                "--symbolizer",
                                "--format",
                                "--source-layout",
                                "--native-symbols",
                                "--stack-traces",
                                "--crash-reports",
                                "--console-mode",
                                "--project",
                                "--destination",
                                "--cmake",
                                "--generator",
                                "--timeout-seconds")
                        .contains(key)
                        || (inline == null && i + 1 >= args.length))
                    throw new IllegalArgumentException("Unknown option or missing value: " + key);
                var values = options.computeIfAbsent(key, ignored -> new ArrayList<>());
                if(!values.isEmpty()
                        && !Set.of(
                                "--cmake-arg",
                                "--cmake-build-arg",
                                "--build-tool-arg",
                                "--classpath",
                                "--native",
                                "--export-class",
                                "--reflect-class",
                                "--reflect-metadata")
                        .contains(key))
                    throw new IllegalArgumentException("Duplicate option: " + key);
                String value = inline == null ? args[++i] : inline;
                values.add(value);
                switch(key) {
                    case "--cmake-arg" -> builder.cmakeArgs(value);
                    case "--cmake-build-arg" -> builder.cmakeBuildArgs(value);
                    case "--build-tool-arg" -> builder.buildToolArgs(value);
                    case "--cmake-toolchain" -> builder.cmakeToolchain(Path.of(value));
                    default -> {
                    }
                }
            }
            if(options.containsKey("--release")) builder.buildType(BuildType.RELEASE);
            if(options.containsKey("--cmake")) builder.cmake(one(options, "--cmake"));
            if(options.containsKey("--generator")) builder.generator(one(options, "--generator"));
            if(options.containsKey("--timeout-seconds"))
                builder.timeout(
                        Duration.ofSeconds(Long.parseLong(one(options, "--timeout-seconds"))));
            if(command.equals("archive")) {
                allow(options, Set.of("--project", "--destination", "--release"));
                output.println(
                        NativeDiagnostics.archive(
                                        Path.of(one(options, "--project")),
                                        options.containsKey("--release")
                                                ? BuildType.RELEASE
                                                : BuildType.DEBUG,
                                        Path.of(one(options, "--destination")))
                                .directory());
            }
            else if(command.equals("decode")) {
                allow(
                        options,
                        Set.of(
                                "--report",
                                "--symbols",
                                "--symbolizer",
                                "--format",
                                "--from-dump",
                                "--core",
                                "--gdb"));
                String symbolizer =
                        options.containsKey("--symbolizer")
                                ? one(options, "--symbolizer")
                                : "addr2line";
                if(options.containsKey("--from-dump") && options.containsKey("--core"))
                    throw new IllegalArgumentException("Choose either --from-dump or --core");
                var decoded =
                        options.containsKey("--core")
                                ? NativeDiagnostics.decodeCore(
                                Path.of(one(options, "--report")),
                                Path.of(one(options, "--core")),
                                Path.of(one(options, "--symbols")),
                                options.containsKey("--gdb")
                                        ? one(options, "--gdb")
                                        : "gdb")
                                : options.containsKey("--from-dump")
                                ? NativeDiagnostics.decodeDump(
                                Path.of(one(options, "--report")),
                                Path.of(one(options, "--symbols")),
                                symbolizer)
                                : NativeDiagnostics.decode(
                                Path.of(one(options, "--report")),
                                Path.of(one(options, "--symbols")),
                                symbolizer);
                String format = options.containsKey("--format") ? one(options, "--format") : "text";
                if(!Set.of("text", "json").contains(format))
                    throw new IllegalArgumentException("Format must be text or json");
                output.print(format.equals("json") ? decoded.json() : decoded.text());
            }
            else if(command.equals("extract-sources")) {
                allow(options, Set.of("--symbols", "--destination"));
                output.println(
                        NativeDiagnostics.extractSources(
                                NativeDiagnostics.openBundle(Path.of(one(options, "--symbols"))),
                                Path.of(one(options, "--destination"))));
            }
            else if(command.equals("reports")) {
                allow(options, Set.of("--directory"));
                NativeDiagnostics.pendingReports(Path.of(one(options, "--directory")))
                        .forEach(output::println);
            }
            else if(command.equals("export-report")) {
                allow(options, Set.of("--report", "--destination", "--without-dump"));
                output.println(
                        NativeDiagnostics.exportReport(
                                Path.of(one(options, "--report")),
                                Path.of(one(options, "--destination")),
                                !options.containsKey("--without-dump")));
            }
            else if(command.equals("compile")) {
                allow(
                        options,
                        Set.of(
                                "--cmake-toolchain",
                                "--cmake-arg",
                                "--cmake-build-arg",
                                "--build-tool-arg",
                                "--project",
                                "--release",
                                "--cmake",
                                "--generator",
                                "--timeout-seconds"));
                output.println(
                        builder.compileProject(Path.of(one(options, "--project")))
                                .artifact()
                                .path());
            }
            else if(command.equals("export")) {
                allow(options, Set.of("--project", "--destination"));
                output.println(
                        NativeProjects.export(
                                        Path.of(one(options, "--project")),
                                        Path.of(one(options, "--destination")))
                                .directory());
            }
            else {
                allow(
                        options,
                        Set.of(
                                "--classpath",
                                "--main",
                                "--output",
                                "--target",
                                "--native",
                                "--export-class",
                                "--reflect-class",
                                "--reflect-metadata",
                                "--cmake-toolchain",
                                "--cmake-arg",
                                "--cmake-build-arg",
                                "--build-tool-arg",
                                "--release",
                                "--debug-information",
                                "--console-mode",
                                "--source-layout",
                                "--native-symbols",
                                "--stack-traces",
                                "--crash-reports",
                                "--cmake",
                                "--generator",
                                "--timeout-seconds"));
                for(String group : options.getOrDefault("--classpath", List.of()))
                    for(String entry : group.split(Pattern.quote(java.io.File.pathSeparator), -1))
                        builder.classpath(Path.of(entry));
                builder.mainClass(one(options, "--main"))
                        .buildRoot(Path.of(one(options, "--output")));
                if(options.containsKey("--target"))
                    builder.targetFileName(one(options, "--target"));
                if(options.containsKey("--source-layout"))
                    builder.sourceLayout(SourceLayout.valueOf(one(options, "--source-layout")));
                if(options.containsKey("--native-symbols"))
                    builder.nativeSymbols(NativeSymbols.valueOf(one(options, "--native-symbols")));
                if(options.containsKey("--stack-traces"))
                    builder.stackTraces(StackTraceMode.valueOf(one(options, "--stack-traces")));
                if(options.containsKey("--crash-reports"))
                    builder.crashReports(CrashReportMode.valueOf(one(options, "--crash-reports")));
                if(options.containsKey("--debug-information")) builder.javaSourceLocations(true);
                if(options.containsKey("--console-mode"))
                    builder.consoleMode(ConsoleMode.valueOf(one(options, "--console-mode")));
                for(String file : options.getOrDefault("--native", List.of()))
                    builder.nativeFile(Path.of(file));
                for(String name : options.getOrDefault("--export-class", List.of()))
                    builder.exportClass(name);
                for(String name : options.getOrDefault("--reflect-class", List.of()))
                    builder.reflectClass(name);
                for(String name : options.getOrDefault("--reflect-metadata", List.of()))
                    builder.reflectMetadata(name);
                if(command.equals("build")) output.println(builder.build().artifact().path());
                else output.println(builder.generate().request().buildRoot().resolve("native"));
            }
            return 0;
        } catch(CompilerException | IllegalArgumentException error) {
            errors.println(error.getMessage());
            return 2;
        }
    }

    private static String one(Map<String, List<String>> values, String key) {
        if(!values.containsKey(key)) throw new IllegalArgumentException("Required option: " + key);
        return values.get(key).getFirst();
    }

    private static void allow(Map<String, List<String>> values, Set<String> allowed) {
        for(String key : values.keySet())
            if(!allowed.contains(key))
                throw new IllegalArgumentException("Option not valid for this command: " + key);
    }
}
