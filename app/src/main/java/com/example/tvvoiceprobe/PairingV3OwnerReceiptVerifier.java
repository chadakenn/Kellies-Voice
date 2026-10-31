package com.example.tvvoiceprobe;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Offline TV-only verified HA-owner receipt, without credentials or network.
 *
 * Never trust a client-supplied owner ID, a plain UUID, or an unverified
 * message. Only the HA peer possessing this exact v3 ECDH transcript can
 * encrypt the one fixed owner-approved message. This does NOT log in a TV.
 */
final class PairingV3OwnerReceiptVerifier implements AutoCloseable {
    private static final byte[] KEY_INFO =
            ascii("KelliesVoice/v3/owner-receipt-key\0");
    private static final byte[] AAD_INFO =
            ascii("KelliesVoice/v3/owner-receipt-aad\0");
    private static final byte[] EXPECTED = ascii("owner-approved");
    private static final byte[] DIRECTION = new byte[] {2};
    private static final long SEQ = 1L;

    private final byte[] key;
    private final byte[] aad;
    private final long expiresAt;
    private boolean closed;

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] part(byte[] v) {
        if (v == null || v.length > 65535)
            throw new IllegalArgumentException("Invalid owner receipt field");
        return ByteBuffer.allocate(2 + v.length)
                .putShort((short) v.length).put(v).array();
    }

    private static byte[] join(byte[]... fields) {
        int count = 0;
        for (byte[] f : fields) count += f.length;
        ByteBuffer buffer = ByteBuffer.allocate(count);
        for (byte[] f : fields) buffer.put(f);
        return buffer.array();
    }

    private static byte[] hmac(byte[] secret, byte[] content)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(content);
    }

    private static byte[] nonce() {
        return ByteBuffer.allocate(12).putInt(0).putLong(SEQ).array();
    }

    PairingV3OwnerReceiptVerifier(PrivateKey tvPrivate,
            PairingKeyCommitment.Session verified, String tvUuid,
            long ttlMillis, long nowMillis) {
        if (verified == null || tvPrivate == null || tvUuid == null
                || !tvUuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || ttlMillis < 1 || ttlMillis > 120_000
                || nowMillis < 0 || nowMillis > Long.MAX_VALUE - ttlMillis
                || !"tv".equals(verified.verifiedRole())) {
            throw new IllegalArgumentException("Invalid verified owner receipt context");
        }
        PairingKeyCommitment.VerifiedTranscript transcript =
                verified.verifiedTranscript();
        byte[] sid = Base64.getUrlDecoder().decode(transcript.session);
        if (sid.length != 16 || !Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sid).equals(transcript.session)) {
            throw new IllegalArgumentException("Invalid v3 session");
        }
        byte[] identity = ascii(tvUuid);
        byte[] prk = PairingV3Verified.qaChannelMaterial(tvPrivate, verified);
        try {
            key = hmac(prk, join(KEY_INFO, part(sid), part(identity),
                    DIRECTION, new byte[] {1}));
        } catch (GeneralSecurityException error) {
            throw new IllegalArgumentException("Owner receipt key unavailable");
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
        aad = join(AAD_INFO, part(sid), part(identity), DIRECTION,
                ByteBuffer.allocate(8).putLong(SEQ).array());
        expiresAt = nowMillis + ttlMillis;
    }

    /** One use only. Any invalid packet closes the verifier. */
    synchronized boolean accept(long sequence, byte[] encrypted, long nowMillis) {
        if (closed || nowMillis < 0 || nowMillis >= expiresAt
                || sequence != SEQ || encrypted == null || encrypted.length != 30) {
            close();
            throw new IllegalArgumentException("Invalid or expired owner receipt");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, nonce()));
            cipher.updateAAD(aad);
            byte[] text = cipher.doFinal(encrypted);
            try {
                if (!MessageDigest.isEqual(text, EXPECTED))
                    throw new IllegalArgumentException("Invalid owner approval");
                return true;
            } finally {
                Arrays.fill(text, (byte) 0);
            }
        } catch (GeneralSecurityException error) {
            throw new IllegalArgumentException("Unauthenticated owner receipt");
        } finally {
            close();
        }
    }

    synchronized boolean isClosed() { return closed; }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        Arrays.fill(key, (byte) 0);
    }
}
