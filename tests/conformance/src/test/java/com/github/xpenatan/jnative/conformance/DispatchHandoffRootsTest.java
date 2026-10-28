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

class DispatchHandoffRootsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void requestedCollectionAndCollectingDispatchProtectRawArguments(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Handoff.java"), classes = temporary.resolve("classes");
        Files.writeString(source, """
                import java.util.HashMap;

                public class Handoff {
                    static Object[] detached;
                    static final class Detaching {
                        int value = 37;
                        public int hashCode() {
                            detached[0] = null;
                            System.gc();
                            return value;
                        }
                    }
                    static final class FastKey {
                        int value;
                        FastKey(int value) { this.value = value; }
                        public int hashCode() { return value; }
                    }
                    static class Key {
                        int value;
                        Key(int value) { this.value = value; }
                        public int hashCode() { System.gc(); return value; }
                        public boolean equals(Object other) {
                            System.gc();
                            return other instanceof Key && ((Key) other).value == value;
                        }
                    }
                    static class Throwing extends Key {
                        Throwing() { super(1); }
                        public int hashCode() { System.gc(); throw new IllegalArgumentException(); }
                    }
                    static class Recursive extends Key {
                        int depth;
                        Recursive(int depth) { super(101); this.depth = depth; }
                        public int hashCode() {
                            if (depth-- == 0) { System.gc(); return value; }
                            return unconditional(this) - 1;
                        }
                    }
                    static int spread(Object key) {
                        int h = key == null ? 0 : key.hashCode();
                        return h ^ (h >>> 16);
                    }
                    static int unconditional(Object key) { return key.hashCode() + 1; }
                    static boolean equal(Object a, Object b) {
                        return a == b || a != null && a.equals(b);
                    }
                    static int retained(Object a, Object b) {
                        int h = a.hashCode(); return h + b.hashCode();
                    }
                    static int arrayHashes(Object[] values) {
                        int sum = 0;
                        for (int i = 0; i < values.length; i++) sum += values[i].hashCode();
                        return sum;
                    }
                    public static void main(String[] args) {
                        HashMap<Object, Key> map = new HashMap<>(1);
                        FastKey fast = new FastKey(7);
                        map.put(fast, new Key(7));
                        if (map.get(fast).value != 7) throw new AssertionError();
                        map.clear();
                        if (map.put(new Key(19), new Key(23)) != null || map.size() != 1)
                            throw new AssertionError();
                        if (spread(new Key(101)) != 101 || spread(null) != 0) throw new AssertionError();
                        if (unconditional(new Key(101)) != 102) throw new AssertionError();
                        if (unconditional(new Recursive(20)) != 102) throw new AssertionError();
                        if (!equal(new Key(101), new Key(101)) || equal(null, new Key(3)))
                            throw new AssertionError();
                        if (retained(new Key(19), new Key(23)) != 42) throw new AssertionError();
                        try { spread(new Throwing()); throw new AssertionError(); }
                        catch (IllegalArgumentException expected) { }
                        detached = new Object[]{new Detaching()};
                        if (arrayHashes(detached) != 37 || detached[0] != null) throw new AssertionError();
                        detached = null;
                        System.out.println("handoff-roots-ok");
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "Handoff"), Duration.ofSeconds(30));
        assertEquals(new ProcessHarness.Output(0, "handoff-roots-ok\n"), expected);
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("Handoff")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5));
        var generation = builder.generate();
        Path generated = generation.request().generatedSourcesDirectory();
        String cpp = Files.readString(generated.resolve("classes/Handoff.cpp"));
        assertTrue(cpp.contains("::jnative::poll_with_roots("), cpp);
        Path launcher = generated.resolve("launcher.cpp");
        String probe = """
                // The collector cannot reclaim these objects until this managed
                // thread cooperates. There are deliberately no caller roots at entry.
                for (int operation = 0; operation < 6; ++operation) {
                    ::jnative::Object *first, *second, *map;
                    {
                        ::jnative::LocalRoot<> owner;
                        if (operation >= 3) owner.set(::generated::java::util::HashMap::create(1));
                        ::jnative::LocalRoot<> a;
                        a.set(operation == 4 ? static_cast<::jnative::Object*>(::generated::Handoff_FastKey::create(101))
                            : static_cast<::jnative::Object*>(::generated::Handoff_Key::create(101)));
                        ::jnative::LocalRoot<> b(::generated::Handoff_Key::create(101));
                        if (operation >= 4)
                            static_cast<::generated::java::util::HashMap*>(owner.get())->put(a.get(), b.get());
                        first = a.get(); second = b.get(); map = owner.get();
                    }
                    auto& heap = ::jnative::managed_heap();
                    std::thread collector([&heap]() {
                        ::jnative::ThreadAttachment attachment;
                        heap.collect();
                    });
                    while (!heap.collection_pending()) std::this_thread::yield();
                    int result;
                    if (operation >= 4) {
                        result = static_cast<::generated::Handoff_Key*>(
                            ::generated::java::util::HashMap::lookupNative(map, first, 1))->value.get();
                    } else if (operation == 3) {
                        auto* table = static_cast<::generated::java::util::HashMap*>(map);
                        table->put(first, second);
                        result = static_cast<::generated::Handoff_Key*>(table->get(first))->value.get();
                    } else {
                        result = operation == 0 ? ::generated::Handoff::spread(first)
                            : operation == 1 ? ::generated::Handoff::unconditional(first)
                            : ::generated::Handoff::equal(first, second);
                    }
                    {
                        ::jnative::NativeRegion waiting;
                        collector.join();
                    }
                    if (result != (operation == 0 || operation >= 3 ? 101 : operation == 1 ? 102 : 1))
                        throw std::logic_error("Handoff lost an unrooted argument");
                }
                """;
        Files.writeString(launcher, "#include <thread>\n" + Files.readString(launcher)
                .replace("generated::initialize_program();", "generated::initialize_program();\n" + probe));
        var result = builder.compile(generation);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
