package com.example.tvvoiceprobe;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.Assert.*;

/**
 * Actual cross-language Python HA #221 to Android Java TV #222 AES-GCM test.
 * The Python generator writes encrypted HA packets; this TV peer opens
 * those exact bytes and writes encrypted Java receipts. A Python verifier
 * later opens both Java outputs with the real HA coordinator.
 *
 * Test-only, synthetic non-secret bytes. No network or saved credentials.
 */
public class TvPythonHandoffInteropTest {
    private static final String TV_PUB =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li_hp_m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q";
    private static final String HA_PUB =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEfPJ7GI0DT36KUjgDBLUaw8CJaeJ38hs1pgtI_EdmmXgHd1UQ247QQCk9msafdDDbun2t5jzpgimeBLedInhz0Q";
    private static final String SESSION = "AQIDBAUGBwgJCgsMDQ4PEA";
    private static final String TV_UUID = "123e4567-e89b-42d3-a456-426614174000";

    private static final class Clock implements TvSealedHandoff.Clock {
        @Override public long unixSeconds() { return 1_700_000_000L; }
        @Override public long elapsedMillis() { return 10_000L; }
    }

    private static KeyPair tvKeys() throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = params.getParameterSpec(ECParameterSpec.class);
        KeyFactory factory = KeyFactory.getInstance("EC");
        return new KeyPair(
                factory.generatePublic(new X509EncodedKeySpec(
                        Base64.getUrlDecoder().decode(TV_PUB))),
                factory.generatePrivate(new ECPrivateKeySpec(BigInteger.ONE, curve)));
    }

    private static byte[] decode(String value) {
        byte[] raw = Base64.getUrlDecoder().decode(value);
        assertEquals(value, Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        return raw;
    }

    private static TvSealedHandoff newTv() throws Exception {
        return new TvSealedHandoff(tvKeys(), SESSION, TV_UUID,
                decode(TV_PUB), decode(HA_PUB), new Clock());
    }

    private static JSONObject pythonFixture() throws Exception {
        String path = System.getenv("KV_INTEROP_FIXTURE");
        Assume.assumeTrue("Python HA fixture is supplied by interop CI",
                path != null && !path.isEmpty());
        return new JSONObject(new String(Files.readAllBytes(java.nio.file.Paths.get(path)), StandardCharsets.UTF_8));
    }

    private static PairingAssistProofState syntheticVerifiedGate() {
        PairingAssistProofState proof = new PairingAssistProofState();
        assertTrue(proof.challenge());
        assertTrue(proof.authenticated());
        assertTrue(proof.pipelineResult(
                PairingAssistProofState.PIPELINES_REQUEST_ID, true, true));
        assertTrue(proof.scopedAlertResult(
                PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID, true));
        return proof;
    }

    @Test public void opensRealPythonPacketsAndExportsJavaReceiptsForPythonVerifier()
            throws Exception {
        JSONObject fixture = pythonFixture();
        assertEquals(SESSION, fixture.getString("session"));
        assertEquals(TV_UUID, fixture.getString("tv_uuid"));
        byte[] synthetic = decode(fixture.getString("candidate"));
        assertTrue(new String(synthetic, StandardCharsets.US_ASCII)
                .startsWith("SYNTHETIC NON-SECRET"));
        byte[] haOffer = decode(fixture.getString("ha_offer"));
        byte[] haCommit = decode(fixture.getString("ha_commit"));

        try (TvSealedHandoff tv = newTv()) {
            tv.onSyntheticOffer(haOffer);
            assertEquals(TvSealedHandoff.Stage.WAIT_CAPABILITIES, tv.stage());
            byte[] proofPacket = tv.syntheticTvProof(syntheticVerifiedGate());
            assertEquals(1, proofPacket[1]); // outbound TV direction
            byte[] persistedPacket = tv.onHaCommitForSynthetic(haCommit, candidate -> {
                assertArrayEquals(synthetic, candidate);
                return true;
            });
            assertEquals(TvSealedHandoff.Stage.PERSISTED, tv.stage());
            JSONObject receipts = new JSONObject();
            receipts.put("tv_proof", Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(proofPacket));
            receipts.put("tv_persisted", Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(persistedPacket));
            String path = System.getenv("KV_INTEROP_RECEIPTS");
            assertNotNull("CI must provide receipt output path", path);
            Path output = Path.of(path);
            Files.createDirectories(output.getParent());
            Files.write(output, receipts.toString().getBytes(StandardCharsets.UTF_8));
            assertFalse(receipts.toString().contains("SYNTHETIC NON-SECRET"));
        }
    }

    @Test public void tamperedPythonEncryptedOfferCannotAdvanceTV() throws Exception {
        JSONObject fixture = pythonFixture();
        byte[] packet = decode(fixture.getString("ha_offer"));
        packet[packet.length - 1] ^= 1;
        try (TvSealedHandoff tv = newTv()) {
            try {
                tv.onSyntheticOffer(packet);
                fail("Tampered real Python ciphertext was accepted by Android Java");
            } catch (IllegalArgumentException expected) { }
            assertEquals(TvSealedHandoff.Stage.ABORTED, tv.stage());
        }
    }
}
