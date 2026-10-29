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
 * #193 isolated, *pending only* Android Keystore storage.
 *
 * This class has no method that activates a saved Home Assistant login.
 * A future, separately security-reviewed gate must authenticate Assist and
 * scoped alerts before making a paired OAuth identity active. Existing
 * CredentialStore login is never read, changed, or deleted here.
 *
 * No public listener, browser UI, OAuth token refresh or microphone access.
 */
public final class OAuthCredentialStagingStore {
    private static final String KEY_ALIAS = "kellies_voice_pending_oauth_v1";
    private static final String PREFS = "kellies_voice_oauth_staging";
    private static final String PENDING = "encrypted_pending";
    private final Context context;

    public OAuthCredentialStagingStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing = (SecretKey) store.getKey(KEY_ALIAS, null);
        if (existing != null) return existing;
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    public synchronized void stage(PairedOAuthCredentials credentials,
                                   String expectedSession, String expectedTvId,
                                   long nowMillis) throws Exception {
        if (credentials == null ||
                !credentials.matches(expectedSession, expectedTvId, nowMillis)) {
            throw new IllegalArgumentException("Unverified or expired pairing context");
        }
        // This is stored ONLY in encrypted_pending. It is NOT live credentials
        // and will not be used by CredentialStore.load()/HaAssistClient.
        JSONObject value = new JSONObject();
        value.put("version", 1);
        value.put("server_url", credentials.serverUrl);
        value.put("client_id", credentials.clientId);
        value.put("access_token", credentials.accessToken);
        value.put("refresh_token", credentials.refreshToken);
        value.put("session", credentials.session);
        value.put("tv_id", credentials.tvId);
        value.put("access_expires_at", credentials.accessExpiresAtMillis);
        value.put("staged_at", credentials.stagedAtMillis);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD("KelliesVoice/oauth-stage/v1".getBytes(StandardCharsets.US_ASCII));
        byte[] plaintext = value.toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try {
            encrypted = cipher.doFinal(plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
        JSONObject envelope = new JSONObject();
        envelope.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        envelope.put("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP));
        if (!prefs().edit().putString(PENDING, envelope.toString()).commit()) {
            throw new IllegalStateException("Cannot stage Home Assistant credentials");
        }
    }

    public synchronized PairedOAuthCredentials loadPending(
            String session, String tvId, long nowMillis) {
        String sealed = prefs().getString(PENDING, null);
        if (sealed == null) return null;
        try {
            JSONObject envelope = new JSONObject(sealed);
            byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP);
            if (iv.length != 12 || ciphertext.length < 17 || ciphertext.length > 8192) {
                throw new IllegalArgumentException("Invalid staged envelope");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD("KelliesVoice/oauth-stage/v1".getBytes(StandardCharsets.US_ASCII));
            byte[] clear = cipher.doFinal(ciphertext);
            JSONObject value;
            try {
                value = new JSONObject(new String(clear, StandardCharsets.UTF_8));
            } finally {
                Arrays.fill(clear, (byte) 0);
            }
            if (value.getInt("version") != 1) {
                throw new IllegalArgumentException("Unknown staged credential version");
            }
            PairedOAuthCredentials credentials = new PairedOAuthCredentials(
                    value.getString("server_url"), value.getString("client_id"),
                    value.getString("access_token"), value.getString("refresh_token"),
                    value.getString("session"), value.getString("tv_id"),
                    value.getLong("access_expires_at"), value.getLong("staged_at"));
            if (!credentials.matches(session, tvId, nowMillis)) {
                throw new IllegalArgumentException("Staged pairing expired or differs");
            }
            return credentials;
        } catch (Exception rejected) {
            // Fail closed, including keystore reset, corruption, stale
            // session/TV or a backward clock jump. Never report secrets.
            discard();
            return null;
        }
    }

    /** Cancel/timeout cleanup. Does NOT touch existing HA credentials. */
    public synchronized void discard() {
        boolean erased = prefs().edit().remove(PENDING).commit();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) {
            // The plaintext is still gone; old keystore key material is
            // non-exportable and unusable without the deleted envelope.
        }
        if (!erased) {
            // Do not claim cleanup succeeded if Android storage rejected it.
            throw new IllegalStateException("Could not discard staged credentials");
        }
    }
}
