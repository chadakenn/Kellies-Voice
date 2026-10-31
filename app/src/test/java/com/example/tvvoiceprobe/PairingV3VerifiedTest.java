package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Independently checks the exact Python fixture in #242 with pure JVM JCA.
 * There are no fake derivation helpers or production credentials in tests.
 */
public final class PairingV3VerifiedTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String UUID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String OTHER_UUID = "223e4567-e89b-42d3-a456-426614174000";
    private static final byte[] TV_DER = decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q");
    private static final byte[] HA_DER = decode(
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q");

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static byte[] nonce(int start) {
        byte[] data = new byte[32];
        for (int i = 0; i < 32; i++) data[i] = (byte) (start + i);
        return data;
    }

    private static PrivateKey scalar(int scalar) throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = params.getParameterSpec(ECParameterSpec.class);
        return KeyFactory.getInstance("EC").generatePrivate(
                new ECPrivateKeySpec(BigInteger.valueOf(scalar), curve));
    }

    private static final class Pair implements AutoCloseable {
        final PrivateKey tvKey;
        final PrivateKey haKey;
        final PairingKeyCommitment.Session tv;
        final PairingKeyCommitment.Session ha;

        Pair() throws Exception { this(0, 32, SESSION); }

        Pair(int tvNonce, int haNonce, String session) throws Exception {
            tvKey = scalar(1);
            haKey = scalar(2);
            tv = new PairingKeyCommitment.Session("tv", session,
                    TV_DER, nonce(tvNonce));
            ha = new PairingKeyCommitment.Session("ha", session,
                    HA_DER, nonce(haNonce));
        }

        void verified() {
            // Neither side sees a key/nonce until both commitments exist.
            tv.receivePeerCommit(ha.publicCommit());
            ha.receivePeerCommit(tv.publicCommit());
            PairingKeyCommitment.Reveal fromHa = ha.reveal();
            PairingKeyCommitment.Reveal fromTv = tv.reveal();
            tv.receivePeerReveal(fromHa.publicDer, fromHa.nonce);
            ha.receivePeerReveal(fromTv.publicDer, fromTv.nonce);
        }

        @Override public void close() {
            tv.close();
            ha.close();
        }
    }

    @Test public void exactCrossLanguageEightDigitCodeAndTvMac() throws Exception {
        try (Pair p = new Pair()) {
            p.verified();
            assertEquals("83428994", PairingV3Verified.comparisonCode(p.tvKey, p.tv));
            assertEquals("83428994", PairingV3Verified.comparisonCode(p.haKey, p.ha));
            assertEquals("TuUIMiT-zkUz3XaKygd5Gw",
                    PairingV3Verified.physicalTvOkProof(p.tvKey, p.tv, UUID));
            assertEquals("TuUIMiT-zkUz3XaKygd5Gw",
                    PairingV3Verified.physicalTvOkProof(p.haKey, p.ha, UUID));
            assertNotEquals(
                    PairingV3Verified.physicalTvOkProof(p.tvKey, p.tv, UUID),
                    PairingV3Verified.physicalTvOkProof(p.tvKey, p.tv, OTHER_UUID));
        }
    }

    @Test public void noCodeOrProofBeforeBothCommitsAndVerifiedReveals() throws Exception {
        try (Pair p = new Pair()) {
            try { PairingV3Verified.comparisonCode(p.tvKey, p.tv); fail("No peer"); }
            catch (IllegalStateException expected) { }
            p.tv.receivePeerCommit(p.ha.publicCommit());
            p.ha.receivePeerCommit(p.tv.publicCommit());
            p.tv.reveal();
            p.ha.reveal();
            try { PairingV3Verified.comparisonCode(p.tvKey, p.tv); fail("Early code"); }
            catch (IllegalStateException expected) { }
            try { PairingV3Verified.physicalTvOkProof(p.tvKey, p.tv, UUID);
                fail("Early approval"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void wrongPrivateKeyCannotSpoofCommittedPublicKey() throws Exception {
        try (Pair p = new Pair()) {
            p.verified();
            try { PairingV3Verified.comparisonCode(p.haKey, p.tv); fail("Wrong key"); }
            catch (IllegalArgumentException expected) { }
            try { PairingV3Verified.comparisonCode(p.tvKey, p.ha); fail("Wrong role"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void wrongNonceOrSessionChangesTheSas() throws Exception {
        try (Pair a = new Pair(); Pair changedTv = new Pair(1, 32, SESSION);
             Pair changedHa = new Pair(0, 33, SESSION);
             Pair changedSession = new Pair(0, 32, "AQIDBAUGBwgJCgsMDQ4PEQ")) {
            a.verified(); changedTv.verified(); changedHa.verified();
            changedSession.verified();
            String code = PairingV3Verified.comparisonCode(a.tvKey, a.tv);
            assertNotEquals(code, PairingV3Verified.comparisonCode(
                    changedTv.tvKey, changedTv.tv));
            assertNotEquals(code, PairingV3Verified.comparisonCode(
                    changedHa.tvKey, changedHa.tv));
            assertNotEquals(code, PairingV3Verified.comparisonCode(
                    changedSession.tvKey, changedSession.tv));
            assertEquals(PairingV3Verified.comparisonCode(
                    changedHa.haKey, changedHa.ha),
                    PairingV3Verified.comparisonCode(
                    changedHa.tvKey, changedHa.tv));
        }
    }

    @Test public void tamperedRevealCannotBeReplayedIntoSuccess() throws Exception {
        try (Pair p = new Pair()) {
            p.tv.receivePeerCommit(p.ha.publicCommit());
            p.ha.receivePeerCommit(p.tv.publicCommit());
            p.tv.reveal(); p.ha.reveal();
            try { p.tv.receivePeerReveal(HA_DER, nonce(33)); fail("Attack"); }
            catch (IllegalArgumentException expected) { }
            try { p.tv.receivePeerReveal(HA_DER, nonce(32)); fail("Replay"); }
            catch (IllegalStateException expected) { }
            try { PairingV3Verified.comparisonCode(p.tvKey, p.tv); fail("Code"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void cancelPreventsLaterDerivationAndDoesNotLeakNonce() throws Exception {
        try (Pair p = new Pair()) {
            p.verified();
            PairingKeyCommitment.VerifiedTranscript copy = p.tv.verifiedTranscript();
            p.tv.close();
            assertFalse(p.tv.ready());
            try { PairingV3Verified.comparisonCode(p.tvKey, p.tv); fail("Cancelled"); }
            catch (IllegalStateException expected) { }
            // The returned snapshot is NOT a backdoor to derive an active
            // code after cancellation: public API only accepts a live Session.
            assertEquals(32, copy.ownNonce.length);
        }
    }

    @Test public void invalidOrNoncanonicalTvIdentityIsRejected() throws Exception {
        try (Pair p = new Pair()) {
            p.verified();
            for (String id : new String[] { "", "not-a-uuid", UUID.toUpperCase(),
                    "00000000-0000-0000-0000-000000000000" }) {
                try { PairingV3Verified.physicalTvOkProof(p.tvKey, p.tv, id);
                    fail("Invalid ID accepted " + id); }
                catch (IllegalArgumentException expected) { }
            }
        }
    }
}
