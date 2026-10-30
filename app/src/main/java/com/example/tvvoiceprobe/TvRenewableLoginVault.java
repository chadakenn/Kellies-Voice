package com.example.tvvoiceprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

/** Android-side renewable scoped HA login, staged OFFLINE pending v3 handoff.
 *
 * No screen, service or network route instantiates this class yet. No account
 * may be installed before cryptographic TV+owner+commit proof and a server
 * scope check. It never reads the existing stable/manual CredentialStore.
 */
final class TvRenewableLoginVault implements PairingV3ScopedTokenHandoff.Vault {
    private static final String PREFS = "kellies_v3_scoped_login_v1";
    private static final String ALIAS = "kellies_v3_scoped_token_v1";
    private static final String KEY = "encrypted_tv_login";
    private static final String AAD = "KelliesVoice/v3/scoped-renewable-login";
    private static final int MAX_JSON = 16_384;
    private final Context context;
    private final OkHttpClient http = new OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS).build();
    private String cachedAccess;
    private long accessExpiresElapsed;

    private static final class Record {
        TvRenewableLoginPolicy.Grant grant;
        String refresh;
        long expiresSeconds;
        boolean active;
    }

    TvRenewableLoginVault(Context context) {
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
        KeyGenerator gen = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return gen.generateKey();
    }

    private void write(Record r) throws Exception {
        TvRenewableLoginPolicy.checkCredential(
                r.refresh, "keystore-refresh-only", r.expiresSeconds);
        JSONObject data = new JSONObject();
        data.put("v", 1);
        data.put("origin", r.grant.haOrigin);
        data.put("tv", r.grant.tvUuid);
        data.put("client", r.grant.clientId);
        data.put("token_id", r.grant.tokenId);
        data.put("refresh", r.refresh);
        data.put("expires", r.expiresSeconds);
        data.put("active", r.active);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(AAD.getBytes(StandardCharsets.US_ASCII));
        byte[] clear = data.toString().getBytes(StandardCharsets.UTF_8);
        byte[] sealed;
        try {
            sealed = cipher.doFinal(clear);
        } finally {
            Arrays.fill(clear, (byte) 0);
        }
        JSONObject envelope = new JSONObject();
        envelope.put("v", 1);
        envelope.put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP));
        envelope.put("sealed", Base64.encodeToString(sealed, Base64.NO_WRAP));
        Arrays.fill(sealed, (byte) 0);
        if (!prefs().edit().putString(KEY, envelope.toString()).commit())
            throw new IllegalStateException("TV secure storage unavailable");
    }

    private Record read() throws Exception {
        String stored = prefs().getString(KEY, null);
        if (stored == null) return null;
        if (stored.length() > MAX_JSON)
            throw new IllegalStateException("TV scoped login invalid");
        JSONObject envelope = new JSONObject(stored);
        if (envelope.getInt("v") != 1)
            throw new IllegalStateException("Unknown TV credential version");
        byte[] iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP);
        byte[] sealed = Base64.decode(envelope.getString("sealed"), Base64.NO_WRAP);
        if (iv.length != 12 || sealed.length > MAX_JSON || sealed.length < 16)
            throw new IllegalStateException("TV credential envelope invalid");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD.getBytes(StandardCharsets.US_ASCII));
        byte[] decoded = cipher.doFinal(sealed);
        Arrays.fill(sealed, (byte) 0);
        JSONObject data;
        try {
            data = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
        if (data.getInt("v") != 1)
            throw new IllegalStateException("Unknown TV session version");
        Record r = new Record();
        r.grant = TvRenewableLoginPolicy.check(
                data.getString("origin"), data.getString("tv"),
                data.getString("client"), data.getString("token_id"));
        r.refresh = data.getString("refresh");
        r.expiresSeconds = data.getLong("expires");
        r.active = data.getBoolean("active");
        TvRenewableLoginPolicy.checkCredential(
                r.refresh, "keystore-refresh-only", r.expiresSeconds);
        return r;
    }

    /** Internal v3 handoff hook ONLY. Provisional token cannot authenticate. */
    @Override public synchronized void stage(TvRenewableLoginPolicy.Grant grant,
            String refresh, String access, long lifetime) throws Exception {
        if (read() != null)
            throw new IllegalStateException("Existing TV credential must be revoked first");
        Record r = new Record();
        r.grant = TvRenewableLoginPolicy.check(
                grant.haOrigin, grant.tvUuid, grant.clientId, grant.tokenId);
        TvRenewableLoginPolicy.checkCredential(refresh, access, lifetime);
        r.refresh = refresh;
        r.expiresSeconds = lifetime;
        r.active = false;
        write(r);
    }

    /** Not wired. Caller must prove server committed exact lease after TV ACK.
     * Any missing ack/cancel/restart MUST leave record inactive.
     */
    @Override public synchronized void activateAfterVerifiedServerCommit(
            String tvUuid, String tokenId) throws Exception {
        Record r = read();
        if (r == null || r.active || !r.grant.tvUuid.equals(tvUuid)
                || !r.grant.tokenId.equals(tokenId))
            throw new IllegalStateException("Missing authenticated enrollment");
        r.active = true;
        write(r);
    }

    /** Must run on a background thread. HA official refresh_token grant.
     * Access token is short-lived RAM cache and is not an owner token.
     */
    synchronized String renewOnWorker() throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Never refresh HA on Android UI thread");
        Record r = read();
        if (r == null || !r.active)
            throw new IllegalStateException("No completed restricted TV login");
        long now = SystemClock.elapsedRealtime();
        if (cachedAccess != null && now + 60_000 < accessExpiresElapsed)
            return cachedAccess;
        FormBody form = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", r.refresh)
                .add("client_id", r.grant.clientId).build();
        Request request = new Request.Builder()
                .url(TvRenewableLoginPolicy.tokenUrl(r.grant)).post(form)
                .header("Cache-Control", "no-store").build();
        try (Response response = http.newCall(request).execute()) {
            if (response.code() == 400 || response.code() == 403) {
                // Denied or revoked token cannot silently fall back to
                // the stable/manual HA owner's bearer token.
                clear();
                throw new IllegalStateException("TV HA authorization revoked or rejected");
            }
            if (!response.isSuccessful() || response.body() == null)
                throw new IllegalStateException("HA token renewal unavailable");
            String body = response.body().string();
            if (body.length() > MAX_JSON)
                throw new IllegalStateException("Invalid HA token response");
            JSONObject data = new JSONObject(body);
            if (!"Bearer".equals(data.getString("token_type")))
                throw new IllegalStateException("Invalid HA token type");
            String access = data.getString("access_token");
            long expires = data.getLong("expires_in");
            TvRenewableLoginPolicy.checkCredential(r.refresh, access, expires);
            cachedAccess = access;
            accessExpiresElapsed = SystemClock.elapsedRealtime() +
                    expires * 1_000;
            return access;
        }
    }

    /** Used after pairing listener closes. Never discard an active login. */
    synchronized void clearIfIncomplete() {
        try {
            Record r = read();
            if (r != null && !r.active) clear();
        } catch (Exception ignored) {
            // A corrupted local token envelope must not become authorized.
            clear();
        }
    }

    /** Existing manual CredentialStore remains separate and unchanged. */
    synchronized CredentialStore.Connection connectionOnWorker() throws Exception {
        Record record = read();
        if (record == null || !record.active)
            return null;
        String token = renewOnWorker();
        return new CredentialStore.Connection(record.grant.haOrigin, token);
    }

    synchronized boolean hasActiveLogin() {
        try {
            Record record = read();
            return record != null && record.active;
        } catch (Exception ignored) {
            return false;
        }
    }

    synchronized void clear() {
        cachedAccess = null;
        accessExpiresElapsed = 0;
        prefs().edit().remove(KEY).commit();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(ALIAS);
        } catch (Exception ignored) {
            // Backend per-token revoke is mandatory, even if TV is lost.
        }
    }
}
