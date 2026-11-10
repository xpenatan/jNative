package com.github.xpenatan.jnative.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class BenchmarkCalibrationTest {
    private final BenchmarkCalibration policy = new BenchmarkCalibration(
            true, 100_000_000, 2_000_000_000, 64L * 1024 * 1024, 6);

    @Test
    void fastestImplementationDeterminesSharedWorkAndGrowthIsBounded() {
        var decision = policy.next(100, 1_000, 4_000,
                policy.limit("general", "search-prefix", 16));
        assertTrue(decision.repeat());
        assertEquals(1600, decision.rounds());
        assertEquals("duration-calibration", decision.reason());
    }

    @Test
    void allImplementationsMustReachTargetWithoutExceedingSlowestBudget() {
        var ready = policy.next(1000, 120_000_000, 1_900_000_000,
                policy.limit("general", "arithmetic", 256));
        assertFalse(ready.repeat());
        assertEquals(1000, ready.rounds());
        assertEquals("target-reached", ready.reason());
        var tooSlow = policy.next(1000, 120_000_000, 4_000_000_000L,
                policy.limit("general", "arithmetic", 256));
        assertTrue(tooSlow.repeat());
        assertEquals(425, tooSlow.rounds());
        assertEquals("slow-implementation-limit", tooSlow.reason());
    }

    @Test
    void coldHashInputBudgetCapsRoundsWithoutGrowingAllSetupIndefinitely() {
        var limit = policy.limit("general", "hash-cold", 4096);
        assertTrue(limit.rounds() < 10_000);
        var grow = policy.next(1000, 1_000_000, 2_000_000, limit);
        assertEquals(limit.rounds(), grow.rounds());
        assertEquals("setup-memory-limit", grow.reason());
        var stop = policy.next(limit.rounds(), 8_000_000, 16_000_000, limit);
        assertFalse(stop.repeat());
        assertEquals("setup-memory-limit", stop.reason());
    }

    @Test
    void extremeImplementationGapIsExplicitInsteadOfAssigningDifferentRounds() {
        var decision = policy.next(1000, 10_000_000, 2_000_000_000,
                policy.limit("general", "search-prefix", 4096));
        assertFalse(decision.repeat());
        assertEquals(1000, decision.rounds());
        assertEquals("slow-implementation-limit", decision.reason());
    }

    @Test
    void invalidDurationsAndSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> policy.next(1, 0, 1, new BenchmarkCalibration.Limit(1, "test")));
        assertThrows(IllegalArgumentException.class, () -> policy.next(1, Double.NaN, 1, new BenchmarkCalibration.Limit(1, "test")));
        assertThrows(IllegalArgumentException.class,
                () -> new BenchmarkCalibration(true, 100_000_000, 1_000_000, 1024 * 1024, 6));
        var invalid = new Properties();
        invalid.setProperty("calibration.enabled", "yes");
        assertThrows(IllegalArgumentException.class, () -> BenchmarkCalibration.from(invalid));
    }

    @Test
    void legacyConfigurationsKeepTheirRoundCountsUnlessCalibrationIsEnabled() {
        var defaults = BenchmarkCalibration.from(new Properties());
        assertFalse(defaults.enabled());
        assertEquals(100_000_000, defaults.targetNanos());
        assertEquals(6, defaults.maximumAttempts());
    }

    @Test
    void pilotAndMeasurementProtocolRejectDifferentWorkCountsAndMissingSamples() throws Exception {
        String valid = "BENCH,arithmetic,256,1000,120000000,42\n";
        var sample = BenchmarkScores.parseOutput(valid, "arithmetic", 256, 1000, 1).getFirst();
        assertEquals(120_000_000, sample.nanos());
        assertEquals("42", sample.checksum());
        assertThrows(IOException.class, () -> BenchmarkScores.parseOutput(valid, "arithmetic", 256, 2000, 1));
        assertThrows(IOException.class, () -> BenchmarkScores.parseOutput(valid, "arithmetic", 16, 1000, 1));
        assertThrows(IOException.class, () -> BenchmarkScores.parseOutput(valid, "arithmetic", 256, 1000, 3));
        assertThrows(IOException.class, () -> BenchmarkScores.parseOutput(valid + valid, "arithmetic", 256, 1000, 1));
    }

    @Test
    void nonpositiveAndMalformedPilotTimesCannotChooseRoundCounts() {
        assertThrows(IOException.class,
                () -> BenchmarkScores.parseOutput("BENCH,arithmetic,256,1000,0,42\n", "arithmetic", 256, 1000, 1));
        assertThrows(IOException.class,
                () -> BenchmarkScores.parseOutput("BENCH,arithmetic,256,1000,invalid,42\n", "arithmetic", 256, 1000, 1));
    }
}
