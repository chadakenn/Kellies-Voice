package com.example.tvvoiceprobe;

import java.util.Arrays;
import java.util.Base64;
import org.junit.Test;

import static org.junit.Assert.*;

/** Identical v3 fixture and commit ordering on HA Python and Android JVM. */
public class PairingKeyCommitmentTest {
    private static final String SID = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final byte[] TV = b64(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q");
    private static final byte[] HA = b64(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q");

    private static byte[] b64(String str) {
        return Base64.getUrlDecoder().decode(str);
    }

    private static byte[] nonce(int offset) {
        byte[] n = new byte[32];
        for (int i = 0; i < n.length; i++) n[i] = (byte) (offset + i);
        return n;
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) result.append(String.format("%02x", b & 255));
        return result.toString();
    }

    @Test public void exactSharedPythonVector() {
        assertEquals("df28eb33b2fb5c6d5e45e8732a5893e3c938892dc2ff642361235f7b5f1a9f2d",
            hex(PairingKeyCommitment.commit("tv", SID, TV, nonce(0))));
        assertEquals("f6fbfcfefc4a3aa3c9ccb39a9bd97888a2c02a564e85c1a869681fe02dc9bd9c",
            hex(PairingKeyCommitment.commit("ha", SID, HA, nonce(32))));
    }

    @Test public void publicKeysOnlyRevealedAfterBothCommits() {
        try (PairingKeyCommitment.Session tv =
                    new PairingKeyCommitment.Session("tv", SID, TV, nonce(0));
             PairingKeyCommitment.Session ha =
                    new PairingKeyCommitment.Session("ha", SID, HA, nonce(32))) {
            assertFalse(tv.ready());
            try { tv.reveal(); fail("Uncommitted key revealed"); }
            catch (IllegalStateException expected) { }
            tv.receivePeerCommit(ha.publicCommit());
            ha.receivePeerCommit(tv.publicCommit());
            PairingKeyCommitment.Reveal tvReveal = tv.reveal();
            PairingKeyCommitment.Reveal haReveal = ha.reveal();
            tv.receivePeerReveal(haReveal.publicDer, haReveal.nonce);
            ha.receivePeerReveal(tvReveal.publicDer, tvReveal.nonce);
            assertTrue(tv.ready());
            assertTrue(ha.ready());
            assertArrayEquals(HA, tv.peerPublic());
            assertArrayEquals(TV, ha.peerPublic());
            try { ha.reveal(); fail("Repeated reveal accepted"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void attackerCannotSwitchKeyAfterCommitOrRetry() {
        try (PairingKeyCommitment.Session tv =
                 new PairingKeyCommitment.Session("tv", SID, TV, nonce(0));
             PairingKeyCommitment.Session ha =
                 new PairingKeyCommitment.Session("ha", SID, HA, nonce(32))) {
            tv.receivePeerCommit(ha.publicCommit());
            ha.receivePeerCommit(tv.publicCommit());
            tv.reveal();
            ha.reveal();
            byte[] substitute = HA.clone();
            substitute[substitute.length - 1] ^= 1;
            try {
                tv.receivePeerReveal(substitute, nonce(32));
                fail("Attacker substituted revealed key");
            } catch (IllegalArgumentException expected) { }
            assertFalse(tv.ready());
            try { tv.receivePeerReveal(HA, nonce(32)); fail("Retried"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void wrongNonceRoleSessionAndReflectionRejected() {
        byte[] digest = PairingKeyCommitment.commit("tv", SID, TV, nonce(0));
        assertFalse(Arrays.equals(digest,
                PairingKeyCommitment.commit("ha", SID, TV, nonce(0))));
        assertFalse(Arrays.equals(digest,
                PairingKeyCommitment.commit("tv", SID, TV, nonce(32))));
        assertFalse(Arrays.equals(digest,
                PairingKeyCommitment.commit("tv",
                    "AQIDBAUGBwgJCgsMDQ4PEQ", TV, nonce(0))));
        try (PairingKeyCommitment.Session tv =
                 new PairingKeyCommitment.Session("tv", SID, TV, nonce(0))) {
            try { tv.receivePeerCommit(tv.publicCommit()); fail("Reflection"); }
            catch (IllegalStateException expected) { }
            try { tv.receivePeerCommit(new byte[4]); fail("Invalid size"); }
            catch (IllegalStateException expected) { }
        }
    }

    @Test public void invalidAndStaleCommitNeverBecomesReady() {
        try (PairingKeyCommitment.Session tv =
                 new PairingKeyCommitment.Session("tv", SID, TV, nonce(0))) {
            try { tv.receivePeerReveal(HA, nonce(32)); fail("Early reveal"); }
            catch (IllegalStateException expected) { }
            tv.close();
            assertFalse(tv.ready());
            try { tv.reveal(); fail("Closed key leaked"); }
            catch (IllegalStateException expected) { }
        }
    }
}
