package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Compose Home Assistant settings must never display secrets. */
public class ComposeHomeStatusTest {
    @Test public void configuredLabelDoesNotMentionSecrets() {
        assertEquals("Configured", ComposeHomeStatus.configuredLabel(true));
        assertEquals("Not configured", ComposeHomeStatus.configuredLabel(false));
        assertTrue(ComposeHomeStatus.summaryHidesSecrets(
                ComposeHomeStatus.configuredLabel(true), "eyJhbGciOiJIUzI1NiJ9"));
    }

    @Test public void addressSummaryShowsHostOnly() {
        assertEquals("ha.local", ComposeHomeStatus.addressSummary("http://ha.local/"));
        assertEquals("ha.local:8123", ComposeHomeStatus.addressSummary("https://ha.local:8123/"));
        assertEquals("192.168.1.10:8123",
                ComposeHomeStatus.addressSummary("http://192.168.1.10:8123/api"));
    }

    @Test public void addressSummaryHidesUserinfoQueryAndToken() {
        String token = "eyJhbGciOiJIUzI1NiJ9.secret";
        assertEquals("", ComposeHomeStatus.addressSummary("http://user:pass@ha.local:8123"));
        assertEquals("", ComposeHomeStatus.addressSummary("https://ha.local/api?token=" + token));
        assertEquals("", ComposeHomeStatus.addressSummary("https://ha.local/api#access_token=abc"));
        assertEquals("", ComposeHomeStatus.addressSummary("https://ha.local/api?password=secret"));
        assertEquals("", ComposeHomeStatus.addressSummary("not a url"));
        assertEquals("", ComposeHomeStatus.addressSummary(""));
        assertEquals("", ComposeHomeStatus.addressSummary(null));
        String summary = ComposeHomeStatus.addressSummary("https://ha.local:8123/");
        assertTrue(ComposeHomeStatus.summaryHidesSecrets(summary, token));
        assertFalse(summary.contains(token));
    }

    @Test public void pairingIdIsNotTreatedAsASecret() {
        String id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        assertTrue(ComposeHomeStatus.summaryHidesSecrets(id, "long-lived-access-token"));
        assertFalse(id.contains("token"));
    }
}
