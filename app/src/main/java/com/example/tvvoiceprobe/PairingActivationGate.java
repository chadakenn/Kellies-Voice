package com.example.tvvoiceprobe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * #193 deterministic, single-use activation policy for TV OAuth login.
 *
 * A caller must independently prove the HA owner's consent, TV remote OK,
 * authenticated AEAD transcript and server acknowledgement. This class
 * does NOT validate those proofs or accept unauthenticated LAN messages.
 *
 * A live login can be written only after:
 *  (1) this exact TV's Assist AND scoped alerts are authorized;
 *  (2) this exact session has a separately authenticated HA commit ack.
 *
 * Clock argument MUST be Android SystemClock.elapsedRealtime(), not wall time.
 * The existing manual CredentialStore is never referenced here.
 */
public final class PairingActivationGate {
    public static final long WINDOW_MILLIS = 120_000L;

    public enum Stage {
        WAIT_CAPABILITIES, WAIT_HA_ACK, READY_TO_COMMIT, COMMITTED, ABORTED
    }

    private final String session;
    private final String tvId;
    private final long startedElapsedMillis;
    private Stage stage = Stage.WAIT_CAPABILITIES;

    public PairingActivationGate(String session, String tvId, long startedElapsedMillis) {
        if (session == null || session.isEmpty() || tvId == null || tvId.isEmpty()
                || startedElapsedMillis < 0L) {
            throw new IllegalArgumentException("Missing verified pairing context");
        }
        this.session = session;
        this.tvId = tvId;
        this.startedElapsedMillis = startedElapsedMillis;
    }

    public synchronized Stage stage() {
        return stage;
    }

    public synchronized boolean validContext(
            String expectedSession, String expectedTvId, long nowElapsedMillis) {
        if (expectedSession == null || expectedTvId == null
                || nowElapsedMillis < startedElapsedMillis
                || nowElapsedMillis - startedElapsedMillis >= WINDOW_MILLIS) {
            return false;
        }
        return MessageDigest.isEqual(
                    session.getBytes(StandardCharsets.US_ASCII),
                    expectedSession.getBytes(StandardCharsets.US_ASCII))
                && MessageDigest.isEqual(
                    tvId.getBytes(StandardCharsets.US_ASCII),
                    expectedTvId.getBytes(StandardCharsets.US_ASCII));
    }

    /** Caller must have authenticated BOTH Assist and per-TV scoped alerts. */
    public synchronized boolean capabilitiesVerified(
            String expectedSession, String expectedTvId, long nowElapsedMillis,
            boolean assistAndAlertsAuthorized) {
        if (stage == Stage.COMMITTED || stage == Stage.ABORTED) return false;
        if (stage != Stage.WAIT_CAPABILITIES
                || !validContext(expectedSession, expectedTvId, nowElapsedMillis)
                || !assistAndAlertsAuthorized) {
            stage = Stage.ABORTED;
            return false;
        }
        stage = Stage.WAIT_HA_ACK;
        return true;
    }

    /**
     * The caller must first authenticate an HA acknowledgement over the
     * verified ECDH/AEAD channel with the exact owner/TV/session/transcript.
     * A plain status message or arbitrary true boolean is NOT valid proof.
     */
    public synchronized boolean authenticatedHaAck(
            String expectedSession, String expectedTvId, long nowElapsedMillis,
            boolean verifiedSealedAcknowledgement) {
        if (stage == Stage.COMMITTED || stage == Stage.ABORTED) return false;
        if (stage != Stage.WAIT_HA_ACK
                || !validContext(expectedSession, expectedTvId, nowElapsedMillis)
                || !verifiedSealedAcknowledgement) {
            stage = Stage.ABORTED;
            return false;
        }
        stage = Stage.READY_TO_COMMIT;
        return true;
    }

    /** Recheck the pair's identity and monotonic TTL at persistence time. */
    public synchronized boolean canCommit(
            String expectedSession, String expectedTvId, long nowElapsedMillis) {
        if (stage == Stage.COMMITTED || stage == Stage.ABORTED) return false;
        if (stage != Stage.READY_TO_COMMIT
                || !validContext(expectedSession, expectedTvId, nowElapsedMillis)) {
            stage = Stage.ABORTED;
            return false;
        }
        return true;
    }

    /** Called only after a successful encrypted credential disk commit. */
    public synchronized void recordWriteResult(boolean persistedSuccessfully) {
        if (stage != Stage.READY_TO_COMMIT) {
            throw new IllegalStateException("No approved pairing commit in progress");
        }
        stage = persistedSuccessfully ? Stage.COMMITTED : Stage.ABORTED;
    }

    public synchronized void abort() {
        if (stage != Stage.COMMITTED) stage = Stage.ABORTED;
    }
}
