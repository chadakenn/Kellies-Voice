package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import org.junit.Test;
import static org.junit.Assert.*;

/** No secrets: the only sample payload is a known public preview phrase. */
public class PairingSealedChannelTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TV =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q";
    private static final String HA =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q";
    private static final String WIRE =
            "01000000000000000000000102030405060708090a0b"
            + "f29a8b99db35f1a23b424b1c0cf5a5a3ecbd7cf98b39cbbc68e2ae4b14808ff445d0c78a";

    private static byte[] fromUrl(String b64) {
        return Base64.getUrlDecoder().decode(b64);
    }

    private static byte[] unhex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    private static KeyPair key(int scalar, String pub) throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = params.getParameterSpec(ECParameterSpec.class);
        KeyFactory factory = KeyFactory.getInstance("EC");
        return new KeyPair(
                factory.generatePublic(new X509EncodedKeySpec(fromUrl(pub))),
                factory.generatePrivate(new ECPrivateKeySpec(BigInteger.valueOf(scalar), curve)));
    }

    private static PairingSealedChannel tv() throws Exception {
        return new PairingSealedChannel(key(1, TV), true, SESSION, fromUrl(TV), fromUrl(HA));
    }

    private static PairingSealedChannel ha() throws Exception {
        return new PairingSealedChannel(key(2, HA), false, SESSION, fromUrl(TV), fromUrl(HA));
    }

    @Test public void crossLanguageHaToTvVectorMatchesPython() throws Exception {
        try (PairingSealedChannel tv = tv(); PairingSealedChannel ha = ha()) {
            byte[] nonce = new byte[12];
            for (int i = 0; i < 12; i++) nonce[i] = (byte) i;
            byte[] packet = ha.sealWithNonce(
                    "PAIRING PREVIEW ONLY".getBytes(StandardCharsets.US_ASCII), nonce);
            assertArrayEquals(unhex(WIRE), packet);
            assertEquals("PAIRING PREVIEW ONLY",
                    new String(tv.open(packet), StandardCharsets.US_ASCII));
            try { tv.open(packet); fail("Replay accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void tvCanReplyButReflectedPacketCannotBeOpened() throws Exception {
        try (PairingSealedChannel tv = tv(); PairingSealedChannel ha = ha()) {
            byte[] packet = tv.seal("TV CONFIRMED".getBytes(StandardCharsets.US_ASCII));
            assertEquals(1, packet[1]);
            assertEquals("TV CONFIRMED", new String(ha.open(packet), StandardCharsets.US_ASCII));
            try { tv.open(packet); fail("Reflection accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void tamperAndWrongSessionAreRejectedWithoutAdvancingCounters() throws Exception {
        try (PairingSealedChannel tv = tv(); PairingSealedChannel ha = ha()) {
            byte[] packet = ha.seal("safe".getBytes(StandardCharsets.US_ASCII));
            for (int index : new int[] {0, 1, 9, 11, 22, packet.length - 1}) {
                byte[] forged = packet.clone();
                forged[index] ^= 1;
                try { tv.open(forged); fail("Tampering accepted at " + index); }
                catch (IllegalArgumentException expected) { }
            }
            try (PairingSealedChannel other = new PairingSealedChannel(
                    key(1, TV), true, "AQIDBAUGBwgJCgsMDQ4PEQ",
                    fromUrl(TV), fromUrl(HA))) {
                try { other.open(packet); fail("Wrong pairing session accepted"); }
                catch (IllegalArgumentException expected) { }
            }
            assertEquals("safe", new String(tv.open(packet), StandardCharsets.US_ASCII));
        }
    }

    @Test public void closeReplayReorderingAndOversizeFailClosed() throws Exception {
        PairingSealedChannel tv = tv(), ha = ha();
        try {
            byte[] first = ha.seal("first".getBytes(StandardCharsets.US_ASCII));
            byte[] second = ha.seal("second".getBytes(StandardCharsets.US_ASCII));
            try { tv.open(second); fail("Out-of-order accepted"); }
            catch (IllegalArgumentException expected) { }
            assertEquals("first", new String(tv.open(first), StandardCharsets.US_ASCII));
            assertEquals("second", new String(tv.open(second), StandardCharsets.US_ASCII));
            try { ha.seal(new byte[2049]); fail("Oversized plaintext accepted"); }
            catch (IllegalStateException expected) { }
        } finally {
            tv.close(); ha.close();
        }
        try { tv.open(unhex(WIRE)); fail("Closed receiver accepted"); }
        catch (IllegalArgumentException expected) { }
        try { ha.seal(new byte[] {1}); fail("Closed sender accepted"); }
        catch (IllegalStateException expected) { }
    }
}
