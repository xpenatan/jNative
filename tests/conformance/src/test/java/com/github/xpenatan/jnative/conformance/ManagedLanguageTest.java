package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ManagedLanguageTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(ints = {17, 25})
    void managedSemanticsMatchJvm(int release) throws Exception {
        Path source = temporary.resolve("Managed.java");
        Files.writeString(
                source,
                """
                        public class Managed {
                            interface Value { int value(); }
                            interface DefaultValue { default int defaultValue() { return 19; } }
                            static class Base {
                                int observed;
                                Base() { observed = value(); }
                                int value() { return 10; }
                            }
                            static class Child extends Base implements Value, DefaultValue {
                                int number = 7;
                                public int value() { return number; }
                            }
                            static class Node {
                                Node next;
                                String name;
                                Node(String name) { this.name = name; }
                                public String toString() { return "Node " + name; }
                            }
                            static class Init {
                                static int order = begin();
                                static Node root = new Node("static root");
                                static int begin() { System.out.println("initialized"); return 41; }
                            }
                            static class Broken {
                                static int value = fail();
                                static int fail() { throw new IllegalArgumentException("init"); }
                            }
                            static int divide(int a, int b) { return a / b; }
                            static void thrown() { throw new IllegalStateException("message"); }
                            public static void main(String[] args) {
                                System.out.println(args.length);
                                Child child = new Child();
                                Base base = child;
                                Value face = child;
                                System.out.println(base.value() + ":" + face.value() + ":" + child.observed);
                                System.out.println(child instanceof Base);
                                System.out.println(((DefaultValue)child).defaultValue());
                                Node a = new Node("first"), b = new Node("second");
                                a.next = b; b.next = a;
                                System.gc();
                                System.out.println(a.next.next.name);
                                System.out.println((Object)a);
                                System.out.println("text: " + a);
                                Object text = new String("abc");
                                System.out.println(text.equals("abc"));
                                System.out.println(text.hashCode());
                                System.out.println(text == String.valueOf(text));
                                System.out.println(Init.order + 1);
                                System.out.println(Init.root.name);
                                int[][] values = new int[2][3];
                                values[1][2] = 123;
                                System.out.println(values[1][2] + ":" + values.length);
                                long[] wide = {Long.MIN_VALUE, 9};
                                System.out.println(wide[0]);
                                byte[] bytes = {(byte)255}; short[] shorts = {(short)65535};
                                boolean[] flags = {true};
                                System.out.println(bytes[0] + ":" + shorts[0] + ":" + flags[0]);
                                Object[] strings = new String[2];
                                strings[0] = "retained";
                                System.gc();
                                System.out.println(((String)strings[0]).substring(2, 5));
                                try { strings[1] = new Object(); } catch (ArrayStoreException e) { System.out.println("store"); }
                                try { int bad = values[-1][0]; } catch (IndexOutOfBoundsException e) { System.out.println("bounds"); }
                                try { Object bad = (Node)(Object)strings; } catch (ClassCastException e) { System.out.println("cast"); }
                                try { int bad = divide(1, 0); } catch (ArithmeticException e) { System.out.println("divide"); }
                                try { Node absent = null; absent.name.length(); } catch (NullPointerException e) { System.out.println("null"); }
                                try { thrown(); } catch (RuntimeException e) {
                                    System.gc();
                                    System.out.println(e.getMessage());
                                } finally { System.out.println("finally"); }
                                try { int bad = Broken.value; } catch (ExceptionInInitializerError e) { System.out.println("first failure"); }
                                try { int bad = Broken.value; } catch (NoClassDefFoundError e) { System.out.println("later failure"); }
                                String unicode = "AðŸŒŽÎ©";
                                System.out.println(unicode.length());
                                System.out.println(unicode.charAt(3));
                                System.out.println(unicode.equals(new String(unicode)));
                                System.out.println("abc".hashCode());
                                System.out.println("abc".compareTo("abd"));
                                System.out.println(new String(new char[]{'o', 'k'}));
                                System.out.println("numbers " + 1.5 + ":" + (-0.0) + ":" + true);
                            }
                        }
                        """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, release);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Managed"),
                        Duration.ofSeconds(20));
        var result =
                NativeBuilder.create()
                        .classpath(classes)
                        .mainClass("Managed")
                        .buildRoot(temporary.resolve("output"))
                        .stackTraces(StackTraceMode.JAVA)
                        .javaSourceLocations(true)
                        .buildType(release == 17 ? BuildType.DEBUG : BuildType.RELEASE)
                        .build();
        var actual =
                ProcessHarness.run(
                        temporary, List.of(result.executable().toString()), Duration.ofSeconds(20));
        assertEquals(0, expected.exitCode(), expected.text());
        assertEquals(expected, actual);
    }
}
