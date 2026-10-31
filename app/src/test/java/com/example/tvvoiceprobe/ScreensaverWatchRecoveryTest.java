package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreensaverWatchRecoveryTest {
    @Test public void featureOffSkipsAndDoesNotRecover() {
        assertFalse(ScreensaverWatchRecovery.decideStart(false, false));
        assertEquals(ScreensaverWatchRecovery.RESULT_SKIPPED,
                ScreensaverWatchRecovery.decideRecovery(false, false));
        assertEquals(ScreensaverWatchRecovery.RESULT_SKIPPED,
                ScreensaverWatchRecovery.decideRecovery(false, true));
    }

    @Test public void screensaverOrHaManageOnAttemptsStart() {
        assertTrue(ScreensaverWatchRecovery.decideStart(true, false));
        assertTrue(ScreensaverWatchRecovery.decideStart(false, true));
        assertTrue(ScreensaverWatchRecovery.decideStart(true, true));
    }

    @Test public void acceptedStartDoesNotPostNotification() {
        assertEquals(ScreensaverWatchRecovery.RESULT_REQUESTED,
                ScreensaverWatchRecovery.decideRecovery(true, true));
    }

    @Test public void deniedStartPostsNotificationRecovery() {
        assertEquals(ScreensaverWatchRecovery.RESULT_NOTIFICATION,
                ScreensaverWatchRecovery.decideRecovery(true, false));
    }

    @Test public void overlayMissingDoesNotBlockHaLayoutStartDecision() {
        // HA layout ON with screensaver OFF still decides to start the service
        // so settings sync can run. Overlay rendering is gated separately.
        assertTrue(ScreensaverWatchRecovery.decideStart(false, true));
    }

    @Test public void bootReceiverMatchesOnlyCurrentBoot() {
        assertTrue(ScreensaverWatchRecovery.bootMatches(42, 42));
        assertFalse(ScreensaverWatchRecovery.bootMatches(41, 42));
        assertFalse(ScreensaverWatchRecovery.bootMatches(-1, 42));
        assertFalse(ScreensaverWatchRecovery.bootMatches(42, -1));
    }

    @Test public void repeatedRebootRequiresFreshReceiverEntry() {
        // After a new boot the stored count no longer matches.
        assertFalse(ScreensaverWatchRecovery.bootMatches(10, 11));
        // After the receiver runs on the new boot it matches again.
        assertTrue(ScreensaverWatchRecovery.bootMatches(11, 11));
    }

    @Test public void startRequestedIsDistinctFromConfirmedRunning() {
        assertNotEquals(ScreensaverWatchRecovery.RESULT_REQUESTED,
                ScreensaverWatchRecovery.RESULT_RUNNING);
    }
}
