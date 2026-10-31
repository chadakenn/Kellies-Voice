package com.example.tvvoiceprobe;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Offline v3 AES-GCM *QA-ping-only* codec, NOT a TV login or credential path.
 *
 * This class is not called by the Android app or any network listener.
 * A future server must separately verify and bind HA owner approval.
 */
final class PairingV3QaPingChannel implements AutoCloseable {
    private static final byte[] KEY_DOMAIN = ascii("KelliesVoice/v3/qa-aead/key\0");
    private static final byte[] AAD_DOMAIN = ascii("KelliesVoice/v3/qa-aead/aad\0");
    private static final byte[] PING = ascii("qa-ping");
    private static final byte[] PONG = ascii("qa-pong");
    private final byte[] sendKey;
    private final byte[] receiveKey;
    private final byte[] sid;
    private final byte[] tvId;
    private final int sendDirection;
    private final int receiveDirection;
    private final long expiresAt;
    private long sendSequence;
    private long receiveSequence;
    private boolean closed;

    static final class Packet {
        final long sequence;
        final byte[] ciphertext;
        Packet(long sequence, byte[] ciphertext) {
            this.sequence = sequence;
            this.ciphertext = ciphertext.clone();
        }
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] part(byte[] input) {
        if (input == null || input.length > 65535)
            throw new IllegalArgumentException("Invalid QA field");
        return ByteBuffer.allocate(2 + input.length)
                .putShort((short) input.length).put(input).array();
    }

    private static byte[] join(byte[]... items) {
        int total = 0;
        for (byte[] item : items) total += item.length;
        ByteBuffer result = ByteBuffer.allocate(total);
        for (byte[] item : items) result.put(item);
        return result.array();
    }

    private static byte[] mac(byte[] key, byte[] content)
            throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        return m.doFinal(content);
    }

    private static byte[] deriveKey(byte[] material, byte[] sid,
            byte[] tvId, int direction) throws GeneralSecurityException {
        return mac(material, join(KEY_DOMAIN, part(sid), part(tvId),
                part(new byte[] {(byte) direction}), new byte[] {1}));
    }

    private static boolean allowed(byte[] payload) {
        return Arrays.equals(payload, PING) || Arrays.equals(payload, PONG);
    }

    private static byte[] nonce(long sequence) {
        return ByteBuffer.allocate(12).putInt(0).putLong(sequence).array();
    }

    private byte[] aad(int direction, long sequence) {
        return join(AAD_DOMAIN, part(sid), part(tvId),
                new byte[] {(byte) direction},
                ByteBuffer.allocate(8).putLong(sequence).array());
    }

    /** Only a live, fully verified local v3 ECDH transcript can derive keys. */
    PairingV3QaPingChannel(PrivateKey privateKey,
            PairingKeyCommitment.Session verified, String tvUuid,
            long ttlMillis, long nowMillis) {
        if (privateKey == null || verified == null || tvUuid == null
                || !tvUuid.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                || ttlMillis < 1 || ttlMillis > 120_000L
                || nowMillis < 0 || nowMillis > Long.MAX_VALUE - ttlMillis) {
            throw new IllegalArgumentException("Invalid QA channel setup");
        }
        UUID.fromString(tvUuid);
        String role = verified.verifiedRole();
        sendDirection = "tv".equals(role) ? 1 : 2;
        receiveDirection = "tv".equals(role) ? 2 : 1;
        try {
            PairingKeyCommitment.VerifiedTranscript transcript =
                    verified.verifiedTranscript();
            sid = java.util.Base64.getUrlDecoder().decode(transcript.session);
            if (sid.length != 16)
                throw new IllegalArgumentException("Invalid QA session");
            tvId = ascii(tvUuid);
            byte[] prk = PairingV3Verified.qaChannelMaterial(privateKey, verified);
            try {
                sendKey = deriveKey(prk, sid, tvId, sendDirection);
                receiveKey = deriveKey(prk, sid, tvId, receiveDirection);
            } finally {
                Arrays.fill(prk, (byte) 0);
            }
        } catch (GeneralSecurityException error) {
            throw new IllegalArgumentException("Unable to derive QA channel", error);
        }
        expiresAt = nowMillis + ttlMillis;
    }

    private void check(long nowMillis) {
        if (closed || nowMillis < 0 || nowMillis >= expiresAt) {
            close();
            throw new IllegalStateException("QA channel expired or closed");
        }
    }

    synchronized Packet seal(byte[] payload, long nowMillis) {
        check(nowMillis);
        if (payload == null || !allowed(payload))
            throw new IllegalArgumentException("Unsupported QA payload");
        if (sendSequence == Long.MAX_VALUE) {
            close();
            throw new IllegalStateException("QA sequence exhausted");
        }
        long next = sendSequence + 1;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sendKey, "AES"),
                    new GCMParameterSpec(128, nonce(next)));
            cipher.updateAAD(aad(sendDirection, next));
            byte[] encrypted = cipher.doFinal(payload);
            sendSequence = next;
            return new Packet(next, encrypted);
        } catch (GeneralSecurityException error) {
            close();
            throw new IllegalStateException("QA encryption failed");
        }
    }

    synchronized byte[] open(long sequence, byte[] ciphertext, long nowMillis) {
        check(nowMillis);
        if (sequence != receiveSequence + 1 || sequence <= 0
                || ciphertext == null || ciphertext.length != 23) {
            close();
            throw new IllegalArgumentException("Invalid QA packet");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(receiveKey, "AES"),
                    new GCMParameterSpec(128, nonce(sequence)));
            cipher.updateAAD(aad(receiveDirection, sequence));
            byte[] plain = cipher.doFinal(ciphertext);
            if (!allowed(plain)) {
                Arrays.fill(plain, (byte) 0);
                close();
                throw new IllegalArgumentException("Unexpected QA content");
            }
            receiveSequence = sequence;
            return plain;
        } catch (GeneralSecurityException error) {
            close();
            throw new IllegalArgumentException("QA authentication failed");
        }
    }

    synchronized boolean isClosed() { return closed; }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        Arrays.fill(sendKey, (byte) 0);
        Arrays.fill(receiveKey, (byte) 0);
    }
}
