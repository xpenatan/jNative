package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.BuildLog;
import com.github.xpenatan.jnative.CrashReportMode;
import com.github.xpenatan.jnative.NativeSymbols;
import com.github.xpenatan.jnative.StackTraceMode;
import com.github.xpenatan.jnative.samples.nativecallbacks.BuildNativeCallbacks;
import com.github.xpenatan.jnative.samples.nativecallbacks.NativeCallbacks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class InlineImplementationTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void nativeCallbacksKeepTheirCallableWrappers(BuildType buildType) throws Exception {
        Path module = Path.of(System.getProperty("jnative.root")).resolve("samples/native-callbacks");
        Path classes = Path.of(NativeCallbacks.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String expected = Files.readString(module.resolve("expected-output.txt")).replace("\r\n", "\n");
        var compiled = BuildNativeCallbacks.builder(module, temporary).classpath(classes).buildType(buildType)
                .generator(System.getProperty("jnative.test.generator", ""))
                .nativeSymbols(NativeSymbols.NONE).stackTraces(StackTraceMode.NONE)
                .crashReports(CrashReportMode.OFF).log(BuildLog.silent()).build();
        assertEquals(new ProcessHarness.Output(0, expected), ProcessHarness.run(temporary,
                List.of(compiled.executable().toString()), Duration.ofSeconds(60),
                Map.of("JNATIVE_GC_INTERVAL", "1")));
    }

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void preservesWrappersVirtualDispatchReflectionRootsAndCheckedLoops(BuildType buildType) throws Exception {
        Path source = temporary.resolve("VisibleImplementations.java");
        Path classes = temporary.resolve("classes");
        Files.writeString(source, """
                import java.util.Arrays;
                public class VisibleImplementations {
                    public interface Action { int apply(int[] array,int count); }
                    public static class Worker implements Action {
                        public int apply(int[] array,int count) { return work(array,count); }
                        public final int work(int[] array,int count) {
                            if(count<0) throw new IllegalArgumentException("negative");
                            if(count==0) { System.gc(); return array.length; }
                            for(int i=0;i<count;i++) array[i]=i+7;
                            return array[count-1];
                        }
                        public int inline_work(int[] array,int count) { return -31; }
                    }
                    public static class Other extends Worker {
                        public int apply(int[] array,int count) { return 41; }
                    }
                    static int recursive(int count) { return count==0?1:count*recursive(count-1); }
                    static void invoke(Action action,int[] array,int count) {
                        try { System.out.println(action.apply(array,count)); }
                        catch(ArrayIndexOutOfBoundsException expected) { System.out.println("bounds"); }
                        catch(IllegalArgumentException expected) { System.out.println(expected.getMessage()); }
                        System.out.println(Arrays.toString(array));
                    }
                    public static void main(String[] args) throws Exception {
                        Worker worker=new Worker();
                        int[] array=new int[3];
                        invoke(worker,array,0);
                        invoke(worker,array,2);
                        invoke(worker,array,4);
                        invoke(worker,array,-1);
                        invoke(new Other(),array,2);
                        System.out.println(worker.apply(array,1));
                        System.out.println(worker.inline_work(array,0));
                        System.out.println(Worker.class.getMethod("apply",int[].class,int.class).invoke(worker,array,0));
                        System.out.println(Worker.class.getMethod("work",int[].class,int.class).invoke(worker,array,0));
                        try { worker.apply(null,0); } catch(NullPointerException expected) { System.out.println("null"); }
                        System.gc();
                        System.out.println(recursive(5)+array[0]);
                    }
                }
                """);
        ProcessHarness.javac(source, classes, 25);
        var expected = ProcessHarness.run(temporary, List.of(ProcessHarness.java(), "-cp", classes.toString(),
                "VisibleImplementations"), Duration.ofSeconds(30));
        assertEquals(0, expected.exitCode(), expected.text());
        var builder = ProcessHarness.behavioralBuilder().classpath(classes).mainClass("VisibleImplementations")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .stackTraces(StackTraceMode.NONE).javaSourceLocations(false)
                .reflectMethod("VisibleImplementations$Worker", "apply", "([II)I")
                .reflectMethod("VisibleImplementations$Worker", "work", "([II)I");
        var generated = builder.generate();
        Path output = generated.request().generatedSourcesDirectory();
        String header = Files.readString(output.resolve("java_inline.hpp"));
        String worker = ProcessHarness.generatedClassSource(output, "VisibleImplementations$Worker");
        assertTrue(header.contains("::inline_work_"), header);
        assertTrue(worker.contains("return inline_work_"), worker);
        assertTrue(header.contains("LoopSafepoint") && header.contains("::jnative::safepoint()"), header);
        assertFalse(header.contains("::inline_recursive("), header);
        var compiled = builder.compile(generated);
        assertEquals(expected, ProcessHarness.run(temporary, List.of(compiled.executable().toString()),
                Duration.ofSeconds(90), Map.of("JNATIVE_GC_INTERVAL", "1")));
    }
}
