package com.example.tvvoiceprobe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Small, testable security primitive for a future Home Assistant OAuth pairing
 * flow. It opens NO port, advertises NO mDNS service, exchanges NO credentials,
 * and cannot mark a TV as connected. Authentication/provisioning must be
 * implemented and reviewed separately before this session is used.
 *
 * Every attempt is explicit, short-lived and one-use. The OAuth authorization
 * code can be accepted only after HA's supported authorization endpoint verifies
 * the owner's login AND a future cryptographically bound TV pairing handshake.
 */
public final class PairingAuthorizationSession {
    public static final long MAX_AGE_MILLIS = 120_000L;

    private final long startedAtMillis;
    private final String state;
    private final String verifier;
    private final String challenge;
    private boolean consumed;

    private PairingAuthorizationSession(long nowMillis, String state, String verifier) {
        this.startedAtMillis = nowMillis;
        this.state = state;
        this.verifier = verifier;
        this.challenge = s256Challenge(verifier);
    }

    /** Call only from a user-initiated, visible pairing action. */
    public static PairingAuthorizationSession begin(long nowMillis, SecureRandom random) {
        if (nowMillis < 0 || random == null) throw new IllegalArgumentException("Invalid pairing context");
        return new PairingAuthorizationSession(nowMillis, randomValue(random), randomValue(random));
    }

    private static String randomValue(SecureRandom random) {
        byte[] bytes = new byte[32]; // 256 bits, URL-safe and no padding
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Challenge for Home Assistant's OAuth PKCE S256 authorize request. */
    public String challenge() { return challenge; }

    public String state() { return state; }

    /** Secret to be supplied only by the client exchanging the OAuth code. */
    public String verifier() { return verifier; }

    public synchronized boolean active(long nowMillis) {
        return !consumed && nowMillis >= startedAtMillis
                && nowMillis - startedAtMillis < MAX_AGE_MILLIS;
    }

    /**
     * Consume once. A failed comparison also closes the attempt to prevent
     * retries; no other calls can reuse it. Never log the state or verifier.
     */
    public synchronized boolean consumeState(String receivedState, long nowMillis) {
        if (!active(nowMillis)) {
            consumed = true;
            return false;
        }
        consumed = true;
        if (receivedState == null || receivedState.length() != state.length()) return false;
        return MessageDigest.isEqual(state.getBytes(StandardCharsets.US_ASCII),
                receivedState.getBytes(StandardCharsets.US_ASCII));
    }

    public synchronized void cancel() { consumed = true; }

    /** RFC 7636 PKCE S256. Always require SHA-256, never fall back to plain. */
    public static String s256Challenge(String verifier) {
        if (verifier == null || !verifier.matches("[A-Za-z0-9._~-]{43,128}")) {
            throw new IllegalArgumentException("Invalid PKCE verifier");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
