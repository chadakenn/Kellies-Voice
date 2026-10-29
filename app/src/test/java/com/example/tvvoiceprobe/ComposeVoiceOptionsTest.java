package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Safe choice validation; none of these tests opens a microphone. */
public class ComposeVoiceOptionsTest {
    @Test public void followUpTimeoutChoicesMatchLegacy() {
        for (int seconds : new int[] {5, 8, 10, 15}) {
            assertEquals(seconds, FollowUpSession.seconds(seconds + " seconds"));
            assertEquals(seconds + " seconds", FollowUpSession.label(seconds));
        }
        assertEquals(8, FollowUpSession.DEFAULT_SECONDS);
        assertEquals(-1, FollowUpSession.seconds("unrecognized"));
        assertTrue(HaLayout.known(HaLayout.FOLLOW_TIMEOUT));
    }

    @Test public void sensitivityKeysAndLabelsMatchLegacy() {
        for (String key : new String[] {
                OverlayPreferences.NOISE_QUIET,
                OverlayPreferences.NOISE_NORMAL,
                OverlayPreferences.NOISE_TV}) {
            assertFalse(OverlayPreferences.noiseLabel(key).isEmpty());
        }
        assertTrue(HaLayout.known(HaLayout.NOISE));
        assertFalse(FollowUpSession.remoteTimeoutAllowed(false));
    }
}
