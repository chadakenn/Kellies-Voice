package com.example.tvvoiceprobe;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

/** TV side of *offline* v3 encrypted renewable-token transport.
 *
 * NO listener or UI invokes this yet. A verified v3 ECDH transcript alone
 * does NOT prove HA owner authorization or TV remote OK. The actual caller
 * must enforce those proofs before creating this class. No standalone bearer
 * endpoint is exposed and no token is activated by decrypting a packet.
 */
final class PairingV3ScopedTokenHandoff implements AutoCloseable {
    private static final byte[] KEY_DOMAIN =
            ascii("KelliesVoice/v3/scoped-token-v1/key\0");
    private static final byte[] AAD_DOMAIN =
            ascii("KelliesVoice/v3/scoped-token-v1/aad\0");
    private static final int SEND = 1;
    private static final int RECEIVE = 2;
    private final byte[] sendKey;
    private final byte[] receiveKey;
    private final byte[] session;
    private final byte[] tv;
    private final String tvUuid;
    private final String expectedOrigin;
    private final long expires;
    private int phase;
    private boolean closed;
    private String tokenId;

    static final class Packet {
        final long sequence;
        final byte[] sealed;
        Packet(long seq, byte[] bytes) {
            sequence = seq;
            sealed = bytes.clone();
        }
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] part(byte[] raw) {
        if (raw == null || raw.length > 65535)
            throw new IllegalArgumentException("Invalid v3 field");
        return ByteBuffer.allocate(raw.length + 2)
                .putShort((short) raw.length).put(raw).array();
    }

    private static byte[] join(byte[]... fields) {
        int count = 0;
        for (byte[] field : fields) count += field.length;
        ByteBuffer b = ByteBuffer.allocate(count);
        for (byte[] field : fields) b.put(field);
        return b.array();
    }

    private static byte[] key(byte[] secret, byte[] sid, byte[] uuid, int direction)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(join(KEY_DOMAIN, part(sid), part(uuid),
                part(new byte[] {(byte) direction}), new byte[] {1}));
    }

    private static byte[] nonce(long seq) {
        return ByteBuffer.allocate(12).putInt(0).putLong(seq).array();
    }

    private byte[] aad(int direction, long seq) {
        return join(AAD_DOMAIN, part(session), part(tv),
                new byte[] {(byte) direction},
                ByteBuffer.allocate(8).putLong(seq).array());
    }

    PairingV3ScopedTokenHandoff(PrivateKey tvPrivate,
            PairingKeyCommitment.Session verified, String uuid,
            String trustedHttpsOrigin, long ttlMillis, long nowMillis) {
        if (tvPrivate == null || verified == null || uuid == null
                || !uuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || trustedHttpsOrigin == null
                || ttlMillis < 1 || ttlMillis > 120000
                || nowMillis < 0 || nowMillis > Long.MAX_VALUE - ttlMillis
                || !"tv".equals(verified.verifiedRole())) {
            throw new IllegalArgumentException("Unverified TV handoff");
        }
        String sid = verified.verifiedTranscript().session;
        byte[] sidBytes = Base64.getUrlDecoder().decode(sid);
        if (sidBytes.length != 16 || !Base64.getUrlEncoder()
                .withoutPadding().encodeToString(sidBytes).equals(sid))
            throw new IllegalArgumentException("Invalid v3 pairing session");
        // Force the same trusted HTTPS identity parser used by the vault.
        TvRenewableLoginPolicy.check(trustedHttpsOrigin, uuid,
                (trustedHttpsOrigin.endsWith("/") ? trustedHttpsOrigin.substring(
                        0, trustedHttpsOrigin.length() - 1) : trustedHttpsOrigin)
                + "/kellies-voice/tv/" + uuid
                + "/attempt/00000000000000000000000000000000", "validator");
        tvUuid = uuid;
        expectedOrigin = trustedHttpsOrigin;
        session = sidBytes;
        tv = ascii(uuid);
        byte[] secret = PairingV3Verified.qaChannelMaterial(tvPrivate, verified);
        try {
            sendKey = key(secret, session, tv, SEND);
            receiveKey = key(secret, session, tv, RECEIVE);
        } catch (GeneralSecurityException error) {
            throw new IllegalArgumentException("TV key derivation unavailable");
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
        expires = nowMillis + ttlMillis;
    }

    private void check(int expected, long now) {
        if (closed || phase != expected || now < 0 || now >= expires) {
            close();
            throw new IllegalStateException("TV scoped handoff unavailable");
        }
    }

    private byte[] decrypt(long seq, byte[] encrypted) {
        if (encrypted == null || encrypted.length < 16 || encrypted.length > 12288) {
            close();
            throw new IllegalArgumentException("Invalid encrypted TV grant");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(receiveKey, "AES"),
                    new GCMParameterSpec(128, nonce(seq)));
            cipher.updateAAD(aad(RECEIVE, seq));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException error) {
            close();
            throw new IllegalArgumentException("TV grant authentication failed");
        }
    }

    private Packet encrypt(long seq, byte[] message) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sendKey, "AES"),
                    new GCMParameterSpec(128, nonce(seq)));
            cipher.updateAAD(aad(SEND, seq));
            return new Packet(seq, cipher.doFinal(message));
        } catch (GeneralSecurityException error) {
            close();
            throw new IllegalStateException("TV grant acknowledgement failed");
        }
    }

    /** Caller MUST provide the private Android Keystore vault.
     * Decrypt and STAGE first; a missing storage commit means NO stage ACK.
     */
    synchronized Packet stageProvisional(long seq, byte[] encrypted,
            long nowMillis, TvRenewableLoginVault vault) throws Exception {
        check(0, nowMillis);
        if (seq != 1 || vault == null) {
            close();
            throw new IllegalArgumentException("Invalid provisional TV grant");
        }
        byte[] plaintext = decrypt(seq, encrypted);
        try {
            JSONObject doc = new JSONObject(
                    new String(plaintext, StandardCharsets.UTF_8));
            if (doc.length() != 7 || doc.getInt("v") != 1
                    || !tvUuid.equals(doc.getString("tv")))
                throw new IllegalArgumentException("Invalid TV token identity");
            TvRenewableLoginPolicy.Grant grant =
                    TvRenewableLoginPolicy.check(
                            expectedOrigin, tvUuid,
                            doc.getString("client"), doc.getString("id"));
            String refresh = doc.getString("refresh");
            String access = doc.getString("access");
            long ttl = doc.getLong("expires");
            TvRenewableLoginPolicy.checkCredential(refresh, access, ttl);
            vault.stage(grant, refresh, access, ttl);
            tokenId = grant.tokenId;
            phase = 1;
            return encrypt(1, ascii("staged"));
        } catch (Exception error) {
            close();
            throw new IllegalArgumentException("Scoped TV enrollment rejected");
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    /** Only a verified HA->TV 'commit-ready' can activate the staged grant. */
    synchronized Packet activateAfterCommitReady(long seq, byte[] encrypted,
            long nowMillis, TvRenewableLoginVault vault) throws Exception {
        check(1, nowMillis);
        if (seq != 2 || vault == null || tokenId == null) {
            close();
            throw new IllegalArgumentException("Missing staged TV grant");
        }
        byte[] clear = decrypt(seq, encrypted);
        try {
            if (!java.security.MessageDigest.isEqual(
                    clear, ascii("commit-ready")))
                throw new IllegalArgumentException("Invalid server commit");
            vault.activateAfterVerifiedServerCommit(tvUuid, tokenId);
            phase = 2;
            return encrypt(2, ascii("activated"));
        } catch (Exception error) {
            close();
            throw new IllegalArgumentException("TV activation failed");
        } finally {
            Arrays.fill(clear, (byte) 0);
            close();
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        Arrays.fill(sendKey, (byte) 0);
        Arrays.fill(receiveKey, (byte) 0);
    }
}
