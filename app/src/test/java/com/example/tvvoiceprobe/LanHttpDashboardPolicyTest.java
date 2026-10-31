package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.*;

/** Strict HTTP LAN viewer: never mistaken for the HTTPS credential bridge. */
public class LanHttpDashboardPolicyTest {
    @Test public void acceptsOnlyExplicitPrivateIpv4HttpHosts() {
        for (String good : new String[] {
                "http://10.0.0.8:8123/lovelace",
                "http://172.16.0.1:8123/",
                "http://172.31.255.254:8123/dashboard?kiosk",
                "http://192.168.1.143:8123/tv-wall/wall?kiosk"
        }) {
            assertTrue(good, LanHttpDashboardPolicy.isStrictLanHttp(good));
        }
    }

    @Test public void rejectsInternetDnsInvalidAndCredentialUrls() {
        for (String bad : new String[] {
                "https://192.168.1.5:8123/",
                "http://8.8.8.8:8123/",
                "http://172.15.1.1:8123/",
                "http://172.32.0.5:8123/",
                "http://100.64.1.3:8123/",
                "http://169.254.1.3:8123/",
                "http://127.0.0.1:8123/",
                "http://0.0.0.0:8123/",
                "http://homeassistant.local:8123/",
                "http://ha.example.net/",
                "http://user:pass@192.168.1.4:8123/",
                "http://192.168.1.3:8123/?token=abc",
                "http://192.168.1.3:8123/?password=abc",
                "http://192.168.1.3:0/",
                "http://192.168.1.3:65536/",
                "http://192.168.001.2:8123/",
                "http://10.0.0.999:8123/",
                "http://192.168.1.1.evil.com:8123/",
                "javascript:alert(1)"
        }) {
            assertFalse(bad, LanHttpDashboardPolicy.isStrictLanHttp(bad));
        }
    }

    @Test public void navigationCannotLeaveSameLanOrigin() {
        String page = "http://192.168.1.2:8123/dashboard?view=tv";
        assertTrue(LanHttpDashboardPolicy.allowedNavigation(page,
                "http://192.168.1.2:8123/lovelace/office"));
        assertFalse(LanHttpDashboardPolicy.allowedNavigation(page,
                "http://192.168.1.3:8123/dashboard"));
        assertFalse(LanHttpDashboardPolicy.allowedNavigation(page,
                "https://192.168.1.2:8123/dashboard"));
        assertFalse(LanHttpDashboardPolicy.allowedNavigation(page,
                "http://192.168.1.2:8124/dashboard"));
        assertFalse(LanHttpDashboardPolicy.allowedNavigation(page,
                "http://evil.example/dashboard"));
        assertFalse(LanHttpDashboardPolicy.allowedNavigation(page,
                "http://192.168.1.2:8123/dashboard?access_token=abc"));
    }
}
