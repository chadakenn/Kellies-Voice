package com.example.tvvoiceprobe;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MicLeaseTest {
    @After public void clear() { MicLease.reset(); }

    @Test public void idleWakeHandsOffImmediately() {
        assertTrue(MicLease.acquire(MicLease.WAKE));
        assertTrue(MicLease.acquire(MicLease.TALK));
        assertEquals(MicLease.TALK, MicLease.holder());
        assertFalse(MicLease.acquire(MicLease.WAKE));
        MicLease.release(MicLease.TALK);
        assertTrue(MicLease.acquire(MicLease.WAKE));
    }

    @Test public void openWakeRecorderBlocksTakeoverUntilReleaseReturns() {
        assertTrue(MicLease.acquire(MicLease.WAKE));
        MicLease.wakeRecorderOpened();
        assertFalse(MicLease.acquire(MicLease.TALK));
        assertEquals(MicLease.WAKE, MicLease.holder());
        assertTrue(MicLease.wakeShouldStop());
        assertFalse(MicLease.acquire(MicLease.WAKE));
        MicLease.wakeRecorderReleased();
        assertEquals(MicLease.TALK, MicLease.holder());
        assertFalse(MicLease.wakeShouldStop());
        assertTrue(MicLease.awaitHeld(MicLease.TALK, 50));
        assertFalse(MicLease.acquire(MicLease.WAKE));
    }

    @Test public void ownersDoNotStealFromEachOther() {
        assertTrue(MicLease.acquire(MicLease.OVERLAY));
        assertFalse(MicLease.acquire(MicLease.TALK));
        assertFalse(MicLease.isWaiting(MicLease.TALK));
        assertFalse(MicLease.acquire(MicLease.DIAGNOSTICS));
        MicLease.release(MicLease.TALK);
        assertEquals(MicLease.OVERLAY, MicLease.holder());
        MicLease.release(MicLease.OVERLAY);
        assertTrue(MicLease.acquire(MicLease.DIAGNOSTICS));
    }

    @Test public void releaseWithoutAnOpenRecorderDoesNotClearAnotherOwner() {
        MicLease.wakeRecorderOpened();
        assertFalse(MicLease.acquire(MicLease.OVERLAY));
        MicLease.wakeRecorderReleased();
        assertEquals(MicLease.OVERLAY, MicLease.holder());
        MicLease.release(MicLease.WAKE);
        assertEquals(MicLease.OVERLAY, MicLease.holder());
        MicLease.release(MicLease.OVERLAY);
        assertNull(MicLease.holder());
    }
}
