package com.example.tvvoiceprobe;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure static policy used by future Android Keystore-protected HA OAuth flow. */
public final class TvRenewableLoginPolicyTest {
    private static final String TV =
            "123e4567-e89b-42d3-a456-426614174000";
    private static final String ORIGIN = "https://ha.example.test";
    private static final String CLIENT = ORIGIN + "/kellies-voice/tv/" + TV
            + "/attempt/0123456789abcdef0123456789abcdef";

    private void rejected(String origin, String tv, String client, String id) {
        try {
            TvRenewableLoginPolicy.check(origin, tv, client, id);
            fail("Should never allow a broad or mismatched TV identity");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void validHttpsLeaseIsBoundToExactTv() {
        TvRenewableLoginPolicy.Grant g =
                TvRenewableLoginPolicy.check(ORIGIN, TV, CLIENT, "token-ID_1");
        assertEquals(ORIGIN + "/auth/token", TvRenewableLoginPolicy.tokenUrl(g));
        assertEquals(TV, g.tvUuid);
    }

    @Test public void neverAllowLocalPlaintextOrInjectedOrigin() {
        for (String origin : new String[] {
            "http://192.168.1.82:8123", "http://homeassistant.local:8123",
            "https://bad.example/path", "https://user:pass@ha.example.test",
            "https://ha.example.test?q=x", "https://ha.example.test#token",
            "file:///tmp/token", "https://ha.example.test/other",
        }) rejected(origin, TV, CLIENT, "token-ID_1");
    }

    @Test public void wrongTvOrSpoofedClientIdCannotReceiveRefreshToken() {
        rejected(ORIGIN, "123e4567-e89b-42d3-a456-426614174001",
                CLIENT, "token-ID_1");
        rejected(ORIGIN, TV, ORIGIN + "/kellies-voice/tv/" + TV,
                "token-ID_1");
        rejected(ORIGIN, TV, CLIENT + "/extra", "token-ID_1");
        rejected(ORIGIN, TV, CLIENT, "../token");
        rejected(ORIGIN, TV.toUpperCase(), CLIENT, "token-ID_1");
        rejected("https://other.example.test", TV, CLIENT, "token-ID_1");
    }

    @Test public void tokensMustBeFiniteAndNonemptyAndCannotContainNewlines() {
        TvRenewableLoginPolicy.checkCredential("r", "a", 1800);
        for (String refresh : new String[] {"", "bad\nrefresh", "\r\n"}) {
            try {
                TvRenewableLoginPolicy.checkCredential(refresh, "access", 1800);
                fail("Unsafe refresh");
            } catch (IllegalArgumentException expected) { }
        }
        for (long timeout : new long[] {0, 1, 29, 86401, Long.MAX_VALUE}) {
            try {
                TvRenewableLoginPolicy.checkCredential("r", "a", timeout);
                fail("Unbounded or invalid lifetime");
            } catch (IllegalArgumentException expected) { }
        }
    }
}
