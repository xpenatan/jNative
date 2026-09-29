package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.xpenatan.jnative.BuildType;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class InternedConstantsTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void literalIdentityAndEnumOrderingMatchJvm(BuildType buildType) throws Exception {
        Path source = temporary.resolve("Constants.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import java.util.Arrays;
                        public class Constants {
                            enum Plain { FIRST, SECOND, THIRD }
                            enum Bodies {
                                FIRST { public String toString() { return "first"; } }, SECOND,
                                THIRD { public String toString() { return "third"; } }
                            }
                            static volatile boolean start;
                            static volatile boolean failed;
                            static String firstText() { return "a long shared literal beyond native small-string storage"; }
                            static String secondText() { return "a long shared literal beyond native small-string storage"; }
                            static String nullText() { return "prefix\\0suffix"; }
                            static String unicodeText() { return "\\u20ac\\ud83d\\ude00\\ud800"; }
                            static void check(boolean valid) {
                                if (!valid) {
                                    failed = true;
                                    throw new IllegalStateException("identity, hash or order");
                                }
                            }
                            static int hash(String text) {
                                int result = 0;
                                for (int i = 0; i < text.length(); i++) result = result * 31 + text.charAt(i);
                                return result;
                            }
                            static void work(String[] results, int index) {
                                while (!start) Thread.yield();
                                for (int i = 0; i < 1000; i++) {
                                    String text = (i & 1) == 0 ? firstText() : secondText();
                                    if (i > 0) check(results[index] == text);
                                    results[index] = text;
                                    check(text.hashCode() == hash(text));
                                    check(unicodeText().hashCode() == hash(unicodeText()));
                                    check("\\0\\0".hashCode() == 0);
                                    check(nullText().length() == 13 && nullText().charAt(6) == 0);
                                    check(unicodeText().length() == 4 && unicodeText().charAt(3) == 0xd800);
                                    if ((i & 63) == 0) System.gc();
                                }
                            }
                            public static void main(String[] args) throws Exception {
                                String[] results = new String[4];
                                Thread[] workers = new Thread[4];
                                for (int i = 0; i < workers.length; i++) {
                                    final int index = i;
                                    workers[i] = new Thread(() -> work(results, index));
                                    workers[i].start();
                                }
                                start = true;
                                for (Thread worker : workers) worker.join();
                                check(!failed);
                                for (String text : results) check(text != null && text == firstText());
                                System.gc();
                                check(firstText() == secondText());
                                check(nullText() == "prefix\\0suffix");
                                check(unicodeText() == "\\u20ac\\ud83d\\ude00\\ud800");
                                check("".length() == 0 && "\\0".length() == 1);
                                System.out.println("literal identity after concurrent collection");
                                for (String value : new String[] {"", "\\0\\0", "Aa", "BB", unicodeText(), firstText()}) {
                                    String copy = new String(value.toCharArray());
                                    String built = new StringBuilder().append(value).toString();
                                    Object object = copy;
                                    for (int i = 0; i < 4; i++) {
                                        check(copy.hashCode() == hash(value));
                                        check(object.hashCode() == hash(value));
                                        check(object.equals(value) && value.equals(built));
                                        check(built.hashCode() == copy.hashCode());
                                        check(!value.equals(null) && !value.equals(new Object()));
                                    }
                                    System.out.println(copy.hashCode());
                                }
                                for (Plain left : Plain.values()) for (Plain right : Plain.values())
                                    check(left.compareTo(right) == left.ordinal() - right.ordinal());
                                for (Bodies left : Bodies.values()) for (Bodies right : Bodies.values()) {
                                    check(left.compareTo(right) == left.ordinal() - right.ordinal());
                                    check(left.getDeclaringClass() == Bodies.class);
                                }
                                System.out.println(Arrays.binarySearch(Plain.values(), Plain.SECOND));
                                try { Plain.FIRST.compareTo(null); }
                                catch (NullPointerException expected) { System.out.println("null enum"); }
                                try { ((Enum) Plain.FIRST).compareTo(Bodies.FIRST); }
                                catch (ClassCastException expected) { System.out.println("other enum"); }
                                System.out.println("enum order and declaring class");
                            }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        var expected =
                ProcessHarness.run(
                        temporary,
                        List.of(ProcessHarness.java(), "-cp", classes.toString(), "Constants"),
                        Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder =
                ProcessHarness.behavioralBuilder()
                        .classpath(classes)
                        .mainClass("Constants")
                        .buildRoot(temporary.resolve("out"))
                        .buildType(buildType);
        var generated = builder.generate();
        var compiled = builder.compile(generated);
        assertEquals(
                expected,
                ProcessHarness.run(
                        temporary,
                        List.of(compiled.executable().toString()),
                        Duration.ofSeconds(45),
                        Map.of("JNATIVE_GC_INTERVAL", "3")));
    }
}
