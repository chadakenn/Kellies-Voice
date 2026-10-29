package com.example.tvvoiceprobe;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.Assert.*;

/**
 * Test private receipt's replay/identity and lifetime rules without opening
 * a real WebSocket. Only the production probe can mint one after verified
 * Assist and scoped-TV-alert socket outcomes.
 */
public class PairingVerifiedCapabilityReceiptTest {
    private static final String TV = "123e4567-e89b-42d3-a456-426614174000";
    private static final String OTHER = "223e4567-e89b-42d3-a456-426614174000";
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";

    private static PairingAssistConnectionProbe.VerifiedCapabilityReceipt fixture(long issued)
            throws Exception {
        // Private constructor is accessed only by this JVM test; production
        // cannot construct a receipt from an OAuth callback, JSON or boolean.
        Constructor<PairingAssistConnectionProbe.VerifiedCapabilityReceipt> ctor =
                PairingAssistConnectionProbe.VerifiedCapabilityReceipt.class
                        .getDeclaredConstructor(String.class, String.class, long.class);
        assertTrue(Modifier.isPrivate(ctor.getModifiers()));
        ctor.setAccessible(true);
        return ctor.newInstance(TV, SESSION, issued);
    }

    @Test public void exactBoundReceiptIsSingleUse() throws Exception {
        PairingAssistConnectionProbe.VerifiedCapabilityReceipt ticket = fixture(10_000);
        assertFalse(ticket.claim(OTHER, SESSION, 10_010));
        assertFalse(ticket.claim(TV, "AQIDBAUGBwgJCgsMDQ4PEQ", 10_010));
        assertTrue(ticket.claim(TV, SESSION, 10_011));
        assertFalse(ticket.claim(TV, SESSION, 10_012));
    }

    @Test public void monotonicDeadlineAndFutureIssueFailClosed() throws Exception {
        assertFalse(fixture(10_000).claim(TV, SESSION, 9_999));
        assertFalse(fixture(10_000).claim(TV, SESSION, 25_000));
        assertTrue(fixture(10_000).claim(TV, SESSION, 24_999));
        assertFalse(fixture(10_000).claim(null, SESSION, 10_010));
        assertFalse(fixture(10_000).claim(TV, null, 10_010));
    }
}
