package com.github.xpenatan.jnative.conformance;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import org.teavm.tooling.ConsoleTeaVMToolLog;
import org.teavm.tooling.TeaVMProblemRenderer;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.vm.TeaVMOptimizationLevel;

/** Runs in a separate JVM, keeping compiler work out of measured processes. */
public final class CompileLookupTeaVm {
    public static void main(String[] args) throws Exception {
        try (var loader = new URLClassLoader(new URL[]{Path.of(args[0]).toUri().toURL()},
                CompileLookupTeaVm.class.getClassLoader())) {
            var tool = new TeaVMTool();
            tool.setClassLoader(loader);
            tool.setMainClass(args.length == 3 ? args[2] : "LookupIsolation");
            tool.setTargetType(TeaVMTargetType.C);
            tool.setTargetDirectory(Path.of(args[1]).toFile());
            tool.setTargetFileName("lookup.c");
            tool.setOptimizationLevel(TeaVMOptimizationLevel.FULL);
            tool.setLog(new ConsoleTeaVMToolLog(true));
            tool.generate();
            TeaVMProblemRenderer.describeProblems(tool.getDependencyInfo().getCallGraph(), tool.getProblemProvider(),
                    new ConsoleTeaVMToolLog(true));
            if (!tool.getProblemProvider().getSevereProblems().isEmpty()) throw new IllegalStateException("TeaVM compilation failed");
        }
    }
}
