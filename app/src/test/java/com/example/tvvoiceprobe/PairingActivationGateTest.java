package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** #193: two independent authorization proofs must precede disk activation. */
public class PairingActivationGateTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TV = "123e4567-e89b-42d3-a456-426614174000";
    private static final long START = 50_000L;

    @Test public void requiresBothIndependentProofsAndDiskCommit() {
        PairingActivationGate gate = new PairingActivationGate(SESSION, TV, START);
        assertEquals(PairingActivationGate.Stage.WAIT_CAPABILITIES, gate.stage());
        assertTrue(gate.capabilitiesVerified(SESSION, TV, START + 500, true));
        assertEquals(PairingActivationGate.Stage.WAIT_HA_ACK, gate.stage());
        assertTrue(gate.authenticatedHaAck(SESSION, TV, START + 1_000, true));
        assertEquals(PairingActivationGate.Stage.READY_TO_COMMIT, gate.stage());
        assertTrue(gate.canCommit(SESSION, TV, START + 1_500));
        gate.recordWriteResult(true);
        assertEquals(PairingActivationGate.Stage.COMMITTED, gate.stage());
        assertFalse(gate.canCommit(SESSION, TV, START + 1_600));
        assertEquals(PairingActivationGate.Stage.ABORTED, gate.stage());
    }

    @Test public void haAckAloneCannotActivate() {
        PairingActivationGate gate = new PairingActivationGate(SESSION, TV, START);
        assertFalse(gate.authenticatedHaAck(SESSION, TV, START + 10, true));
        assertEquals(PairingActivationGate.Stage.ABORTED, gate.stage());
        assertFalse(gate.capabilitiesVerified(SESSION, TV, START + 20, true));
    }

    @Test public void assistOnlyOrDeniedAlertsCannotActivate() {
        PairingActivationGate gate = new PairingActivationGate(SESSION, TV, START);
        assertFalse(gate.capabilitiesVerified(SESSION, TV, START + 10, false));
        assertEquals(PairingActivationGate.Stage.ABORTED, gate.stage());
        assertFalse(gate.authenticatedHaAck(SESSION, TV, START + 20, true));
    }

    @Test public void wrongTvAndWrongSessionFailClosed() {
        PairingActivationGate wrongTv = new PairingActivationGate(SESSION, TV, START);
        assertFalse(wrongTv.capabilitiesVerified(
                SESSION, "123e4567-e89b-42d3-a456-426614174001", START, true));
        assertEquals(PairingActivationGate.Stage.ABORTED, wrongTv.stage());

        PairingActivationGate wrongSession = new PairingActivationGate(SESSION, TV, START);
        assertTrue(wrongSession.capabilitiesVerified(SESSION, TV, START, true));
        assertFalse(wrongSession.authenticatedHaAck(
                "AQIDBAUGBwgJCgsMDQ4PEQ", TV, START + 50, true));
        assertEquals(PairingActivationGate.Stage.ABORTED, wrongSession.stage());
    }

    @Test public void expiredOrBackwardsMonotonicClockFailsClosed() {
        PairingActivationGate expired = new PairingActivationGate(SESSION, TV, START);
        assertFalse(expired.capabilitiesVerified(
                SESSION, TV, START + PairingActivationGate.WINDOW_MILLIS, true));
        assertEquals(PairingActivationGate.Stage.ABORTED, expired.stage());

        PairingActivationGate rewound = new PairingActivationGate(SESSION, TV, START);
        assertFalse(rewound.capabilitiesVerified(SESSION, TV, START - 1, true));

        PairingActivationGate boundary = new PairingActivationGate(SESSION, TV, START);
        assertTrue(boundary.capabilitiesVerified(SESSION, TV, START + 1, true));
        assertTrue(boundary.authenticatedHaAck(SESSION, TV, START + 2, true));
        assertFalse(boundary.canCommit(
                SESSION, TV, START + PairingActivationGate.WINDOW_MILLIS));
        assertEquals(PairingActivationGate.Stage.ABORTED, boundary.stage());
    }

    @Test public void duplicateAckAndCancelPreventLaterCommit() {
        PairingActivationGate duplicate = new PairingActivationGate(SESSION, TV, START);
        assertTrue(duplicate.capabilitiesVerified(SESSION, TV, START, true));
        assertTrue(duplicate.authenticatedHaAck(SESSION, TV, START + 1, true));
        assertFalse(duplicate.authenticatedHaAck(SESSION, TV, START + 2, true));
        assertFalse(duplicate.canCommit(SESSION, TV, START + 3));

        PairingActivationGate cancelled = new PairingActivationGate(SESSION, TV, START);
        assertTrue(cancelled.capabilitiesVerified(SESSION, TV, START, true));
        cancelled.abort();
        assertFalse(cancelled.authenticatedHaAck(SESSION, TV, START + 2, true));
    }

    @Test public void diskFailureCannotBeReportedCommitted() {
        PairingActivationGate gate = new PairingActivationGate(SESSION, TV, START);
        assertTrue(gate.capabilitiesVerified(SESSION, TV, START, true));
        assertTrue(gate.authenticatedHaAck(SESSION, TV, START + 1, true));
        gate.recordWriteResult(false);
        assertEquals(PairingActivationGate.Stage.ABORTED, gate.stage());
        assertFalse(gate.canCommit(SESSION, TV, START + 2));
    }

    @Test public void neverAcceptDiskResultBeforeProof() {
        PairingActivationGate gate = new PairingActivationGate(SESSION, TV, START);
        try {
            gate.recordWriteResult(true);
            fail("Commit without two proofs");
        } catch (IllegalStateException expected) {
            assertEquals(PairingActivationGate.Stage.WAIT_CAPABILITIES, gate.stage());
        }
    }
}
