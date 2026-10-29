package com.example.tvvoiceprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Isolated AndroidKeyStore storage for a VERIFIED, owner-consented #193 login.
 *
 * IMPORTANT: no existing app service reads this slot yet. It cannot change
 * the stable manual CredentialStore, trigger Assist, or show Connected.
 * A later, independently reviewed activation/reconnect coordinator must
 * integrate this store and token refresh with the existing app.
 *
 * Only PairingActivationGate permits a candidate to be committed after
 * (a) Assist + scoped alerts proof and (b) an authenticated HA AEAD ACK.
 */
public final class OAuthVerifiedCredentialStore {
    private static final String ALIAS = "kellies_voice_verified_oauth_v1";
    private static final String PREFS = "kellies_voice_oauth_verified";
    private static final String SECRET = "encrypted_verified";
    private static final byte[] AAD =
            "KelliesVoice/oauth-verified/v1".getBytes(StandardCharsets.US_ASCII);
    private final Context context;

    public static final class Connection {
        public final String serverUrl;
        public final String clientId;
        public final String accessToken;
        public final String refreshToken;
        public final String tvId;
        public final long accessExpiresAtMillis;

        Connection(String serverUrl, String clientId, String accessToken,
                   String refreshToken, String tvId, long accessExpiresAtMillis) {
            this.serverUrl = serverUrl;
            this.clientId = clientId;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.tvId = tvId;
            this.accessExpiresAtMillis = accessExpiresAtMillis;
        }
        // Deliberately no toString(), logging or serialization helper.
    }

    public OAuthVerifiedCredentialStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing = (SecretKey) store.getKey(ALIAS, null);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    /**
     * Call only inside a verified, live owner+TV onboarding coordinator.
     *
     * The caller must have validated an authenticated, transcript-bound HA
     * commit acknowledgement, not merely a public /status response.
     * Wall time checks the OAuth token; elapsed time checks the 2-minute
     * physical pairing session. The gate monitor prevents simultaneous
     * callbacks from committing twice.
     */
    public void saveAfterProof(
            PairedOAuthCredentials candidate, PairingActivationGate gate,
            String expectedSession, String expectedTvId,
            long nowWallMillis, long nowElapsedMillis) {
        if (gate == null || candidate == null) {
            throw new IllegalArgumentException("Missing verified pairing");
        }
        synchronized (gate) {
            if (!gate.canCommit(expectedSession, expectedTvId, nowElapsedMillis)
                    || !candidate.matches(expectedSession, expectedTvId, nowWallMillis)
                    || !candidate.serverUrl.startsWith("https://")) {
                gate.abort();
                throw new IllegalArgumentException("Unverified or expired pairing");
            }

            // Store the OAuth identity separately. Do NOT overwrite the
            // legacy manually configured HA URL/token or pending stage.
            byte[] plaintext = null;
            try {
                JSONObject payload = new JSONObject();
                payload.put("version", 1);
                payload.put("server_url", candidate.serverUrl);
                payload.put("client_id", candidate.clientId);
                payload.put("access_token", candidate.accessToken);
                payload.put("refresh_token", candidate.refreshToken);
                payload.put("tv_id", candidate.tvId);
                payload.put("access_expires_at", candidate.accessExpiresAtMillis);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key());
                cipher.updateAAD(AAD);
                plaintext = payload.toString().getBytes(StandardCharsets.UTF_8);
                byte[] encrypted = cipher.doFinal(plaintext);
                JSONObject envelope = new JSONObject();
                envelope.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
                envelope.put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP));
                Arrays.fill(encrypted, (byte) 0);

                // Replacement/re-pair needs an explicit crash-safe rollback
                // protocol. Until that is independently reviewed, reject it
                // instead of overwriting a working verified OAuth identity.
                if (prefs().contains(SECRET)) {
                    throw new IllegalStateException("Verified OAuth login already exists");
                }
                // SharedPreferences commit is synchronous. The existing
                // manual connection is always in an entirely separate store.
                if (!prefs().edit().putString(SECRET, envelope.toString()).commit()) {
                    throw new IllegalStateException("Verified login could not be saved");
                }
                gate.recordWriteResult(true);
            } catch (Exception failure) {
                gate.abort();
                throw new IllegalStateException(
                        "Verified Home Assistant login could not be stored");
            } finally {
                if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
            }
        }
    }

    /** Read only this new, encrypted OAuth identity; never the legacy slot. */
    public synchronized Connection load(String expectedTvId) throws Exception {
        if (expectedTvId == null || expectedTvId.isEmpty()) {
            throw new IllegalArgumentException("Expected TV required");
        }
        String stored = prefs().getString(SECRET, null);
        if (stored == null) return null;
        JSONObject envelope = new JSONObject(stored);
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP);
        if (iv.length != 12 || ciphertext.length < 17 || ciphertext.length > 8192) {
            throw new IllegalStateException("Invalid encrypted OAuth record");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        byte[] plain = cipher.doFinal(ciphertext);
        JSONObject value;
        try {
            value = new JSONObject(new String(plain, StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
        if (value.getInt("version") != 1 || value.length() != 7
                || !expectedTvId.equals(value.getString("tv_id"))) {
            throw new IllegalStateException("Invalid or wrong-TV OAuth record");
        }
        String origin = value.getString("server_url");
        String clientId = value.getString("client_id");
        if (!origin.startsWith("https://")
                || !clientId.equals(origin.endsWith("/") ? origin : origin + "/")) {
            throw new IllegalStateException("Invalid OAuth client binding");
        }
        return new Connection(origin, clientId,
                value.getString("access_token"), value.getString("refresh_token"),
                value.getString("tv_id"), value.getLong("access_expires_at"));
    }

    /** Explicit user-initiated unlink, not invoked by failed new pairing. */
    public synchronized void clearVerified() {
        if (!prefs().edit().remove(SECRET).commit()) {
            throw new IllegalStateException("Could not clear verified OAuth record");
        }
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(ALIAS);
        } catch (Exception ignored) {
            // The encrypted record has already been removed.
        }
    }
}
