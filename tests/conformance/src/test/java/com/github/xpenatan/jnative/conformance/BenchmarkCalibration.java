package com.github.xpenatan.jnative.conformance;

import java.util.Properties;

/** Chooses identical repeated work for every implementation, with explicit limits. */
record BenchmarkCalibration(boolean enabled, long targetNanos, long maximumNanos,
                            long setupBytes, int maximumAttempts) {
    static final int MAXIMUM_ROUNDS = 1_000_000_000;

    record Limit(int rounds, String reason) {
    }

    record Decision(int rounds, boolean repeat, String reason) {
    }

    BenchmarkCalibration {
        if(targetNanos < 1_000_000 || maximumNanos < targetNanos || maximumNanos > 10_000_000_000L)
            throw new IllegalArgumentException("Calibration requires 1 <= targetMillis <= maxSampleMillis <= 10000");
        if(setupBytes < 1024 * 1024 || setupBytes > 4096L * 1024 * 1024)
            throw new IllegalArgumentException("Calibration maxSetupMiB must be between 1 and 4096");
        if(maximumAttempts < 1 || maximumAttempts > 12)
            throw new IllegalArgumentException("Calibration maxAttempts must be between 1 and 12");
    }

    static BenchmarkCalibration from(Properties configuration) {
        String enabled = configuration.getProperty("calibration.enabled", "false");
        if(!enabled.equals("true") && !enabled.equals("false"))
            throw new IllegalArgumentException("calibration.enabled must be true or false");
        return new BenchmarkCalibration(Boolean.parseBoolean(enabled),
                Math.multiplyExact(Long.parseLong(configuration.getProperty("calibration.targetMillis", "100")), 1_000_000),
                Math.multiplyExact(Long.parseLong(configuration.getProperty("calibration.maxSampleMillis", "2000")), 1_000_000),
                Math.multiplyExact(Long.parseLong(configuration.getProperty("calibration.maxSetupMiB", "64")), 1024 * 1024),
                Integer.parseInt(configuration.getProperty("calibration.maxAttempts", "6")));
    }

    Limit limit(String suite, String operation, int size) {
        if(size < 1) throw new IllegalArgumentException("Calibration size must be positive");
        if(suite.equals("general") && operation.equals("hash-cold")) {
            // Round-scaled setup retains distinct strings and their storage. This
            // conservative estimate includes references, headers and UTF-16 data;
            // it is not a bound on the entire JVM/native process or GC heap.
            long bytesPerRound = 160L + 2L * size;
            return new Limit((int)Math.min(MAXIMUM_ROUNDS, Math.max(1, setupBytes / bytesPerRound)),
                    "setup-memory-limit");
        }
        return new Limit(MAXIMUM_ROUNDS, "round-limit");
    }

    Decision next(int rounds, double fastestNanos, double slowestNanos, Limit limit) {
        if(rounds < 1 || limit.rounds() < 1 || !Double.isFinite(fastestNanos)
                || !Double.isFinite(slowestNanos) || fastestNanos <= 0 || slowestNanos < fastestNanos)
            throw new IllegalArgumentException("Invalid calibration pilot measurements");
        if(fastestNanos >= targetNanos && slowestNanos <= maximumNanos)
            return new Decision(rounds, false, "target-reached");

        // Headroom reduces the chance that ordinary measurement variation puts
        // the final process medians just below the requested target.
        long desired = Math.max(1, (long)Math.ceil(rounds * (targetNanos * 1.2 / fastestNanos)));
        long timeLimit = Math.max(1, (long)Math.floor(rounds * (maximumNanos * 0.85 / slowestNanos)));
        long growthLimit = Math.min(MAXIMUM_ROUNDS, (long)rounds * 16);
        long chosen = Math.min(Math.min(desired, timeLimit), Math.min(growthLimit, limit.rounds()));
        String reason = timeLimit < desired && timeLimit <= limit.rounds() ? "slow-implementation-limit"
                : limit.rounds() < desired ? limit.reason() : "duration-calibration";
        int next = (int)Math.max(1, chosen);
        if(next == rounds || slowestNanos <= maximumNanos && next < rounds)
            return new Decision(rounds, false, reason);
        return new Decision(next, true, reason);
    }
}
