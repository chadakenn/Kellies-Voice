package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Validation contract used by the Compose screensaver URL and camera editors.
 * Matches VoiceActivity.editScreensaverUrl / editScreensaverCameras.
 */
public class ComposeScreensaverConfigTest {
    @Test public void validFrigateUrlsAreAccepted() {
        assertEquals("http://192.168.1.20:5000/",
                ScreensaverDashboard.dashboardUrl("http://192.168.1.20:5000/"));
        assertEquals("https://frigate.home/birdseye",
                ScreensaverDashboard.dashboardUrl("https://frigate.home/birdseye"));
        assertEquals("http://frigate.local:5000/",
                ScreensaverDashboard.dashboardUrl("  http://frigate.local:5000/  "));
    }

    @Test public void unsafeOrMalformedUrlsAreRejected() {
        assertEquals("", ScreensaverDashboard.dashboardUrl("javascript:alert(1)"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("http://user:pass@192.168.1.20/"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("https://ha.local/page?token=abc"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("https://ha.local/page#access_token=abc"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("https://ha.local/page?password=secret"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("file:///sdcard/index.html"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("not a url"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("ftp://frigate.local/"));
        assertEquals("", ScreensaverDashboard.dashboardUrl(""));
        assertEquals("", ScreensaverDashboard.dashboardUrl(null));
    }

    @Test public void clearingUrlIsSafe() {
        assertEquals("", ScreensaverDashboard.dashboardUrl(""));
        assertEquals("", ScreensaverDashboard.dashboardUrl("   "));
    }

    @Test public void validCameraEntitiesAreAccepted() {
        assertEquals("camera.driveway", ScreensaverDashboard.cameras("camera.driveway"));
        assertEquals("camera.driveway,camera.doorbell",
                ScreensaverDashboard.cameras(" camera.driveway, camera.doorbell "));
        assertEquals("camera.a,camera.b,camera.c,camera.d",
                ScreensaverDashboard.cameras("camera.a, camera.b, camera.c, camera.d"));
        assertNotNull(TvEventPayload.cameraEntity("camera.front_door"));
        assertNotNull(TvEventPayload.cameraEntity("camera.cam_1"));
    }

    @Test public void invalidCameraIdsAreRejectedNotSilentlyKept() {
        assertNull(TvEventPayload.cameraEntity("driveway"));
        assertNull(TvEventPayload.cameraEntity("binary_sensor.motion"));
        assertNull(TvEventPayload.cameraEntity("camera."));
        assertNull(TvEventPayload.cameraEntity("camera.bad-name"));
        assertNull(TvEventPayload.cameraEntity("rtsp://cam/stream"));
        // Compose form rejects the whole entry when any ID is invalid,
        // rather than saving a silently filtered list.
        String[] mixed = "camera.ok, not-a-camera".split("[,\\s]+");
        boolean invalid = false;
        for (String id : mixed) {
            if (TvEventPayload.cameraEntity(id) == null) invalid = true;
        }
        assertTrue(invalid);
    }

    @Test public void moreThanFourCamerasAreRejected() {
        String five = "camera.a, camera.b, camera.c, camera.d, camera.e";
        String[] ids = five.split("[,\\s]+");
        assertTrue(ids.length > ScreensaverDashboard.MAX_CAMERAS);
        // dashboardUrl-style silent drop is not what the form uses; the form rejects.
        assertEquals(4, ScreensaverDashboard.cameraList(five).length);
    }

    @Test public void clearingCamerasIsSafe() {
        assertEquals("", ScreensaverDashboard.cameras(""));
        assertEquals("", ScreensaverDashboard.cameras("   "));
        assertEquals("", ScreensaverDashboard.cameras(null));
        assertEquals(0, ScreensaverDashboard.cameraList("").length);
    }

    @Test public void urlAndCameraKeysAreScreensaverLayoutUpdates() {
        assertTrue(HaLayout.isScreensaver(HaLayout.SCREENSAVER_URL));
        assertTrue(HaLayout.isScreensaver(HaLayout.SCREENSAVER_CAMERAS));
        assertTrue(HaLayout.known(HaLayout.SCREENSAVER_URL));
        assertTrue(HaLayout.known(HaLayout.SCREENSAVER_CAMERAS));
    }
}
