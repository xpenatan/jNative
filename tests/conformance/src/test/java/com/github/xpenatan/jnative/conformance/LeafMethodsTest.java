package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class LeafMethodsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void boundedLeafReferencesAndExceptionsMatchJvm(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Leaves.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.nio.ByteBuffer;
                        public class Leaves {
                            Object value;
                            volatile int number;
                            int[] numbers = {3, 5, 7};
                            Object[] references = {"first", "second"};
                            Leaves set(Object next, int n) { value = next; number = n; return this; }
                            Object get() { return value; }
                            int getNumber() { return number; }
                            int transitive(Leaves other) { return other.getNumber() + getNumber(); }
                            static class AllocatingLeaves extends Leaves {
                                int getNumber() { System.gc(); return 23; }
                            }
                            final int identityNumber() { return number; }
                            int transitiveFinal(Leaves other) { return other.identityNumber() + identityNumber(); }
                            Object select(Object a, Object b, int selector) {
                                Object result;
                                switch (selector) { case 0: result = a; break; case 5: result = b; break; default: result = value; }
                                return result;
                            }
                            int element(int[] array, int index, int divisor) { return array[index] / divisor; }
                            Object reference(int index) { return references[index]; }
                            String cast(Object value) { return (String) value; }
                            boolean matches(Object value) { return value instanceof CharSequence; }
                            Object store(Object[] array, int index, Object value) {
                                array[index] = value;
                                return array[index];
                            }
                            Object caughtStore(Object[] array, Object value) {
                                try { array[0] = value; }
                                catch (ArrayStoreException expected) { System.gc(); return value; }
                                return array[0];
                            }
                            int length() { return numbers.length; }
                            int caught(int[] array) {
                                try { return array[0]; } catch (NullPointerException expected) { System.gc(); return 19; }
                            }
                            static String handlerAfterLeafFailure() {
                                String retained = new String(new char[]{'s', 'a', 'f', 'e'});
                                try { new Leaves().element(null, 0, 1); }
                                catch (NullPointerException expected) { return retained; }
                                return "missing failure";
                            }
                            int loop(int n) { int sum = 0; while (n > 0) { sum += getNumber(); n--; } return sum; }
                            int recursive(int n) { return n == 0 ? getNumber() : recursive(n - 1); }
                            synchronized Object guarded() { System.gc(); return value; }
                            static Object staticValue(Object value) { return value; }
                            static int rotate(int value, int distance) { return (value >>> distance) | (value << -distance); }
                            static int quantize(float value) { return Math.round(Math.min(1, Math.max(0, value)) * 255); }
                            static double roundedRoot(double value) { return Math.floor(Math.sqrt(value)) + Math.ceil(value); }
                            static float bits(int value) { return Float.intBitsToFloat(value); }
                            static int bufferScalar(ByteBuffer buffer, int index, int value) {
                                buffer.putInt(index, value);
                                return buffer.getInt(index);
                            }
                            static class Bootstrap {
                                static String text = init();
                                static String init() { System.gc(); System.out.println("boot-init"); return "boot"; }
                                static int arithmetic(int value) { return value + 3; }
                            }
                            static class RetainsArgument {
                                static { System.gc(); }
                                static Object identity(Object value) { return value; }
                            }
                            static class BrokenArgument {
                                static int value = fail();
                                static int fail() { System.gc(); throw new IllegalStateException("broken"); }
                                static Object identity(Object value) { return value; }
                            }
                            public static void main(String[] args) throws Exception {
                                System.out.println(handlerAfterLeafFailure());
                                Leaves leaf = new Leaves();
                                Object selected = leaf.set(new String("kept"), 17).get();
                                System.gc();
                                System.out.println(selected + ":" + leaf.getNumber() + ":" + leaf.length());
                                System.out.println(leaf.select("a", "b", 0));
                                System.out.println(leaf.select("a", "b", 5));
                                System.out.println(leaf.select("a", "b", 12));
                                System.out.println(leaf.reference(1));
                                System.out.println(leaf.cast("cast") + ":" + leaf.cast(null));
                                System.out.println(leaf.matches("text") + ":" + leaf.matches(null)
                                        + ":" + leaf.matches(new Object()));
                                try { leaf.cast(new Object()); }
                                catch (ClassCastException expected) { System.gc(); System.out.println("cast failure"); }
                                Object[] typed = new String[1];
                                System.out.println(leaf.store(typed, 0, "stored"));
                                System.out.println(leaf.store(typed, 0, null));
                                try { leaf.store(null, -1, new Object()); }
                                catch (NullPointerException expected) { System.gc(); System.out.println("store null first"); }
                                try { leaf.store(typed, -1, new Object()); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.gc(); System.out.println("store bounds first"); }
                                try { leaf.store(typed, 0, new Object()); }
                                catch (ArrayStoreException expected) { System.gc(); System.out.println("store type failure"); }
                                Object surviving = new StringBuilder("surviving");
                                System.out.println(leaf.caughtStore(typed, surviving) == surviving);
                                System.out.println(leaf.element(new int[] {9}, 0, 3));
                                try { leaf.element(null, 0, 1); }
                                catch (NullPointerException expected) { System.gc(); System.out.println("null"); }
                                try { leaf.element(new int[1], 2, 1); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.gc(); System.out.println("bounds"); }
                                try { leaf.element(new int[1], -1, 1); }
                                catch (ArrayIndexOutOfBoundsException expected) { System.gc(); System.out.println("negative bounds"); }
                                try { leaf.element(new int[1], 0, 0); }
                                catch (ArithmeticException expected) { System.gc(); System.out.println("divide"); }
                                System.out.println(leaf.caught(null) + ":" + leaf.loop(100) + ":" + leaf.recursive(30));
                                System.out.println(leaf.transitive(new AllocatingLeaves()));
                                System.out.println(leaf.transitiveFinal(leaf));
                                System.out.println(leaf.guarded() + ":" + staticValue(leaf.get()));
                                System.out.println(Bootstrap.arithmetic(4));
                                System.out.println(Bootstrap.text);
                                System.out.println(RetainsArgument.identity(new String("rooted argument")));
                                try { BrokenArgument.identity(new Object()); }
                                catch (ExceptionInInitializerError expected) { System.out.println("first initialization failure"); }
                                try { BrokenArgument.identity(new Object()); }
                                catch (NoClassDefFoundError expected) { System.out.println("cached initialization failure"); }
                                ByteBuffer buffer = ByteBuffer.allocateDirect(9);
                                System.out.println(bufferScalar(buffer, 1, 0x71234567));
                                try { bufferScalar(buffer, 6, 3); }
                                catch (IndexOutOfBoundsException expected) { System.out.println("buffer bounds:" + buffer.getInt(1)); }
                                for (int distance : new int[] {-33, -1, 0, 7, 31, 32, 65})
                                    System.out.println(rotate(0x12345678, distance));
                                for (int raw : new int[] {0, 0x80000000, 1, 0x3f000000, 0x3f000001,
                                        0x7f800000, 0xff800000, 0x7fc12345, 0x7f800001}) {
                                    float number = bits(raw);
                                    System.out.println(quantize(number));
                                    System.out.println(Double.doubleToLongBits(roundedRoot(number)));
                                }
                                Thread collector = new Thread(() -> { for (int i = 0; i < 100; i++) System.gc(); });
                                collector.start();
                                int result = 0;
                                for (int i = 0; i < 10000; i++) {
                                    result += leaf.getNumber() + quantize(i * 0.0001f);
                                    Object stored = leaf.store(typed, 0, selected);
                                    if (stored != selected || leaf.cast(stored) != selected || !leaf.matches(stored))
                                        throw new IllegalStateException("reference leaf changed its value");
                                }
                                collector.join();
                                System.out.println(result);
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Leaves"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Leaves")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generation = builder.generate();
        String generated =
                Files.readString(
                        generation
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("classes/Leaves.cpp"));
        String getter = generated.substring(generated.indexOf("\nstd::int32_t Leaves::getNumber("));
        getter = getter.substring(0, getter.indexOf("\n}"));
        assertFalse(getter.contains("LocalRoot"), getter);
        assertFalse(getter.contains("safepoint"), getter);
        String arithmetic =
                generated.substring(generated.indexOf("\nstd::int32_t Leaves::rotate("));
        arithmetic = arithmetic.substring(0, arithmetic.indexOf("\n}"));
        assertFalse(arithmetic.contains("safepoint"), arithmetic);
        assertTrue(arithmetic.contains("ensure_initialized"), arithmetic);
        for(String declaration :
                List.of(
                        "std::int32_t Leaves::quantize(",
                        "double Leaves::roundedRoot(",
                        "float Leaves::bits(")) {
            String numerical = generated.substring(generated.indexOf("\n" + declaration));
            numerical = numerical.substring(0, numerical.indexOf("\n}"));
            assertFalse(numerical.contains("safepoint"), numerical);
            assertTrue(numerical.contains("ensure_initialized"), numerical);
        }
        assertTrue(generated.contains("LoopSafepoint"), generated);
        String transitive = generated.substring(generated.indexOf("\nstd::int32_t Leaves::transitiveFinal("));
        transitive = transitive.substring(0, transitive.indexOf("\n}"));
        assertFalse(transitive.contains("gc_roots"), transitive);
        assertFalse(transitive.contains("safepoint"), transitive);
        String override = generated.substring(generated.indexOf("\nstd::int32_t Leaves::transitive("));
        override = override.substring(0, override.indexOf("\n}"));
        assertTrue(override.contains("gc_roots"), override);
        String caughtStore =
                generated.substring(generated.indexOf("\n::jnative::Object* Leaves::caughtStore("));
        caughtStore = caughtStore.substring(0, caughtStore.indexOf("\n}"));
        assertTrue(caughtStore.contains("gc_roots"), caughtStore);
        String scalar =
                generated.substring(generated.indexOf("\nstd::int32_t Leaves::bufferScalar("));
        scalar = scalar.substring(0, scalar.indexOf("\n}"));
        assertFalse(scalar.contains("safepoint"), scalar);
        assertTrue(scalar.contains("initialization_roots"), scalar);
        Path launcher = generation.request().generatedSourcesDirectory().resolve("launcher.cpp");
        // The handwritten caller deliberately passes its only reference without a
        // caller root. The Java class initializer collects before identity returns.
        Files.writeString(
                launcher,
                """
                        #include "application.hpp"
                        static bool initialization_argument_collected = false;
                        struct InitializationArgument final : jnative::Object {
                            ~InitializationArgument() { initialization_argument_collected = true; }
                        };
                        """
                        + Files.readString(launcher)
                        .replace(
                                "generated::initialize_program();",
                                """
                                        generated::initialize_program();
                                        auto* initialization_argument = jnative::allocate<InitializationArgument>();
                                        auto* retained_argument = generated::Leaves_RetainsArgument::identity(initialization_argument);
                                        if (initialization_argument_collected || retained_argument != initialization_argument)
                                            throw std::logic_error("Leaf argument was lost during class initialization");
                                        """));
        var compiled = builder.compile(generation);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
