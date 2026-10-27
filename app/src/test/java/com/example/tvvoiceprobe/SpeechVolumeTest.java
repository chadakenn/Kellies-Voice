package com.example.tvvoiceprobe;

import static org.junit.Assert.*;
import org.junit.Test;

public class SpeechVolumeTest {
    @Test public void defaultGainIsQuieterThanFullVolume() {
        assertEquals(60, SpeechVolume.DEFAULT_PERCENT);
        assertEquals(0.6f, SpeechVolume.gain(SpeechVolume.DEFAULT_PERCENT), 0.001f);
    }

    @Test public void choicesAreBoundedAndNormalized() {
        assertArrayEquals(new int[]{20, 40, 60, 80, 100}, SpeechVolume.levels());
        for (int level : SpeechVolume.levels()) {
            assertTrue(SpeechVolume.gain(level) > 0.0f);
            assertTrue(SpeechVolume.gain(level) <= 1.0f);
        }
        for (int invalid : new int[]{-100, 0, 1, 21, 61, 101, 1000}) {
            assertEquals(60, SpeechVolume.normalizePercent(invalid));
        }
    }

    @Test public void levelsMapToAndroidTtsGainWithoutChangingSystemVolume() {
        assertEquals(0.2f, SpeechVolume.gain(20), 0.001f);
        assertEquals(0.4f, SpeechVolume.gain(40), 0.001f);
        assertEquals(0.8f, SpeechVolume.gain(80), 0.001f);
        assertEquals(1.0f, SpeechVolume.gain(100), 0.001f);
        int[] levels = SpeechVolume.levels();
        levels[0] = 100; // Consumers must not mutate the menu options.
        assertEquals(20, SpeechVolume.levels()[0]);
    }
}
