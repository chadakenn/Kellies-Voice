package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpeechPauseTest {
    @Test public void serverVadGetsTheFirstQuietSecond() {
        assertFalse(SpeechPause.fallback(true, 2000L, 3000L, "normal"));
        assertFalse(SpeechPause.fallback(true, 2000L, 4000L, "tv"));
        assertFalse(SpeechPause.fallback(false, 8000L, 9000L, "quiet"));
    }

    @Test public void localDetectorIsOnlyTheLongFallback() {
        assertTrue(SpeechPause.fallback(true, 5000L, 6000L, "normal"));
        assertTrue(SpeechPause.fallback(true, 6500L, 8000L, "tv"));
        assertTrue(SpeechPause.fallback(true, 4000L, 5000L, "quiet"));
        assertFalse(SpeechPause.fallback(true, 5000L, 1000L, "normal"));
    }
}
