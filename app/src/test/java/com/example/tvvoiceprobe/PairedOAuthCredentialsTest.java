package com.example.tvvoiceprobe;

import org.junit.Test;

import static org.junit.Assert.*;

/** Test-only tokens and IDs, never real Home Assistant accounts. */
public class PairedOAuthCredentialsTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TV_ID = "123e4567-e89b-42d3-a456-426614174000";
    private static final long NOW = 1_000_000L;

    private static PairedOAuthCredentials good() {
        return new PairedOAuthCredentials(
                "http://192.168.1.143:8123",
                "http://192.168.1.143:8123/",
                "fake.access.token.not.real",
                "fake.refresh.token.not.real",
                SESSION, TV_ID, NOW + 1_800_000L, NOW);
    }

    @Test public void acceptsExactTvAndSessionUntilPendingTtl() {
        PairedOAuthCredentials c = good();
        assertTrue(c.matches(SESSION, TV_ID, NOW));
        assertTrue(c.matches(SESSION, TV_ID, NOW + 119_999L));
        assertFalse(c.matches(SESSION, TV_ID, NOW + 120_000L));
        assertFalse(c.matches(SESSION, TV_ID, NOW - 1L));
        assertFalse(c.matches("AQIDBAUGBwgJCgsMDQ4PEQ", TV_ID, NOW));
        assertFalse(c.matches(SESSION,
                "123e4567-e89b-42d3-a456-426614174001", NOW));
        assertFalse(c.matches(null, TV_ID, NOW));
        assertFalse(c.matches(SESSION, null, NOW));
    }

    @Test public void rejectsPublicPlaintextHttpAndUntrustedOrigins() {
        for (String bad : new String[] {
                "http://example.org", "http://192.0.2.45:8123",
                "http://100.64.1.1:8123", "http://169.254.5.4:8123",
                "http://user:password@192.168.1.143:8123",
                "http://192.168.1.143:8123/path",
                "http://192.168.1.143:8123/?code=fake",
                "http://192.168.1.143:8123/#fragment",
                "http://192.168.1.143:0",
                "http://192.168.1.143\\@evil.test",
                "file:///tmp/ha", "http://192.168.001.143:8123",
        }) {
            try {
                new PairedOAuthCredentials(bad, bad + "/",
                        "fake.access.token.not.real", "fake.refresh.token.not.real",
                        SESSION, TV_ID, NOW + 1_800_000L, NOW);
                fail("Bad URL accepted");
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void allowsHttpsWithSameOriginClientId() {
        PairedOAuthCredentials c = new PairedOAuthCredentials(
                "https://myha.example.org",
                "https://myha.example.org/",
                "fake.access.token.not.real", "fake.refresh.token.not.real",
                SESSION, TV_ID, NOW + 1_800_000L, NOW);
        assertTrue(c.matches(SESSION, TV_ID, NOW));
    }

    @Test public void rejectsMismatchedClientIdAndMalformedValues() {
        try {
            new PairedOAuthCredentials(
                    "https://myha.example.org", "https://evil.test/",
                    "fake.access.token.not.real", "fake.refresh.token.not.real",
                    SESSION, TV_ID, NOW + 50000L, NOW);
            fail("Wrong client ID accepted");
        } catch (IllegalArgumentException expected) { }
        for (String invalid : new String[] {
                "", "bad", "fake access token with spaces", "x".repeat(2049)
        }) {
            try {
                new PairedOAuthCredentials(
                        "https://myha.example.org", "https://myha.example.org/",
                        invalid, "fake.refresh.token.not.real",
                        SESSION, TV_ID, NOW + 50000L, NOW);
                fail("Invalid secret accepted");
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void rejectsWrongTvIdSessionAndInvalidTime() {
        for (String tv : new String[] {
                "not-a-uuid", "123e4567-e89b-12d3-a456-426614174000",
                "123E4567-E89B-42D3-A456-426614174000",
        }) {
            try {
                new PairedOAuthCredentials(
                        "https://myha.example.org", "https://myha.example.org/",
                        "fake.access.token.not.real", "fake.refresh.token.not.real",
                        SESSION, tv, NOW + 30000L, NOW);
                fail("Invalid device ID accepted");
            } catch (IllegalArgumentException expected) { }
        }
        for (String sid : new String[] {"bad", "AQIDBAUGBwgJCgsMDQ4PE=", "AQIDBAUGBwgJCgsMDQ4PE!?"}) {
            try {
                new PairedOAuthCredentials(
                        "https://myha.example.org", "https://myha.example.org/",
                        "fake.access.token.not.real", "fake.refresh.token.not.real",
                        sid, TV_ID, NOW + 30000L, NOW);
                fail("Invalid session ID accepted");
            } catch (IllegalArgumentException expected) { }
        }
        try {
            new PairedOAuthCredentials(
                    "https://myha.example.org", "https://myha.example.org/",
                    "fake.access.token.not.real", "fake.refresh.token.not.real",
                    SESSION, TV_ID, NOW - 1L, NOW);
            fail("Expired access accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
