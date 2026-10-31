package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Sensitive flow IDs/URL query strings must never reach diagnostic labels. */
public class ScreensaverWebDiagnosticsTest {
    @Test public void originReasonsNeverRevealActualHostnamesOrFlowTokens() {
        String initial = "http://192.168.1.82:8123/mobile-command/home";
        String same = "http://192.168.1.82:8123/auth/login_flow/SECRET123?token=SECRET";
        String alias = "http://homeassistant.local:8123/auth/login_flow/SECRET123";
        String changedPort = "http://192.168.1.82:8888/auth/login_flow/SECRET123";
        String changedScheme = "https://192.168.1.82:8123/auth/login_flow/SECRET123";
        assertEquals("sameScheme=true sameHost=true samePort=true",
                ScreensaverWebDiagnostics.originComparison(initial, same));
        assertEquals("sameScheme=true sameHost=false samePort=true",
                ScreensaverWebDiagnostics.originComparison(initial, alias));
        assertEquals("sameScheme=true sameHost=true samePort=false",
                ScreensaverWebDiagnostics.originComparison(initial, changedPort));
        assertEquals("sameScheme=false sameHost=true samePort=true",
                ScreensaverWebDiagnostics.originComparison(initial, changedScheme));
        String diagnostics = ScreensaverWebDiagnostics.originComparison(initial, alias);
        assertFalse(diagnostics.contains("192.168."));
        assertFalse(diagnostics.contains("homeassistant.local"));
        assertFalse(diagnostics.contains("SECRET"));
        assertEquals("originComparison=invalid",
                ScreensaverWebDiagnostics.originComparison(initial, "bad url"));
    }

    @Test public void categoriesNeverExposeFlowIdentifiersOrTokens() {
        String flowId = "a1b2c3d4sensitive";
        String token = "sensitive-secret-token";
        String flow = "http://192.168.1.82:8123/auth/login_flow/" + flowId + "?token=" + token;
        String kind = ScreensaverWebDiagnostics.requestKind(flow);
        assertEquals("auth-login-flow", kind);
        assertFalse(kind.contains(flowId));
        assertFalse(kind.contains(token));
        assertFalse(kind.contains("192.168"));
        assertEquals("auth-providers", ScreensaverWebDiagnostics.requestKind(
                "http://192.168.1.82:8123/auth/providers"));
        assertEquals("auth-token", ScreensaverWebDiagnostics.requestKind(
                "https://ha.example.com/auth/token?refresh_token=" + token));
        assertEquals("auth-authorize", ScreensaverWebDiagnostics.requestKind(
                "http://192.168.1.82:8123/auth/authorize"));
        assertEquals("other", ScreensaverWebDiagnostics.requestKind(
                "http://192.168.1.82:8123/mobile-command/home"));
        assertEquals("other", ScreensaverWebDiagnostics.requestKind("not a valid url"));
    }
}
