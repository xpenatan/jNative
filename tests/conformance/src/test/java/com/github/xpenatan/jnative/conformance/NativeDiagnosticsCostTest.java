package com.github.xpenatan.jnative.conformance;

import com.github.xpenatan.jnative.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeDiagnosticsCostTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    Path temporary;

    @Test
    void recordReleaseCostsWithoutTimingAssertions() throws Exception {
        Path source = temporary.resolve("Cost.java"), classes = temporary.resolve("classes");
        Files.writeString(
                source,
                """
                        import com.github.xpenatan.jnative.interop.NativeImport;
                        import com.github.xpenatan.jnative.interop.NativeInclude;
                        @NativeInclude("jnative_imports.h")
                        public class Cost {
                            @NativeImport("measure_cost") static int measure() { return 0; }
                            public static void main(String[] args) { System.out.println(measure()); }
                        }
                        """);
        ProcessHarness.javac(source, classes, 25);
        Path nativeFile = temporary.resolve("cost.cpp");
        Files.writeString(
                nativeFile,
                """
                        #include "jn_runtime.hpp"
                        #include <chrono>
                        #include <iostream>
                        #ifdef _WIN32
                        #include <windows.h>
                        #include <psapi.h>
                        #include <tlhelp32.h>
                        #endif
                        using Clock=std::chrono::steady_clock;
                        static volatile std::uint64_t sink=0;
                        __attribute__((noinline)) static std::uint64_t tick(std::uint64_t n) {
                        #if JNATIVE_JAVA_TRACES
                            jnative::JavaFrame frame("game.Cost.tick", "Cost.java");
                        #endif
                            return ((n ^ (n >> 11)) * 6364136223846793005ULL) + 1442695040888963407ULL;
                        }
                        extern "C" std::int32_t measure_cost() {
                            auto begin=Clock::now();
                            auto value=sink;
                            for(int i=0;i<50000000;i++) value=tick(value);
                            sink=value;
                            auto loop=Clock::now();
                            for(int i=0;i<1000;i++) { jnative::Throwable error; sink+=error.stack.size(); }
                            auto exceptions=Clock::now();
                            std::cout<<"loop_ms="<<std::chrono::duration<double,std::milli>(loop-begin).count()
                                     <<" capture_us="<<std::chrono::duration<double,std::micro>(exceptions-loop).count()/1000;
                        #ifdef _WIN32
                            HANDLE snapshot=CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS,0);
                            PROCESSENTRY32W entry{};entry.dwSize=sizeof(entry);
                            if(snapshot!=INVALID_HANDLE_VALUE && Process32FirstW(snapshot,&entry))do{
                                if(entry.th32ParentProcessID!=GetCurrentProcessId() || std::wstring(entry.szExeFile)!=L"jnative-diagnostics.exe")continue;
                                HANDLE process=OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ,FALSE,entry.th32ProcessID);
                                PROCESS_MEMORY_COUNTERS counters{};counters.cb=sizeof(counters);
                                if(process && GetProcessMemoryInfo(process,&counters,sizeof(counters)))std::cout<<" helper_working_set="<<counters.WorkingSetSize;
                                if(process)CloseHandle(process);
                            }while(Process32NextW(snapshot,&entry));
                            if(snapshot!=INVALID_HANDLE_VALUE)CloseHandle(snapshot);
                        #endif
                            std::cout<<std::endl;
                            return 7;
                        }
                        """);
        for(String preset : List.of("off", "native", "strong", "java")) {
            var builder =
                    NativeBuilder.create()
                            .classpath(classes)
                            .mainClass("Cost")
                            .nativeFile(nativeFile)
                            .buildRoot(temporary.resolve(preset))
                            .buildType(BuildType.RELEASE)
                            .stackTraces(
                                    preset.equals("off")
                                            ? StackTraceMode.NONE
                                            : preset.equals("java")
                                            ? StackTraceMode.JAVA
                                            : StackTraceMode.NATIVE)
                            .crashReports(
                                    preset.equals("off")
                                            ? CrashReportMode.OFF
                                            : CrashReportMode.LOCAL);
            var generation = builder.generate();
            Path project = generation.request().buildRoot().resolve("native");
            if(preset.equals("strong"))
                Files.writeString(
                        project.resolve("user/CMakeLists.txt"),
                        "set(JNATIVE_STRONG_UNWIND ON CACHE BOOL \"\" FORCE)\n");
            var built = builder.compile(generation);
            for(int iteration = 0; iteration < 3; iteration++) {
                var start = System.nanoTime();
                var measured =
                        ProcessHarness.run(
                                temporary,
                                List.of(built.executable().toString()),
                                Duration.ofSeconds(30));
                assertEquals(0, measured.exitCode(), measured.text());
                assertTrue(measured.text().endsWith("7\n"), measured.text());
                System.out.println(
                        preset
                                + " run="
                                + iteration
                                + " wall_ms="
                                + (System.nanoTime() - start) / 1_000_000
                                + " exe_bytes="
                                + Files.size(built.executable())
                                + " source_zip_bytes="
                                + Files.size(built.diagnostics().sources())
                                + " symbols_bytes="
                                + Files.size(
                                built.diagnostics()
                                        .directory()
                                        .resolve("symbols")
                                        .resolve(
                                                built.executable().getFileName()
                                                        + ".debug"))
                                + " "
                                + measured.text().strip());
            }
        }
    }
}
