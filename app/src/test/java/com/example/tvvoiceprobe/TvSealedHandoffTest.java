package com.example.tvvoiceprobe;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** #221 interoperability contract, all payloads synthetic; no real OAuth. */
public class TvSealedHandoffTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TV_UUID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String WRONG_TV = "223e4567-e89b-42d3-a456-426614174000";
    private static final byte[] CANDIDATE =
            "SYNTHETIC NON-SECRET TEST PAYLOAD".getBytes(StandardCharsets.US_ASCII);

    private static final class FakeClock implements TvSealedHandoff.Clock {
        long epoch = 1_700_000_000L, elapsed = 10_000L;
        @Override public long unixSeconds() { return epoch; }
        @Override public long elapsedMillis() { return elapsed; }
    }

    private static final class Peer implements AutoCloseable {
        final FakeClock clock = new FakeClock();
        final KeyPair tvKey = PairingVerificationCode.freshKeyPair();
        final KeyPair haKey = PairingVerificationCode.freshKeyPair();
        final byte[] tvDer = tvKey.getPublic().getEncoded();
        final byte[] haDer = haKey.getPublic().getEncoded();
        final PairingSealedChannel ha = new PairingSealedChannel(
                haKey, false, SESSION, tvDer, haDer);
        final TvSealedHandoff tv = new TvSealedHandoff(
                tvKey, SESSION, TV_UUID, tvDer, haDer, clock);
        long expiry = clock.epoch + 120;
        String transcript;

        Peer() throws Exception {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update("KVPair/v1/transcript".getBytes(StandardCharsets.US_ASCII));
            part(hash, Base64.getUrlDecoder().decode(SESSION));
            part(hash, tvDer);
            part(hash, haDer);
            byte[] digest = hash.digest();
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value & 255));
            transcript = result.toString();
        }

        private static void part(MessageDigest hash, byte[] bytes) {
            hash.update((byte) (bytes.length >>> 8));
            hash.update((byte) bytes.length);
            hash.update(bytes);
        }

        JSONObject base(String type) throws Exception {
            return new JSONObject()
                    .put("v", 1)
                    .put("type", type)
                    .put("tv_uuid", TV_UUID)
                    .put("session", SESSION)
                    .put("transcript", transcript)
                    .put("expires_at", expiry);
        }

        byte[] send(JSONObject frame) {
            return ha.seal(frame.toString().getBytes(StandardCharsets.US_ASCII));
        }

        JSONObject receive(byte[] packet) throws Exception {
            return new JSONObject(new String(ha.open(packet), StandardCharsets.US_ASCII));
        }

        void offer() throws Exception {
            JSONObject frame = base("credential_offer");
            frame.put("candidate_b64", Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(CANDIDATE));
            byte[] encrypted = send(frame);
            assertFalse(new String(encrypted, StandardCharsets.ISO_8859_1)
                    .contains("SYNTHETIC"));
            tv.onSyntheticOffer(encrypted);
            assertEquals(TvSealedHandoff.Stage.WAIT_CAPABILITIES, tv.stage());
        }

        byte[] authorize() throws Exception {
            return send(base("ha_commit_authorization").put("authorize_commit", true));
        }

        PairingAssistProofState capabilities() {
            PairingAssistProofState proof = new PairingAssistProofState();
            assertTrue(proof.challenge());
            assertTrue(proof.authenticated());
            assertTrue(proof.pipelineResult(
                    PairingAssistProofState.PIPELINES_REQUEST_ID, true, true));
            assertTrue(proof.scopedAlertResult(
                    PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));
            return proof;
        }

        JSONObject proof() throws Exception {
            byte[] encrypted = tv.syntheticTvProof(capabilities());
            JSONObject frame = receive(encrypted);
            assertEquals("tv_proof_receipt", frame.getString("type"));
            assertEquals("credential_offer", frame.getString("ack_for"));
            assertTrue(frame.getBoolean("assist_ok"));
            assertTrue(frame.getBoolean("alerts_ok"));
            assertEquals(transcript, frame.getString("transcript"));
            assertEquals(TV_UUID, frame.getString("tv_uuid"));
            return frame;
        }

        @Override public void close() {
            tv.close();
            ha.close();
        }
    }

    @Test public void fourMessageFlowRequiresSynchronousSyntheticPersistence() throws Exception {
        try (Peer p = new Peer()) {
            p.offer();
            p.proof();
            AtomicInteger writes = new AtomicInteger();
            AtomicReference<byte[]> saved = new AtomicReference<>();
            byte[] receipt = p.tv.onHaCommitForSynthetic(p.authorize(), candidate -> {
                writes.incrementAndGet();
                saved.set(candidate.clone());
                return true;
            });
            JSONObject response = p.receive(receipt);
            assertEquals("tv_persisted_receipt", response.getString("type"));
            assertEquals("ha_commit_authorization", response.getString("ack_for"));
            assertTrue(response.getBoolean("verified_store_committed"));
            assertEquals(TV_UUID, response.getString("tv_uuid"));
            assertEquals(1, writes.get());
            assertArrayEquals(CANDIDATE, saved.get());
            assertEquals(TvSealedHandoff.Stage.PERSISTED, p.tv.stage());
            try { p.tv.onHaCommitForSynthetic(p.authorize(), candidate -> true);
                fail("Repeat commit accepted"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void missingOrFailedDualCapabilityNeverSendsReceipt() throws Exception {
        try (Peer p = new Peer()) {
            p.offer();
            try { p.tv.syntheticTvProof(new PairingAssistProofState());
                fail("Unproven capabilities accepted"); }
            catch (IllegalStateException expected) { }
            assertEquals(TvSealedHandoff.Stage.ABORTED, p.tv.stage());
        }
        try (Peer p = new Peer()) {
            p.offer();
            PairingAssistProofState proof = p.capabilities();
            proof.fail();
            try { p.tv.syntheticTvProof(proof); fail("Failed capability accepted"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void forgedWrongIdentitySessionTranscriptOrExtraFieldsFailsClosed() throws Exception {
        for (String field : new String[] {"tv_uuid","session","transcript","v","extra"}) {
            try (Peer p = new Peer()) {
                JSONObject frame = p.base("credential_offer");
                frame.put("candidate_b64", Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(CANDIDATE));
                switch (field) {
                    case "tv_uuid": frame.put(field, WRONG_TV); break;
                    case "session": frame.put(field, "AQIDBAUGBwgJCgsMDQ4PEQ"); break;
                    case "transcript": frame.put(field, "0".repeat(64)); break;
                    case "v": frame.put(field, false); break;
                    default: frame.put("extra", 1);
                }
                try { p.tv.onSyntheticOffer(p.send(frame));
                    fail("Invalid offer accepted: " + field); }
                catch (IllegalArgumentException expected) { }
                assertEquals(TvSealedHandoff.Stage.ABORTED, p.tv.stage());
            }
        }
    }

    @Test public void candidateMustBeSyntheticAndCanonical() throws Exception {
        try (Peer p = new Peer()) {
            JSONObject offer = p.base("credential_offer")
                    .put("candidate_b64", Base64.getUrlEncoder().withoutPadding()
                            .encodeToString("access-token-value".getBytes(StandardCharsets.US_ASCII)));
            try { p.tv.onSyntheticOffer(p.send(offer));
                fail("Live-like credential accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        try (Peer p = new Peer()) {
            JSONObject offer = p.base("credential_offer").put("candidate_b64", "AA=");
            try { p.tv.onSyntheticOffer(p.send(offer));
                fail("Padded candidate accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void noCommitBeforeCapabilitiesAndPersistenceFailureNeverReceipts() throws Exception {
        try (Peer p = new Peer()) {
            p.offer();
            try { p.tv.onHaCommitForSynthetic(p.authorize(), c -> true);
                fail("Early commit accepted"); }
            catch (IllegalStateException expected) { }
            assertEquals(TvSealedHandoff.Stage.ABORTED, p.tv.stage());
        }
        try (Peer p = new Peer()) {
            p.offer();
            p.proof();
            AtomicInteger count = new AtomicInteger();
            try { p.tv.onHaCommitForSynthetic(p.authorize(), c -> {
                    count.incrementAndGet();
                    return false;
                }); fail("Failed disk commit sent receipt"); }
            catch (IllegalStateException expected) { }
            assertEquals(1, count.get());
            assertEquals(TvSealedHandoff.Stage.ABORTED, p.tv.stage());
        }
    }

    @Test public void tamperingReplayAndCrossDirectionPacketsFailClosed() throws Exception {
        try (Peer p = new Peer()) {
            JSONObject frame = p.base("credential_offer").put("candidate_b64",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(CANDIDATE));
            byte[] valid = p.send(frame);
            byte[] forged = valid.clone();
            forged[forged.length - 1] ^= 1;
            try { p.tv.onSyntheticOffer(forged); fail("Tampered GCM accepted"); }
            catch (IllegalArgumentException expected) { }
            assertEquals(TvSealedHandoff.Stage.ABORTED, p.tv.stage());
        }
        try (Peer p = new Peer()) {
            p.offer();
            p.proof();
            byte[] auth = p.authorize();
            p.tv.onHaCommitForSynthetic(auth, c -> true);
            try { p.tv.onHaCommitForSynthetic(auth, c -> true);
                fail("Replay accepted"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void expirationAndCancelledSessionNeverEmitProofOrAck() throws Exception {
        try (Peer p = new Peer()) {
            p.offer();
            p.clock.elapsed += 120_001L;
            try { p.tv.syntheticTvProof(p.capabilities());
                fail("Expired pairing produced proof"); }
            catch (IllegalStateException expected) { }
            assertEquals(TvSealedHandoff.Stage.ABORTED, p.tv.stage());
        }
        try (Peer p = new Peer()) {
            p.offer();
            p.proof();
            p.clock.epoch += 120;
            try { p.tv.onHaCommitForSynthetic(p.authorize(), c -> true);
                fail("Expired commit accepted"); }
            catch (IllegalStateException expected) { }
        }
        try (Peer p = new Peer()) {
            p.offer();
            p.tv.close();
            try { p.tv.syntheticTvProof(p.capabilities());
                fail("Cancelled pairing accepted"); }
            catch (IllegalStateException expected) { }
        }
    }
}
