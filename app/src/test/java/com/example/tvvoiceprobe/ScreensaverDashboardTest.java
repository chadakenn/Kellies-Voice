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

    @Test public void haDashboardDoesNotInheritFrigateDesktopScaling() {
        String haLocal = "http://homeassistant.local:8123/";
        String haNumeric = "http://192.168.1.25:8123/lovelace/security";
        String haSecure = "https://my-home.example.com/dashboard-home";
        String frigate = "http://192.168.1.31:5000/";
        assertTrue(ScreensaverDashboard.isHomeAssistantPage(haLocal, false));
        assertTrue(ScreensaverDashboard.isHomeAssistantPage(haNumeric, false));
        assertTrue(ScreensaverDashboard.isHomeAssistantPage(haSecure, true));
        assertFalse(ScreensaverDashboard.isHomeAssistantPage(haSecure, false));
        assertFalse(ScreensaverDashboard.isHomeAssistantPage(frigate, false));
        assertFalse(ScreensaverDashboard.isHomeAssistantPage("javascript:alert(1)", false));
        assertFalse(ScreensaverDashboard.useFrigateDesktopViewport(haLocal, true, false));
        assertFalse(ScreensaverDashboard.useFrigateDesktopViewport(haNumeric, true, false));
        assertFalse(ScreensaverDashboard.useFrigateDesktopViewport(haSecure, true, true));
        assertFalse(ScreensaverDashboard.useFrigateDesktopViewport(frigate, false, false));
        assertTrue(ScreensaverDashboard.useFrigateDesktopViewport(frigate, true, false));
    }

    @Test public void haLoadingChecksConfiguredRouteWithoutExposingAuthQuery() {
        String script = ScreensaverDashboard.dashboardReadyScript(
                "http://192.168.1.42:8123/tv-wall/wall?kiosk=true&auth_code=never_print");
        assertTrue(script.contains("tv-wall/wall"));
        assertTrue(script.contains("location.pathname"));
        assertTrue(script.contains("document.readyState"));
        assertTrue(script.contains("home-assistant"));
        assertFalse(script.contains("auth_code"));
        assertFalse(script.contains("never_print"));
        assertFalse(script.contains("192.168.1.42"));
        assertTrue(ScreensaverDashboard.dashboardReadyScript(
                "http://192.168.1.42:8123/").contains("expected="));
    }

    @Test public void triggerDashboardPathsStayOnTheConfiguredHaServer() {
        String base = "http://192.168.1.42:8123/mobile-command/home?kiosk";
        assertEquals("http://192.168.1.42:8123/mobile-command/bedroom?kiosk",
                ScreensaverDashboard.dashboardForPath(base, "/mobile-command/bedroom"));
        assertEquals("http://192.168.1.42:8123/dashboard-cameras/front?kiosk",
                ScreensaverDashboard.dashboardForPath(base, "/dashboard-cameras/front"));
        assertEquals(base, ScreensaverDashboard.dashboardForPath(base, ""));
        assertTrue(ScreensaverDashboard.validDashboardPath("/mobile-command/cameras"));
        assertFalse(ScreensaverDashboard.validDashboardPath("https://evil.example/"));
        assertFalse(ScreensaverDashboard.validDashboardPath("//evil.example"));
        assertFalse(ScreensaverDashboard.validDashboardPath("/../admin"));
        assertFalse(ScreensaverDashboard.validDashboardPath("/%2F"));
        assertFalse(ScreensaverDashboard.validDashboardPath("/dashboard?token=x"));
        assertFalse(ScreensaverDashboard.validDashboardPath("/dashboard#fragment"));
        assertFalse(ScreensaverDashboard.validDashboardPath(null));
        assertEquals("", ScreensaverDashboard.dashboardForPath(base, "//evil.example"));
        assertEquals("", ScreensaverDashboard.dashboardForPath(base, "/../admin"));
        assertEquals("", ScreensaverDashboard.dashboardForPath("javascript:alert(1)", "/bedroom"));
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
        assertEquals("", ScreensaverDashboard.dashboardUrl(""));
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

    @Test public void passwordlessHaLoginRedirectStaysInsideExactOrigin() {
        String configured = "http://192.168.1.82:8123/mobile-command/home";
        // Owner-supplied dashboard URLs must remain secret-free.
        assertEquals("", ScreensaverDashboard.dashboardUrl(
                configured + "?access_token=not-for-settings"));
        // But the *browser* must be free to follow HA's transient local
        // authentication callback without logging or stripping its parameters.
        assertTrue(ScreensaverDashboard.sameOriginNavigation(configured,
                "http://192.168.1.82:8123/auth/authorize?client_id=TV&redirect_uri=%2F"));
        assertTrue(ScreensaverDashboard.sameOriginNavigation(configured,
                "http://192.168.1.82:8123/mobile-command/home?auth_callback=1&access_token=temporary"));
        assertTrue(ScreensaverDashboard.sameOriginNavigation(configured,
                "http://192.168.1.82:8123/auth/flow#access_token=temporary"));
        assertTrue(ScreensaverDashboard.sameOriginNavigation(configured,
                "HTTP://192.168.1.82:8123/auth/token?code=temporary"));
        // No scheme, host, port, userinfo or origin widening.
        for (String unsafe : new String[] {
                "https://192.168.1.82:8123/auth/token?code=temporary",
                "http://192.168.1.83:8123/auth/token?code=temporary",
                "http://192.168.1.82:8124/auth/token?code=temporary",
                "http://192.168.1.82:8123.evil.example/auth/token",
                "http://other.example:8123/auth/token",
                "http://user:pass@192.168.1.82:8123/auth/token",
                "javascript:alert(1)", "intent://192.168.1.82:8123",
                "file:///data/local/tmp/auth", "about:blank",
                "http://192.168.1.82:8123/other\\\\path",
                "http://192.168.1.82:8123/other\npath",
                "http://192.168.1.82:8123/other%ZZ"
        }) {
            assertFalse(unsafe, ScreensaverDashboard.sameOriginNavigation(configured, unsafe));
        }
        assertFalse(ScreensaverDashboard.sameOriginNavigation(
                configured + "?token=forbidden", configured));
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

    @Test public void httpLanDashboardIsAcceptedButNeverCarriesSecrets() {
        String http = "http://192.168.1.10:8123/lovelace/wall?kiosk";
        assertEquals(http, ScreensaverDashboard.dashboardUrl(http));
        assertTrue(ScreensaverDashboard.sameSite(http, "http://192.168.1.10:8123/auth/authorize"));
        assertFalse(ScreensaverDashboard.sameSite(http, "https://192.168.1.10:8123/lovelace"));
        assertFalse(ScreensaverDashboard.sameSite(http, "http://192.168.1.11:8123/lovelace"));
        assertFalse(ScreensaverDashboard.sameSite(http, "http://192.168.1.10:8124/lovelace"));
        // Token in URL is rejected
        assertEquals("", ScreensaverDashboard.dashboardUrl(
                "http://192.168.1.10:8123/lovelace?token=secret"));
    }

    @Test public void authenticatedUrlNeverDowngradesToHttpExternalAuth() {
        String httpPage = "http://192.168.1.10:8123/lovelace/wall";
        String httpsHa = "https://ha.example.org:8123";
        assertEquals("", HaDashboardAuthPolicy.authenticatedUrl(httpPage, httpsHa));
        assertEquals("", HaDashboardAuthPolicy.authenticatedUrl(httpPage, httpPage));
        // HTTPS same-origin still works
        assertTrue(HaDashboardAuthPolicy.authenticatedUrl(
                "https://ha.example.org:8123/lovelace", httpsHa).contains("external_auth=1"));
    }
}
