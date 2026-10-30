package com.example.tvvoiceprobe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * KVPair/v3 SECURITY PROTOTYPE, pure Java and NOT integrated into a live
 * socket, saved login, Home Assistant OAuth, or credential store.
 *
 * Both parties commit to their key+32-byte random nonce before revealing
 * either ephemeral public key. This stops last-mover key-grinding attacks
 * against the eight-digit visual comparison code. Existing v2 wires must
 * never silently downgrade or skip this future versioned exchange.
 */
public final class PairingKeyCommitment {
    private static final byte[] DOMAIN =
            "KelliesVoice/v3/key-commit\0".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RNG = new SecureRandom();

    private PairingKeyCommitment() {}

    private static byte[] sid(String session) {
        if (session == null || !session.matches("[A-Za-z0-9_-]{22}"))
            throw new IllegalArgumentException("Invalid v3 session");
        try {
            byte[] result = Base64.getUrlDecoder().decode(session);
            if (result.length != 16 || !Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(result).equals(session))
                throw new IllegalArgumentException("Noncanonical v3 session");
            return result;
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid v3 session", ex);
        }
    }

    private static void part(MessageDigest digest, byte[] value) {
        if (value.length > 65535)
            throw new IllegalArgumentException("Oversized v3 commit part");
        digest.update((byte) (value.length >>> 8));
        digest.update((byte) value.length);
        digest.update(value);
    }

    public static byte[] commit(String role, String session,
                                byte[] publicDer, byte[] nonce) {
        if (!"tv".equals(role) && !"ha".equals(role))
            throw new IllegalArgumentException("Invalid v3 role");
        byte[] rawSession = sid(session);
        if (publicDer == null || publicDer.length < 70 || publicDer.length > 150
                || nonce == null || nonce.length != 32)
            throw new IllegalArgumentException("Invalid v3 commit material");
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(DOMAIN);
            part(sha, role.getBytes(StandardCharsets.US_ASCII));
            part(sha, rawSession);
            part(sha, publicDer);
            part(sha, nonce);
            return sha.digest();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot commit to v3 key", error);
        }
    }

    public static final class Reveal {
        public final byte[] publicDer;
        public final byte[] nonce;

        private Reveal(byte[] publicDer, byte[] nonce) {
            this.publicDer = publicDer.clone();
            this.nonce = nonce.clone();
        }
    }

    /**
     * One consistent, independently copied view of a fully verified session.
     * No exposed field refers to live mutable state. Only the v3 verifier in
     * this Java package needs to consume this internal transcript.
     */
    static final class VerifiedTranscript {
        final String role;
        final String session;
        final byte[] ownPublic;
        final byte[] otherPublic;
        final byte[] ownNonce;
        final byte[] otherNonce;

        private VerifiedTranscript(String role, String session, byte[] ownPublic,
                byte[] otherPublic, byte[] ownNonce, byte[] otherNonce) {
            this.role = role;
            this.session = session;
            this.ownPublic = ownPublic.clone();
            this.otherPublic = otherPublic.clone();
            this.ownNonce = ownNonce.clone();
            this.otherNonce = otherNonce.clone();
        }
    }

    public static final class Session implements AutoCloseable {
        private final String role;
        private final String session;
        private final byte[] localPublic;
        private final byte[] localNonce;
        private final byte[] localCommit;
        private byte[] peerCommit;
        private byte[] peerPublic;
        private byte[] peerNonce;
        private boolean localRevealed;
        private boolean closed;

        public Session(String role, String session, byte[] publicDer) {
            this(role, session, publicDer, randomNonce());
        }

        public Session(String role, String session, byte[] publicDer,
                       byte[] nonce) {
            localCommit = commit(role, session, publicDer, nonce);
            this.role = role;
            this.session = session;
            localPublic = publicDer.clone();
            localNonce = nonce.clone();
        }

        private static byte[] randomNonce() {
            byte[] bytes = new byte[32];
            RNG.nextBytes(bytes);
            return bytes;
        }

        public synchronized byte[] publicCommit() { return localCommit.clone(); }

        public synchronized void receivePeerCommit(byte[] value) {
            if (closed || peerCommit != null || localRevealed
                    || value == null || value.length != 32
                    || MessageDigest.isEqual(value, localCommit))
                throw new IllegalStateException("Invalid or repeated v3 commit");
            peerCommit = value.clone();
        }

        public synchronized Reveal reveal() {
            if (closed || peerCommit == null || localRevealed)
                throw new IllegalStateException(
                        "Must receive peer's commit before local reveal");
            localRevealed = true;
            return new Reveal(localPublic, localNonce);
        }

        public synchronized void receivePeerReveal(byte[] publicDer, byte[] nonce) {
            if (closed || peerCommit == null || !localRevealed
                    || peerPublic != null)
                throw new IllegalStateException("v3 reveal out of sequence");
            try {
                byte[] expected = commit("tv".equals(role) ? "ha" : "tv",
                        session, publicDer, nonce);
                if (!MessageDigest.isEqual(expected, peerCommit)
                        || MessageDigest.isEqual(localPublic, publicDer))
                    throw new IllegalArgumentException("Wrong v3 peer key");
                peerPublic = publicDer.clone();
                peerNonce = nonce.clone();
            } catch (RuntimeException error) {
                close();
                throw new IllegalArgumentException(
                        "Uncommitted or invalid v3 peer reveal");
            }
        }

        public synchronized boolean ready() {
            return !closed && peerPublic != null;
        }

        /** Package-only role accessor, fails if commitments were not verified. */
        synchronized String verifiedRole() {
            if (!ready()) throw new IllegalStateException("v3 peer not verified");
            return role;
        }

        synchronized VerifiedTranscript verifiedTranscript() {
            if (!ready()) throw new IllegalStateException("v3 peer not verified");
            // Capture all six fields with one lock. A cancellation on another
            // thread cannot splice data from two independent pairing attempts.
            return new VerifiedTranscript(role, session, localPublic,
                    peerPublic, localNonce, peerNonce);
        }

        public synchronized byte[] peerPublic() {
            if (!ready()) throw new IllegalStateException("v3 peer not verified");
            return peerPublic.clone();
        }

        public synchronized byte[] peerNonce() {
            if (!ready()) throw new IllegalStateException("v3 peer not verified");
            return peerNonce.clone();
        }

        @Override public synchronized void close() {
            closed = true;
            Arrays.fill(localNonce, (byte) 0);
            if (peerNonce != null) Arrays.fill(peerNonce, (byte) 0);
            peerNonce = null;
            peerPublic = null;
        }
    }
}
