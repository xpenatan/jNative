package com.github.xpenatan.jnative.backend.cpp;

import static org.junit.jupiter.api.Assertions.*;

import com.github.xpenatan.jnative.BuildType;
import com.github.xpenatan.jnative.ConsoleMode;
import com.github.xpenatan.jnative.CrashReportMode;
import com.github.xpenatan.jnative.DiagnosticsOptions;
import com.github.xpenatan.jnative.NativeBuildRequest;
import com.github.xpenatan.jnative.NativeOptions;
import com.github.xpenatan.jnative.NativeSymbols;
import com.github.xpenatan.jnative.SourceLayout;
import com.github.xpenatan.jnative.StackTraceMode;
import com.github.xpenatan.jnative.compiler.BytecodeCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeapEffectsTest {
    @TempDir Path directory;

    @Test void reusesOnlyReadsUnaffectedByTheTransitiveWriteSet() throws Exception {
        Path source = directory.resolve("MemoryCalls.java");
        Files.writeString(source, """
                public class MemoryCalls {
                    static class Base {
                        int x=3, y=5;
                        volatile int ticket;
                        int read() { return x; }
                        int helper() { return read()+1; }
                        void other() { y=7; }
                        void mutate() { x=11; }
                        int poll() { return ticket; }
                    }
                    static final class Child extends Base {}
                    static int stable(Base b) { int first=b.x; int result=b.helper(); b.other(); return first+b.x+result; }
                    static int changed(Base b, Base alias) { int first=b.x; alias.mutate(); return first+b.x; }
                    static int barrier(Base b) { int first=b.x; int ticket=b.poll(); return first+b.x+ticket; }
                    public static void main(String[] args) {
                        Base b=new Child();
                        System.out.println(stable(b)+changed(b,b)+barrier(b));
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        var request = new NativeBuildRequest(List.of(directory), "MemoryCalls", directory.resolve("out"),
                null, null, "memory-calls", BuildType.RELEASE, false, NativeOptions.defaults(),
                List.of(), List.of(), ConsoleMode.NORMAL, SourceLayout.PACKAGE_DIRECTORIES,
                new DiagnosticsOptions(NativeSymbols.NONE, StackTraceMode.NONE, CrashReportMode.OFF));
        var sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
        String cpp = sources.get("classes/MemoryCalls.cpp");
        assertNotNull(cpp);
        assertEquals(1, reads(body(cpp,"stable")), cpp);
        assertEquals(2, reads(body(cpp,"changed")), cpp);
        assertEquals(2, reads(body(cpp,"barrier")), cpp);
    }

    private static long reads(String body) {
        return body.split("x\\.get\\(\\)", -1).length-1;
    }

    private static String body(String cpp, String name) {
        String definition = cpp.lines().filter(line -> line.contains("::" + name + "(")
                && line.endsWith(" {")).findFirst().orElseThrow();
        int start = cpp.indexOf(definition);
        return cpp.substring(start, cpp.indexOf("\n}", start));
    }
}
