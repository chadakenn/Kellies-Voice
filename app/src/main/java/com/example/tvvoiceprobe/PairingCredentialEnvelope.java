package com.example.tvvoiceprobe;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Decrypt ONE authorized #193 OAuth candidate from the exact ECDH session.
 *
 * The caller must first verify HA owner approval, the matching 8-digit
 * code and physical TV OK. Possession of an encrypted packet ALONE is not
 * sufficient consent. This decoder does not open a listener, save or
 * activate login credentials, start audio, or mark HA pairing complete.
 */
public final class PairingCredentialEnvelope {
    private static final String KIND = "kellies_voice/pair/credentials/v1";
    private static final int MAX_PLAINTEXT = 2048;

    private PairingCredentialEnvelope() { }

    public static PairedOAuthCredentials receive(
            PairingSealedChannel channel, byte[] sealed, String expectedSession,
            String expectedTvId, long nowMillis) {
        if (channel == null || sealed == null || expectedSession == null
                || expectedTvId == null || nowMillis < 1) {
            throw new IllegalArgumentException("Invalid verified pairing context");
        }
        byte[] clear = null;
        try {
            // The channel rejects invalid AEAD, wrong role, session, replay,
            // direction, order or oversize BEFORE we can parse a payload.
            clear = channel.open(sealed);
            if (clear.length == 0 || clear.length > MAX_PLAINTEXT) {
                throw new IllegalArgumentException("Invalid provisioned credential");
            }
            JSONObject payload = new JSONObject(
                    new String(clear, StandardCharsets.UTF_8));
            if (payload.length() != 8 || !KIND.equals(payload.getString("kind"))) {
                throw new IllegalArgumentException("Invalid provisioned credential");
            }
            PairedOAuthCredentials value = new PairedOAuthCredentials(
                    payload.getString("server_url"),
                    payload.getString("client_id"),
                    payload.getString("access_token"),
                    payload.getString("refresh_token"),
                    payload.getString("session"),
                    payload.getString("tv_id"),
                    payload.getLong("expires_at_ms"), nowMillis);
            if (!value.matches(expectedSession, expectedTvId, nowMillis)) {
                throw new IllegalArgumentException("Mismatched provisioned TV");
            }
            // Still pending-only: a different component must stage this in
            // Android Keystore, prove scoped Assist + alerts, then atomically
            // activate it only after full owner/TV consent.
            return value;
        } catch (Exception error) {
            // A failed packet poisons the session; never keep a potentially
            // compromised channel open for the attacker to retry.
            channel.close();
            throw new IllegalArgumentException(
                    "Encrypted pairing credential rejected");
        } finally {
            if (clear != null) Arrays.fill(clear, (byte) 0);
        }
    }
}
