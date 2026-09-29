package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeSupportShardingTest {
    @TempDir Path temporary;

    @Test void supportShardsPreserveInitializationDispatchAndManagedAdapters() throws Exception {
        Path source = temporary.resolve("Sharded.java");
        Path classes = temporary.resolve("classes");
        // Long legal identifiers reach the production threshold with few classes.
        String padding = "value".repeat(80);
        StringBuilder methods = new StringBuilder();
        StringBuilder calls = new StringBuilder();
        for(int i = 0; i < 900; i++) {
            String name = padding + i;
            methods.append("public int ").append(name).append("() { return ")
                    .append(i).append("; }\n");
            calls.append("total += value.").append(name).append("();\n");
        }
        Files.writeString(source, """
                public class Sharded {
                    static int initialized = initialize();
                    static int initialize() { System.out.println("initialized"); return 7; }
                    static class Value {
                        final Object retained;
                        Value(Object retained) { this.retained = retained; }
                        public Object identity(Object argument) { System.gc(); return argument; }
                        %s
                    }
                    public static void main(String[] args) {
                        Value value = new Value(new String("retained"));
                        int total = initialized;
                        %s
                        System.out.println(total);
                        Object argument = new String("argument");
                        System.out.println(value.identity(argument) == argument);
                        System.out.println(value.retained);
                    }
                }
                """.formatted(methods, calls));
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "Sharded"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("Sharded")
                .buildRoot(temporary.resolve("out")).buildType(BuildType.RELEASE);
        var generated = builder.generate();
        Path directory = generated.request().generatedSourcesDirectory();
        Map<String, String> shards = supportSources(directory);
        assertTrue(shards.size() > 2, "Expected generated and managed API namespace splits: " + shards.keySet());
        int index = 0;
        for(var shard : shards.entrySet()) {
            assertEquals(index == 0 ? "runtime_support.cpp"
                    : "runtime_support_%03d.cpp".formatted(index), shard.getKey());
            assertTrue(shard.getValue().startsWith("#include \"application.hpp\""), shard.getKey());
            // A complete group can exceed the threshold by its own size.
            assertTrue(shard.getValue().length() < 1024 * 1024 + 16 * 1024, shard.getKey());
            index++;
        }
        String bootstrap = shards.get("runtime_support.cpp");
        for(String helper : List.of("static void register_types_0()", "static void configure_substitutions()",
                "static ::jnative::Object* construct_native_exception(", "void initialize_program()"))
            assertTrue(bootstrap.contains(helper), helper);
        String factory = shards.values().stream().filter(text -> text.contains("// Managed factory:"))
                .findFirst().orElseThrow();
        assertTrue(factory.contains("::create("), "Factory and member wrapper must remain together");
        builder.generate();
        assertEquals(shards, supportSources(directory), "Support emission must be deterministic");
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static Map<String, String> supportSources(Path directory) throws Exception {
        var result = new LinkedHashMap<String, String>();
        try(var paths = Files.list(directory)) {
            for(Path path : paths.filter(p -> p.getFileName().toString().startsWith("runtime_support")
                    && p.getFileName().toString().endsWith(".cpp")).sorted().toList())
                result.put(path.getFileName().toString(), Files.readString(path));
        }
        return result;
    }
}
