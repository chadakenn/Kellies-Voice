package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class WakeDuckingLevelsTest {
    @Test public void onlyExplicitLevelsAreAccepted() {
        assertArrayEquals(new int[] {0, 25, 50, 75}, WakeDuckingLevels.levels());
        for (int percent : WakeDuckingLevels.levels()) {
            assertEquals(percent, WakeDuckingLevels.normalize(percent));
        }
        assertEquals(0, WakeDuckingLevels.normalize(-1));
        assertEquals(0, WakeDuckingLevels.normalize(101));
        assertEquals(0, WakeDuckingLevels.normalize(33));
    }

    @Test public void percentIsReductionNotTargetPercent() {
        assertEquals(20, WakeDuckingLevels.target(20, 0));
        assertEquals(15, WakeDuckingLevels.target(20, 25));
        assertEquals(10, WakeDuckingLevels.target(20, 50));
        assertEquals(5, WakeDuckingLevels.target(20, 75));
        assertEquals(20, WakeDuckingLevels.target(20, 99));
    }

    @Test public void mutedOrLowVolumeNeverBecomesLouder() {
        assertEquals(0, WakeDuckingLevels.target(0, 75));
        assertEquals(1, WakeDuckingLevels.target(1, 75));
        assertEquals(1, WakeDuckingLevels.target(2, 75));
        assertEquals(0, WakeDuckingLevels.target(-5, 75));
    }

    @Test public void readingLevelListCannotChangeAllowedValues() {
        int[] modified = WakeDuckingLevels.levels();
        modified[0] = 99;
        assertEquals(0, WakeDuckingLevels.levels()[0]);
    }
}
