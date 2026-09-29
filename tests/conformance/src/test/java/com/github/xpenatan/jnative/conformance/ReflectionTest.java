package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import com.github.xpenatan.jnative.cli.Main;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @FunctionalInterface
    private interface Configuration {
        void accept(NativeBuilder builder) throws Exception;
    }

    private NativeCompilationResult compare(
            String name, String source, int release, Configuration configure) throws Exception {
        Path file = temporary.resolve(name + ".java"), classes = temporary.resolve("classes");
        Files.writeString(file, source);
        ProcessHarness.javac(file, classes, release);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), name),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass(name)
                        .buildRoot(temporary.resolve("native-output"))
                        .buildType(release == 17 ? BuildType.DEBUG : BuildType.RELEASE)
                        .timeout(Duration.ofMinutes(3));
        configure.accept(builder);
        var nativeResult = builder.build();
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(nativeResult.executable().toString()),
                        Duration.ofSeconds(60),
                        Map.of("JNATIVE_GC_INTERVAL", "1")));
        if(release == 25 && Boolean.getBoolean("jnative.linux"))
            ProcessHarness.compareLinux(temporary, nativeResult, expected, 1);
        return nativeResult;
    }

    @Test
    void classMetadataAndInitialization() throws Exception {
        compare(
                "ClassFacts",
                """
                        import java.lang.reflect.Modifier;

                        public class ClassFacts {
                            static int initialized;
                            public interface Marker {}
                            public static class Parent {}
                            public static class Target extends Parent implements Marker, Runnable {
                                static { ++initialized; }
                                public void run() {}
                            }
                            public static void main(String[] args) throws Exception {
                                Class<?> type = Target.class;
                                System.out.println(type.getName());
                                System.out.println(initialized);
                                System.out.println(Class.forName("ClassFacts$Target") == type);
                                System.out.println(initialized);
                                System.out.println(type.getSuperclass() == Parent.class);
                                System.out.println(type.getInterfaces()[0] == Marker.class);
                                System.out.println(Marker.class.isInterface());
                                System.out.println(Marker.class.getSuperclass() == null);
                                Object target = type.getConstructor().newInstance();
                                System.out.println(target.getClass() == type);
                                System.out.println(Parent.class.isInstance(target));
                                System.out.println(Parent.class.isAssignableFrom(type));
                                System.out.println(type.isAssignableFrom(Parent.class));
                                System.out.println(type.cast(target) == target);
                                System.out.println(type.cast(null) == null);
                                System.out.println(type.getMethod("run").invoke(target) == null);
                                System.out.println(int.class.getName());
                                System.out.println(void.class.isPrimitive());
                                System.out.println(int.class.getMethods().length);
                                System.out.println(int.class.getFields().length);
                                System.out.println(int[].class.getFields().length);
                                System.out.println(int[].class.getConstructors().length);
                                System.out.println(Object.class.isAssignableFrom(int.class));
                                System.out.println(int[][].class.getName());
                                System.out.println(int[][].class.getTypeName());
                                System.out.println(int[][].class.getComponentType() == int[].class);
                                System.out.println(int[].class.getComponentType() == int.class);
                                System.out.println(int[].class.getSuperclass() == Object.class);
                                System.out.println(Class.forName("[[I") == int[][].class);
                                System.out.println(String[].class.getInterfaces().length);
                                System.out.println(String.class.toString());
                                System.out.println(Modifier.isNative(Object.class.getMethod("hashCode").getModifiers()));
                                try { Class.forName("missing.Type"); } catch (ClassNotFoundException expected) { System.out.println("missing class"); }
                                try { Class.forName("int"); } catch (ClassNotFoundException expected) { System.out.println("primitive name rejected"); }
                            }
                        }
                        """,
                25,
                builder -> {
                    builder.reflectClass("ClassFacts$Target");
                    // Keep CLI artifacts alive throughout native validation before JUnit cleanup.
                    var output = new ByteArrayOutputStream();
                    try(var print = new PrintStream(output)) {
                        int status =
                                Main.execute(
                                        new String[]{
                                                "generate",
                                                "--classpath",
                                                temporary.resolve("classes").toString(),
                                                "--main",
                                                "ClassFacts",
                                                "--output",
                                                temporary.resolve("cli").toString(),
                                                "--reflect-class",
                                                "ClassFacts$Target",
                                                "--reflect-metadata",
                                                "ClassFacts$Parent"
                                        },
                                        print,
                                        print);
                        assertEquals(0, status, output.toString());
                    }
                    String registrations =
                            Files.readString(temporary.resolve("cli/native/src/reflection.tsv"));
                    assertTrue(registrations.contains("PUBLIC_MEMBERS\tClassFacts$Target"));
                    assertTrue(registrations.contains("METADATA\tClassFacts$Parent"));
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {17, 25})
    void publicFieldsMethodsConstructorsAndThreads(int release) throws Exception {
        compare(
                "ReflectApp",
                """
                        import java.lang.reflect.*;
                        import java.util.concurrent.atomic.AtomicInteger;
                        public class ReflectApp {
                            public interface Named { default String label() { return "named"; } }
                            public static class Base { public int inherited = 7; public String text() { return "base"; } }
                            public static class Player extends Base implements Named {
                                public volatile int health;
                                public String name;
                                public Object reference;
                                public static long total;
                                public final int limit = 100;
                                public Player() { this("default", 0); }
                                public Player(String name, int health) { this.name = name; this.health = health; System.gc(); }
                                public synchronized int damage(int amount) { System.gc(); return health -= amount; }
                                public String text() { return "player"; }
                                public static long sum(long a, long b) { return a + b; }
                                public String echo(String text) { System.gc(); return name + text; }
                                public int overloaded(int x) { return x + 1; }
                                public long overloaded(long x) { return x + 2; }
                                public void fail() { throw new IllegalStateException("target failure"); }
                                public void noop() {}
                                private int hidden;
                            }
                            public static void main(String[] args) throws Exception {
                                Class<?> type = Class.forName("ReflectApp$Player");
                                Constructor<?> constructor = type.getConstructor(String.class, int.class);
                                Object player = constructor.newInstance("Ada ðŸŒŽ", (byte)100);
                                Field health = type.getField("health");
                                System.out.println(health.getInt(player));
                                health.set(player, Short.valueOf((short)80));
                                System.out.println(health.getLong(player));
                                System.out.println(health.get(player).getClass() == Integer.class);
                                System.out.println(type.getField("inherited").getInt(player));
                                System.out.println(health.getType() == int.class);
                                System.out.println(Modifier.isVolatile(health.getModifiers()));
                                System.out.println(health.getDeclaringClass() == type);
                                System.out.println(health.equals(type.getField("health")));
                                System.out.println(health.hashCode() == type.getField("health").hashCode());
                                Field name = type.getField("name");
                                name.set(player, "Grace");
                                System.out.println(name.get(player));
                                Field reference = type.getField("reference");
                                reference.set(player, new int[]{4, 5});
                                System.gc();
                                System.out.println(((int[])reference.get(player))[1]);
                                type.getField("total").setInt(null, 123);
                                System.out.println(type.getField("total").getLong(new Object()));
                                Method damage = type.getMethod("damage", int.class);
                                System.out.println(damage.invoke(player, (byte)5));
                                System.out.println(damage.getReturnType() == int.class);
                                System.out.println(damage.getParameterTypes()[0] == int.class);
                                System.out.println(damage.getParameterCount());
                                System.out.println(Modifier.isSynchronized(damage.getModifiers()));
                                System.out.println(damage.equals(type.getMethod("damage", int.class)));
                                System.out.println(type.getMethod("echo", String.class).invoke(player, "!"));
                                System.out.println(Base.class.getMethod("text").invoke(player));
                                System.out.println(type.getMethod("label").invoke(player));
                                System.out.println(type.getMethod("sum", long.class, long.class).invoke(null, 2, (short)3));
                                System.out.println(type.getMethod("overloaded", int.class).invoke(player, 9));
                                System.out.println(type.getMethod("overloaded", long.class).invoke(player, 9));
                                System.out.println(type.getMethod("noop").invoke(player) == null);
                                System.out.println(type.getMethod("getClass").invoke(player) == type);
                                System.out.println(type.getConstructors().length);
                                int publicFields = 0;
                                for (Field field : type.getFields()) { if (Modifier.isPublic(field.getModifiers())) ++publicFields; }
                                System.out.println(publicFields);
                                boolean foundInherited = false;
                                for (Method method : type.getMethods()) if (method.getName().equals("label")) foundInherited = true;
                                System.out.println(foundInherited);
                                try { type.getField("hidden"); } catch (NoSuchFieldException expected) { System.out.println("hidden field"); }
                                try { type.getMethod("absent"); } catch (NoSuchMethodException expected) { System.out.println("missing method"); }
                                try { type.getField("limit").setInt(player, 4); } catch (IllegalAccessException expected) { System.out.println("final field"); }
                                try { damage.invoke(player, "bad"); } catch (IllegalArgumentException expected) { System.out.println("bad argument"); }
                                try { damage.invoke(player); } catch (IllegalArgumentException expected) { System.out.println("bad count"); }
                                try { damage.invoke(null, 1); } catch (NullPointerException expected) { System.out.println("null receiver"); }
                                try { health.getInt(new Object()); } catch (IllegalArgumentException expected) { System.out.println("bad receiver"); }
                                try { health.setLong(player, 2); } catch (IllegalArgumentException expected) { System.out.println("no narrowing"); }
                                try { name.set(player, new Object()); } catch (IllegalArgumentException expected) { System.out.println("bad reference"); }
                                try { type.getMethod("fail").invoke(player); } catch (InvocationTargetException expected) {
                                    System.out.println(expected.getCause().getMessage());
                                    System.out.println(expected.getTargetException() instanceof IllegalStateException);
                                }
                                health.setInt(player, 100);
                                AtomicInteger failures = new AtomicInteger();
                                Thread[] workers = new Thread[3];
                                for (int i = 0; i < workers.length; ++i) {
                                    workers[i] = new Thread(() -> {
                                        try { for (int j = 0; j < 5; ++j) damage.invoke(player, 1); }
                                        catch (Exception failure) { failures.incrementAndGet(); }
                                    });
                                    workers[i].start();
                                }
                                for (Thread worker : workers) worker.join();
                                System.out.println(health.getInt(player));
                                System.out.println(failures.get());
                                System.out.println(constructor.getName());
                            }
                        }
                        """,
                release,
                builder -> builder.reflectClass("ReflectApp$Player"));
    }

    @Test
    void primitiveConversionsAndInvocationFailures() throws Exception {
        compare(
                "ReflectionEdges",
                """
                        import java.lang.reflect.*;
                        public class ReflectionEdges {
                            public static class Values {
                                public boolean z;
                                public byte b;
                                public short s;
                                public char c;
                                public int i;
                                public long j;
                                public float f;
                                public double d;
                                public int[] array = {4};
                                public static double convert(double value) { System.gc(); return value; }
                                public static Object echo(Object value) { System.gc(); return value; }
                                public static String text(String... values) { return values[0]; }
                                public Values() {}
                            }
                            public static class Broken {
                                public Broken() { throw new IllegalArgumentException("constructor failed"); }
                            }
                            public abstract static class Abstract { public Abstract() {} }
                            public static class BadInit {
                                static { fail(); }
                                static void fail() { throw new IllegalStateException("initialization failed"); }
                                public static void work() {}
                            }
                            public interface Factory { Object make(); }
                            public static class StringFactory implements Factory { public String make() { return "made"; } }
                            public static void main(String[] args) throws Exception {
                                Class<?> type = Values.class;
                                Object value = type.getConstructor().newInstance();
                                type.getField("z").setBoolean(value, true);
                                type.getField("b").setByte(value, (byte)-128);
                                type.getField("s").setShort(value, (short)-32000);
                                type.getField("c").setChar(value, '\\uffff');
                                type.getField("i").setInt(value, Integer.MIN_VALUE);
                                type.getField("j").setLong(value, Long.MIN_VALUE);
                                type.getField("f").setFloat(value, -0.0f);
                                type.getField("d").setDouble(value, 1.25);
                                String[] names = {"z", "b", "s", "c", "i", "j", "f", "d"};
                                for (String name : names) {
                                    Field field = type.getField(name);
                                    Object boxed = field.get(value);
                                    System.out.println(boxed.getClass().getName());
                                    field.set(value, boxed);
                                    System.out.println(boxed.equals(field.get(value)));
                                }
                                System.out.println(type.getField("z").getBoolean(value));
                                System.out.println(type.getField("b").getByte(value));
                                System.out.println(type.getField("s").getShort(value));
                                System.out.println((int)type.getField("c").getChar(value));
                                System.out.println(type.getField("i").getInt(value));
                                System.out.println(type.getField("j").getLong(value));
                                System.out.println(Float.floatToIntBits(type.getField("f").getFloat(value)));
                                System.out.println(type.getField("d").getDouble(value));
                                Method convert = type.getMethod("convert", double.class);
                                Object[] primitiveValues = {(byte)2, (short)3, (char)4, 5, 6L, 7.5f, 8.25};
                                for (Object argument : primitiveValues) System.out.println(convert.invoke(null, argument));
                                try { convert.invoke(null, true); } catch (IllegalArgumentException expected) { System.out.println("boolean is separate"); }
                                try { convert.invoke(null, new Object[]{null}); } catch (IllegalArgumentException expected) { System.out.println("null primitive"); }
                                try { type.getField("z").getInt(value); } catch (IllegalArgumentException expected) { System.out.println("boolean getter"); }
                                try { type.getField("i").set(value, null); } catch (IllegalArgumentException expected) { System.out.println("null field"); }
                                try { type.getField("array").set(value, new String[]{"bad"}); } catch (IllegalArgumentException expected) { System.out.println("wrong array"); }
                                System.out.println(type.getMethod("echo", Object.class).invoke(null, new Object[]{null}) == null);
                                System.out.println(type.getMethod("text", String[].class).isVarArgs());
                                System.out.println(type.getMethod("text", String[].class).invoke(null, (Object)new String[]{"array argument"}));
                                Method covariant = StringFactory.class.getMethod("make");
                                System.out.println(covariant.getReturnType() == String.class);
                                System.out.println(covariant.invoke(new StringFactory()));
                                int bridges = 0;
                                for (Method method : StringFactory.class.getMethods()) if (method.isBridge()) ++bridges;
                                System.out.println(bridges);
                                try { Broken.class.getConstructor().newInstance(); } catch (InvocationTargetException expected) {
                                    System.out.println(expected.getCause().getMessage());
                                }
                                try { Abstract.class.getConstructor().newInstance(); } catch (InstantiationException expected) { System.out.println("abstract constructor"); }
                                Method work = BadInit.class.getMethod("work");
                                System.out.println("metadata does not initialize");
                                try { work.invoke(null); } catch (ExceptionInInitializerError expected) { System.out.println("initialization outside target"); }
                                try { work.invoke(null); } catch (NoClassDefFoundError expected) { System.out.println("failed initialization cached"); }
                                System.out.println(Float.valueOf(-0.0f).equals(Float.valueOf(0.0f)));
                                System.out.println(Double.valueOf(0.0 / 0.0).equals(Double.valueOf(0.0 / 0.0)));
                                System.out.println(Double.doubleToLongBits(-0.0));
                                IllegalStateException cause = new IllegalStateException("original");
                                InvocationTargetException wrapped = new InvocationTargetException(cause);
                                System.out.println(wrapped.getMessage() == null);
                                System.out.println(wrapped.getCause() == cause);
                                System.out.println(new InvocationTargetException(cause, "explicit message").getMessage());
                            }
                        }
                        """,
                25,
                builder ->
                        builder.reflectClass("ReflectionEdges$Values")
                                .reflectClass("ReflectionEdges$Broken")
                                .reflectClass("ReflectionEdges$Abstract")
                                .reflectClass("ReflectionEdges$BadInit")
                                .reflectClass("ReflectionEdges$StringFactory"));
    }

    @Test
    void selectiveRegistrationRetainsOnlyRequestedExecutableMembers() throws Exception {
        var result =
                compare(
                        "Selective",
                        """
                                import java.lang.reflect.*;
                                public class Selective {
                                    public static class Model {
                                        public int value = 40;
                                        public int other;
                                        public Model() {}
                                        public int add(int amount) { return value + amount; }
                                        public String omitted() { return System.getenv("UNUSED"); }
                                    }
                                    public static class MetadataOnly {
                                        public int field;
                                        public MetadataOnly() {}
                                        public String omitted() { return System.getenv("UNUSED"); }
                                    }
                                    public static class Unregistered {
                                        static { System.out.println("must not initialize without registration"); }
                                    }
                                    public static void main(String[] args) throws Exception {
                                        Class<?> type = Class.forName("Selective$Model");
                                        Object model = type.getConstructor().newInstance();
                                        System.out.println(type.getField("value").getInt(model));
                                        System.out.println(type.getMethod("add", int.class).invoke(model, 2));
                                        System.out.println(type.getMethod("omitted").getReturnType() == String.class);
                                        Class<?> metadata = Class.forName("Selective$MetadataOnly");
                                        System.out.println(metadata.getField("field").getType() == int.class);
                                        System.out.println(metadata.getMethod("omitted").getName());
                                        System.out.println(Unregistered.class.getName());
                                        if (args.length > 0) {
                                            try { type.getMethod("omitted").invoke(model); }
                                            catch (UnsupportedOperationException expected) { System.out.println(expected.getMessage()); }
                                            try { type.getField("other").get(model); }
                                            catch (UnsupportedOperationException expected) { System.out.println(expected.getMessage()); }
                                            try { metadata.getConstructor().newInstance(); }
                                            catch (UnsupportedOperationException expected) { System.out.println(expected.getMessage()); }
                                            try { Class.forName("Selective$Unregistered"); }
                                            catch (UnsupportedOperationException expected) { System.out.println(expected.getMessage()); }
                                        }
                                    }
                                }
                                """,
                        25,
                        builder ->
                                builder.reflectConstructor("Selective$Model", "()V")
                                        .reflectMethod("Selective$Model", "add", "(I)I")
                                        .reflectField("Selective$Model", "value")
                                        .reflectMetadata("Selective$MetadataOnly"));
        var missing =
                ProcessHarness.run(
                        temporary,
                        List.of(result.executable().toString(), "missing"),
                        Duration.ofSeconds(30));
        assertEquals(0, missing.exitCode(), missing.text());
        assertTrue(
                missing.text()
                        .contains(
                                "Missing reflection access registration: Selective$Model.omitted"),
                missing.text());
        assertTrue(
                missing.text()
                        .contains("Missing reflection access registration: Selective$Model.other"),
                missing.text());
        assertTrue(
                missing.text()
                        .contains(
                                "Missing reflection access registration: Selective$MetadataOnly.<init>"),
                missing.text());
        assertTrue(
                missing.text()
                        .contains("Missing reflection class registration: Selective$Unregistered"),
                missing.text());
        assertFalse(missing.text().contains("must not initialize"));
        String symbols =
                Files.readString(
                        result.generation()
                                .request()
                                .generatedSourcesDirectory()
                                .resolve("java-symbols.tsv"));
        assertFalse(symbols.contains("Selective$Model.omitted"));
        assertFalse(symbols.contains("Selective$MetadataOnly.omitted"));
    }

    @Test
    void invalidRegistrationsAndPrivateAccessFailBeforeGeneration() throws Exception {
        Path source = temporary.resolve("BadReflection.java"),
                classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        public class BadReflection {
                            private int hidden;
                            private void secret() {}
                            public static void main(String[] args) {}
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        List<Consumer<NativeBuilder>> invalid =
                List.of(
                        b -> b.reflectField("BadReflection", "hidden"),
                        b -> b.reflectMethod("BadReflection", "secret", "()V"),
                        b -> b.reflectMethod("BadReflection", "missing", "broken"),
                        b -> b.reflectConstructor("BadReflection", "(I)V"),
                        b -> b.reflectClass("java.lang.String"));
        for(var configure : invalid) {
            Path output = temporary.resolve("bad");
            var builder =
                    NativeBuilder.create()
                            .classpath(classes)
                            .mainClass("BadReflection")
                            .buildRoot(output);
            configure.accept(builder);
            var failure = assertThrows(CompilerException.class, builder::generate);
            assertTrue(failure.getMessage().contains("JN1101"), failure.getMessage());
            assertFalse(Files.exists(output));
        }
        Files.writeString(
                source,
                """
                        public class BadReflection {
                            public static void main(String[] args) throws Exception { BadReflection.class.getDeclaredField("hidden"); }
                            private int hidden;
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var error =
                assertThrows(
                        CompilerException.class,
                        () ->
                                NativeBuilder.create()
                                        .classpath(classes)
                                        .mainClass("BadReflection")
                                        .buildRoot(temporary.resolve("private"))
                                        .reflectClass("BadReflection")
                                        .generate());
        assertTrue(
                error.getMessage()
                        .contains("Unsupported platform method java.lang.Class.getDeclaredField"),
                error.getMessage());
    }

    @Test
    void metadataQueriesDoNotRetainUncalledImplementations() throws Exception {
        compare(
                "QuietReflection",
                """
                        public class QuietReflection {
                            public int number;
                            public String toString() { return System.getenv("UNUSED"); }
                            public static void main(String[] args) throws Exception {
                                System.out.println(QuietReflection.class.getField("number").getName());
                                System.out.println(QuietReflection.class.getMethod("toString").getReturnType() == String.class);
                            }
                        }
                        """,
                17,
                builder -> builder.reflectMetadata("QuietReflection"));
    }
}
