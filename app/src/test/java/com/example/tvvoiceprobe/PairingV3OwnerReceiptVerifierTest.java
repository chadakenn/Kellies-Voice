package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import org.junit.Test;
import static org.junit.Assert.*;

/** Cross-language v3 encrypted owner-receipt fixture, never an HA login. */
public final class PairingV3OwnerReceiptVerifierTest {
    private static final String SID = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String UUID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String OTHER_UUID = "123e4567-e89b-42d3-a456-426614174001";
    private static final String FIXTURE =
            "e5604d85a1208646dc1e6db915a359d53967bb7822568f04b77b8ba2146c";
    private static final byte[] TV_DER = Base64.getUrlDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q");
    private static final byte[] HA_DER = Base64.getUrlDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q");

    private static byte[] nonce(int value) {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (value + i);
        return bytes;
    }

    private static byte[] fixed() {
        byte[] out = new byte[FIXTURE.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(
                    FIXTURE.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static PrivateKey scalar(int value) throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = params.getParameterSpec(ECParameterSpec.class);
        return KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(BigInteger.valueOf(value), curve));
    }

    static final class Session implements AutoCloseable {
        final PairingKeyCommitment.Session tv;
        final PairingKeyCommitment.Session ha;
        Session(String sid) {
            tv = new PairingKeyCommitment.Session("tv", sid, TV_DER, nonce(0));
            ha = new PairingKeyCommitment.Session("ha", sid, HA_DER, nonce(32));
            tv.receivePeerCommit(ha.publicCommit());
            ha.receivePeerCommit(tv.publicCommit());
            PairingKeyCommitment.Reveal t = tv.reveal();
            PairingKeyCommitment.Reveal h = ha.reveal();
            tv.receivePeerReveal(h.publicDer, h.nonce);
            ha.receivePeerReveal(t.publicDer, t.nonce);
        }
        @Override public void close() {
            tv.close();
            ha.close();
        }
    }

    @Test public void pythonIssuerCiphertextAuthenticatesOnJVM() throws Exception {
        try (Session p = new Session(SID);
             PairingV3OwnerReceiptVerifier v =
                 new PairingV3OwnerReceiptVerifier(scalar(1), p.tv,
                                                   UUID, 60_000, 100)) {
            assertTrue(v.accept(1L, fixed(), 102));
            assertTrue(v.isClosed());
            try { v.accept(1L, fixed(), 103); fail("Replay accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void wrongTvAndWrongSessionAndWrongRoleDenied() throws Exception {
        try (Session p = new Session(SID);
             PairingV3OwnerReceiptVerifier wrong =
                 new PairingV3OwnerReceiptVerifier(scalar(1), p.tv,
                                                   OTHER_UUID, 60_000, 100)) {
            try { wrong.accept(1, fixed(), 102); fail("Wrong TV"); }
            catch (IllegalArgumentException expected) { }
        }
        try (Session p = new Session("AQIDBAUGBwgJCgsMDQ4PEQ");
             PairingV3OwnerReceiptVerifier wrong =
                 new PairingV3OwnerReceiptVerifier(scalar(1), p.tv,
                                                   UUID, 60_000, 100)) {
            try { wrong.accept(1, fixed(), 102); fail("Wrong v3 session"); }
            catch (IllegalArgumentException expected) { }
        }
        try (Session p = new Session(SID)) {
            try {
                new PairingV3OwnerReceiptVerifier(scalar(2), p.ha,
                                                  UUID, 60_000, 100);
                fail("HA cannot pose as TV");
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void tamperSequenceExpiryAndUnverifiedSessionBurned() throws Exception {
        try (Session p = new Session(SID);
             PairingV3OwnerReceiptVerifier v =
                 new PairingV3OwnerReceiptVerifier(scalar(1), p.tv,
                                                   UUID, 60_000, 100)) {
            byte[] packet = fixed();
            packet[29] ^= 1;
            try { v.accept(1L, packet, 101); fail("Tamper"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(v.isClosed());
        }
        try (Session p = new Session(SID);
             PairingV3OwnerReceiptVerifier v =
                 new PairingV3OwnerReceiptVerifier(scalar(1), p.tv,
                                                   UUID, 60_000, 100)) {
            try { v.accept(2L, fixed(), 101); fail("Wrong sequence"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(v.isClosed());
        }
        try (Session p = new Session(SID);
             PairingV3OwnerReceiptVerifier v =
                 new PairingV3OwnerReceiptVerifier(scalar(1), p.tv,
                                                   UUID, 60_000, 100)) {
            try { v.accept(1L, fixed(), 60100); fail("Expired"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(v.isClosed());
        }
        PairingKeyCommitment.Session incomplete =
                new PairingKeyCommitment.Session("tv", SID, TV_DER, nonce(0));
        try {
            new PairingV3OwnerReceiptVerifier(
                scalar(1), incomplete, UUID, 60_000, 100);
            fail("Unverified transcript");
        } catch (IllegalStateException expected) {
        } finally {
            incomplete.close();
        }
    }
}
