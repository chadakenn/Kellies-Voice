package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual P-256 v3 transcript -> TV staged grant, independent signed ACKs. */
public final class PairingV3ScopedTokenHandoffTest {
    private static final String SID = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TV = "123e4567-e89b-42d3-a456-426614174000";
    private static final String ORIGIN = "https://ha.example.test";
    private static final String CLIENT = ORIGIN + "/kellies-voice/tv/" + TV
            + "/attempt/0123456789abcdef0123456789abcdef";
    private static final byte[] TV_DER = Base64.getUrlDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q");
    private static final byte[] HA_DER = Base64.getUrlDecoder().decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q");

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
    private static byte[] nonce(int start) {
        byte[] out = new byte[32];
        for (int i=0; i<32; i++) out[i] = (byte) (start + i);
        return out;
    }
    private static PrivateKey scalar(int n) throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = params.getParameterSpec(ECParameterSpec.class);
        return KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(BigInteger.valueOf(n), curve));
    }
    private static byte[] part(byte[] value) {
        return ByteBuffer.allocate(2+value.length)
                .putShort((short)value.length).put(value).array();
    }
    private static byte[] join(byte[]... items) {
        int size = 0;
        for(byte[] item : items) size += item.length;
        ByteBuffer b = ByteBuffer.allocate(size);
        for(byte[] item : items) b.put(item);
        return b.array();
    }
    private static byte[] directionKey(byte[] secret, int dir)
            throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(join(ascii("KelliesVoice/v3/scoped-token-v1/key\0"),
                part(Base64.getUrlDecoder().decode(SID)), part(ascii(TV)),
                part(new byte[] {(byte)dir}), new byte[] {1}));
    }
    private static byte[] aad(int dir, long seq) {
        return join(ascii("KelliesVoice/v3/scoped-token-v1/aad\0"),
                part(Base64.getUrlDecoder().decode(SID)), part(ascii(TV)),
                new byte[] {(byte) dir},
                ByteBuffer.allocate(8).putLong(seq).array());
    }
    private static byte[] iv(long seq) {
        return ByteBuffer.allocate(12).putInt(0).putLong(seq).array();
    }

    private static final class Vault implements PairingV3ScopedTokenHandoff.Vault {
        int staged;
        int activated;
        String storedId;
        @Override public void stage(
                TvRenewableLoginPolicy.Grant grant, String refresh,
                String access, long lifetime) {
            assertEquals(CLIENT, grant.clientId);
            assertEquals("recorded-token-id", grant.tokenId);
            assertEquals("SECRET-REFRESH-TOKEN", refresh);
            assertEquals("SECRET-ACCESS-TOKEN", access);
            assertEquals(1800, lifetime);
            staged++;
            storedId = grant.tokenId;
        }
        @Override public void activateAfterVerifiedServerCommit(
                String tv, String tokenId) {
            assertEquals(TV, tv);
            assertEquals(storedId, tokenId);
            assertEquals(1, staged);
            activated++;
        }
    }

    private static final class Pair implements AutoCloseable {
        final PairingKeyCommitment.Session tv =
                new PairingKeyCommitment.Session("tv", SID, TV_DER, nonce(0));
        final PairingKeyCommitment.Session ha =
                new PairingKeyCommitment.Session("ha", SID, HA_DER, nonce(32));
        final PairingV3ScopedTokenHandoff receiver;
        final byte[] secret;
        Pair() throws Exception {
            tv.receivePeerCommit(ha.publicCommit());
            ha.receivePeerCommit(tv.publicCommit());
            PairingKeyCommitment.Reveal a = tv.reveal();
            PairingKeyCommitment.Reveal b = ha.reveal();
            tv.receivePeerReveal(b.publicDer, b.nonce);
            ha.receivePeerReveal(a.publicDer, a.nonce);
            secret = PairingV3Verified.qaChannelMaterial(scalar(2), ha);
            receiver = new PairingV3ScopedTokenHandoff(
                    scalar(1), tv, TV, ORIGIN, 60000, 100);
        }
        byte[] fromHa(long seq, byte[] plaintext) throws Exception {
            byte[] key = directionKey(secret, 2);
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key,"AES"),
                        new GCMParameterSpec(128, iv(seq)));
                cipher.updateAAD(aad(2, seq));
                return cipher.doFinal(plaintext);
            } finally { Arrays.fill(key, (byte) 0); }
        }
        String tvAck(PairingV3ScopedTokenHandoff.Packet packet) throws Exception {
            byte[] key = directionKey(secret, 1);
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                        new GCMParameterSpec(128, iv(packet.sequence)));
                cipher.updateAAD(aad(1, packet.sequence));
                return new String(cipher.doFinal(packet.sealed),
                        StandardCharsets.US_ASCII);
            } finally { Arrays.fill(key, (byte) 0); }
        }
        String payload(String client) {
            return "{\"access\":\"SECRET-ACCESS-TOKEN\","
                    + "\"client\":\"" + client + "\","
                    + "\"expires\":1800,\"id\":\"recorded-token-id\","
                    + "\"refresh\":\"SECRET-REFRESH-TOKEN\","
                    + "\"tv\":\"" + TV + "\",\"v\":1}";
        }
        @Override public void close() {
            receiver.close();
            tv.close(); ha.close();
            Arrays.fill(secret, (byte) 0);
        }
    }

    @Test public void encryptedGrantStagesThenRequiresSignedCommitBeforeActivation()
            throws Exception {
        try (Pair x = new Pair()) {
            Vault vault = new Vault();
            PairingV3ScopedTokenHandoff.Packet stage = x.receiver.stageProvisional(
                    1, x.fromHa(1, ascii(x.payload(CLIENT))), 101, vault);
            assertEquals("staged", x.tvAck(stage));
            assertEquals(1, vault.staged);
            assertEquals(0, vault.activated);
            PairingV3ScopedTokenHandoff.Packet activated =
                    x.receiver.activateAfterCommitReady(
                        2, x.fromHa(2, ascii("commit-ready")), 102, vault);
            assertEquals("activated", x.tvAck(activated));
            assertEquals(1, vault.activated);
            try { x.receiver.stageProvisional(
                    1, x.fromHa(1, ascii(x.payload(CLIENT))), 103, vault);
                fail("Replay accepted");
            } catch (IllegalStateException expected) { }
        }
    }

    @Test public void tamperAndUnapprovedClientCannotStageOrAck()
            throws Exception {
        try (Pair x = new Pair()) {
            Vault vault = new Vault();
            byte[] tampered = x.fromHa(1, ascii(x.payload(CLIENT)));
            tampered[tampered.length-1] ^= 1;
            try { x.receiver.stageProvisional(1, tampered, 101, vault);
                fail("Tampered token accepted");
            } catch (IllegalArgumentException expected) { }
            assertEquals(0, vault.staged);
        }
        try (Pair x = new Pair()) {
            Vault vault = new Vault();
            String evil = ORIGIN + "/kellies-voice/tv/" + TV
                    + "/attempt/ffffffffffffffffffffffffffffffff";
            // This is a DIFFERENT legitimate per-attempt client ID. It is
            // permitted only if the HA server actually issued it.
            // A forged different *host* must never be accepted.
            evil = "https://evil.example.test/kellies-voice/tv/" + TV
                    + "/attempt/0123456789abcdef0123456789abcdef";
            try { x.receiver.stageProvisional(
                    1, x.fromHa(1, ascii(x.payload(evil))), 101, vault);
                fail("Foreign token issuer accepted");
            } catch (IllegalArgumentException expected) { }
            assertEquals(0, vault.staged);
        }
    }

    @Test public void cannotActivateBeforeStageOrAfterExpiry() throws Exception {
        try (Pair x = new Pair()) {
            Vault vault = new Vault();
            try { x.receiver.activateAfterCommitReady(
                    2, x.fromHa(2, ascii("commit-ready")), 101, vault);
                fail("No staged grant");
            } catch (IllegalStateException expected) { }
            assertEquals(0, vault.activated);
        }
        try (Pair x = new Pair()) {
            Vault vault = new Vault();
            try { x.receiver.stageProvisional(
                    1, x.fromHa(1, ascii(x.payload(CLIENT))), 60100, vault);
                fail("Expired v3");
            } catch (IllegalStateException expected) { }
            assertEquals(0, vault.staged);
        }
    }
}
