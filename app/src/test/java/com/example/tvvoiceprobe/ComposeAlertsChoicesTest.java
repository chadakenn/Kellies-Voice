package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure validation of the choices offered by Compose, without starting TV services. */
public class ComposeAlertsChoicesTest {
    @Test public void alertPositionsAndSizesAreValid() {
        for (String key : new String[] {
                TvAlertLayout.TOP_LEFT, TvAlertLayout.TOP_RIGHT,
                TvAlertLayout.BOTTOM_LEFT, TvAlertLayout.BOTTOM_RIGHT, TvAlertLayout.CENTER}) {
            assertEquals(key, TvAlertLayout.positionOrNull(key));
            assertNotNull(TvAlertLayout.positionLabel(key));
        }
        for (String key : new String[] {
                TvAlertLayout.SMALL, TvAlertLayout.MEDIUM, TvAlertLayout.LARGE}) {
            assertEquals(key, TvAlertLayout.sizeOrNull(key));
            assertNotNull(TvAlertLayout.sizeLabel(key));
        }
        assertTrue(HaLayout.known(HaLayout.ALERT_POSITION));
        assertTrue(HaLayout.known(HaLayout.ALERT_SIZE));
    }

    @Test public void voiceAlertLevelsAreIsolatedAndValidated() {
        for (int percent : new int[] {20, 40, 60, 80, 100}) {
            assertEquals(percent, SpeechVolume.normalizePercent(percent));
        }
        assertEquals(SpeechVolume.DEFAULT_PERCENT, SpeechVolume.normalizePercent(101));
        assertEquals(SpeechVolume.DEFAULT_PERCENT, SpeechVolume.normalizePercent(-1));
    }

    @Test public void hardwareCameraModeRequiresExplicitSelection() {
        assertEquals(LiveCameraMode.SOFTWARE, LiveCameraMode.normalize(null));
        assertEquals(LiveCameraMode.SOFTWARE, LiveCameraMode.normalize("bad-value"));
        assertEquals(LiveCameraMode.SNAPSHOT, LiveCameraMode.normalize(LiveCameraMode.SNAPSHOT));
        assertEquals(LiveCameraMode.HARDWARE, LiveCameraMode.normalize(LiveCameraMode.HARDWARE));
    }
}
