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

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceArrayAllocationTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void knownAndReflectiveComponentsPreserveJavaArrayContracts(BuildType buildType) throws Exception {
        Path source = temporary.resolve("ArrayMetadata.java");
        Files.writeString(source, """
                import java.lang.reflect.Array;

                public class ArrayMetadata {
                    static int initialized, evaluations;
                    interface Face {}
                    static class Base implements Face {
                        static { initialized++; }
                    }
                    static class Child extends Base {}
                    static class Other {}
                    static int negative() { evaluations++; return -1; }
                    static void store(Object[] array, Object value) {
                        try { array[0] = value; System.out.println("stored"); }
                        catch (ArrayStoreException expected) { System.out.println("rejected"); }
                    }
                    public static void main(String[] args) {
                        Base[] base = new Base[2];
                        Face[] faces = new Face[2];
                        Base[][] nested = new Base[2][];
                        System.out.println("initialization:" + initialized);
                        try { Object ignored = new Base[negative()]; }
                        catch (NegativeArraySizeException expected) { System.out.println("negative:" + evaluations + ":" + initialized); }
                        Child child = new Child();
                        Other other = new Other();
                        store(base, new Base()); store(base, child); store(base, other);
                        store(faces, child); store(faces, other);
                        store(nested, base); store(nested, new Object[1]);
                        store(base.clone(), other);
                        Object[] reflected = (Object[]) Array.newInstance(Base.class, 2);
                        store(reflected, new Base()); store(reflected, child); store(reflected, other);
                        Object[] source = {child, other};
                        try { System.arraycopy(source, 0, base, 0, 2); }
                        catch (ArrayStoreException expected) { System.out.println("partial:" + (base[0] == child) + ":" + (base[1] == null)); }
                        System.gc();
                        System.out.println("retained:" + (base[0] == child) + ":" + (nested[0] == base) + ":" + initialized);
                    }
                }
                """);
        Path classes = temporary.resolve("classes");
        ProcessHarness.javac(source, classes, 17);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ArrayMetadata"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var result = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("ArrayMetadata")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .cmakeBuildArgs("--parallel", "2").timeout(Duration.ofMinutes(5)).build();
        assertEquals(expected, ProcessHarness.run(temporary, List.of(result.executable().toString()),
                Duration.ofSeconds(60), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
