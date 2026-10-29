package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Offline KVPair v3 short authentication string and remote TV-OK proof.
 *
 * This is deliberately NOT an Android login, listener or credential channel.
 * Inputs must come from one completed commit-before-reveal Session. The
 * implementation matches pairing_v3_verified.py and its frozen test vectors.
 *
 * DO NOT reuse v2's code/proof for provisioning credentials.
 */
public final class PairingV3Verified {
    private static final byte[] SAS_DOMAIN =
            ascii("KelliesVoice/v3/sas-transcript\0");
    private static final byte[] SAS_INFO =
            ascii("KelliesVoice/v3/eight-digit-code\0");
    private static final byte[] PROOF_INFO =
            ascii("KelliesVoice/v3/remote-ok-uuid\0");
    private static final byte[] KEY_CHECK =
            ascii("KelliesVoice/v3/own-key-check\0");

    private PairingV3Verified() { }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] part(byte[] value) {
        if (value == null || value.length > 65535) {
            throw new IllegalArgumentException("Invalid v3 transcript component");
        }
        byte[] result = new byte[value.length + 2];
        result[0] = (byte) (value.length >>> 8);
        result[1] = (byte) value.length;
        System.arraycopy(value, 0, result, 2, value.length);
        return result;
    }

    private static byte[] join(byte[]... fields) {
        int size = 0;
        for (byte[] field : fields) {
            if (field == null) throw new IllegalArgumentException("Missing v3 field");
            size += field.length;
        }
        byte[] result = new byte[size];
        int at = 0;
        for (byte[] field : fields) {
            System.arraycopy(field, 0, result, at, field.length);
            at += field.length;
        }
        return result;
    }

    private static byte[] sha(byte[] input) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    private static byte[] mac(byte[] key, byte[] value) throws GeneralSecurityException {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(key, "HmacSHA256"));
        return hmac.doFinal(value);
    }

    private static ECParameterSpec p256() throws GeneralSecurityException {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        return params.getParameterSpec(ECParameterSpec.class);
    }

    private static boolean sameCurve(ECParameterSpec actual, ECParameterSpec expected) {
        return actual != null
                && actual.getCurve().equals(expected.getCurve())
                && actual.getGenerator().equals(expected.getGenerator())
                && actual.getOrder().equals(expected.getOrder())
                && actual.getCofactor() == expected.getCofactor();
    }

    private static ECPublicKey p256Public(byte[] encoded, ECParameterSpec expected)
            throws GeneralSecurityException {
        if (encoded == null || encoded.length < 70 || encoded.length > 150) {
            throw new IllegalArgumentException("Invalid v3 DER key length");
        }
        java.security.PublicKey decoded = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(encoded));
        if (!(decoded instanceof ECPublicKey)
                || !sameCurve(((ECPublicKey) decoded).getParams(), expected)
                || !MessageDigest.isEqual(encoded, decoded.getEncoded())) {
            throw new IllegalArgumentException("Noncanonical or non-P256 v3 public key");
        }
        return (ECPublicKey) decoded;
    }

    private static byte[] keyMaterial(PrivateKey privateKey,
            PairingKeyCommitment.VerifiedTranscript verified)
            throws GeneralSecurityException {
        if (!(privateKey instanceof ECPrivateKey)
                || verified == null
                || !("tv".equals(verified.role) || "ha".equals(verified.role))) {
            throw new IllegalArgumentException("Invalid v3 role or private key");
        }

        ECParameterSpec reference = p256();
        if (!sameCurve(((ECPrivateKey) privateKey).getParams(), reference)) {
            throw new IllegalArgumentException("Invalid v3 private key curve");
        }
        ECPublicKey own = p256Public(verified.ownPublic, reference);
        ECPublicKey other = p256Public(verified.otherPublic, reference);
        if (MessageDigest.isEqual(verified.ownPublic, verified.otherPublic)) {
            throw new IllegalArgumentException("Reflected v3 public key");
        }

        // A private EC key cannot be trusted to belong to the supplied public
        // key solely because they share a curve. Prove that relation locally
        // by signing an ephemeral challenge and verifying under the local
        // commitment-bound public key. The signature never leaves memory.
        byte[] check = join(KEY_CHECK, part(ascii(verified.session)),
                part(ascii(verified.role)), part(verified.ownPublic));
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(check);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(own);
        verifier.update(check);
        if (!verifier.verify(signature)) {
            throw new IllegalArgumentException("Local v3 key/public mismatch");
        }

        byte[] tvKey, haKey, tvNonce, haNonce;
        if ("tv".equals(verified.role)) {
            tvKey = verified.ownPublic;
            tvNonce = verified.ownNonce;
            haKey = verified.otherPublic;
            haNonce = verified.otherNonce;
        } else {
            tvKey = verified.otherPublic;
            tvNonce = verified.otherNonce;
            haKey = verified.ownPublic;
            haNonce = verified.ownNonce;
        }
        if (tvNonce.length != 32 || haNonce.length != 32) {
            throw new IllegalArgumentException("Invalid committed v3 nonce");
        }

        byte[] rawSession;
        try {
            rawSession = Base64.getUrlDecoder().decode(verified.session);
            if (rawSession.length != 16 || !Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(rawSession).equals(verified.session)) {
                throw new IllegalArgumentException("Noncanonical v3 session");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid v3 session") ;
        }

        byte[] tvCommit = PairingKeyCommitment.commit("tv",
                verified.session, tvKey, tvNonce);
        byte[] haCommit = PairingKeyCommitment.commit("ha",
                verified.session, haKey, haNonce);

        byte[] transcript = join(SAS_DOMAIN, part(rawSession),
                part(tvCommit), part(haCommit), part(tvKey),
                part(haKey), part(tvNonce), part(haNonce));
        KeyAgreement ecdh = KeyAgreement.getInstance("ECDH");
        ecdh.init(privateKey);
        ecdh.doPhase(other, true);
        byte[] shared = ecdh.generateSecret();
        try {
            return mac(sha(transcript), shared);
        } finally {
            Arrays.fill(shared, (byte) 0);
        }
    }

    private static byte[] derive(PrivateKey privateKey,
            PairingKeyCommitment.Session session) {
        if (session == null) throw new IllegalArgumentException("v3 session required");
        // The lock prevents cancellation from interleaving halfway through
        // acquisition/derivation of the commitment-verified transcript.
        synchronized (session) {
            try {
                byte[] secret = keyMaterial(privateKey, session.verifiedTranscript());
                if (!session.ready()) {
                    Arrays.fill(secret, (byte) 0);
                    throw new IllegalStateException("v3 pairing was cancelled");
                }
                return secret;
            } catch (GeneralSecurityException ex) {
                throw new IllegalArgumentException("Invalid v3 key agreement") ;
            }
        }
    }

    /** Exactly eight ASCII digits, derived only from the full v3 transcript. */
    public static String comparisonCode(PrivateKey privateKey,
            PairingKeyCommitment.Session session) {
        byte[] prk = derive(privateKey, session);
        try {
            byte[] digest = mac(prk, join(SAS_INFO, new byte[] { 1 }));
            long number = (((long) digest[0] & 255) << 24)
                    | (((long) digest[1] & 255) << 16)
                    | (((long) digest[2] & 255) << 8)
                    | ((long) digest[3] & 255);
            return String.format(Locale.US, "%08d", number % 100_000_000L);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("V3 code derivation unavailable") ;
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
    }

    /** TV remote proof after a genuine physical OK, never authorization alone. */
    public static String physicalTvOkProof(PrivateKey privateKey,
            PairingKeyCommitment.Session session, String tvUuid) {
        if (tvUuid == null || !tvUuid.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Canonical v4 TV UUID required");
        }
        byte[] prk = derive(privateKey, session);
        try {
            byte[] digest = mac(prk, join(PROOF_INFO,
                    part(ascii(tvUuid)), new byte[] { 1 }));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    Arrays.copyOf(digest, 16));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("V3 TV proof unavailable");
        } finally {
            Arrays.fill(prk, (byte) 0);
        }
    }
}
