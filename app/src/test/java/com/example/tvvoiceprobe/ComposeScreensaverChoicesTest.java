package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure checks for the validated options exposed by Compose screensaver Settings. */
public class ComposeScreensaverChoicesTest {
    @Test public void dashboardSizeChoicesAreAccepted() {
        for (String size : new String[] {"small", "medium", "large"}) {
            assertEquals(size, ScreensaverDashboard.sizeKey(size));
            assertFalse(ScreensaverDashboard.sizeLabel(size).isEmpty());
        }
    }

    @Test public void liveGridModesAreExplicitAndSafeByDefault() {
        assertEquals(ScreensaverLiveMode.SNAPSHOTS, ScreensaverLiveMode.normalize(null));
        assertEquals(ScreensaverLiveMode.SNAPSHOTS, ScreensaverLiveMode.normalize("unknown"));
        for (String mode : new String[] {
                ScreensaverLiveMode.SNAPSHOTS, ScreensaverLiveMode.ONE,
                ScreensaverLiveMode.TWO, ScreensaverLiveMode.FOUR}) {
            assertEquals(mode, ScreensaverLiveMode.normalize(mode));
        }
        assertEquals(0, ScreensaverLiveMode.streamLimit(ScreensaverLiveMode.FOUR,
                LiveCameraMode.SNAPSHOT, 4));
        assertEquals(1, ScreensaverLiveMode.streamLimit(ScreensaverLiveMode.ONE,
                LiveCameraMode.SOFTWARE, 4));
    }

    @Test public void layoutsUseKnownHomeAssistantKeys() {
        for (String key : new String[] {
                HaLayout.SCREENSAVER_ENABLED, HaLayout.SCREENSAVER_SIZE,
                HaLayout.SCREENSAVER_PAGE_LAYOUT, HaLayout.SCREENSAVER_FIT,
                HaLayout.SCREENSAVER_LIVE}) {
            assertTrue(HaLayout.known(key));
            assertTrue(HaLayout.isScreensaver(key));
        }
    }
}
