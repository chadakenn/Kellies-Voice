package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MicSignalHealthTest {
    @Test public void flatlineWarnsOnceAfterThreeSeconds() {
        MicSignalHealth signal = new MicSignalHealth();
        short[] digitalZero = new short[1600];
        for (int i = 0; i < 29; i++) {
            assertFalse(signal.observe(digitalZero, digitalZero.length, 16000));
        }
        assertTrue(signal.observe(digitalZero, digitalZero.length, 16000));
        assertFalse(signal.observe(digitalZero, digitalZero.length, 16000));
    }

    @Test public void realSignalResetsConsecutiveFlatlineTime() {
        MicSignalHealth signal = new MicSignalHealth();
        short[] silent = new short[1600];
        short[] speech = new short[1600];
        speech[5] = 120;
        for (int i = 0; i < 29; i++) {
            assertFalse(signal.observe(silent, silent.length, 16000));
        }
        assertFalse(signal.observe(speech, speech.length, 16000));
        assertFalse(signal.observe(silent, silent.length, 16000));
    }

    @Test public void invalidInputDoesNotWarn() {
        MicSignalHealth signal = new MicSignalHealth();
        assertFalse(signal.observe(null, 0, 16000));
        assertFalse(signal.observe(new short[10], 10, 0));
    }
}
