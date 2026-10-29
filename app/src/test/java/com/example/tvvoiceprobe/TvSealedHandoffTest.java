package com.example.tvvoiceprobe;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TvSealedHandoffTest {

    private static final String TV = "123e4567-e89b-42d3-a456-426614174000";
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TRANSCRIPT = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final long EXPIRES = 1_000_000L;

    private static class FakeChannel implements TvSealedHandoff.SealedChannel {
        byte[] lastSealed;
        boolean closed;
        boolean failOpen;

        @Override public byte[] open(byte[] packet) {
            if (failOpen) throw new RuntimeException("open failed");
            return packet;
        }
        @Override public byte[] seal(byte[] plaintext) {
            lastSealed = plaintext;
            return plaintext;
        }
        @Override public void close() { closed = true; }
    }

    private static class FakeSink implements TvSealedHandoff.FakeDiskSink {
        boolean fail;
        boolean committed;
        @Override public boolean commit(String tvUuid, String session, byte[] candidate) {
            if (fail) return false;
            committed = true;
            return true;
        }
    }

    private static class Evidence implements TvSealedHandoff.ProofEvidence {
        boolean assist = true;
        boolean alerts = true;
        @Override public boolean assistSucceeded() { return assist; }
        @Override public boolean alertsSucceeded() { return alerts; }
    }

    private byte[] offerPacket() throws Exception {
        JSONObject doc = new JSONObject();
        doc.put("v", 1);
        doc.put("type", "credential_offer");
        doc.put("tv_uuid", TV);
        doc.put("session", SESSION);
        doc.put("transcript", TRANSCRIPT);
        doc.put("expires_at", EXPIRES);
        doc.put("candidate_b64", Base64.getUrlEncoder().withoutPadding()
                .encodeToString("SYNTHETIC".getBytes(StandardCharsets.US_ASCII)));
        return doc.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] authPacket() throws Exception {
        JSONObject doc = new JSONObject();
        doc.put("v", 1);
        doc.put("type", "ha_commit_authorization");
        doc.put("tv_uuid", TV);
        doc.put("session", SESSION);
        doc.put("transcript", TRANSCRIPT);
        doc.put("expires_at", EXPIRES);
        doc.put("authorize_commit", true);
        return doc.toString().getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    public void happyPathStagesSyntheticAndPersistsOnlyAfterAuthorization() throws Exception {
        FakeChannel channel = new FakeChannel();
        FakeSink sink = new FakeSink();
        TvSealedHandoff handoff = new TvSealedHandoff(
                channel, TV, SESSION, TRANSCRIPT, EXPIRES, sink);
        handoff.acceptOffer(offerPacket(), EXPIRES - 10);
        assertEquals(TvSealedHandoff.Stage.OFFER_RECEIVED, handoff.stage());
        byte[] proof = handoff.sendProofReceipt(new Evidence(), EXPIRES - 10);
        assertTrue(new String(proof, StandardCharsets.US_ASCII).contains("tv_proof_receipt"));
        handoff.acceptAuthorization(authPacket(), EXPIRES - 10);
        byte[] persisted = handoff.sendPersistedReceipt(EXPIRES - 10);
        assertTrue(new String(persisted, StandardCharsets.US_ASCII).contains("tv_persisted_receipt"));
        assertEquals(TvSealedHandoff.Stage.PERSISTED, handoff.stage());
        assertTrue(sink.committed);
        assertTrue(channel.closed);
    }

    @Test
    public void rejectsOutOfOrderAuthorization() throws Exception {
        FakeChannel channel = new FakeChannel();
        TvSealedHandoff handoff = new TvSealedHandoff(
                channel, TV, SESSION, TRANSCRIPT, EXPIRES, new FakeSink());
        try {
            handoff.acceptAuthorization(authPacket(), EXPIRES - 10);
            fail("expected rejection");
        } catch (IllegalStateException expected) {
            assertEquals(TvSealedHandoff.Stage.ABORTED, handoff.stage());
        }
    }

    @Test
    public void rejectsExpiredOffer() throws Exception {
        FakeChannel channel = new FakeChannel();
        TvSealedHandoff handoff = new TvSealedHandoff(
                channel, TV, SESSION, TRANSCRIPT, EXPIRES, new FakeSink());
        try {
            handoff.acceptOffer(offerPacket(), EXPIRES);
            fail("expected rejection");
        } catch (IllegalStateException expected) {
            assertEquals(TvSealedHandoff.Stage.ABORTED, handoff.stage());
        }
    }

    @Test
    public void rejectsMissingAssistEvidence() throws Exception {
        FakeChannel channel = new FakeChannel();
        TvSealedHandoff handoff = new TvSealedHandoff(
                channel, TV, SESSION, TRANSCRIPT, EXPIRES, new FakeSink());
        handoff.acceptOffer(offerPacket(), EXPIRES - 10);
        Evidence evidence = new Evidence();
        evidence.assist = false;
        try {
            handoff.sendProofReceipt(evidence, EXPIRES - 10);
            fail("expected rejection");
        } catch (IllegalStateException expected) {
            assertEquals(TvSealedHandoff.Stage.ABORTED, handoff.stage());
        }
    }

    @Test
    public void rejectsDiskFailureAndDoesNotEmitReceipt() throws Exception {
        FakeChannel channel = new FakeChannel();
        FakeSink sink = new FakeSink();
        sink.fail = true;
        TvSealedHandoff handoff = new TvSealedHandoff(
                channel, TV, SESSION, TRANSCRIPT, EXPIRES, sink);
        handoff.acceptOffer(offerPacket(), EXPIRES - 10);
        handoff.sendProofReceipt(new Evidence(), EXPIRES - 10);
        handoff.acceptAuthorization(authPacket(), EXPIRES - 10);
        try {
            handoff.sendPersistedReceipt(EXPIRES - 10);
            fail("expected rejection");
        } catch (IllegalStateException expected) {
            assertEquals(TvSealedHandoff.Stage.ABORTED, handoff.stage());
        }
    }

    @Test
    public void rejectsWrongTvUuid() throws Exception {
        FakeChannel channel = new FakeChannel();
        TvSealedHandoff handoff = new TvSealedHandoff(
                channel, "223e4567-e89b-42d3-a456-426614174000",
                SESSION, TRANSCRIPT, EXPIRES, new FakeSink());
        try {
            handoff.acceptOffer(offerPacket(), EXPIRES - 10);
            fail("expected rejection");
        } catch (IllegalStateException expected) {
            assertEquals(TvSealedHandoff.Stage.ABORTED, handoff.stage());
        }
    }
}
