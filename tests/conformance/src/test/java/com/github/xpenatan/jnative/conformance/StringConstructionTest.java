package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.xpenatan.jnative.BuildType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class StringConstructionTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void codePointsSnapshotsAndCaseConversionMatchJvm(BuildType buildType) throws Exception {
        Path source = temporary.resolve("StringConstruction.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                import java.util.Locale;
                public class StringConstruction {
                    static void units(String text) {
                        System.out.print(text.length() + ":");
                        for (int i = 0; i < text.length(); i++)
                            System.out.print(Integer.toHexString(text.charAt(i)) + ",");
                        System.out.println();
                    }
                    public static void main(String[] args) {
                        for (int capacity : new int[] {0, 1, 2, 16}) {
                            StringBuilder builder = new StringBuilder(capacity);
                            for (int point : new int[] {0, 65, 0xd7ff, 0xd800, 0xdbff,
                                    0xdc00, 0xdfff, 0xffff, 0x10000, 0x1f30e, 0x10ffff}) {
                                builder.appendCodePoint(point);
                                System.gc();
                            }
                            String snapshot = builder.toString();
                            units(snapshot);
                            for (int point : new int[] {-1, 0x110000, Integer.MIN_VALUE,
                                    Integer.MAX_VALUE}) {
                                try { builder.appendCodePoint(point); }
                                catch (IllegalArgumentException expected) {
                                    System.out.println(builder.toString().equals(snapshot));
                                }
                            }
                            builder.setCharAt(0, 'x');
                            builder.setLength(1);
                            builder.appendCodePoint(0x1f30e);
                            units(snapshot);
                            units(builder.toString());
                            builder.setLength(0);
                            units(builder.toString());
                        }
                        for (Locale locale : new Locale[] {Locale.ROOT, Locale.ENGLISH,
                                new Locale("tr"), new Locale("az")}) {
                            for (String text : new String[] {"", "123", "already lower",
                                    "ALREADY UPPER", "aBc I i", "Straße", "İIıi", "éΩ",
                                    "🌎x🌎", "\\uD800x\\uDC00", "\\uD801\\uDC00"}) {
                                units(text.toLowerCase(locale));
                                units(text.toUpperCase(locale));
                            }
                        }
                        String unchanged = new String("already lower");
                        System.out.println(unchanged == unchanged.toLowerCase(Locale.ROOT));
                        try { "".toLowerCase(null); }
                        catch (NullPointerException expected) { System.out.println("null locale"); }
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "StringConstruction"),
                Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var compiled = ProcessHarness.behavioralBuilder().classpath(classes)
                .mainClass("StringConstruction").buildRoot(temporary.resolve("native"))
                .buildType(buildType).build();
        assertEquals(expected, ProcessHarness.run(temporary,
                List.of(compiled.executable().toString()), Duration.ofSeconds(60),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
