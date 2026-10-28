package com.github.xpenatan.jnative.conformance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Builds C++ counterparts separately without overwriting saved Java-tool baselines. */
public final class BuildMapCppComparison {
    public static void main(String[] args) throws Exception {
        if ((args.length != 4 && args.length != 5) || !args[3].matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("Expected source, build directory, generator, new run name and optional suite");
        String suite = args.length == 5 ? args[4] : "map-probe";
        String target = switch (suite) {
            case "map-probe" -> "map_probe_cpp";
            case "lookup" -> "lookup_cpp";
            case "allocation" -> "allocation_cpp";
            case "general" -> "general_cpp";
            case "map-growth" -> "map_growth_cpp";
            case "map-iteration" -> "map_iteration_cpp";
            case "nested-array" -> "nested_array_cpp";
            case "runtime-cost" -> "runtime_cost_cpp";
            case "numeric" -> "numeric_cpp";
            case "transfer" -> "transfer_cpp";
            default -> throw new IllegalArgumentException("Unknown C++ probe suite: " + suite);
        };
        Path root = Path.of(args[1]);
        Files.createDirectories(root);
        Path output = Files.createDirectory(root.resolve(args[3]));
        Files.copy(Path.of(args[0]), output.resolve("probe.cpp"));
        Files.writeString(output.resolve("CMakeLists.txt"), """
                cmake_minimum_required(VERSION 3.24)
                project(%1$s CXX)
                add_executable(%1$s probe.cpp)
                target_compile_features(%1$s PRIVATE cxx_std_17)
                set_property(TARGET %1$s PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
                if(MSVC)
                    target_compile_options(%1$s PRIVATE /O2 /Ob3 /fp:strict)
                else()
                    target_compile_options(%1$s PRIVATE -O3)
                endif()
                """.formatted(target));
        if(suite.equals("runtime-cost")) Files.writeString(output.resolve("CMakeLists.txt"), """
                add_executable(runtime_cost_atomic_cpp probe.cpp)
                target_compile_features(runtime_cost_atomic_cpp PRIVATE cxx_std_17)
                target_compile_definitions(runtime_cost_atomic_cpp PRIVATE ATOMIC_PAYLOAD)
                set_property(TARGET runtime_cost_atomic_cpp PROPERTY INTERPROCEDURAL_OPTIMIZATION TRUE)
                if(MSVC)
                    target_compile_options(runtime_cost_atomic_cpp PRIVATE /O2 /Ob3 /fp:strict)
                else()
                    target_compile_options(runtime_cost_atomic_cpp PRIVATE -O3)
                endif()
                """, StandardOpenOption.APPEND);
        Path nativeOutput = output.resolve("native");
        BuildLookupComparisons.run(output, List.of("cmake", "-S", output.toString(), "-B", nativeOutput.toString(),
                "-G", args[2], "-DCMAKE_BUILD_TYPE=Release"), 120);
        BuildLookupComparisons.run(output, List.of("cmake", "--build", nativeOutput.toString(), "--config", "Release",
                "--parallel", "2"), 120);
        System.out.println("C++ probe build: " + nativeOutput);
    }
}
