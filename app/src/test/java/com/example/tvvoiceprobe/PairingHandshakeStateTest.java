package com.example.tvvoiceprobe;

import java.security.KeyPair;
import java.util.Base64;
import org.junit.Test;

import static org.junit.Assert.*;

/** A forged or stale LAN message cannot silently approve pairing. */
public class PairingHandshakeStateTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test public void publicKeyExchangeDisplaysSameCodeButNeedsTvOk() {
        PairingHandshakeState tv = new PairingHandshakeState(SESSION, 500);
        KeyPair ha = PairingVerificationCode.freshKeyPair();
        assertNull(tv.code(510));
        assertNull(tv.proof(510));
        assertFalse(tv.approve(510));

        PairingHandshakeState.Offer offer = tv.offer(
                SESSION, b64(ha.getPublic().getEncoded()), 511);
        assertEquals(8, offer.code.length());
        assertTrue(offer.code.matches("[0-9]{8}"));
        assertEquals(offer.code, tv.code(512));
        assertEquals(offer.code, PairingVerificationCode.calculate(
                ha, false, SESSION, Base64.getUrlDecoder().decode(offer.tvKey),
                ha.getPublic().getEncoded()));
        assertNull(tv.proof(513));
        assertTrue(tv.approve(514));
        assertFalse(tv.approve(515));
        assertEquals(PairingVerificationCode.approvedProof(
                ha, false, SESSION, Base64.getUrlDecoder().decode(offer.tvKey),
                ha.getPublic().getEncoded()), tv.proof(516));
        tv.close();
        assertNull(tv.code(517));
        assertNull(tv.proof(517));
        assertFalse(tv.approve(517));
    }

    @Test public void rejectedAndRepeatedOffersNeverRotateDisplayedCode() {
        PairingHandshakeState tv = new PairingHandshakeState(SESSION, 100);
        KeyPair first = PairingVerificationCode.freshKeyPair();
        KeyPair second = PairingVerificationCode.freshKeyPair();
        try {
            tv.offer("AQIDBAUGBwgJCgsMDQ4PEQ", b64(first.getPublic().getEncoded()), 110);
            fail("Wrong session accepted");
        } catch (IllegalStateException expected) { }
        PairingHandshakeState.Offer offer = tv.offer(
                SESSION, b64(first.getPublic().getEncoded()), 111);
        try {
            tv.offer(SESSION, b64(second.getPublic().getEncoded()), 112);
            fail("Second peer displaced first");
        } catch (IllegalStateException expected) { }
        assertEquals(offer.code, tv.code(113));
    }

    @Test public void timeoutForbidsOfferApprovalAndProof() {
        PairingHandshakeState expired = new PairingHandshakeState(SESSION, 50);
        KeyPair ha = PairingVerificationCode.freshKeyPair();
        try {
            expired.offer(SESSION, b64(ha.getPublic().getEncoded()), 120050);
            fail("Expired pairing accepted");
        } catch (IllegalStateException expected) { }
        assertFalse(expired.approve(120050));
        assertNull(expired.proof(120050));
    }

    @Test public void malformedKeyCannotYieldApproval() {
        PairingHandshakeState tv = new PairingHandshakeState(SESSION, 50);
        try {
            tv.offer(SESSION, "bad", 51);
            fail("Malformed key accepted");
        } catch (IllegalStateException expected) { }
        assertFalse(tv.approve(52));
        assertNull(tv.proof(53));
    }
}
