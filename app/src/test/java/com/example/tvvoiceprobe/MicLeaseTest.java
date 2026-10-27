package com.example.tvvoiceprobe;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MicLeaseTest {
    @After public void clear() { MicLease.reset(); }

    @Test public void userCaptureTakesTheMicrophoneFromWakeImmediately() {
        assertTrue(MicLease.acquire(MicLease.WAKE));
        assertTrue(MicLease.acquire(MicLease.TALK));
        assertEquals(MicLease.TALK, MicLease.holder());
        assertTrue(MicLease.heldByOther(MicLease.WAKE));
        assertFalse(MicLease.acquire(MicLease.WAKE));
        MicLease.release(MicLease.TALK);
        assertNull(MicLease.holder());
        assertTrue(MicLease.acquire(MicLease.WAKE));
    }

    @Test public void ownersDoNotStealFromEachOther() {
        assertTrue(MicLease.acquire(MicLease.OVERLAY));
        assertFalse(MicLease.acquire(MicLease.TALK));
        assertFalse(MicLease.acquire(MicLease.DIAGNOSTICS));
        MicLease.release(MicLease.TALK);
        assertEquals(MicLease.OVERLAY, MicLease.holder());
        MicLease.release(MicLease.OVERLAY);
        assertTrue(MicLease.acquire(MicLease.DIAGNOSTICS));
    }
}
