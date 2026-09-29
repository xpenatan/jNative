package com.github.xpenatan.jnative;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Native build settings. Files are copied into the generated project's user directory.
 *
 * @param files     C/C++ sources and headers, with unique file names
 * @param cmake     executable used to configure and build
 * @param generator CMake generator, or empty for the platform default
 * @param timeout   maximum duration of each native tool invocation
 */
public record NativeOptions(
        List<Path> files,
        String cmake,
        String generator,
        Duration timeout,
        List<String> cmakeArguments,
        List<String> cmakeBuildArguments,
        List<String> buildToolArguments) {
    public NativeOptions(List<Path> files, String cmake, String generator, Duration timeout) {
        this(files, cmake, generator, timeout, List.of(), List.of(), List.of());
    }

    public NativeOptions {
        files = files.stream().map(p -> p.toAbsolutePath().normalize()).distinct().toList();
        if(cmake == null || cmake.isBlank())
            throw new IllegalArgumentException("cmake must be set");
        if(generator == null) throw new NullPointerException("generator");
        if(timeout == null || timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("timeout must be positive");
        cmakeArguments = arguments(cmakeArguments);
        cmakeBuildArguments = arguments(cmakeBuildArguments);
        buildToolArguments = arguments(buildToolArguments);
    }

    private static List<String> arguments(List<String> values) {
        var copy = List.copyOf(values);
        if(copy.stream().anyMatch(value -> value.indexOf('\0') >= 0))
            throw new IllegalArgumentException("Native arguments cannot contain a zero character");
        return copy;
    }

    public static NativeOptions defaults() {
        return new NativeOptions(List.of(), "cmake", "", Duration.ofMinutes(2));
    }
}
