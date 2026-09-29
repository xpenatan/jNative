package com.github.xpenatan.jnative;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import static org.junit.jupiter.api.Assertions.*;

class BuildLogTest {
    @Test
    @ResourceLock(Resources.SYSTEM_ERR)
    @ResourceLock(Resources.SYSTEM_OUT)
    void warningsLoggerReportsProblemsWithoutVerboseProgress() {
        var errors = new ByteArrayOutputStream();
        var output = new ByteArrayOutputStream();
        PrintStream previousError = System.err, previousOutput = System.out;
        try(var errorLog = new PrintStream(errors, true, StandardCharsets.UTF_8);
            var outputLog = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setErr(errorLog);
            System.setOut(outputLog);
            try {
                var log = BuildLog.warnings();
                log.log(BuildLog.Level.INFO, "verbose progress");
                log.log(BuildLog.Level.WARNING, "readability fallback");
                log.log(BuildLog.Level.ERROR, "build failure");
            } finally {
                System.setErr(previousError);
                System.setOut(previousOutput);
            }
        }
        String text = errors.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("[jNative WARNING] readability fallback"), text);
        assertTrue(text.contains("[jNative ERROR] build failure"), text);
        assertFalse(text.contains("verbose progress"), text);
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }
}
