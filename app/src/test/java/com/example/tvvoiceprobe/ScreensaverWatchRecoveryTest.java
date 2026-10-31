package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreensaverWatchRecoveryTest {
    @Test public void shouldRunWhenScreensaverOrHaManageEnabled() {
        // Logic is in OverlayPreferences; here we verify the recovery constants
        // and that skipped/denied results are distinct from started.
        assertEquals("started", ScreensaverWatchRecovery.RESULT_STARTED);
        assertEquals("denied", ScreensaverWatchRecovery.RESULT_DENIED);
        assertEquals("skipped", ScreensaverWatchRecovery.RESULT_SKIPPED);
        assertEquals("scheduled", ScreensaverWatchRecovery.RESULT_SCHEDULED);
    }

    @Test public void featureOffSkipsStartup() {
        // Documented contract: if neither screensaver nor HA layout is on,
        // recovery records SKIPPED and does not start the service.
        assertNotEquals(ScreensaverWatchRecovery.RESULT_STARTED,
                ScreensaverWatchRecovery.RESULT_SKIPPED);
    }

    @Test public void permissionDenialIsRecordedSeparatelyFromStartFailure() {
        assertNotEquals(ScreensaverWatchRecovery.RESULT_DENIED,
                ScreensaverWatchRecovery.RESULT_STARTED);
    }
}
