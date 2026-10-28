package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class DispatchRootDelegationTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void handwrittenCallsKeepTransientReceiverAndArgumentsAlive(BuildType buildType) throws Exception {
        Path source = temporary.resolve("DispatchRoots.java"), classes = temporary.resolve("classes");
        Files.writeString(source, """
                public class DispatchRoots {
                    interface Operation { int select(Object first, Object second, boolean collect); }
                    static class Payload { int value; Payload(int value) { this.value = value; } }
                    static class Left implements Operation {
                        int bias = 7;
                        public int select(Object first, Object second, boolean collect) {
                            System.gc();
                            return bias + ((Payload)first).value + ((Payload)second).value;
                        }
                    }
                    static class Right implements Operation {
                        int bias = 11;
                        public int select(Object first, Object second, boolean collect) {
                            System.gc();
                            return bias + ((Payload)first).value + ((Payload)second).value;
                        }
                    }
                    static class Monitor implements Operation {
                        int bias = 17;
                        public synchronized int select(Object first, Object second, boolean collect) {
                            System.gc();
                            return bias + ((Payload)first).value + ((Payload)second).value;
                        }
                    }
                    static class Fast implements Operation {
                        int bias = 21;
                        public int select(Object first, Object second, boolean collect) {
                            return bias + ((Payload)first).value + ((Payload)second).value;
                        }
                    }
                    static class Conditional implements Operation {
                        int bias = 25;
                        public int select(Object first, Object second, boolean collect) {
                            if (collect) System.gc();
                            return bias + ((Payload)first).value + ((Payload)second).value;
                        }
                    }
                    public static void main(String[] args) {
                        Operation[] operations = {new Left(), new Right(), new Monitor(), new Fast(), new Conditional()};
                        int[] expected = {49, 53, 59, 63, 67};
                        Object first = new Payload(19), second = new Payload(23);
                        for (int i = 0; i < operations.length; i++)
                            if (operations[i].select(first, second, true) != expected[i]) throw new AssertionError();
                        System.out.println("dispatch-roots-ok");
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "DispatchRoots"), Duration.ofSeconds(30));
        assertEquals(new ProcessHarness.Output(0, "dispatch-roots-ok\n"), expected);
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("DispatchRoots")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5));
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String api = Files.readString(generated.resolve("java-api.tsv")).lines()
                .filter(line -> line.startsWith("DispatchRoots$Operation.select("))
                .map(line -> line.split("\t")[1]).findFirst().orElseThrow();
        String support = Files.readString(generated.resolve("runtime_support.cpp"));
        int adapterStart = support.indexOf(" select(::jnative::Object* receiver,");
        assertTrue(adapterStart >= 0);
        String adapter = support.substring(adapterStart, support.indexOf("\n}", adapterStart));
        assertTrue(adapter.contains("selected dispatch branch protects arguments"), adapter);
        assertFalse(adapter.contains("call_roots"), adapter);
        assertFalse(adapter.contains("LocalRoot<"), adapter);
        Path launcher = generated.resolve("launcher.cpp");
        String probe = """
                int expected[] = {49, 53, 59, 63, 67};
                for (int implementation = 0; implementation < 5; ++implementation) {
                    ::jnative::Object *receiver, *first, *second;
                    {
                        ::jnative::LocalRoot<> owner;
                        switch (implementation) {
                            case 0: owner.set(::generated::DispatchRoots_Left::create()); break;
                            case 1: owner.set(::generated::DispatchRoots_Right::create()); break;
                            case 2: owner.set(::generated::DispatchRoots_Monitor::create()); break;
                            case 3: owner.set(::generated::DispatchRoots_Fast::create()); break;
                            case 4: owner.set(::generated::DispatchRoots_Conditional::create()); break;
                        }
                        ::jnative::LocalRoot<> a(::generated::DispatchRoots_Payload::create(19));
                        ::jnative::LocalRoot<> b(::generated::DispatchRoots_Payload::create(23));
                        receiver = owner.get(); first = a.get(); second = b.get();
                    }
                    // No caller roots remain. The selected branch or body must
                    // publish all live arguments before any possible collection.
                    int value = %s(receiver, first, second, true);
                    if (value != expected[implementation])
                        throw std::logic_error("Dispatch lost an unrooted argument");
                }
                """.formatted(api);
        Files.writeString(launcher, Files.readString(launcher).replace("generated::initialize_program();",
                "generated::initialize_program();\n" + probe));
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
