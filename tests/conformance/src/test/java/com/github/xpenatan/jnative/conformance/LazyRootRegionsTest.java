package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LazyRootRegionsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void lazyRegionsRetainReferencesAndCopyEvaluationOrder(BuildType buildType) throws Exception {
        Path source = temporary.resolve("LazyRootRegions.java");
        try(var input = getClass().getResourceAsStream("/fixtures/LazyRootRegions.java")) {
            assertNotNull(input);
            Files.copy(input, source, StandardCopyOption.REPLACE_EXISTING);
        }
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "LazyRootRegions"), Duration.ofSeconds(60));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("LazyRootRegions")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5));
        var generated = builder.generate();
        Path emitted = generated.request().generatedSourcesDirectory().resolve("classes/LazyRootRegions.cpp");
        String cpp = Files.readString(emitted);
        List<String> lazyMethods = List.of("loops", "twoLoops", "mutation", "copy", "checkedCopy",
                "orderedCopy", "coldLastUse", "loopLastUse", "nestedFrontier", "rollback",
                "duplicatedTail", "largeFallback");
        for(String name : lazyMethods) {
            String body = body(cpp, name);
            assertTrue(body.contains("RootAddressFrame<"), body);
            assertFalse(body.contains("DeferredRootFrame<"), body);
            assertFalse(body.substring(0, body.indexOf("if (")).contains("safepoint"), body);
        }
        assertTrue(body(cpp, "coldLastUse").contains("RootValue<> derived("), cpp);
        assertTrue(body(cpp, "loopLastUse").contains("RootValue<> derived("), cpp);
        String scalarLoop = body(cpp, "scalarLoop");
        assertFalse(scalarLoop.contains("gc_roots"), scalarLoop);
        assertTrue(scalarLoop.indexOf("::jnative::LoopSafepoint") > scalarLoop.indexOf("if (i"), scalarLoop);
        for(String wrapper : List.of("terminalInstance", "terminalFirstUse", "terminalFailure")) {
            String terminal = body(cpp, wrapper);
            assertFalse(terminal.contains("gc_roots") || terminal.contains("safepoint"), terminal);
        }
        Path targetSource = emitted.resolveSibling("LazyRootRegions_Target.cpp");
        String targetCpp = Files.readString(targetSource);
        String target = body(targetCpp, "LazyRootRegions_Target", "apply");
        assertFalse(target.contains("gc_roots"), target);
        var frames = Pattern.compile("RootAddressFrame<[^>]+> \\w+\\(\\{([^}]+)\\}\\)").matcher(target);
        int publications = 0;
        while(frames.find()) {
            publications++;
            for(String holder : List.of("keep_alive", "source", "target", "unused"))
                assertTrue(frames.group(1).contains(holder + ".address()"), frames.group());
        }
        assertTrue(publications >= 3, target); // cold arm, loop entry, copy slow arm
        String nested = body(cpp, "nestedFrontier");
        int outerElse = nested.indexOf("} else {");
        int nestedCold = nested.indexOf("if (cold", outerElse);
        assertTrue(nestedCold > outerElse, nested);
        assertFalse(nested.substring(outerElse, nestedCold).contains("RootAddressFrame<"), nested);
        assertFalse(nested.substring(outerElse, nestedCold).contains("safepoint"), nested);
        String duplicated = body(cpp, "duplicatedTail");
        assertEquals(2, duplicated.split("::platform_System_gc_", -1).length - 1, duplicated);
        assertEquals(3, duplicated.split("::jnative::safepoint\\(\\);", -1).length - 1, duplicated);
        // Force GC at every new publication boundary. Detached Box fields leave
        // arrays reachable only through generated local/temporary root slots.
        String forced = cpp;
        for(String name : lazyMethods) {
            String original = body(forced, name);
            String replacement = original.replace("::jnative::safepoint();", "::jnative::managed_heap().collect();")
                    .replace("::jnative::LoopSafepoint loop_safepoints;",
                            "::jnative::LoopSafepoint loop_safepoints;\n        ::jnative::managed_heap().collect();");
            assertNotEquals(original, replacement, original);
            forced = forced.replace(original, replacement);
        }
        Files.writeString(emitted, forced);
        String forcedTarget = target.replace("::jnative::safepoint();", "::jnative::managed_heap().collect();")
                .replace("::jnative::LoopSafepoint loop_safepoints;",
                        "::jnative::LoopSafepoint loop_safepoints;\n        ::jnative::managed_heap().collect();");
        assertNotEquals(target, forcedTarget);
        Files.writeString(targetSource, targetCpp.replace(target, forcedTarget));
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(120), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    private static String body(String cpp, String name) {
        return body(cpp, "LazyRootRegions", name);
    }

    private static String body(String cpp, String owner, String name) {
        var declaration = Pattern.compile("(?m)^\\S[^\\n]* " + Pattern.quote(owner)
                + "::" + Pattern.quote(name) + "\\(").matcher(cpp);
        assertTrue(declaration.find(), name);
        return cpp.substring(declaration.start(), cpp.indexOf("\n}", declaration.end()));
    }
}
