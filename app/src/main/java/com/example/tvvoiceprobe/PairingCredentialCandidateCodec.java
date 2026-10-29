package com.example.tvvoiceprobe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Canonical, strictly bounded binary representation for one pending OAuth
 * candidate. The representation is intended to be encrypted inside a
 * previously owner- and TV-verified P-256/AES-GCM handoff. NOT a public wire
 * endpoint, token exchange, token storage or permission to activate.
 *
 * All fields are ordered and length-prefixed, including the exact origin,
 * client ID, access/refresh tokens, TV identity, session and time bounds.
 * The offer byte digest is calculated locally, never logged or sent via mDNS.
 * The HA peer must implement and test precisely the same encoding before
 * any actual OAuth credential may cross the sealed channel.
 */
public final class PairingCredentialCandidateCodec {
    private static final byte[] MAGIC =
            "KVPair/credential-candidate/v1\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] BIND_DOMAIN =
            "KVPair/v1/exact-candidate-binding\0".getBytes(StandardCharsets.US_ASCII);
    public static final int MAX_BYTES = 1024;

    private PairingCredentialCandidateCodec() { }

    private static void field(DataOutputStream out, String value) throws Exception {
        if (value == null) throw new IllegalArgumentException("Missing candidate field");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > 2048)
            throw new IllegalArgumentException("Invalid candidate field size");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readField(DataInputStream in) throws Exception {
        int size = in.readUnsignedShort();
        if (size < 1 || size > 2048 || size > in.available())
            throw new IllegalArgumentException("Invalid credential field");
        byte[] bytes = new byte[size];
        in.readFully(bytes);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(text.getBytes(StandardCharsets.UTF_8), bytes))
            throw new IllegalArgumentException("Noncanonical credential UTF-8");
        return text;
    }

    public static byte[] encode(PairedOAuthCredentials credentials) {
        if (credentials == null) throw new IllegalArgumentException("Candidate required");
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buffer);
            out.write(MAGIC);
            field(out, credentials.serverUrl);
            field(out, credentials.clientId);
            field(out, credentials.accessToken);
            field(out, credentials.refreshToken);
            field(out, credentials.session);
            field(out, credentials.tvId);
            out.writeLong(credentials.accessExpiresAtMillis);
            out.writeLong(credentials.stagedAtMillis);
            out.flush();
            byte[] bytes = buffer.toByteArray();
            if (bytes.length > MAX_BYTES)
                throw new IllegalArgumentException("Credential candidate exceeds sealed offer cap");
            return bytes;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Candidate encoding unavailable");
        }
    }

    /** Decode into the already validated, in-memory value object; no storage. */
    public static PairedOAuthCredentials decode(byte[] candidate) {
        if (candidate == null || candidate.length < MAGIC.length + 28
                || candidate.length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid candidate envelope");
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(candidate));
            byte[] header = new byte[MAGIC.length];
            in.readFully(header);
            if (!MessageDigest.isEqual(header, MAGIC))
                throw new IllegalArgumentException("Wrong candidate protocol version");
            String origin = readField(in);
            String clientId = readField(in);
            String access = readField(in);
            String refresh = readField(in);
            String session = readField(in);
            String tv = readField(in);
            if (in.available() != 16) throw new IllegalArgumentException("Extra candidate data");
            long accessExpiry = in.readLong();
            long staged = in.readLong();
            PairedOAuthCredentials credentials = new PairedOAuthCredentials(
                    origin, clientId, access, refresh, session, tv, accessExpiry, staged);
            if (!MessageDigest.isEqual(candidate, encode(credentials)))
                throw new IllegalArgumentException("Noncanonical candidate");
            return credentials;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid candidate encoding");
        }
    }

    /**
     * Domain-separated digest of the exact candidate bytes supplied inside
     * credential_offer. This is only a local equality handle, not an OAuth
     * authentication tag: the surrounding AEAD and explicit owner consent are
     * still required. Caller must not expose this fingerprint as a password.
     */
    public static byte[] fingerprint(byte[] exactCandidate) {
        if (exactCandidate == null || exactCandidate.length < 1
                || exactCandidate.length > MAX_BYTES)
            throw new IllegalArgumentException("Invalid candidate size");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(BIND_DOMAIN);
            hash.update((byte) (exactCandidate.length >>> 8));
            hash.update((byte) exactCandidate.length);
            return hash.digest(exactCandidate);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 not available", error);
        }
    }

    public static byte[] fingerprint(PairedOAuthCredentials credentials) {
        return fingerprint(encode(credentials));
    }
}
