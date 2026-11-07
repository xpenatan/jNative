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
import com.github.xpenatan.jnative.interop.NativeImport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedNativeInliningTest {
    @TempDir Path directory;

    @Test void requiresScalarBoundedImportsWithoutInitializationOrMonitorWork() throws Exception {
        Path source = directory.resolve("ScalarImports.java");
        Path api = Path.of(NativeImport.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Files.writeString(source, """
                import com.github.xpenatan.jnative.interop.NativeImport;
                import com.github.xpenatan.jnative.interop.NativeInclude;

                public class ScalarImports {
                    @NativeInclude("jn_classlib.hpp")
                    static class Helpers {
                        @NativeImport(value="jnative::math_min",managed=true,runtimeOnly=true,bounded=true)
                        static int scalar(int a,int b) { return Integer.parseInt("0") + (a < b ? a : b); }
                        @NativeImport(value="jnative::array_length",managed=true,runtimeOnly=true)
                        static int reference(int[] a) { return a.length; }
                        @NativeImport(value="jnative::math_min",managed=true,runtimeOnly=true)
                        static int unknown(int a,int b) { return a < b ? a : b; }
                        @NativeImport(value="jnative::math_min",managed=true,runtimeOnly=true)
                        static synchronized int monitored(int a,int b) { return a < b ? a : b; }
                    }
                    @NativeInclude("jn_classlib.hpp")
                    static class Cold {
                        static int marker = initialize();
                        static int initialize() { System.gc(); return 1; }
                        @NativeImport(value="jnative::math_min",managed=true,runtimeOnly=true,bounded=true)
                        static int scalar(int a,int b) { return a < b ? a : b; }
                    }
                    static int apply(int x) {
                        return Helpers.scalar(x,3) + Helpers.reference(new int[]{x})
                            + Helpers.unknown(x,3) + Helpers.monitored(x,3) + Cold.scalar(x,3);
                    }
                    public static void main(String[] args) { System.out.println(apply(args.length)); }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-cp", api.toString(), "-g", "-d", directory.toString(), source.toString()));
        for(boolean javaFrames : List.of(false, true)) {
            var request = new NativeBuildRequest(List.of(directory, api), "ScalarImports", directory.resolve("out"),
                    null, null, "scalar-imports", BuildType.RELEASE, false, NativeOptions.defaults(),
                    List.of(), List.of(), ConsoleMode.NORMAL, SourceLayout.PACKAGE_DIRECTORIES,
                    new DiagnosticsOptions(NativeSymbols.AUTO, javaFrames ? StackTraceMode.JAVA : StackTraceMode.NATIVE,
                            CrashReportMode.OFF));
            var sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
            String cpp = sources.get("classes/ScalarImports.cpp");
            cpp += sources.getOrDefault(InliningPlan.FILE, "");
            assertEquals(javaFrames ? 0 : 1, cpp.lines().filter(line -> line.contains("invoke_bounded_native(")).count(), cpp);
            assertTrue(cpp.contains("::reference("), cpp);
            assertTrue(cpp.contains("::unknown("), cpp);
            assertTrue(cpp.contains("::monitored("), cpp);
            assertTrue(cpp.contains("Cold::scalar("), cpp);
            if(!javaFrames) assertTrue(cpp.contains("-> std::int32_t { return ::jnative::math_min("), cpp);
        }
    }
}
