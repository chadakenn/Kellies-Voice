package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Beta status reports only observed state, never a token or false v3 login. */
public final class FullAppBetaReadinessTest {
    @Test public void v3OwnerProofIsNotMisreportedAsAssistLogin() {
        String status = FullAppBetaReadiness.describe(
                false, false, false, false, false, false, false);
        assertTrue(status.contains("NOT CONFIGURED"));
        assertTrue(status.contains("optional owner-approved dedicated TV login"));
        assertTrue(status.contains("owner receipt alone does NOT"));
        assertFalse(status.contains("CONNECTED AND AUTHENTICATED"));
    }

    @Test public void savedLoginIsNotProofOfLiveAuthentication() {
        String status = FullAppBetaReadiness.describe(
                true, false, true, false, true, false, true);
        assertTrue(status.contains("NOT CONNECTED (check HA login/network)"));
        assertTrue(status.contains("NOT CONNECTED (open TV alert status)"));
        assertTrue(status.contains("Frigate screensaver: ENABLED"));
    }

    @Test public void assistAndAlertConnectionsAreIndependentlyObserved() {
        String status = FullAppBetaReadiness.describe(
                true, true, true, false, true, true, false);
        assertTrue(status.contains("HOME ASSISTANT: CONNECTED AND AUTHENTICATED"));
        assertTrue(status.contains("TV alerts & cameras: CONNECTED"));
        assertTrue(status.contains("send an actual popup to confirm delivery"));
    }

    @Test public void manualMicDisableAndOverlayOptOutAreVisible() {
        String status = FullAppBetaReadiness.describe(
                true, true, false, true, true, false, false);
        assertTrue(status.contains("TV microphone: MANUALLY DISABLED"));
        assertTrue(status.contains("NEEDS DISPLAY OVER APPS PERMISSION"));
        assertTrue(status.contains("Overlay permission: NOT GRANTED"));
    }

    @Test public void readinessDoesNotLeakUrlOrTokens() {
        String status = FullAppBetaReadiness.describe(
                true, true, true, false, true, true, true);
        assertFalse(status.contains("http://"));
        assertFalse(status.contains("https://"));
        assertFalse(status.contains("Bearer "));
        assertFalse(status.contains("eyJ"));
        assertTrue(status.contains("No token or network credentials are displayed"));
    }
}
