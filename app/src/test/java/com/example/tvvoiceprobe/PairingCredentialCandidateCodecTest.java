package com.example.tvvoiceprobe;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import static org.junit.Assert.*;

/** Exact candidate fingerprint never authorizes a different login or origin. */
public class PairingCredentialCandidateCodecTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TV = "123e4567-e89b-42d3-a456-426614174000";

    private static PairedOAuthCredentials candidate(String origin, String access,
                                                     String refresh, String tv,
                                                     long expiry, long staged) {
        return new PairedOAuthCredentials(origin, origin + "/", access, refresh,
                SESSION, tv, expiry, staged);
    }

    @Test public void canonicalEncryptedCandidateReencodesExactly() {
        PairedOAuthCredentials original = candidate(
                "https://ha.example", "a".repeat(32), "r".repeat(32),
                TV, 1700001800000L, 1700000000000L);
        byte[] bytes = PairingCredentialCandidateCodec.encode(original);
        assertTrue(bytes.length <= PairingCredentialCandidateCodec.MAX_BYTES);
        PairedOAuthCredentials parsed = PairingCredentialCandidateCodec.decode(bytes);
        assertEquals(original.serverUrl, parsed.serverUrl);
        assertEquals(original.clientId, parsed.clientId);
        assertEquals(original.accessToken, parsed.accessToken);
        assertEquals(original.refreshToken, parsed.refreshToken);
        assertEquals(original.session, parsed.session);
        assertEquals(original.tvId, parsed.tvId);
        assertEquals(original.stagedAtMillis, parsed.stagedAtMillis);
        assertEquals(original.accessExpiresAtMillis, parsed.accessExpiresAtMillis);
        assertArrayEquals(bytes, PairingCredentialCandidateCodec.encode(parsed));
        assertArrayEquals(PairingCredentialCandidateCodec.fingerprint(bytes),
                PairingCredentialCandidateCodec.fingerprint(parsed));
    }

    @Test public void changingAnyBoundCredentialOrOriginChangesFingerprint() {
        long start = 1700000000000L, expiry = 1700001800000L;
        PairedOAuthCredentials a = candidate("https://ha.example", "a".repeat(32),
                "r".repeat(32), TV, expiry, start);
        byte[] baseline = PairingCredentialCandidateCodec.fingerprint(a);
        PairedOAuthCredentials[] others = {
                candidate("https://different.example", "a".repeat(32),
                        "r".repeat(32), TV, expiry, start),
                candidate("https://ha.example", "b".repeat(32),
                        "r".repeat(32), TV, expiry, start),
                candidate("https://ha.example", "a".repeat(32),
                        "s".repeat(32), TV, expiry, start),
                candidate("https://ha.example", "a".repeat(32),
                        "r".repeat(32), "223e4567-e89b-42d3-a456-426614174000",
                        expiry, start),
                candidate("https://ha.example", "a".repeat(32),
                        "r".repeat(32), TV, expiry + 1, start),
                candidate("https://ha.example", "a".repeat(32),
                        "r".repeat(32), TV, expiry, start + 1),
        };
        for (PairedOAuthCredentials other : others) {
            assertFalse("Different candidate reused verification",
                    MessageDigest.isEqual(baseline,
                            PairingCredentialCandidateCodec.fingerprint(other)));
        }
    }

    @Test public void malformedAndTrailingCandidateBytesFailClosed() {
        byte[] encoded = PairingCredentialCandidateCodec.encode(candidate(
                "https://ha.example", "a".repeat(32), "r".repeat(32),
                TV, 1700001800000L, 1700000000000L));
        byte[] forged = encoded.clone();
        forged[0] ^= 1;
        try { PairingCredentialCandidateCodec.decode(forged);
            fail("Wrong candidate protocol accepted"); }
        catch (IllegalArgumentException expected) { }
        try { PairingCredentialCandidateCodec.decode(
                Arrays.copyOf(encoded, encoded.length + 1));
            fail("Trailing bytes accepted"); }
        catch (IllegalArgumentException expected) { }
        try { PairingCredentialCandidateCodec.decode(Arrays.copyOf(
                encoded, encoded.length - 1));
            fail("Truncated time accepted"); }
        catch (IllegalArgumentException expected) { }
        try { PairingCredentialCandidateCodec.fingerprint(new byte[0]);
            fail("Empty candidate accepted"); }
        catch (IllegalArgumentException expected) { }
        try { PairingCredentialCandidateCodec.decode(new byte[1025]);
            fail("Oversized candidate accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void sealedOfferSizeLimitIsFailClosed() {
        PairedOAuthCredentials lengthy = candidate(
                "https://ha.example", "a".repeat(1020), "r".repeat(1020),
                TV, 1700001800000L, 1700000000000L);
        try { PairingCredentialCandidateCodec.encode(lengthy);
            fail("Oversized OAuth candidate would violate current AEAD limit"); }
        catch (IllegalArgumentException expected) { }
    }
}
