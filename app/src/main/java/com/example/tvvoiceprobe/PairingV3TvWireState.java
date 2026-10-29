package com.example.tvvoiceprobe;

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.LongSupplier;

/**
 * Local-only, strictly ordered KVPair/v3 TV protocol state.
 *
 * No HTTP server, Android lifecycle, OAuth, microphone, URL or token handling.
 * A future on-screen, 120-second Android listener must map these four
 * one-shot operations onto explicit /v3/pair/* HTTP routes. The v2 routes
 * MUST NOT be accepted by that listener.
 *
 * Both public keys stay private until both peer SHA-256 commitments exist.
 * The TV reveal is held back until the HA reveal is checked against its
 * original commitment. A remote-OK MAC is available ONLY through the
 * physical TV remote controller call after a displayed verified code.
 */
public final class PairingV3TvWireState implements AutoCloseable {
    public static final long TIMEOUT_MS = 120_000L;
    public static final int VERSION = 3;

    public static final class TvReveal {
        public final byte[] tvKey;
        public final byte[] tvNonce;

        private TvReveal(byte[] key, byte[] nonce) {
            this.tvKey = key.clone();
            this.tvNonce = nonce.clone();
        }
    }

    public static final class Approval {
        public final String tvUuid;
        public final String proof;

        private Approval(String uuid, String proof) {
            this.tvUuid = uuid;
            this.proof = proof;
        }
    }

    private enum State {
        FRESH, COMMITTED, HA_VERIFIED, REVEALED, APPROVED, CLOSED
    }

    private final KeyPair localKeys;
    private final String sessionId;
    private final String tvUuid;
    private final LongSupplier clock;
    private final long startMillis;
    private final PairingKeyCommitment.Session commitment;
    private State state = State.FRESH;
    private PairingKeyCommitment.Reveal heldReveal;
    private String visualCode;
    private String physicalProof;

    /** Production randomness belongs to SecureRandom; test injection is explicit. */
    public PairingV3TvWireState(KeyPair keys, String session, String tvUuid,
                                LongSupplier elapsedRealtimeMillis) {
        this(keys, session, tvUuid, elapsedRealtimeMillis, randomNonce());
    }

    PairingV3TvWireState(KeyPair keys, String session, String tvUuid,
                         LongSupplier elapsedRealtimeMillis, byte[] nonce) {
        if (keys == null || keys.getPrivate() == null || keys.getPublic() == null
                || elapsedRealtimeMillis == null || tvUuid == null
                || !tvUuid.matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Invalid v3 TV identity or keys");
        }
        localKeys = keys;
        sessionId = session;
        this.tvUuid = tvUuid;
        clock = elapsedRealtimeMillis;
        startMillis = clock.getAsLong();
        commitment = new PairingKeyCommitment.Session(
                "tv", session, keys.getPublic().getEncoded(), nonce);
    }

    private static byte[] randomNonce() {
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        return nonce;
    }

    private void require(State expected, String session) {
        long now = clock.getAsLong();
        if (now < startMillis || now - startMillis >= TIMEOUT_MS) {
            close();
            throw new IllegalStateException("V3 pairing expired");
        }
        if (state != expected || !sessionId.equals(session)) {
            // Wrong session is an intentional hard failure, not a retry.
            close();
            throw new IllegalStateException("Unexpected v3 step or session");
        }
    }

    /** /v3/pair/commit: HA commitment first; TV returns commitment only. */
    public synchronized byte[] acceptHaCommit(String session, byte[] haCommit) {
        require(State.FRESH, session);
        try {
            commitment.receivePeerCommit(haCommit);
            state = State.COMMITTED;
            return commitment.publicCommit();
        } catch (RuntimeException ex) {
            close();
            throw new IllegalArgumentException("Invalid HA commitment") ;
        }
    }

    /** /v3/pair/reveal-ha: verification before TV may reveal its key. */
    public synchronized void acceptHaReveal(String session, byte[] haKey,
                                             byte[] haNonce) {
        require(State.COMMITTED, session);
        try {
            // Local reveal must be marked before verifying the peer, but is
            // never returned to any client by this operation.
            heldReveal = commitment.reveal();
            commitment.receivePeerReveal(haKey, haNonce);
            visualCode = PairingV3Verified.comparisonCode(
                    localKeys.getPrivate(), commitment);
            state = State.HA_VERIFIED;
        } catch (RuntimeException ex) {
            close();
            throw new IllegalArgumentException("Invalid HA key reveal") ;
        }
    }

    /** /v3/pair/reveal-tv: one-time TV public-key + nonce reveal. */
    public synchronized TvReveal revealTv(String session) {
        require(State.HA_VERIFIED, session);
        state = State.REVEALED;
        return new TvReveal(heldReveal.publicDer, heldReveal.nonce);
    }

    /** Show to the human ONLY after verified remote reveal was released. */
    public synchronized String codeForTv(String session) {
        require(State.REVEALED, session);
        return visualCode;
    }

    /** Physical TV remote OK callback, never from a network request. */
    public synchronized Approval approveFromPhysicalTvRemote(String session) {
        require(State.REVEALED, session);
        physicalProof = PairingV3Verified.physicalTvOkProof(
                localKeys.getPrivate(), commitment, tvUuid);
        state = State.APPROVED;
        return new Approval(tvUuid, physicalProof);
    }

    /** /v3/pair/status: no secrets or login, only TV-remote proof. */
    public synchronized Approval approvedStatus(String session) {
        long now = clock.getAsLong();
        if (now < startMillis || now - startMillis >= TIMEOUT_MS) {
            close();
            throw new IllegalStateException("V3 session expired");
        }
        if (!sessionId.equals(session) || state == State.CLOSED) {
            close();
            throw new IllegalStateException("Unexpected TV session");
        }
        return state == State.APPROVED ? new Approval(tvUuid, physicalProof) : null;
    }

    public synchronized boolean isClosed() { return state == State.CLOSED; }

    @Override public synchronized void close() {
        if (state == State.CLOSED) return;
        state = State.CLOSED;
        commitment.close();
        if (heldReveal != null) {
            Arrays.fill(heldReveal.nonce, (byte) 0);
            heldReveal = null;
        }
        physicalProof = null;
        visualCode = null;
    }
}
