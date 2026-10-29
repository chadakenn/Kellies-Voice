package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Pure generation check used by PairingSetupActivity callbacks.
 * A closed window must never mutate a newer session's UI.
 */
public class PairingGenerationGuardTest {
    @Test public void staleGenerationIsIgnored() {
        int active = 2;
        int stale = 1;
        assertTrue(stale != active);
        assertFalse(shouldApply(stale, active));
    }

    @Test public void matchingGenerationIsApplied() {
        int active = 3;
        assertTrue(shouldApply(3, active));
    }

    @Test public void stopIncrementsAndInvalidates() {
        int active = 1;
        active++; // stopWindow
        int captured = 1;
        assertFalse(shouldApply(captured, active));
    }

    private static boolean shouldApply(int captured, int active) {
        return captured == active;
    }
}
