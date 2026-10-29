package com.example.tvvoiceprobe;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * #218 / #221: isolated TV-side encrypted-message state machine.
 *
 * Matches the exact canonical JSON fields from the HA draft coordinator.
 * Does not touch CredentialStore, display Connected, start the microphone,
 * or accept plaintext HTTP tokens. Uses a fake disk sink until the real
 * credential lifecycle is reviewed.
 *
 * Sequence:
 * 1. Decrypt HA credential_offer under the verified transcript.
 * 2. Stage a synthetic candidate only.
 * 3. Require real Assist + scoped-alert proof evidence before sending
 *    tv_proof_receipt.
 * 4. Decrypt HA ha_commit_authorization (exact match required).
 * 5. Send tv_persisted_receipt only after the isolated sink commits.
 */
public final class TvSealedHandoff {

    public enum Stage {
        READY,
        OFFER_RECEIVED,
        PROOF_SENT,
        AUTHORIZED,
        PERSISTED,
        ABORTED
    }

    public interface SealedChannel {
        byte[] open(byte[] packet);
        byte[] seal(byte[] plaintext);
        void close();
    }

    public interface ProofEvidence {
        boolean assistSucceeded();
        boolean alertsSucceeded();
    }

    public interface FakeDiskSink {
        boolean commit(String tvUuid, String session, byte[] candidate);
    }

    private final SealedChannel channel;
    private final String tvUuid;
    private final String session;
    private final String transcript;
    private final long expiresAt;
    private final FakeDiskSink sink;
    private Stage stage = Stage.READY;
    private byte[] stagedCandidate;

    public TvSealedHandoff(
            SealedChannel channel,
            String tvUuid,
            String session,
            String transcript,
            long expiresAt,
            FakeDiskSink sink) {
        if (channel == null || sink == null) {
            throw new IllegalArgumentException("Channel and sink required");
        }
        if (tvUuid == null || tvUuid.isEmpty() || "unknown".equals(tvUuid)) {
            throw new IllegalArgumentException("Verified TV UUID required");
        }
        if (session == null || session.isEmpty()) {
            throw new IllegalArgumentException("Session required");
        }
        if (transcript == null || transcript.length() != 64) {
            throw new IllegalArgumentException("Transcript hash required");
        }
        this.channel = channel;
        this.tvUuid = tvUuid;
        this.session = session;
        this.transcript = transcript;
        this.expiresAt = expiresAt;
        this.sink = sink;
    }

    public Stage stage() {
        return stage;
    }

    private void abort() {
        if (stage != Stage.PERSISTED) {
            stage = Stage.ABORTED;
            stagedCandidate = null;
            try { channel.close(); } catch (RuntimeException ignored) {}
        }
    }

    private JSONObject base(String type) throws Exception {
        JSONObject doc = new JSONObject();
        doc.put("v", 1);
        doc.put("type", type);
        doc.put("tv_uuid", tvUuid);
        doc.put("session", session);
        doc.put("transcript", transcript);
        doc.put("expires_at", expiresAt);
        return doc;
    }

    private void checkBase(JSONObject frame, String expectedType) throws Exception {
        if (frame.optInt("v", -1) != 1) {
            throw new IllegalArgumentException("Wrong version");
        }
        if (!expectedType.equals(frame.optString("type"))) {
            throw new IllegalArgumentException("Wrong type");
        }
        if (!tvUuid.equals(frame.optString("tv_uuid"))) {
            throw new IllegalArgumentException("Wrong TV UUID");
        }
        if (!session.equals(frame.optString("session"))) {
            throw new IllegalArgumentException("Wrong session");
        }
        if (!transcript.equals(frame.optString("transcript"))) {
            throw new IllegalArgumentException("Wrong transcript");
        }
        if (frame.optLong("expires_at", -1) != expiresAt) {
            throw new IllegalArgumentException("Wrong expiry");
        }
    }

    /**
     * Decrypt and stage a credential offer. Candidate is synthetic for tests.
     */
    public void acceptOffer(byte[] packet, long now) {
        if (stage != Stage.READY || now >= expiresAt) {
            abort();
            throw new IllegalStateException("Offer not acceptable");
        }
        try {
            byte[] plain = channel.open(packet);
            JSONObject frame = new JSONObject(new String(plain, StandardCharsets.US_ASCII));
            checkBase(frame, "credential_offer");
            String b64 = frame.optString("candidate_b64", "");
            if (b64.isEmpty()) {
                throw new IllegalArgumentException("Missing candidate");
            }
            stagedCandidate = Base64.getUrlDecoder().decode(b64);
            if (stagedCandidate.length == 0 || stagedCandidate.length > 1024) {
                throw new IllegalArgumentException("Invalid candidate size");
            }
            stage = Stage.OFFER_RECEIVED;
        } catch (Exception e) {
            abort();
            throw new IllegalStateException("Offer rejected", e);
        }
    }

    /**
     * Build an authenticated proof receipt. Requires real evidence; caller
     * booleans are not accepted.
     */
    public byte[] sendProofReceipt(ProofEvidence evidence, long now) {
        if (stage != Stage.OFFER_RECEIVED || now >= expiresAt) {
            abort();
            throw new IllegalStateException("Proof not acceptable");
        }
        if (evidence == null || !evidence.assistSucceeded() || !evidence.alertsSucceeded()) {
            abort();
            throw new IllegalStateException("Assist and alert evidence required");
        }
        try {
            JSONObject frame = base("tv_proof_receipt");
            frame.put("ack_for", "credential_offer");
            frame.put("assist_ok", true);
            frame.put("alerts_ok", true);
            byte[] packet = channel.seal(frame.toString().getBytes(StandardCharsets.US_ASCII));
            stage = Stage.PROOF_SENT;
            return packet;
        } catch (Exception e) {
            abort();
            throw new IllegalStateException("Could not seal proof", e);
        }
    }

    /**
     * Accept the HA commit authorization. Must match exact session/UUID/transcript.
     */
    public void acceptAuthorization(byte[] packet, long now) {
        if (stage != Stage.PROOF_SENT || now >= expiresAt) {
            abort();
            throw new IllegalStateException("Authorization not acceptable");
        }
        try {
            byte[] plain = channel.open(packet);
            JSONObject frame = new JSONObject(new String(plain, StandardCharsets.US_ASCII));
            checkBase(frame, "ha_commit_authorization");
            if (!frame.optBoolean("authorize_commit", false)) {
                throw new IllegalArgumentException("Commit not authorized");
            }
            stage = Stage.AUTHORIZED;
        } catch (Exception e) {
            abort();
            throw new IllegalStateException("Authorization rejected", e);
        }
    }

    /**
     * Persist via the fake sink and only then emit the receipt.
     */
    public byte[] sendPersistedReceipt(long now) {
        if (stage != Stage.AUTHORIZED || now >= expiresAt || stagedCandidate == null) {
            abort();
            throw new IllegalStateException("Persist not acceptable");
        }
        if (!sink.commit(tvUuid, session, stagedCandidate)) {
            abort();
            throw new IllegalStateException("Disk sink failed");
        }
        try {
            JSONObject frame = base("tv_persisted_receipt");
            frame.put("ack_for", "ha_commit_authorization");
            frame.put("verified_store_committed", true);
            byte[] packet = channel.seal(frame.toString().getBytes(StandardCharsets.US_ASCII));
            stage = Stage.PERSISTED;
            stagedCandidate = null;
            channel.close();
            return packet;
        } catch (Exception e) {
            abort();
            throw new IllegalStateException("Could not seal persisted receipt", e);
        }
    }
}
