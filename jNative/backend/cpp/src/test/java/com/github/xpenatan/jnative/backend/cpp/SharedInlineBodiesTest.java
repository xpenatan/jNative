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
import com.github.xpenatan.jnative.internal.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedInlineBodiesTest {
    @TempDir Path directory;

    @Test void movesOnlyProvenBoundedEntriesAndMapsTheActualDefinition() throws Exception {
        Path source = directory.resolve("VisibleBodies.java");
        Files.writeString(source, """
                public class VisibleBodies {
                    static class Table { static final int[] values = {2,3,5,7}; }
                    static class Lookup { static int read(int index) { return Table.values[index&3]; } }
                    static final class Worker {
                        int read(int index) { return Lookup.read(index); }
                        int collect(int index) { System.gc(); return Lookup.read(index); }
                    }
                    static class Pair {
                        static int read(Worker worker, int index) {
                            return worker.read(index) + worker.read(index + 1);
                        }
                    }
                    public static void main(String[] args) {
                        Worker worker = new Worker();
                        System.out.println(Pair.read(worker,args.length)+worker.collect(1));
                    }
                }
                """);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-g", "-d", directory.toString(), source.toString()));
        for(StackTraceMode tracing : List.of(StackTraceMode.NONE, StackTraceMode.JAVA)) {
            var request = new NativeBuildRequest(List.of(directory), "VisibleBodies", directory.resolve("out"),
                    null, null, "visible-bodies", BuildType.RELEASE, false, NativeOptions.defaults(),
                    List.of(), List.of(), ConsoleMode.NORMAL, SourceLayout.PACKAGE_DIRECTORIES,
                    new DiagnosticsOptions(NativeSymbols.NONE, tracing, CrashReportMode.OFF));
            var sources = new CppEmitter().emit(new BytecodeCompiler().compile(request), request);
            Map<String, Object> map = Json.object(Json.read(sources.get("source-map.json")));
            if(tracing.javaFrames()) {
                assertFalse(sources.containsKey(InliningPlan.FILE));
                assertTrue(Json.array(map.get("inlineClasses")).isEmpty());
                continue;
            }
            String header = sources.get(InliningPlan.FILE);
            assertNotNull(header);
            assertTrue(header.contains("JNATIVE_FORCEINLINE "), header);
            assertTrue(header.contains("::initialized_read("), header);
            assertFalse(header.contains("::initialized_collect("), header);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(header.getBytes(StandardCharsets.UTF_8)));
            int methods = 0;
            for(Object value : Json.array(map.get("inlineClasses"))) {
                var owner = Json.object(value);
                assertEquals(InliningPlan.FILE, owner.get("cppFile"));
                assertEquals(hash, owner.get("sha256"));
                for(Object mapped : Json.array(owner.get("methods"))) {
                    var method = Json.object(mapped);
                    int start = ((Number)method.get("cppLineStart")).intValue();
                    int end = ((Number)method.get("cppLineEnd")).intValue();
                    String body = String.join("\n", header.lines().toList().subList(start - 1, end));
                    assertTrue(body.startsWith("JNATIVE_FORCEINLINE "), body);
                    assertTrue(body.contains("::" + method.get("cppMethod") + "("), body);
                    methods++;
                }
            }
            assertTrue(methods >= 2, header);
            for(Object value : Json.array(map.get("classes"))) {
                var owner = Json.object(value);
                String cpp = sources.get((String)owner.get("cppFile"));
                assertTrue(cpp.contains("#include \"java_inline.hpp\""), cpp);
                assertFalse(cpp.lines().anyMatch(line -> line.startsWith("JNATIVE_FORCEINLINE ")), cpp);
            }
        }
    }
}
