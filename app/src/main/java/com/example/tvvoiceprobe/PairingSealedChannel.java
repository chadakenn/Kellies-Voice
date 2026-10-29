package com.example.tvvoiceprobe;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * #193 staged protocol primitive only. Does not start a listener, save a
 * credential, or claim a verified identity. Call only after the owner has
 * compared both codes AND a TV OK proof has been validated.
 *
 * Wire v1: byte(version=1), byte(direction: HA->TV=0 / TV->HA=1),
 * uint64 big-endian sequence starting at 0, 12 random nonce bytes,
 * AES-256-GCM ciphertext+16-byte tag.
 *
 * Session-bound transcript is additional authenticated data alongside header.
 * Separate directional keys prevent a reflected message from authenticating.
 * Strict in-order sequence counters reject replays. A new window/keypair is
 * required after cancellation, expiry or connection loss.
 */
public final class PairingSealedChannel implements AutoCloseable {
    private static final byte[] TRANSCRIPT_LABEL =
            "KVPair/v1/transcript".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] AAD_LABEL =
            "KelliesVoice/v1/channel-aad".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] HA_KEY_LABEL =
            "KelliesVoice/v1/channel/ha-to-tv".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] TV_KEY_LABEL =
            "KelliesVoice/v1/channel/tv-to-ha".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER = 22;
    private static final int TAG_BYTES = 16;
    private static final int MAX_PLAINTEXT = 2048;
    private final byte[] sendKey;
    private final byte[] receiveKey;
    private final byte[] transcriptHash;
    private final byte sendDirection;
    private final SecureRandom random;
    private long nextSend;
    private long nextReceive;
    private boolean closed;

    public PairingSealedChannel(KeyPair local, boolean localIsTv, String session,
                                byte[] tvDer, byte[] haDer) {
        this(local, localIsTv, session, tvDer, haDer, new SecureRandom());
    }

    // Deterministic nonce source for cross-language test vectors only.
    PairingSealedChannel(KeyPair local, boolean localIsTv, String session,
                         byte[] tvDer, byte[] haDer, SecureRandom random) {
        if (random == null || local == null) throw new IllegalArgumentException("Missing pairing context");
        // Reuse the strict public-key/session/role validation from #201.
        PairingVerificationCode.calculate(local, localIsTv, session, tvDer, haDer);
        this.random = random;
        this.sendDirection = (byte) (localIsTv ? 1 : 0);
        byte[] secret = null, prk = null;
        try {
            byte[] sid = Base64.getUrlDecoder().decode(session);
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(TRANSCRIPT_LABEL);
            hashPart(hash, sid);
            hashPart(hash, tvDer);
            hashPart(hash, haDer);
            transcriptHash = hash.digest();

            ECPublicKey remote = (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(
                    new X509EncodedKeySpec(localIsTv ? haDer : tvDer));
            KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
            ecdh.init(local.getPrivate());
            ecdh.doPhase(remote, true);
            secret = ecdh.generateSecret();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(transcriptHash, "HmacSHA256"));
            prk = mac.doFinal(secret);
            sendKey = hkdf(mac, prk, localIsTv ? TV_KEY_LABEL : HA_KEY_LABEL);
            receiveKey = hkdf(mac, prk, localIsTv ? HA_KEY_LABEL : TV_KEY_LABEL);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot derive pairing transport keys", ex);
        } finally {
            if (secret != null) Arrays.fill(secret, (byte) 0);
            if (prk != null) Arrays.fill(prk, (byte) 0);
        }
    }

    private static byte[] hkdf(Mac mac, byte[] key, byte[] info) throws Exception {
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        mac.update(info);
        mac.update((byte) 1);
        return mac.doFinal();
    }

    private static void hashPart(MessageDigest hash, byte[] value) {
        if (value.length > 65535) throw new IllegalArgumentException("Oversized transcript part");
        hash.update((byte) (value.length >>> 8));
        hash.update((byte) value.length);
        hash.update(value);
    }

    public synchronized byte[] seal(byte[] plaintext) {
        if (closed || plaintext == null || plaintext.length == 0
                || plaintext.length > MAX_PLAINTEXT || nextSend == Long.MAX_VALUE) {
            throw new IllegalStateException("Pairing channel unavailable");
        }
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        return sealWithNonce(plaintext, nonce);
    }

    /** Test-only deterministic nonce hook; production uses SecureRandom above. */
    synchronized byte[] sealWithNonce(byte[] plaintext, byte[] nonce) {
        if (closed || plaintext == null || plaintext.length == 0
                || plaintext.length > MAX_PLAINTEXT || nonce == null
                || nonce.length != 12 || nextSend == Long.MAX_VALUE) {
            throw new IllegalStateException("Pairing channel unavailable");
        }
        byte[] header = ByteBuffer.allocate(HEADER)
                .put((byte) 1).put(sendDirection).putLong(nextSend)
                .put(nonce).array();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(sendKey, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD_LABEL);
            cipher.updateAAD(transcriptHash);
            cipher.updateAAD(header);
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] result = new byte[header.length + encrypted.length];
            System.arraycopy(header, 0, result, 0, HEADER);
            System.arraycopy(encrypted, 0, result, HEADER, encrypted.length);
            nextSend++;
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Pairing encryption unavailable", ex);
        }
    }

    public synchronized byte[] open(byte[] packet) {
        if (closed || packet == null || packet.length < HEADER + TAG_BYTES + 1
                || packet.length > HEADER + TAG_BYTES + MAX_PLAINTEXT) {
            throw new IllegalArgumentException("Invalid pairing packet");
        }
        ByteBuffer head = ByteBuffer.wrap(packet, 0, HEADER);
        byte version = head.get();
        byte direction = head.get();
        long sequence = head.getLong();
        byte[] nonce = new byte[12];
        head.get(nonce);
        if (version != 1 || direction != (byte) (1 - sendDirection)
                || sequence != nextReceive || nextReceive == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Wrong direction, version or replayed packet");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(receiveKey, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(AAD_LABEL);
            cipher.updateAAD(transcriptHash);
            cipher.updateAAD(packet, 0, HEADER);
            byte[] clear = cipher.doFinal(packet, HEADER, packet.length - HEADER);
            nextReceive++;
            return clear;
        } catch (Exception ex) {
            // Authentication failed: do not advance sequence or return plaintext.
            throw new IllegalArgumentException("Pairing packet authentication failed", ex);
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        Arrays.fill(sendKey, (byte) 0);
        Arrays.fill(receiveKey, (byte) 0);
        Arrays.fill(transcriptHash, (byte) 0);
    }
}
