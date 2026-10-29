package com.example.tvvoiceprobe;

import java.security.KeyPair;
import java.util.Base64;

/**
 * One TV-side, short-lived verification preview. No token, URL, login or HA
 * connection is ever accepted. Only the visible TV screen can call approve().
 */
public final class PairingHandshakeState {
    public static final class Offer {
        public final String tvKey;
        public final String code;

        private Offer(String tvKey, String code) {
            this.tvKey = tvKey;
            this.code = code;
        }
    }

    private final String session;
    private final String tvUuid;
    private final KeyPair tv;
    private final long startedAt;
    private boolean offered;
    private boolean approved;
    private boolean closed;
    private byte[] haKey;
    private String code;

    public PairingHandshakeState(String session, String tvUuid, long startedAt) {
        if (session == null || !session.matches("[A-Za-z0-9_-]{22}") || startedAt < 0) {
            throw new IllegalArgumentException("Invalid pairing context");
        }
        this.session = session;
        this.tvUuid = PairingVerificationCode.canonicalTvUuid(tvUuid);
        this.tv = PairingVerificationCode.freshKeyPair();
        this.startedAt = startedAt;
    }

    private boolean alive(long now) {
        return !closed && now >= startedAt
                && now - startedAt < PairingWindowPolicy.LIFETIME_MILLIS;
    }

    /** First valid offer wins; a rogue LAN offer cannot silently approve. */
    public synchronized Offer offer(String requestedSession, String haKeyB64, long now) {
        if (!alive(now) || offered || !session.equals(requestedSession)
                || haKeyB64 == null || !haKeyB64.matches("[A-Za-z0-9_-]{110,200}")) {
            throw new IllegalStateException("Pairing offer unavailable");
        }
        byte[] remote;
        try {
            remote = Base64.getUrlDecoder().decode(haKeyB64);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid peer key", error);
        }
        String computed = PairingVerificationCode.calculate(
                tv, true, session, tv.getPublic().getEncoded(), remote);
        haKey = remote;
        code = computed;
        offered = true;
        return new Offer(Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tv.getPublic().getEncoded()), computed);
    }

    public synchronized String code(long now) {
        return alive(now) && offered ? code : null;
    }

    /** Called only by explicit remote OK while the matching code is shown. */
    public synchronized boolean approve(long now) {
        if (!alive(now) || !offered || approved) return false;
        approved = true;
        return true;
    }

    /** Identity is exposed only alongside an approved physical-OK proof. */
    public synchronized String approvedTvUuid(long now) {
        return alive(now) && approved && haKey != null ? tvUuid : null;
    }

    /** HMAC proof of the approved ephemeral transcript, not the visible code. */
    public synchronized String proof(long now) {
        if (!alive(now) || !approved || haKey == null) return null;
        return PairingVerificationCode.approvedProof(
                tv, true, session, tv.getPublic().getEncoded(), haKey, tvUuid);
    }

    public synchronized void close() {
        closed = true;
        code = null;
        haKey = null;
        approved = false;
    }
}
