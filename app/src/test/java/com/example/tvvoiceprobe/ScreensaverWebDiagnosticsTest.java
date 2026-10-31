package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Sensitive flow IDs/URL query strings must never reach diagnostic labels. */
public class ScreensaverWebDiagnosticsTest {
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
