package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.ECPrivateKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * #193 protocol primitive: transcript-bound, human-verifiable comparison code.
 *
 * Both endpoints must already know which public key belongs to the TV and
 * which belongs to Home Assistant. The resulting 8-digit code is displayed
 * on BOTH screens and compared by the owner; it is NOT authentication without
 * that physical comparison and an explicit OK on the TV.
 *
 * NO networking, credentials, OAuth tokens or application preferences here.
 * In particular, never call this method to silently approve a connection.
 */
public final class PairingVerificationCode {
    private static final String CURVE = "secp256r1"; // Android API 26-compatible P-256
    private static final byte[] TRANSCRIPT_DOMAIN =
            "KVPair/v1/transcript".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CODE_DOMAIN =
            "KelliesVoice/v1/visual-compare".getBytes(StandardCharsets.US_ASCII);
    private PairingVerificationCode() { }

    public static KeyPair freshKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException("Secure pairing key generation unavailable", error);
        }
    }

    /**
     * @param own local ephemeral P-256 key pair
     * @param localIsTv true on the TV, false for the HA peer
     * @param session base64url-without-padding encoding of exactly 16 random bytes
     * @param tvKey DER/X.509 P-256 public key of TV
     * @param haKey DER/X.509 P-256 public key of HA
     */
    public static String calculate(KeyPair own, boolean localIsTv, String session,
                                   byte[] tvKey, byte[] haKey) {
        if (own == null || own.getPublic() == null
                || !(own.getPrivate() instanceof ECPrivateKey)) {
            throw new IllegalArgumentException("Missing ephemeral P-256 key");
        }
        byte[] sessionBytes = decodeSession(session);
        ECPublicKey tv = requireP256(tvKey);
        ECPublicKey ha = requireP256(haKey);
        if (MessageDigest.isEqual(tv.getEncoded(), ha.getEncoded())) {
            throw new IllegalArgumentException("TV and HA must have different ephemeral keys");
        }
        byte[] claimedOwnKey = localIsTv ? tvKey : haKey;
        if (!MessageDigest.isEqual(own.getPublic().getEncoded(), claimedOwnKey)) {
            throw new IllegalArgumentException("Pairing peer role/key mismatch");
        }
        ECParameterSpec ownCurve = ((ECPrivateKey) own.getPrivate()).getParams();
        requireCurve(ownCurve, p256Params());
        ECPublicKey remote = localIsTv ? ha : tv;

        byte[] secret = null;
        byte[] prk = null;
        byte[] codeBytes = null;
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(own.getPrivate());
            agreement.doPhase(remote, true);
            secret = agreement.generateSecret();
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(TRANSCRIPT_DOMAIN);
            transcriptPart(sha, sessionBytes);
            transcriptPart(sha, tvKey);
            transcriptPart(sha, haKey);
            byte[] transcriptHash = sha.digest();

            // HKDF-SHA256 extract with salt bound to both ordered public keys.
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(transcriptHash, "HmacSHA256"));
            prk = hmac.doFinal(secret);
            hmac.init(new SecretKeySpec(prk, "HmacSHA256"));
            hmac.update(CODE_DOMAIN);
            hmac.update((byte) 1); // HKDF expand block 1
            codeBytes = hmac.doFinal();
            long number = ((codeBytes[0] & 255L) << 24)
                    | ((codeBytes[1] & 255L) << 16)
                    | ((codeBytes[2] & 255L) << 8)
                    | (codeBytes[3] & 255L);
            return String.format(Locale.US, "%08d", number % 100_000_000L);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot compute secure pairing comparison", error);
        } finally {
            if (secret != null) Arrays.fill(secret, (byte) 0);
            if (prk != null) Arrays.fill(prk, (byte) 0);
            if (codeBytes != null) Arrays.fill(codeBytes, (byte) 0);
        }
    }

    /**
     * A separate domain-separated MAC only emitted AFTER a physical TV OK.
     * Do not use the visible eight-digit code as a MAC or session password.
     * The HA peer independently recomputes this exact 128-bit proof from ECDH.
     */
    public static String approvedProof(KeyPair own, boolean localIsTv, String session,
                                       byte[] tvKey, byte[] haKey) {
        if (own == null || !(own.getPrivate() instanceof ECPrivateKey)) {
            throw new IllegalArgumentException("Missing private key");
        }
        byte[] sid = decodeSession(session);
        ECPublicKey tv = requireP256(tvKey), ha = requireP256(haKey);
        if (MessageDigest.isEqual(tv.getEncoded(), ha.getEncoded())
                || !MessageDigest.isEqual(own.getPublic().getEncoded(),
                    localIsTv ? tvKey : haKey)) {
            throw new IllegalArgumentException("Peer identity mismatch");
        }
        requireCurve(((ECPrivateKey) own.getPrivate()).getParams(), p256Params());
        byte[] secret = null, prk = null, expanded = null;
        try {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(own.getPrivate());
            agreement.doPhase(localIsTv ? ha : tv, true);
            secret = agreement.generateSecret();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(TRANSCRIPT_DOMAIN);
            transcriptPart(digest, sid);
            transcriptPart(digest, tvKey);
            transcriptPart(digest, haKey);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(digest.digest(), "HmacSHA256"));
            prk = mac.doFinal(secret);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update("KelliesVoice/v1/tv-approved".getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) 1);
            expanded = mac.doFinal();
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Arrays.copyOf(expanded, 16));
        } catch (Exception error) {
            throw new IllegalStateException("TV confirmation proof unavailable", error);
        } finally {
            if (secret != null) Arrays.fill(secret, (byte) 0);
            if (prk != null) Arrays.fill(prk, (byte) 0);
            if (expanded != null) Arrays.fill(expanded, (byte) 0);
        }
    }

    private static void transcriptPart(MessageDigest sha, byte[] bytes) {
        if (bytes.length > 65535) throw new IllegalArgumentException("Oversized pairing data");
        sha.update((byte) (bytes.length >>> 8));
        sha.update((byte) bytes.length);
        sha.update(bytes);
    }

    private static byte[] decodeSession(String session) {
        if (session == null || !session.matches("[A-Za-z0-9_-]{22}")) {
            throw new IllegalArgumentException("Invalid pairing session");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(session);
            if (decoded.length != 16
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)
                    .equals(session)) {
                throw new IllegalArgumentException("Noncanonical pairing session");
            }
            return decoded;
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid pairing session", error);
        }
    }

    private static ECPublicKey requireP256(byte[] encoded) {
        if (encoded == null || encoded.length < 70 || encoded.length > 150) {
            throw new IllegalArgumentException("Invalid pairing public key");
        }
        try {
            java.security.PublicKey decoded =
                    KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
            if (!(decoded instanceof ECPublicKey)) {
                throw new IllegalArgumentException("Expected elliptic-curve public key");
            }
            ECPublicKey key = (ECPublicKey) decoded;
            ECParameterSpec expected = p256Params();
            requireCurve(key.getParams(), expected);
            // Reject point at infinity, out-of-field values and points off the curve.
            ECPoint p = key.getW();
            if (p == null || p.equals(ECPoint.POINT_INFINITY)) {
                throw new IllegalArgumentException("Invalid pairing EC point");
            }
            BigInteger field = ((java.security.spec.ECFieldFp) expected.getCurve().getField()).getP();
            BigInteger x = p.getAffineX();
            BigInteger y = p.getAffineY();
            if (x == null || y == null || x.signum() < 0 || y.signum() < 0
                    || x.compareTo(field) >= 0 || y.compareTo(field) >= 0) {
                throw new IllegalArgumentException("Public key point out of range");
            }
            BigInteger left = y.modPow(BigInteger.valueOf(2), field);
            BigInteger right = x.modPow(BigInteger.valueOf(3), field)
                    .add(expected.getCurve().getA().multiply(x))
                    .add(expected.getCurve().getB()).mod(field);
            if (!left.equals(right)) {
                throw new IllegalArgumentException("Public key not on P-256 curve");
            }
            if (!MessageDigest.isEqual(key.getEncoded(), encoded)) {
                throw new IllegalArgumentException("Noncanonical public key encoding");
            }
            return key;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid pairing public key", error);
        }
    }

    private static ECParameterSpec p256Params() {
        try {
            AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
            ap.init(new ECGenParameterSpec(CURVE));
            return ap.getParameterSpec(ECParameterSpec.class);
        } catch (Exception error) {
            throw new IllegalStateException("P-256 is not supported on this device", error);
        }
    }

    private static void requireCurve(ECParameterSpec given, ECParameterSpec expected) {
        if (given == null || !(given.getCurve().getField() instanceof java.security.spec.ECFieldFp)
                || !given.getCurve().equals(expected.getCurve())
                || !given.getGenerator().equals(expected.getGenerator())
                || !given.getOrder().equals(expected.getOrder())
                || given.getCofactor() != expected.getCofactor()) {
            throw new IllegalArgumentException("Only P-256 pairing keys are supported");
        }
    }
}
