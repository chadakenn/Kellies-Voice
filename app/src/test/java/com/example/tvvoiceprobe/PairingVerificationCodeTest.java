package com.example.tvvoiceprobe;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.junit.Test;
import static org.junit.Assert.*;

/** Java/HA-Python interop vector for #193; no HA tokens or sockets. */
public class PairingVerificationCodeTest {
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TV_UUID = "123e4567-e89b-42d3-a456-426614174000";
    private static final String OTHER_UUID = "223e4567-e89b-42d3-a456-426614174000";
    private static final String TV =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q";
    private static final String HA =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q";

    private static byte[] encoded(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static KeyPair testKey(int scalar, String publicDer) throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = params.getParameterSpec(ECParameterSpec.class);
        KeyFactory factory = KeyFactory.getInstance("EC");
        return new KeyPair(
                factory.generatePublic(new X509EncodedKeySpec(encoded(publicDer))),
                factory.generatePrivate(new ECPrivateKeySpec(BigInteger.valueOf(scalar), curve)));
    }

    @Test public void matchesSharedPythonReferenceVectorOnBothPeers() throws Exception {
        KeyPair tv = testKey(1, TV);
        KeyPair ha = testKey(2, HA);
        String tvCode = PairingVerificationCode.calculate(
                tv, true, SESSION, encoded(TV), encoded(HA));
        String haCode = PairingVerificationCode.calculate(
                ha, false, SESSION, encoded(TV), encoded(HA));
        assertEquals("34821918", tvCode);
        assertEquals(tvCode, haCode);
        String tvProof = PairingVerificationCode.approvedProof(
                tv, true, SESSION, encoded(TV), encoded(HA));
        assertEquals("zWMp2ha4rIAnsdSpGP8sow", tvProof);
        assertEquals(tvProof, PairingVerificationCode.approvedProof(
                ha, false, SESSION, encoded(TV), encoded(HA)));
    }

    @Test public void v2UuidBoundProofMatchesPythonAndRejectsSubstitution() throws Exception {
        KeyPair tv = testKey(1, TV);
        KeyPair ha = testKey(2, HA);
        String proof = PairingVerificationCode.approvedProof(
                tv, true, SESSION, encoded(TV), encoded(HA), TV_UUID);
        assertEquals("IQTCP6AIBcrvhq11xt54HA", proof);
        assertEquals(proof, PairingVerificationCode.approvedProof(
                ha, false, SESSION, encoded(TV), encoded(HA), TV_UUID));
        assertNotEquals(proof, PairingVerificationCode.approvedProof(
                tv, true, SESSION, encoded(TV), encoded(HA), OTHER_UUID));
        assertNotEquals(proof, PairingVerificationCode.approvedProof(
                tv, true, SESSION, encoded(TV), encoded(HA)));
        for (String invalid : new String[] { "AQIDBAUGBwgJCgsMDQ4PEA",
                TV_UUID.toUpperCase(), "123e4567-e89b-12d3-a456-426614174000",
                "123e4567-e89b-42d3-7456-426614174000" }) {
            try {
                PairingVerificationCode.approvedProof(
                        tv, true, SESSION, encoded(TV), encoded(HA), invalid);
                fail("Invalid or uncanonical TV UUID accepted");
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void changesWhenSessionOrPeerChanges() throws Exception {
        KeyPair tv = testKey(1, TV);
        String original = PairingVerificationCode.calculate(
                tv, true, SESSION, encoded(TV), encoded(HA));
        assertNotEquals(original, PairingVerificationCode.calculate(
                tv, true, "AQIDBAUGBwgJCgsMDQ4PEQ", encoded(TV), encoded(HA)));
        KeyPair otherHa = PairingVerificationCode.freshKeyPair();
        assertNotEquals(original, PairingVerificationCode.calculate(
                tv, true, SESSION, encoded(TV), otherHa.getPublic().getEncoded()));
    }

    @Test public void refusesSwappedRolesOrDuplicatePublicKey() throws Exception {
        KeyPair tv = testKey(1, TV);
        try {
            PairingVerificationCode.calculate(tv, false, SESSION, encoded(TV), encoded(HA));
            fail("TV could claim HA's ephemeral key");
        } catch (IllegalArgumentException expected) { }
        try {
            PairingVerificationCode.calculate(tv, true, SESSION, encoded(TV), encoded(TV));
            fail("Same public key accepted for two roles");
        } catch (IllegalArgumentException expected) { }
        try {
            PairingVerificationCode.calculate(tv, true, SESSION, encoded(HA), encoded(TV));
            fail("Swapped transcript roles accepted");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsWrongCurveAndMalformedKeys() throws Exception {
        KeyPair tv = testKey(1, TV);
        for (byte[] bad : new byte[][] { new byte[0], new byte[150], encoded(TV) }) {
            try {
                PairingVerificationCode.calculate(tv, true, SESSION, encoded(TV), bad);
                fail("Invalid peer accepted");
            } catch (IllegalArgumentException expected) { }
        }
        KeyPairGenerator p384 = KeyPairGenerator.getInstance("EC");
        p384.initialize(new ECGenParameterSpec("secp384r1"));
        try {
            PairingVerificationCode.calculate(
                    tv, true, SESSION, encoded(TV), p384.generateKeyPair().getPublic().getEncoded());
            fail("Non-P256 peer accepted");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsInvalidOrNoncanonicalSessions() throws Exception {
        KeyPair tv = testKey(1, TV);
        for (String sid : new String[] { "", "not-base64", SESSION + "A",
                SESSION.substring(0, 21) + "B", "######################" }) {
            try {
                PairingVerificationCode.calculate(tv, true, sid, encoded(TV), encoded(HA));
                fail("Invalid session accepted: " + sid);
            } catch (IllegalArgumentException expected) { }
        }
    }
}
