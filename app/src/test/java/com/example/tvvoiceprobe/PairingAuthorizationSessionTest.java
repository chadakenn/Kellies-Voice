package com.example.tvvoiceprobe;

import org.junit.Test;
import java.security.SecureRandom;
import static org.junit.Assert.*;

public class PairingAuthorizationSessionTest {
    @Test public void pkceMatchesRfc7636S256Vector() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                PairingAuthorizationSession.s256Challenge(verifier));
    }

    @Test public void createsRandomUrlSafeSessionWithoutLoggingSecrets() {
        PairingAuthorizationSession first = PairingAuthorizationSession.begin(1000, new SecureRandom());
        PairingAuthorizationSession second = PairingAuthorizationSession.begin(1000, new SecureRandom());
        assertNotEquals(first.state(), second.state());
        assertNotEquals(first.verifier(), second.verifier());
        assertEquals(43, first.state().length());
        assertEquals(43, first.verifier().length());
        assertEquals(PairingAuthorizationSession.s256Challenge(first.verifier()), first.challenge());
        assertTrue(first.active(1000));
        assertTrue(first.active(120999));
        assertFalse(first.active(121000));
    }

    @Test public void validStateCanOnlyBeConsumedOnce() {
        PairingAuthorizationSession session = PairingAuthorizationSession.begin(200, new SecureRandom());
        assertTrue(session.consumeState(session.state(), 201));
        assertFalse(session.consumeState(session.state(), 202));
        assertFalse(session.active(202));
    }

    @Test public void incorrectStateCancelAndExpirationFailClosed() {
        PairingAuthorizationSession rejected = PairingAuthorizationSession.begin(1, new SecureRandom());
        assertFalse(rejected.consumeState("not-the-same-state", 5));
        assertFalse(rejected.consumeState(rejected.state(), 6));

        PairingAuthorizationSession cancelled = PairingAuthorizationSession.begin(10, new SecureRandom());
        cancelled.cancel();
        assertFalse(cancelled.consumeState(cancelled.state(), 11));

        PairingAuthorizationSession expired = PairingAuthorizationSession.begin(1000, new SecureRandom());
        assertFalse(expired.consumeState(expired.state(),
                1000 + PairingAuthorizationSession.MAX_AGE_MILLIS));
        assertFalse(expired.consumeState(expired.state(), 1001));
    }

    @Test public void rejectsPlainPkceAndInvalidContexts() {
        try {
            PairingAuthorizationSession.s256Challenge("short");
            fail("Weak PKCE verifier was accepted");
        } catch (IllegalArgumentException expected) { }
        try {
            PairingAuthorizationSession.begin(-1, new SecureRandom());
            fail("Invalid clock was accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
