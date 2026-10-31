package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Secure URL and callback rules. No credential or Android device required. */
public class HaDashboardAuthPolicyTest {
    private final String ha = "https://ha.example.org:8123";

    @Test public void authenticatedHaRouteKeepsKioskParamsAndFragment() {
        assertEquals("https://ha.example.org:8123/tv-wall/wall?kiosk&external_auth=1#living",
                HaDashboardAuthPolicy.authenticatedUrl(
                        "https://ha.example.org:8123/tv-wall/wall?kiosk#living", ha));
        assertEquals("https://ha.example.org:8123/lovelace/home?external_auth=1",
                HaDashboardAuthPolicy.authenticatedUrl(
                        "https://ha.example.org:8123/lovelace/home", ha));
        assertFalse(HaDashboardAuthPolicy.authenticatedUrl(
                "https://ha.example.org:8123/lovelace/home", ha).contains("access_token"));
    }

    @Test public void exactHttpsOriginOnlyNoCrossHostOrDowngrade() {
        assertTrue(HaDashboardAuthPolicy.sameOrigin(
                "https://ha.example.org:8123/lovelace", ha));
        assertFalse(HaDashboardAuthPolicy.sameOrigin(
                "http://ha.example.org:8123/lovelace", ha));
        assertFalse(HaDashboardAuthPolicy.sameOrigin(
                "https://ha.example.org:8124/lovelace", ha));
        assertFalse(HaDashboardAuthPolicy.sameOrigin(
                "https://evil.example.org:8123/lovelace", ha));
        assertFalse(HaDashboardAuthPolicy.sameOrigin(
                "https://ha.example.org.evil.com:8123/lovelace", ha));
        assertFalse(HaDashboardAuthPolicy.sameOrigin(
                "https://ha.example.org:8123@evil.example.org/lovelace", ha));
        assertFalse(HaDashboardAuthPolicy.sameOrigin(
                "https://ha.example.org:8123/?token=secret", ha));
        assertEquals("", HaDashboardAuthPolicy.authenticatedUrl(
                "https://ha.example.org:8123/lovelace", "https://other.org:8123"));
        assertEquals("https://ha.example.org", HaDashboardAuthPolicy.origin(
                "https://HA.EXAMPLE.ORG:443/"));
    }

    @Test public void existingExternalAuthQueryCannotBeDuplicated() {
        assertEquals("", HaDashboardAuthPolicy.authenticatedUrl(
                "https://ha.example.org:8123/dashboard?external_auth=0", ha));
    }

    @Test public void onlyTwoOfficialCallbacksAllowed() {
        assertTrue(HaDashboardAuthPolicy.callbackAllowed(
                "getExternalAuth", "externalAuthSetToken"));
        assertTrue(HaDashboardAuthPolicy.callbackAllowed(
                "revokeExternalAuth", "externalAuthRevokeToken"));
        assertFalse(HaDashboardAuthPolicy.callbackAllowed(
                "getExternalAuth", "eval"));
        assertFalse(HaDashboardAuthPolicy.callbackAllowed(
                "getExternalAuth", "externalAuthRevokeToken"));
        assertFalse(HaDashboardAuthPolicy.callbackAllowed(
                "openExternalUrl", "externalAuthSetToken"));
    }
}
