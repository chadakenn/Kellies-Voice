package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** v0.17.1: owner-approved v3 logins must enable Okay Nabu without manual tokens. */
public class WakeWordLoginGateTest {
    @Test public void activeOwnerApprovedTvLoginCanEnableWakeWord() {
        assertTrue(WakeWordLoginGate.eligible(true, true, false));
    }

    @Test public void revokeOrInterruptedV3GrantNeverFallsBackToOldManualToken() {
        assertFalse(WakeWordLoginGate.eligible(true, false, true));
        assertFalse(WakeWordLoginGate.eligible(true, false, false));
    }

    @Test public void existingManualPairingStillEnablesWakeWord() {
        assertTrue(WakeWordLoginGate.eligible(false, false, true));
    }

    @Test public void neverEnableMicrophoneWithoutAnyConfiguredLogin() {
        assertFalse(WakeWordLoginGate.eligible(false, false, false));
    }

    @Test public void staleRenewableSlotIgnoredIfNotSelected() {
        assertFalse(WakeWordLoginGate.eligible(false, true, false));
    }
}
