package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real P-256 transcript, frozen Python/JVM AES-GCM interoperability vectors. */
public final class PairingV3QaPingChannelTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String UUID = "123e4567-e89b-42d3-a456-426614174000";
    private static final byte[] TV_DER = Base64.getUrlDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q");
    private static final byte[] HA_DER = Base64.getUrlDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q");

    private static byte[] nonce(int start) {
        byte[] x = new byte[32];
        for (int i = 0; i < 32; i++) x[i] = (byte) (start + i);
        return x;
    }

    private static PrivateKey scalar(int number) throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = params.getParameterSpec(ECParameterSpec.class);
        return KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(BigInteger.valueOf(number), curve));
    }

    private static String hex(byte[] bytes) {
        StringBuilder s = new StringBuilder();
        for (byte b : bytes) s.append(String.format("%02x", b & 255));
        return s.toString();
    }

    private static final class Pair implements AutoCloseable {
        final PairingKeyCommitment.Session tv =
                new PairingKeyCommitment.Session("tv", SESSION, TV_DER, nonce(0));
        final PairingKeyCommitment.Session ha =
                new PairingKeyCommitment.Session("ha", SESSION, HA_DER, nonce(32));
        final PairingV3QaPingChannel tvChannel;
        final PairingV3QaPingChannel haChannel;
        Pair() throws Exception {
            tv.receivePeerCommit(ha.publicCommit());
            ha.receivePeerCommit(tv.publicCommit());
            PairingKeyCommitment.Reveal fromHa = ha.reveal();
            PairingKeyCommitment.Reveal fromTv = tv.reveal();
            tv.receivePeerReveal(fromHa.publicDer, fromHa.nonce);
            ha.receivePeerReveal(fromTv.publicDer, fromTv.nonce);
            tvChannel = new PairingV3QaPingChannel(
                    scalar(1), tv, UUID, 60000, 100);
            haChannel = new PairingV3QaPingChannel(
                    scalar(2), ha, UUID, 60000, 100);
        }
        @Override public void close() {
            tvChannel.close();
            haChannel.close();
            tv.close();
            ha.close();
        }
    }

    @Test public void fixedPythonVectorsAndTwoWayAES() throws Exception {
        try (Pair p = new Pair()) {
            PairingV3QaPingChannel.Packet a =
                    p.tvChannel.seal("qa-ping".getBytes(StandardCharsets.US_ASCII), 101);
            assertEquals(1L, a.sequence);
            assertEquals("32cced09c0407f709d358666bff467c580c102e4fea474",
                    hex(a.ciphertext));
            assertEquals("qa-ping", new String(p.haChannel.open(
                    a.sequence, a.ciphertext, 101), StandardCharsets.US_ASCII));
            PairingV3QaPingChannel.Packet b =
                    p.haChannel.seal("qa-pong".getBytes(StandardCharsets.US_ASCII), 101);
            assertEquals("c96732d43dab9211e1d9a068bdd766428e25a33a9ae4d7",
                    hex(b.ciphertext));
            assertEquals("qa-pong", new String(p.tvChannel.open(
                    b.sequence, b.ciphertext, 101), StandardCharsets.US_ASCII));
        }
    }

    @Test public void tamperFailsClosedAndNoReplay() throws Exception {
        try (Pair p = new Pair()) {
            PairingV3QaPingChannel.Packet a =
                    p.tvChannel.seal("qa-ping".getBytes(StandardCharsets.US_ASCII), 101);
            a.ciphertext[a.ciphertext.length - 1] ^= 1;
            try { p.haChannel.open(a.sequence, a.ciphertext, 101); fail("Tampered"); }
            catch (IllegalArgumentException expected) { }
            assertTrue(p.haChannel.isClosed());
        }
        try (Pair p = new Pair()) {
            PairingV3QaPingChannel.Packet a =
                    p.tvChannel.seal("qa-ping".getBytes(StandardCharsets.US_ASCII), 101);
            p.haChannel.open(a.sequence, a.ciphertext, 101);
            try { p.haChannel.open(a.sequence, a.ciphertext, 101); fail("Replay"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void noTokenPayloadAndExpiredChannelRejected() throws Exception {
        try (Pair p = new Pair()) {
            try { p.tvChannel.seal("refresh-secret".getBytes(StandardCharsets.US_ASCII),
                    101); fail("Token path"); }
            catch (IllegalArgumentException expected) { }
            try { p.tvChannel.seal("qa-ping".getBytes(StandardCharsets.US_ASCII),
                    60100); fail("Expiration"); }
            catch (IllegalStateException expected) { }
            assertTrue(p.tvChannel.isClosed());
        }
    }

    @Test public void mustCompleteCommitmentsBeforeChannelKeyDerivation()
            throws Exception {
        PairingKeyCommitment.Session pending =
                new PairingKeyCommitment.Session("tv", SESSION, TV_DER, nonce(0));
        try {
            new PairingV3QaPingChannel(scalar(1), pending, UUID, 60000, 100);
            fail("Unverified keys");
        } catch (IllegalStateException expected) {
        } finally {
            pending.close();
        }
    }
}
