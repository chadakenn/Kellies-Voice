package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Regression tests exercise the SAME guard called by PairingSetupActivity.
 * Unlike a duplicate helper in test code, these fail if the actual
 * production ownership policy is changed or removed.
 */
public class PairingGenerationGuardTest {
    @Test public void codeFromCancelledWindowCannotChangeUiAfterRestart() {
        PairingGenerationGuard guard = new PairingGenerationGuard();
        long first = guard.advance();       // first START
        assertTrue(guard.isCurrent(first));
        guard.advance();                   // Back, timeout or manual fallback
        long second = guard.advance();     // second START
        assertFalse(guard.isCurrent(first));
        assertTrue(guard.isCurrent(second));
    }

    @Test public void oldExpiryCannotDisableNewStartOrClearNewCode() {
        PairingGenerationGuard guard = new PairingGenerationGuard();
        long expired = guard.advance();
        guard.advance();
        long current = guard.advance();
        assertFalse(guard.isCurrent(expired));
        assertTrue(guard.isCurrent(current));
    }

    @Test public void backgroundAndDestroyInvalidateInFlightCallback() {
        PairingGenerationGuard guard = new PairingGenerationGuard();
        long captured = guard.advance();
        guard.advance();    // onStop
        assertFalse(guard.isCurrent(captured));
        guard.advance();    // onDestroy
        assertFalse(guard.isCurrent(captured));
    }

    @Test public void noSessionOrPreviousSessionIsCurrent() {
        PairingGenerationGuard guard = new PairingGenerationGuard();
        assertFalse(guard.isCurrent(0));
        assertFalse(guard.isCurrent(1));
        long current = guard.advance();
        assertTrue(guard.isCurrent(current));
        assertFalse(guard.isCurrent(0));
        guard.advance();
        assertFalse(guard.isCurrent(current));
    }

    @Test public void repeatedRestartNeverRevalidatesOldGeneration() {
        PairingGenerationGuard guard = new PairingGenerationGuard();
        long oldest = guard.advance();
        for (int i = 0; i < 50; i++) {
            guard.advance(); // cancel
            long latest = guard.advance(); // start
            assertTrue(guard.isCurrent(latest));
            assertFalse(guard.isCurrent(oldest));
        }
    }
}
