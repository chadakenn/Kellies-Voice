package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the production v3 TV state, not a duplicated test policy. */
public final class PairingV3TvWireStateTest {
    private static final String SID = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String UUID = "123e4567-e89b-42d3-a456-426614174000";
    private static final byte[] HA_DER = Base64.getUrlDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q");
    private static final byte[] TV_DER = Base64.getUrlDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q");

    private static byte[] nonce(int start) {
        byte[] raw = new byte[32];
        for (int i=0;i<32;i++) raw[i] = (byte) (start + i);
        return raw;
    }

    private static KeyPair keys(int scalar, byte[] pub) throws Exception {
        AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
        p.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = p.getParameterSpec(ECParameterSpec.class);
        KeyFactory factory = KeyFactory.getInstance("EC");
        return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(pub)),
                factory.generatePrivate(
                    new ECPrivateKeySpec(BigInteger.valueOf(scalar), curve)));
    }

    private static final class Setup implements AutoCloseable {
        final AtomicLong clock = new AtomicLong(1000);
        final PairingV3TvWireState tv;
        final PairingKeyCommitment.Session ha;
        final KeyPair haKeys;
        Setup() throws Exception {
            tv = new PairingV3TvWireState(keys(1, TV_DER), SID, UUID,
                    clock::get, nonce(0));
            haKeys = keys(2, HA_DER);
            ha = new PairingKeyCommitment.Session("ha", SID,
                    haKeys.getPublic().getEncoded(), nonce(32));
        }
        void commits() {
            byte[] tvCommit = tv.acceptHaCommit(SID, ha.publicCommit());
            ha.receivePeerCommit(tvCommit);
        }
        void haReveal() {
            PairingKeyCommitment.Reveal r = ha.reveal();
            tv.acceptHaReveal(SID, r.publicDer, r.nonce);
        }
        void revealTv() {
            PairingV3TvWireState.TvReveal r = tv.revealTv(SID);
            ha.receivePeerReveal(r.tvKey, r.tvNonce);
        }
        @Override public void close() { tv.close(); ha.close(); }
    }

    @Test public void exactRealCrossPeerHandshakeAndPhysicalRemoteProof() throws Exception {
        try (Setup x = new Setup()) {
            assertNull(x.tv.approvedStatus(SID));
            x.commits(); x.haReveal(); x.revealTv();
            assertEquals("83428994", x.tv.codeForTv(SID));
            assertEquals("83428994", PairingV3Verified.comparisonCode(
                    x.haKeys.getPrivate(), x.ha));
            assertNull(x.tv.approvedStatus(SID));
            PairingV3TvWireState.Approval approved = x.tv.approveFromPhysicalTvRemote(SID);
            assertEquals(UUID, approved.tvUuid);
            assertEquals("TuUIMiT-zkUz3XaKygd5Gw", approved.proof);
            assertEquals(approved.proof, x.tv.approvedStatus(SID).proof);
        }
    }

    @Test public void mustCommitBeforeHaRevealAndRevealTvOnlyAfterHaVerified() throws Exception {
        try (Setup x = new Setup()) {
            try { x.tv.revealTv(SID); fail("TV revealed early"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
        try (Setup x = new Setup()) {
            x.commits();
            try { x.tv.revealTv(SID); fail("TV revealed before HA proof"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
    }

    @Test public void incorrectHaNonceClosesSessionAndDeniesRetry() throws Exception {
        try (Setup x = new Setup()) {
            x.commits();
            PairingKeyCommitment.Reveal r = x.ha.reveal();
            try { x.tv.acceptHaReveal(SID, r.publicDer, nonce(33));
                fail("Accepted substituted HA key"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(x.tv.isClosed());
            try { x.tv.acceptHaReveal(SID, r.publicDer, r.nonce);
                fail("Retried after bad reveal"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void wrongSessionIsFatalAndCannotBeUsedForAnotherTv() throws Exception {
        try (Setup x = new Setup()) {
            try { x.tv.acceptHaCommit("AQIDBAUGBwgJCgsMDQ4PEQ", x.ha.publicCommit());
                fail("Cross-session"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
    }

    @Test public void physicalRemoteOkNeverWorksBeforeFinalReveal() throws Exception {
        try (Setup x = new Setup()) {
            x.commits(); x.haReveal();
            try { x.tv.approveFromPhysicalTvRemote(SID);
                fail("Remote OK before final reveal"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
    }

    @Test public void windowExpiryBackgroundCloseAndReplaysNeverEmitProof() throws Exception {
        try (Setup x = new Setup()) {
            x.commits(); x.haReveal(); x.revealTv();
            x.clock.addAndGet(PairingV3TvWireState.TIMEOUT_MS);
            try { x.tv.approveFromPhysicalTvRemote(SID); fail("Expired"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
        try (Setup x = new Setup()) {
            x.commits(); x.haReveal(); x.revealTv();
            x.tv.approveFromPhysicalTvRemote(SID);
            try { x.tv.approveFromPhysicalTvRemote(SID);
                fail("Double OK accepted"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
    }

    @Test public void duplicateCommitAndRevealAreRejected() throws Exception {
        try (Setup x = new Setup()) {
            x.commits();
            try { x.tv.acceptHaCommit(SID, x.ha.publicCommit());
                fail("Replay commit"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
        try (Setup x = new Setup()) {
            x.commits(); x.haReveal(); x.revealTv();
            try { x.tv.revealTv(SID); fail("Double TV reveal"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
    }

    @Test public void malformedCommitIsRejectedWithoutExposingAnyKey() throws Exception {
        try (Setup x = new Setup()) {
            try { x.tv.acceptHaCommit(SID, new byte[3]); fail("Bad hash"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(x.tv.isClosed());
        }
    }
    private static byte[] receiptFixture() {
        String hex = "e5604d85a1208646dc1e6db915a359d53967bb7822568f04b77b8ba2146c";
        byte[] body = new byte[hex.length() / 2];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return body;
    }

    @Test public void actualTvStateRequiresOwnerReceiptAfterPhysicalOkAndAcksCryptographically()
            throws Exception {
        try (Setup x = new Setup()) {
            x.commits(); x.haReveal(); x.revealTv();
            PairingV3QaPingChannel haAck = new PairingV3QaPingChannel(
                    x.haKeys.getPrivate(), x.ha, UUID, 60000, x.clock.get());
            try {
                // No encrypted receipt before real physical TV remote action.
                try { x.tv.consumeOwnerReceipt(SID, 1, receiptFixture());
                    fail("Accepted owner receipt without physical remote OK"); }
                catch (IllegalStateException expected) { }
                assertTrue(x.tv.isClosed());
            } finally { haAck.close(); }
        }
        try (Setup x = new Setup()) {
            x.commits(); x.haReveal(); x.revealTv();
            x.tv.approveFromPhysicalTvRemote(SID);
            try (PairingV3QaPingChannel haAck = new PairingV3QaPingChannel(
                    x.haKeys.getPrivate(), x.ha, UUID, 60000, x.clock.get())) {
                PairingV3QaPingChannel.Packet ack = x.tv.consumeOwnerReceipt(
                        SID, 1, receiptFixture());
                assertEquals(1L, ack.sequence);
                assertEquals("qa-pong", new String(haAck.open(
                    ack.sequence, ack.ciphertext, x.clock.get()),
                    java.nio.charset.StandardCharsets.US_ASCII));
                assertTrue(x.tv.isClosed());
                try { x.tv.consumeOwnerReceipt(SID, 1, receiptFixture());
                    fail("Owner receipt replay"); }
                catch (IllegalStateException expected) { }
            }
        }
    }

    @Test public void invalidOwnerReceiptCannotCompleteAndClosesTvTranscript()
            throws Exception {
        try (Setup x = new Setup()) {
            x.commits(); x.haReveal(); x.revealTv();
            x.tv.approveFromPhysicalTvRemote(SID);
            byte[] corrupted = receiptFixture();
            corrupted[0] ^= 1;
            try { x.tv.consumeOwnerReceipt(SID, 1, corrupted);
                fail("Tampered owner approval"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(x.tv.isClosed());
        }
        try (Setup x = new Setup()) {
            x.commits(); x.haReveal(); x.revealTv();
            x.tv.approveFromPhysicalTvRemote(SID);
            x.clock.addAndGet(PairingV3TvWireState.TIMEOUT_MS);
            try { x.tv.consumeOwnerReceipt(SID, 1, receiptFixture());
                fail("Expired receipt"); }
            catch (IllegalStateException expected) { }
            assertTrue(x.tv.isClosed());
        }
    }

}
