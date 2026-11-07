package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnergyVadTest {
    @Test public void silenceAfterSpeechEndsTheTurn() {
        EnergyVad vad = new EnergyVad();
        byte[] loud = tone(2000);
        byte[] quiet = silence();
        assertFalse(vad.observe(loud, loud.length, "normal"));
        assertFalse(vad.observe(loud, loud.length, "normal"));
        boolean ended = false;
        for (int i = 0; i < 70 && !ended; i++) ended = vad.observe(quiet, quiet.length, "normal");
        assertTrue(ended);
    }

    @Test public void aTwoSecondPauseDoesNotEndNormalSpeech() {
        EnergyVad vad = new EnergyVad();
        byte[] loud = tone(2000);
        vad.observe(loud, loud.length, "normal");
        vad.observe(loud, loud.length, "normal");
        for (int i = 0; i < 20; i++) {
            assertFalse(vad.observe(silence(), silence().length, "normal"));
        }
    }

    @Test public void wakeLeadCountsAsSpeechAndDoesNotCutOffEarly() {
        EnergyVad vad = new EnergyVad();
        vad.noteLead(2400L);
        for (int i = 0; i < 20; i++) {
            assertFalse(vad.observe(silence(), silence().length, "tv"));
        }
    }

    @Test public void speakingAgainAfterLongPauseResetsTheFallback() {
        EnergyVad vad = new EnergyVad();
        byte[] voice = tone(2000);
        byte[] quiet = silence();
        vad.observe(voice, voice.length, "normal");
        vad.observe(voice, voice.length, "normal");
        for (int i = 0; i < 45; i++) {
            assertFalse(vad.observe(quiet, quiet.length, "normal"));
        }
        assertFalse(vad.observe(voice, voice.length, "normal"));
        for (int i = 0; i < 49; i++) {
            assertFalse(vad.observe(quiet, quiet.length, "normal"));
        }
        assertTrue(vad.observe(quiet, quiet.length, "normal"));
    }

    @Test public void twoBufferedLeadsBothCountTowardElapsedSpeech() {
        EnergyVad vad = new EnergyVad();
        vad.noteLead(2400L);
        vad.noteLead(400L);
        byte[] quiet = silence();
        // TV mode needs 6.5 seconds of continuous quiet after a buffered command.
        for (int i = 0; i < 64; i++) {
            assertFalse(vad.observe(quiet, quiet.length, "tv"));
        }
        assertTrue(vad.observe(quiet, quiet.length, "tv"));
    }

    @Test public void noSpeechNeverAutoEndsAndQuietRoomGetsFourSeconds() {
        EnergyVad silent = new EnergyVad();
        byte[] quiet = silence();
        for (int i = 0; i < 90; i++) {
            assertFalse(silent.observe(quiet, quiet.length, "quiet"));
        }
        EnergyVad spoken = new EnergyVad();
        spoken.noteLead(400L);
        for (int i = 0; i < 39; i++) {
            assertFalse(spoken.observe(quiet, quiet.length, "quiet"));
        }
        assertTrue(spoken.observe(quiet, quiet.length, "quiet"));
    }

    @Test public void televisionCalibrationDoesNotEndImmediately() {
        EnergyVad vad = new EnergyVad();
        assertFalse(vad.observe(tone(8000), 640, "tv"));
    }

    private static byte[] silence() {
        return new byte[3200];
    }

    private static byte[] tone(int amplitude) {
        byte[] pcm = new byte[3200];
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            pcm[i] = (byte) amplitude;
            pcm[i + 1] = (byte) (amplitude >> 8);
        }
        return pcm;
    }
}
