package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class HeapEffectsTest {
    @TempDir Path temporary;

    @ParameterizedTest
    @EnumSource(BuildType.class)
    void transitiveEffectsPreserveAliasesBarriersAndFailedWrites(BuildType buildType) throws Exception {
        Path source=temporary.resolve("MemoryCalls.java");
        Path classes=temporary.resolve("classes");
        Files.writeString(source,"""
                public class MemoryCalls {
                    static Base current;
                    static class Base {
                        int x=3, y=5;
                        volatile int ticket;
                        int[] data={1,2,3};
                        int read() { return x; }
                        int helper() { return read()+1; }
                        void other() { y=7; }
                        void mutate() { x=11; }
                        int poll() { return ticket; }
                        void put(int[] array,int index) { array[index]=13; }
                        void putPair(int[] array,int index) { array[0]=31; array[index]=37; }
                        void replace(int[] array) { data=array; }
                        synchronized void synchronize() { x=17; }
                    }
                    static final class Child extends Base {}
                    static class Cold { static int value=initialize(); static int initialize(){current.x=29;System.gc();return 19;} }
                    static int stable(Base b) { int first=b.x; int result=b.helper(); b.other(); return first+b.x+result; }
                    static int changed(Base b,Base alias) { int first=b.x; alias.mutate(); return first+b.x; }
                    static int barrier(Base b) { int first=b.x; int ticket=b.poll(); return first+b.x+ticket; }
                    static int arrays(Base b,Base alias,int index) { int first=b.data[0]; alias.put(alias.data,index); return first+b.data[0]; }
                    static int references(Base b,Base alias) { int[] first=b.data; alias.replace(new int[]{23}); return first[0]+b.data[0]; }
                    static int cold(Base b) { int first=b.x; int other=Cold.value; return first+b.x+other; }
                    public static void main(String[] args) {
                        Base b=new Child(), different=new Child();
                        current=b;
                        System.out.println(stable(b));
                        System.out.println(changed(b,different));
                        System.out.println(changed(b,b));
                        System.out.println(barrier(b));
                        System.out.println(arrays(b,different,0));
                        System.out.println(arrays(b,b,0));
                        try { arrays(b,b,3); } catch(ArrayIndexOutOfBoundsException expected) { System.out.println(b.data[0]); }
                        try { b.putPair(b.data,3); } catch(ArrayIndexOutOfBoundsException expected) { System.out.println(b.data[0]); }
                        System.out.println(references(b,b));
                        System.out.println(cold(b));
                        b.synchronize();
                        System.out.println(stable(b));
                        try { changed(b,null); } catch(NullPointerException expected) { System.out.println(b.x); }
                        System.gc();
                        System.out.println(b.data[0]+different.x);
                    }
                }
                """);
        ProcessHarness.javac(source,classes,25);
        var expected=ProcessHarness.run(temporary,List.of(ProcessHarness.java(),"-cp",classes.toString(),"MemoryCalls"),Duration.ofSeconds(30));
        assertEquals(0,expected.exitCode(),expected.text());
        var builder=ProcessHarness.behavioralBuilder().classpath(classes).mainClass("MemoryCalls")
                .buildRoot(temporary.resolve("out")).buildType(buildType)
                .stackTraces(StackTraceMode.NONE).javaSourceLocations(false);
        var compiled=builder.compile(builder.generate());
        assertEquals(expected,ProcessHarness.run(temporary,List.of(compiled.executable().toString()),
                Duration.ofSeconds(90),Map.of("JNATIVE_GC_INTERVAL","1")));
    }
}
