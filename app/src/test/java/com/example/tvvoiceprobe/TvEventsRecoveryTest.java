package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TvEventsRecoveryTest {
    @org.junit.Test public void deniedAlertsShowActionablePermissionsError() {
        String text = TvEventsRecovery.describe(true, true, true, TvEventsRecovery.DENIED);
        org.junit.Assert.assertTrue(text.contains("denied"));
        org.junit.Assert.assertTrue(text.contains("read access"));
        org.junit.Assert.assertFalse(text.contains("must be an administrator"));
    }

    @org.junit.Test public void missingScopedIntegrationShowsUpdateInstruction() {
        String text = TvEventsRecovery.describe(true, true, true, TvEventsRecovery.UPDATE_HACS);
        org.junit.Assert.assertTrue(text.contains("Update"));
    }

    @Test public void onlyBootAndUpdateIntentsCanResumeAlerts() {
        assertTrue(TvEventsRecovery.accepts(TvEventsRecovery.BOOT));
        assertTrue(TvEventsRecovery.accepts(TvEventsRecovery.REPLACED));
        assertFalse(TvEventsRecovery.accepts(null));
        assertFalse(TvEventsRecovery.accepts("android.intent.action.BOOT_COMPLETED.extra"));
        assertFalse(TvEventsRecovery.accepts("android.intent.action.SCREEN_ON"));
        assertFalse(TvEventsRecovery.accepts("com.example.tvvoiceprobe.TV_EVENTS_START"));
    }

    @Test public void alertsStayOffUntilTheOwnerAlreadyOptedIn() {
        assertEquals(TvEventsRecovery.SKIP, TvEventsRecovery.decide(false, true, true));
        assertEquals(TvEventsRecovery.SKIP, TvEventsRecovery.decide(false, false, false));
        assertEquals(TvEventsRecovery.NEEDS_USER, TvEventsRecovery.decide(true, false, true));
        assertEquals(TvEventsRecovery.NEEDS_USER, TvEventsRecovery.decide(true, true, false));
        assertEquals(TvEventsRecovery.START, TvEventsRecovery.decide(true, true, true));
    }

    @Test public void blockedMessageDoesNotAskToTurnTheMicrophoneOn() {
        String signedOut = TvEventsRecovery.blockedMessage(true, false);
        String noOverlay = TvEventsRecovery.blockedMessage(false, true);
        String blocked = TvEventsRecovery.blockedMessage(true, true);
        assertTrue(signedOut.contains("Home Assistant"));
        assertTrue(noOverlay.contains("display over other apps"));
        assertTrue(blocked.contains("Open Kellie's Voice"));
        assertFalse(signedOut.toLowerCase().contains("microphone"));
        assertFalse(noOverlay.toLowerCase().contains("microphone"));
        assertFalse(blocked.toLowerCase().contains("microphone"));
    }

    @Test public void statusExplainsReconnectWithoutReplayingOldEvents() {
        String waiting = TvEventsRecovery.describe(true, true, true, "");
        String reconnecting = TvEventsRecovery.describe(true, true, true, TvEventsRecovery.RECONNECTING);
        assertTrue(waiting.contains("open Kellie's Voice"));
        assertTrue(reconnecting.contains("not replayed"));
        assertEquals("Off. TV alerts stay off until you turn them on.",
                TvEventsRecovery.describe(false, true, true, TvEventsRecovery.CONNECTED));
        assertTrue(TvEventsRecovery.describe(true, true, false, TvEventsRecovery.CONNECTED)
                .contains("Signed out"));
    }
}
