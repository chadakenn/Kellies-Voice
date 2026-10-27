package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreensaverDashboardTest {
    @Test public void overlayAppearsOnlyForAnArmedScreensaver() {
        assertTrue(ScreensaverDashboard.showOverlay(true, true, false, false, true, true));
        assertFalse(ScreensaverDashboard.showOverlay(false, true, false, false, true, true));
        assertFalse(ScreensaverDashboard.showOverlay(true, false, false, false, true, true));
        assertFalse(ScreensaverDashboard.showOverlay(true, true, true, false, true, true));
        assertFalse(ScreensaverDashboard.showOverlay(true, true, false, true, true, true));
        assertFalse(ScreensaverDashboard.showOverlay(true, true, false, false, false, true));
        assertFalse(ScreensaverDashboard.showOverlay(true, true, false, false, true, false));
    }

    @Test public void anEarlyDreamExitDoesNotRemoveTheDashboard() {
        assertTrue(ScreensaverDashboard.keepAfterEarlyStop(1_000, 1_400));
        assertFalse(ScreensaverDashboard.keepAfterEarlyStop(1_000, 3_000));
        assertFalse(ScreensaverDashboard.keepAfterEarlyStop(0, 100));
    }

    @Test public void desktopWebViewUsesInstalledChromeVersionAndSafeStaticViewport() {
        String mobile = "Mozilla/5.0 (Linux; Android 14; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/141.0.7390.0 Mobile Safari/537.36";
        String desktop = ScreensaverDashboard.desktopUserAgent(mobile);
        assertTrue(desktop.contains("X11; Linux x86_64"));
        assertTrue(desktop.contains("Chrome/141.0.7390.0"));
        assertFalse(desktop.contains("Android"));
        assertFalse(desktop.contains("Mobile"));
        assertEquals("unknown browser", ScreensaverDashboard.desktopUserAgent("unknown browser"));
        assertTrue(ScreensaverDashboard.desktopViewportScript().contains("width=1920"));
        assertFalse(ScreensaverDashboard.desktopViewportScript().contains("token"));
    }

    @Test public void fitPresetsChangeViewportAndFallbackSafely() {
        assertEquals(1920, ScreensaverDashboard.viewportWidth("Fit more cameras"));
        assertEquals(1440, ScreensaverDashboard.viewportWidth("Balanced"));
        assertEquals(1200, ScreensaverDashboard.viewportWidth("Larger text"));
        assertEquals(1920, ScreensaverDashboard.viewportWidth("bad"));
        assertTrue(ScreensaverDashboard.desktopViewportScript(1920).contains("width=1920"));
        assertTrue(ScreensaverDashboard.desktopViewportScript(1440).contains("width=1440"));
        assertTrue(ScreensaverDashboard.desktopViewportScript(1200).contains("width=1200"));
        assertTrue(ScreensaverDashboard.desktopViewportScript(-1).contains("width=1920"));
    }

    @Test public void pageAddressRejectsTokensAndStrangeSchemes() {
        assertEquals("http://192.168.1.20:5000/",
                ScreensaverDashboard.dashboardUrl(" http://192.168.1.20:5000/ "));
        assertEquals("https://frigate.home/birdseye",
                ScreensaverDashboard.dashboardUrl("https://frigate.home/birdseye"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("javascript:alert(1)"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("http://user:pass@192.168.1.20/"));
        assertEquals("", ScreensaverDashboard.dashboardUrl(
                "http://192.168.1.20:5000/?token=secret"));
        assertEquals("", ScreensaverDashboard.dashboardUrl(
                "https://ha.local/lovelace/cameras#access_token=abc"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("file:///sdcard/index.html"));
        assertEquals("", ScreensaverDashboard.dashboardUrl("http://example.com/frigate"));
        assertEquals("https://example.com/frigate",
                ScreensaverDashboard.dashboardUrl("https://example.com/frigate"));
    }

    @Test public void navigationStaysOnTheSameFrigateHost() {
        assertTrue(ScreensaverDashboard.sameSite(
                "http://192.168.1.20:5000/", "http://192.168.1.20:5000/cameras/driveway"));
        assertFalse(ScreensaverDashboard.sameSite(
                "http://192.168.1.20:5000/", "http://192.168.1.20:8123/"));
        assertFalse(ScreensaverDashboard.sameSite(
                "http://192.168.1.20:5000/", "https://evil.example/"));
        assertFalse(ScreensaverDashboard.sameSite(
                "http://192.168.1.20:5000/", "http://192.168.1.20:5000/?token=abc"));
    }

    @Test public void cameraListKeepsFourRealEntities() {
        assertEquals("camera.driveway,camera.doorbell",
                ScreensaverDashboard.cameras(" camera.driveway, nope, camera.doorbell "));
        assertEquals("camera.a,camera.b,camera.c,camera.d",
                ScreensaverDashboard.cameras("camera.a camera.b camera.c camera.d camera.e"));
        assertEquals(0, ScreensaverDashboard.cameraList("sensor.person").length);
        assertFalse(ScreensaverDashboard.configured("", "nope"));
        assertTrue(ScreensaverDashboard.configured("http://10.0.0.8:5000/", ""));
        assertTrue(ScreensaverDashboard.configured("", "camera.doorbell"));
    }

    @Test public void sizeAndCameraEditsStayBounded() {
        assertEquals("small", ScreensaverDashboard.sizeKey("Small"));
        assertEquals("medium", ScreensaverDashboard.sizeKey("medium"));
        assertEquals("large", ScreensaverDashboard.sizeKey("Large"));
        assertEquals("", ScreensaverDashboard.sizeKey("Huge"));
        assertEquals("Small", ScreensaverDashboard.sizeLabel("small"));
        assertEquals("Large", ScreensaverDashboard.sizeLabel("nope"));
        assertEquals(0.48f, ScreensaverDashboard.fraction("small"), 0.001f);
        assertEquals(0.72f, ScreensaverDashboard.fraction("medium"), 0.001f);
        assertEquals(1f, ScreensaverDashboard.fraction("large"), 0.001f);
        assertEquals("", ScreensaverDashboard.cameras("none"));
    }
}
