package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.StackTraceMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BoundedNativeInliningTest {
    @TempDir Path temporary;

    @ParameterizedTest @EnumSource(BuildType.class)
    void scalarImportsKeepEvaluationOrderOverflowAndFloatingBits(BuildType buildType) throws Exception {
        Path source = temporary.resolve("ScalarNativeCalls.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                public class ScalarNativeCalls {
                    static int order;
                    static int argument(int value) { order=order*10+value; return value; }
                    static int minimum() { return Math.min(argument(2),argument(1)); }
                    static class Cold {
                        static int value=initialize();
                        static int initialize() { System.gc(); return minimum(); }
                    }
                    public static void main(String[] args) {
                        Object alive=new Object();
                        int identity=alive.hashCode();
                        System.out.println(minimum()+":"+order+":"+Cold.value+":"+order);
                        for(int a : new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE})
                            for(int b : new int[]{Integer.MIN_VALUE,-1,0,1,Integer.MAX_VALUE})
                                System.out.println(Math.min(a,b)+":"+Math.max(a,b)+":"+Math.abs(a));
                        for(int bits : new int[]{0,0x80000000,0x7fc12345,0x7f800000,0xff800000,0x3f800001}) {
                            float value=Float.intBitsToFloat(bits);
                            System.out.println(Float.floatToIntBits(Math.min(value,0f))+":"
                                +Float.floatToIntBits(Math.max(value,-0f))+":"+Math.round(value));
                        }
                        try { System.out.println(Math.addExact(Integer.MAX_VALUE,1)); }
                        catch(ArithmeticException expected) { System.out.println("overflow"); }
                        System.gc();
                        System.out.println(identity==alive.hashCode());
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary,
                List.of(ProcessHarness.java(), "-cp", classes.toString(), "ScalarNativeCalls"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("ScalarNativeCalls")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .stackTraces(StackTraceMode.NONE).javaSourceLocations(false);
        var generated = builder.generate();
        String cpp = ProcessHarness.generatedClassSource(generated.request().generatedSourcesDirectory(), "ScalarNativeCalls");
        assertTrue(cpp.contains("invoke_bounded_native("), cpp);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
