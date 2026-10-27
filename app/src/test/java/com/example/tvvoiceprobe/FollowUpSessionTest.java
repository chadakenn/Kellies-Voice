package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FollowUpSessionTest {
    @Test public void followUpStaysOffUntilTheTvOptsIn() {
        FollowUpSession session = new FollowUpSession();
        int token = session.open();
        assertFalse(session.arm(false, token));
        assertFalse(FollowUpSession.remoteTimeoutAllowed(false));
        assertTrue(FollowUpSession.remoteTimeoutAllowed(true));
        assertEquals(8, FollowUpSession.seconds("8 seconds"));
        assertEquals(-1, FollowUpSession.seconds("on"));
        assertEquals(-1, FollowUpSession.seconds("3 seconds"));
        assertEquals("8 seconds", FollowUpSession.label(99));
    }

    @Test public void oldCallbacksCannotRestartTheMicrophone() {
        FollowUpSession session = new FollowUpSession();
        int first = session.open();
        assertTrue(session.arm(true, first));
        int second = session.cancel();
        assertFalse(session.active());
        assertFalse(session.same(first));
        assertFalse(session.arm(true, first));
        assertFalse(session.arm(true, second));
        int reopened = session.open();
        assertTrue(session.arm(true, reopened));
        assertFalse(FollowUpSession.captureAllowed(true, true, true));
        assertFalse(FollowUpSession.captureAllowed(false, true, false));
        assertTrue(FollowUpSession.captureAllowed(true, true, false));
    }

    @Test public void turnCapAndSilenceDetectionStayBounded() {
        FollowUpSession session = new FollowUpSession();
        int token = session.open();
        for (int i = 0; i < FollowUpSession.MAX_TURNS; i++) {
            assertTrue(session.arm(true, token));
            session.markQuestion(token);
        }
        assertFalse(session.arm(true, token));
        byte[] silence = new byte[320];
        byte[] voice = new byte[320];
        for (int i = 0; i < voice.length; i += 2) {
            voice[i] = (byte) 0x00;
            voice[i + 1] = 0x02;
        }
        assertFalse(FollowUpSession.voiced(silence, silence.length, "normal"));
        assertTrue(FollowUpSession.voiced(voice, voice.length, "normal"));
        assertFalse(FollowUpSession.voiced(voice, voice.length, "tv"));
    }

    @Test public void stopAndStalePlaybackCannotRearm() {
        FollowUpSession session = new FollowUpSession();
        int token = session.open();
        int playback = session.bindPlayback();
        session.deferUntilIdle(token);
        int retired = session.cancel();
        assertEquals(0, session.takeDeferred());
        assertFalse(session.playbackCurrent(playback, token));
        assertFalse(session.arm(true, retired));
        int next = session.open();
        assertFalse(session.playbackCurrent(playback, next));
        int rebound = session.bindPlayback();
        assertTrue(session.playbackCurrent(rebound, next));
    }
}
