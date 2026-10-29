package com.example.tvvoiceprobe;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;

/**
 * #193 isolated TV peer for the #221 HA four-message AEAD draft.
 *
 * DEVELOPMENT / SYNTHETIC PAYLOADS ONLY. Does not open a socket, receive a
 * real OAuth token, call the app credential store, alter a manual login,
 * start a microphone, or report Connected.
 *
 * The caller MUST already have independently verified the two-screen code,
 * TV remote OK and owner-approved HA session. ECDH by itself authenticates
 * neither party. Neither this class nor PairingAssistProofState proves that
 * trusted owner consent occurred.
 */
public final class TvSealedHandoff implements AutoCloseable {
    public interface Clock {
        long unixSeconds();
        long elapsedMillis();
    }

    /** Test-only stand-in for future independently verified Keystore persistence. */
    public interface SyntheticDiskSink {
        boolean persistSynthetic(byte[] candidate) throws Exception;
    }

    public enum Stage {
        WAIT_OFFER, WAIT_CAPABILITIES, WAIT_HA_COMMIT,
        PERSISTED, ABORTED
    }

    private static final String TRANSCRIPT_LABEL = "KVPair/v1/transcript";
    private static final byte[] SYNTHETIC_PREFIX =
            "SYNTHETIC NON-SECRET".getBytes(StandardCharsets.US_ASCII);
    private static final long WINDOW_MILLIS = 120_000L;
    private static final int MAX_CANDIDATE = 1024;
    private final Clock clock;
    private final long startedElapsed;
    private final PairingSealedChannel channel;
    private final String session;
    private final String tvUuid;
    private final String transcript;
    private Stage stage = Stage.WAIT_OFFER;
    private long expiresAt = -1;
    private byte[] candidate;

    public TvSealedHandoff(KeyPair verifiedTvKeys, String verifiedSession,
                           String verifiedTvUuid, byte[] tvDer, byte[] haDer,
                           Clock clock) {
        if (clock == null || verifiedTvKeys == null) {
            throw new IllegalArgumentException("Verified pairing context required");
        }
        tvUuid = PairingVerificationCode.canonicalTvUuid(verifiedTvUuid);
        // Validate canonical session, P-256 public keys, peer role and local
        // key before creating any AES-GCM channel.
        PairingVerificationCode.calculate(verifiedTvKeys, true, verifiedSession, tvDer, haDer);
        session = verifiedSession;
        transcript = publicTranscript(verifiedSession, tvDer, haDer);
        this.clock = clock;
        startedElapsed = clock.elapsedMillis();
        if (startedElapsed < 0 || clock.unixSeconds() <= 0) {
            throw new IllegalArgumentException("Invalid pairing time");
        }
        channel = new PairingSealedChannel(
                verifiedTvKeys, true, verifiedSession, tvDer, haDer);
    }

    private static String publicTranscript(String session, byte[] tv, byte[] ha) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(TRANSCRIPT_LABEL.getBytes(StandardCharsets.US_ASCII));
            part(sha, Base64.getUrlDecoder().decode(session));
            part(sha, tv);
            part(sha, ha);
            byte[] hashed = sha.digest();
            char[] out = new char[hashed.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int i = 0; i < hashed.length; i++) {
                out[2 * i] = alphabet[(hashed[i] >>> 4) & 15];
                out[2 * i + 1] = alphabet[hashed[i] & 15];
            }
            return new String(out);
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid pairing transcript", error);
        }
    }

    private static void part(MessageDigest sha, byte[] value) {
        if (value == null || value.length > 65535)
            throw new IllegalArgumentException("Invalid transcript field");
        sha.update((byte) (value.length >>> 8));
        sha.update((byte) value.length);
        sha.update(value);
    }

    private boolean live() {
        long elapsed = clock.elapsedMillis() - startedElapsed;
        long now = clock.unixSeconds();
        return elapsed >= 0 && elapsed < WINDOW_MILLIS
                && now > 0 && (expiresAt < 0 || now < expiresAt);
    }

    private void expect(Stage expected) {
        if (stage != expected || !live()) {
            abort();
            throw new IllegalStateException("TV handoff unavailable");
        }
    }

    private static boolean exactly(JSONObject frame, String... keys) {
        if (frame.length() != keys.length) return false;
        for (String key : keys) if (!frame.has(key)) return false;
        return true;
    }

    private JSONObject open(byte[] packet, String kind, boolean initial) {
        try {
            // PairingSealedChannel authenticates key, direction, nonce,
            // sequence and ECDH transcript before exposing JSON.
            byte[] clear = channel.open(packet);
            JSONObject frame = new JSONObject(new String(clear, StandardCharsets.UTF_8));
            Object version = frame.get("v");
            Object expiryValue = frame.get("expires_at");
            if (!(version instanceof Integer)
                    || frame.getInt("v") != 1
                    || !kind.equals(frame.get("type"))
                    || !tvUuid.equals(frame.get("tv_uuid"))
                    || !session.equals(frame.get("session"))
                    || !transcript.equals(frame.get("transcript"))
                    || !(expiryValue instanceof Integer || expiryValue instanceof Long)) {
                throw new IllegalArgumentException("Mismatched authenticated context");
            }
            long expiry = frame.getLong("expires_at");
            if (initial) {
                long now = clock.unixSeconds();
                if (expiry <= now || expiry > now + 120)
                    throw new IllegalArgumentException("Expiry outside pairing window");
                expiresAt = expiry;
            } else if (expiry != expiresAt) {
                throw new IllegalArgumentException("Expiry changed");
            }
            return frame;
        } catch (RuntimeException | JSONException error) {
            abort();
            throw new IllegalArgumentException("Invalid authenticated HA handoff") ;
        }
    }

    private JSONObject frame(String kind) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("v", 1);
        result.put("type", kind);
        result.put("tv_uuid", tvUuid);
        result.put("session", session);
        result.put("transcript", transcript);
        result.put("expires_at", expiresAt);
        return result;
    }

    /**
     * Receive sealed synthetic offer. No real token is accepted or installed.
     * The uncredentialed in-memory test payload remains private to this gate.
     */
    public synchronized void onSyntheticOffer(byte[] packet) {
        expect(Stage.WAIT_OFFER);
        JSONObject offer = open(packet, "credential_offer", true);
        try {
            if (!exactly(offer, "v", "type", "tv_uuid", "session",
                    "transcript", "expires_at", "candidate_b64")) {
                throw new IllegalArgumentException("Unexpected offer fields");
            }
            if (!(offer.get("candidate_b64") instanceof String))
                throw new IllegalArgumentException("Candidate must be a string");
            String encoded = offer.getString("candidate_b64");
            if (!encoded.matches("[A-Za-z0-9_-]{1,1366}"))
                throw new IllegalArgumentException("Invalid test payload");
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            if (decoded.length < SYNTHETIC_PREFIX.length
                    || decoded.length > MAX_CANDIDATE
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
                            .equals(encoded)) {
                throw new IllegalArgumentException("Noncanonical test payload");
            }
            for (int i = 0; i < SYNTHETIC_PREFIX.length; i++) {
                if (decoded[i] != SYNTHETIC_PREFIX[i])
                    throw new IllegalArgumentException("Only synthetic fixtures allowed");
            }
            candidate = decoded;
            stage = Stage.WAIT_CAPABILITIES;
        } catch (JSONException | IllegalArgumentException error) {
            abort();
            throw new IllegalArgumentException("Rejected synthetic credential offer");
        }
    }

    /**
     * This stage can use only a verified two-capability gate, not booleans.
     * No production probe callback is connected to this isolated draft.
     */
    public synchronized byte[] syntheticTvProof(PairingAssistProofState proof) {
        expect(Stage.WAIT_CAPABILITIES);
        if (proof == null || proof.stage() != PairingAssistProofState.Stage.VERIFIED) {
            abort();
            throw new IllegalStateException("Assist and scoped alerts not both verified");
        }
        return sealTvProof();
    }

    /**
     * The future live protocol MUST use the actual scoped Assist/WebSocket
     * receipt, never a caller-controlled gate or boolean. The receipt is
     * minted only by PairingAssistConnectionProbe after a TLS-authenticated
     * auth_ok, nonempty Assist list AND per-TV alerts subscription.
     * Still synthetic-only: onSyntheticOffer refuses all real token payloads.
     */
    public synchronized byte[] tvProofFromVerifiedSocket(
            PairingAssistConnectionProbe.VerifiedCapabilityReceipt receipt) {
        expect(Stage.WAIT_CAPABILITIES);
        // A successful WebSocket on another candidate must NEVER authorize
        // this one, even when the owner, TV UUID and session are identical.
        byte[] exactOfferedDigest = candidate == null ? null
                : PairingCredentialCandidateCodec.fingerprint(candidate);
        if (receipt == null || !receipt.claim(
                tvUuid, session, exactOfferedDigest,
                android.os.SystemClock.elapsedRealtime())) {
            abort();
            throw new IllegalStateException("Live Assist/alerts receipt unavailable");
        }
        return sealTvProof();
    }

    private byte[] sealTvProof() {
        try {
            JSONObject frame = frame("tv_proof_receipt");
            frame.put("ack_for", "credential_offer");
            frame.put("assist_ok", true);
            frame.put("alerts_ok", true);
            byte[] result = channel.seal(
                    frame.toString().getBytes(StandardCharsets.US_ASCII));
            stage = Stage.WAIT_HA_COMMIT;
            return result;
        } catch (Exception error) {
            abort();
            throw new IllegalStateException("Could not authenticate TV proof");
        }
    }

    /**
     * A receipt is emitted only after this test sink actually reports
     * synchronous success. Never connect it to live Keystore until verified.
     */
    public synchronized byte[] onHaCommitForSynthetic(byte[] packet, SyntheticDiskSink disk) {
        expect(Stage.WAIT_HA_COMMIT);
        JSONObject auth = open(packet, "ha_commit_authorization", false);
        try {
            if (!exactly(auth, "v", "type", "tv_uuid", "session",
                    "transcript", "expires_at", "authorize_commit")
                    || !(auth.get("authorize_commit") instanceof Boolean)
                    || !auth.getBoolean("authorize_commit") || disk == null) {
                throw new IllegalArgumentException("Commit was not authenticated");
            }
            if (candidate == null || !disk.persistSynthetic(candidate.clone())) {
                throw new IllegalStateException("Synthetic disk verification failed");
            }
            JSONObject receipt = frame("tv_persisted_receipt");
            receipt.put("ack_for", "ha_commit_authorization");
            receipt.put("verified_store_committed", true);
            byte[] sealed = channel.seal(receipt.toString().getBytes(StandardCharsets.US_ASCII));
            clearCandidate();
            stage = Stage.PERSISTED;
            channel.close();
            return sealed;
        } catch (Exception error) {
            abort();
            throw new IllegalStateException("Synthetic commit rejected, no persisted receipt");
        }
    }

    public synchronized Stage stage() {
        return stage;
    }

    private void clearCandidate() {
        if (candidate != null) {
            Arrays.fill(candidate, (byte) 0);
            candidate = null;
        }
    }

    public synchronized void abort() {
        if (stage == Stage.ABORTED || stage == Stage.PERSISTED) return;
        stage = Stage.ABORTED;
        clearCandidate();
        channel.close();
    }

    @Override public synchronized void close() {
        abort();
        if (stage == Stage.PERSISTED) channel.close();
    }
}
