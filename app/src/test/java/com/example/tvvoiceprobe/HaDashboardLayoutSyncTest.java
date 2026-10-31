package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Explicit HA device switch and existing dashboard URL use the same
 * screensaver setting pipeline. No Activity, HA server or microphone needed.
 */
public class HaDashboardLayoutSyncTest {
    @Test public void acceptsPerTvDashboardControlsAsScreensaverSettings() {
        assertTrue(HaLayout.known(HaLayout.SCREENSAVER_URL));
        assertTrue(HaLayout.isScreensaver(HaLayout.SCREENSAVER_URL));
        assertTrue(HaLayout.known(HaLayout.SCREENSAVER_HA_AUTH));
        assertTrue(HaLayout.isScreensaver(HaLayout.SCREENSAVER_HA_AUTH));
        assertFalse(HaLayout.changesPanel(HaLayout.SCREENSAVER_HA_AUTH));
    }

    @Test public void untrustedSettingCannotBeAccepted() {
        assertFalse(HaLayout.known("screensaver_auth_token"));
        assertFalse(HaLayout.isScreensaver("screensaver_auth_token"));
        assertFalse(HaLayout.isScreensaver("open_external_url"));
    }
}
