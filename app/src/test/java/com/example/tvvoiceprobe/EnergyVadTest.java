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
        for (int i = 0; i < 20 && !ended; i++) ended = vad.observe(quiet, quiet.length, "normal");
        assertTrue(ended);
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
