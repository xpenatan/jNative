package com.github.xpenatan.jnative;

import java.util.Objects;

/**
 * Independent native symbol, exception, and fatal-crash policies.
 */
public record DiagnosticsOptions(
        NativeSymbols symbols, StackTraceMode stackTraces, CrashReportMode crashReports) {
    public DiagnosticsOptions {
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(stackTraces, "stackTraces");
        Objects.requireNonNull(crashReports, "crashReports");
    }

    public static DiagnosticsOptions defaults() {
        return new DiagnosticsOptions(
                NativeSymbols.AUTO, StackTraceMode.NATIVE, CrashReportMode.LOCAL);
    }

    public static DiagnosticsOptions legacy() {
        return new DiagnosticsOptions(NativeSymbols.AUTO, StackTraceMode.JAVA, CrashReportMode.OFF);
    }
}
