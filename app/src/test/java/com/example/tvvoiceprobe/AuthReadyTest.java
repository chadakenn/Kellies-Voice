package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthReadyTest {
    @Test public void standbyAuthDoesNotStartUntilTheOverlayArrives() {
        AuthReady gate = new AuthReady();
        assertFalse(gate.onAuthenticated(false));
        assertTrue(gate.ownerAttached());
        assertFalse(gate.ownerAttached());
    }

    @Test public void overlayThatArrivedFirstGetsExactlyOneReady() {
        AuthReady gate = new AuthReady();
        assertFalse(gate.ownerAttached());
        assertTrue(gate.onAuthenticated(true));
        assertFalse(gate.onAuthenticated(true));
        assertFalse(gate.ownerAttached());
    }

    @Test public void disconnectLetsTheNextLoginDeliverReadyAgain() {
        AuthReady gate = new AuthReady();
        assertTrue(gate.onAuthenticated(true));
        gate.reset();
        assertFalse(gate.ownerAttached());
        assertTrue(gate.onAuthenticated(true));
    }

    @Test public void aDroppedStandbyLoginDoesNotLookReady() {
        AuthReady gate = new AuthReady();
        assertFalse(gate.onAuthenticated(false));
        gate.reset();
        assertFalse(gate.ownerAttached());
        assertFalse(gate.onAuthenticated(false));
        assertTrue(gate.ownerAttached());
        assertFalse(gate.ownerAttached());
    }
}
