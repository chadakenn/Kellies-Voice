package com.example.tvvoiceprobe;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * One-shot read-only proof that a PENDING token is actually accepted by
 * Home Assistant, has a usable Assist pipeline AND is authorized to
 * subscribe to alerts for this exact paired TV.
 *
 * Never sends a microphone frame, never starts Assist, never touches a saved
 * login, never stages/promotes a credential and never marks HA pairing done.
 * Only HTTPS with normal platform TLS trust is accepted. Local plain HTTP
 * token validation requires a separately reviewed encrypted tunnel.
 */
public final class PairingAssistConnectionProbe implements AutoCloseable {
    public interface Result {
        /** Delivered on Android's main thread, at most once. */
        void complete(boolean assistAndScopedAlertsAuthorized);
    }

    /**
     * One-use proof minted exclusively after the live HTTPS WebSocket reached
     * auth_ok -> nonempty Assist list -> same-TV scoped alerts success.
     * This never contains credentials, and cannot be constructed by a caller.
     */
    public static final class VerifiedCapabilityReceipt {
        private static final long RECEIPT_TTL_MS = 15_000L;
        private final String tvId;
        private final String session;
        private final long issuedElapsed;
        private final AtomicBoolean claimed = new AtomicBoolean();

        private VerifiedCapabilityReceipt(String tvId, String session, long issuedElapsed) {
            this.tvId = tvId;
            this.session = session;
            this.issuedElapsed = issuedElapsed;
        }

        /** Single-use exact TV/session binding; use a monotonic Android clock. */
        public boolean claim(String expectedTvId, String expectedSession, long nowElapsed) {
            return expectedTvId != null && expectedSession != null
                    && tvId.equals(expectedTvId) && session.equals(expectedSession)
                    && nowElapsed >= issuedElapsed
                    && nowElapsed - issuedElapsed < RECEIPT_TTL_MS
                    && claimed.compareAndSet(false, true);
        }
    }

    public interface VerifiedResult {
        /** A null proof means failure; always on main thread and at most once. */
        void complete(VerifiedCapabilityReceipt proof);
    }

    private static final long TIMEOUT_MS = 15_000L;
    private final PairingAssistProofState gate = new PairingAssistProofState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            // Never follow redirects from the HA server when negotiating a
            // sensitive temporary authentication socket.
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final Result callback;
    private final VerifiedResult verifiedCallback;
    private final String expectedSession;
    private WebSocket socket;
    private boolean finished;
    private Runnable timeout;
    private String token;
    private final String tvId;

    private PairingAssistConnectionProbe(Result callback, VerifiedResult verifiedCallback,
                                         String token, String tvId, String session) {
        this.callback = callback;
        this.verifiedCallback = verifiedCallback;
        this.token = token;
        this.tvId = tvId;
        this.expectedSession = session;
    }

    /** Existing boolean proof callback remains for draft compatibility. */
    public static PairingAssistConnectionProbe check(
            PairedOAuthCredentials credentials, String expectedSession,
            String expectedTvId, Result callback) {
        if (callback == null) throw new IllegalArgumentException("Callback required");
        HttpUrl base = approvedOrigin(credentials, expectedSession, expectedTvId);
        PairingAssistConnectionProbe probe = new PairingAssistConnectionProbe(
                callback, null, credentials.accessToken, expectedTvId, expectedSession);
        probe.open(base);
        return probe;
    }

    /**
     * Stronger, one-use capability proof bound to the exact pairing attempt.
     * No credential stage/activation is performed here. This API may only be
     * called by a future independently authorized and verified coordinator.
     */
    public static PairingAssistConnectionProbe checkBound(
            PairedOAuthCredentials credentials, String expectedSession,
            String expectedTvId, VerifiedResult callback) {
        if (callback == null) throw new IllegalArgumentException("Callback required");
        HttpUrl base = approvedOrigin(credentials, expectedSession, expectedTvId);
        PairingAssistConnectionProbe probe = new PairingAssistConnectionProbe(
                null, callback, credentials.accessToken, expectedTvId, expectedSession);
        probe.open(base);
        return probe;
    }

    private static HttpUrl approvedOrigin(
            PairedOAuthCredentials credentials, String expectedSession, String expectedTvId) {
        if (credentials == null || !credentials.matches(
                expectedSession, expectedTvId, System.currentTimeMillis())) {
            throw new IllegalArgumentException("Unverified pairing proof context");
        }
        HttpUrl base = HttpUrl.parse(credentials.serverUrl);
        // Fail closed: even if the local HA origin is legal for discovery,
        // an OAuth access token must not traverse an unencrypted LAN socket.
        if (base == null || !"https".equals(base.scheme()) ||
                !base.username().isEmpty() || !base.password().isEmpty() ||
                !base.encodedPath().equals("/") ||
                base.query() != null || base.fragment() != null) {
            throw new IllegalArgumentException("Pairing proof requires trusted HTTPS");
        }
        return base;
    }

    private synchronized void open(HttpUrl base) {
        if (finished) return;
        HttpUrl endpoint = base.newBuilder().addPathSegments("api/websocket").build();
        timeout = () -> finish(false);
        handler.postDelayed(timeout, TIMEOUT_MS);
        try {
            socket = client.newWebSocket(
                    new Request.Builder().url(endpoint).build(),
                    new WebSocketListener() {
                        @Override public void onMessage(WebSocket ws, String data) {
                            handle(ws, data);
                        }

                        @Override public void onFailure(
                                WebSocket ws, Throwable problem, Response response) {
                            finish(false);
                        }

                        @Override public void onClosed(WebSocket ws, int code, String reason) {
                            finish(false);
                        }
                    });
        } catch (Exception ignored) {
            finish(false);
        }
    }

    private void handle(WebSocket ws, String data) {
        synchronized (this) {
            if (finished || ws != socket) return;
            try {
                JSONObject message = new JSONObject(data);
                String type = message.optString("type", "");
                if ("auth_required".equals(type)) {
                    if (!gate.challenge()) { finish(false); return; }
                    JSONObject auth = new JSONObject();
                    auth.put("type", "auth");
                    auth.put("access_token", token);
                    if (!ws.send(auth.toString())) { finish(false); return; }
                    // Token no longer needed by this class after sending.
                    token = null;
                    return;
                }
                if ("auth_ok".equals(type)) {
                    if (!gate.authenticated()) { finish(false); return; }
                    JSONObject command = new JSONObject();
                    command.put("id", PairingAssistProofState.PIPELINES_REQUEST_ID);
                    command.put("type", "assist_pipeline/pipeline/list");
                    if (!ws.send(command.toString())) finish(false);
                    return;
                }
                if ("result".equals(type)) {
                    int id = message.optInt("id", -1);
                    boolean ok = message.optBoolean("success", false);
                    if (gate.stage() == PairingAssistProofState.Stage.WAIT_PIPELINES) {
                        // A successful API call with no usable Assist pipeline
                        // must not be reported as a functioning assistant.
                        JSONObject result = message.optJSONObject("result");
                        org.json.JSONArray pipelines = result == null
                                ? null : result.optJSONArray("pipelines");
                        if (!gate.pipelineResult(id, ok,
                                pipelines != null && pipelines.length() > 0)) {
                            finish(false);
                            return;
                        }
                        JSONObject command = new JSONObject();
                        command.put("id", PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID);
                        command.put("type", "kellies_voice/subscribe_alerts");
                        command.put("device_id", tvId);
                        if (!ws.send(command.toString())) finish(false);
                        return;
                    }
                    if (gate.stage() == PairingAssistProofState.Stage.WAIT_SCOPED_ALERTS) {
                        boolean bothAuthorized = gate.scopedAlertResult(id, ok);
                        if (bothAuthorized) {
                            // Cancel the temporary alert listener before
                            // closing this one-shot proof socket.
                            JSONObject unsubscribe = new JSONObject();
                            unsubscribe.put("id", 77113);
                            unsubscribe.put("type", "unsubscribe_events");
                            unsubscribe.put("subscription",
                                    PairingAssistProofState.SCOPED_ALERTS_REQUEST_ID);
                            ws.send(unsubscribe.toString());
                        }
                        finish(bothAuthorized);
                        return;
                    }
                    finish(false);
                    return;
                }
                if ("auth_invalid".equals(type)) {
                    finish(false);
                    return;
                }
                // Do not trust other messages as proof; timeout unless HA
                // eventually sends the expected result in this session.
            } catch (Exception ignored) {
                finish(false);
            }
        }
    }

    private synchronized void finish(boolean ok) {
        if (finished) return;
        finished = true;
        if (!ok) gate.fail();
        VerifiedCapabilityReceipt verifiedProof =
                ok && gate.stage() == PairingAssistProofState.Stage.VERIFIED
                ? new VerifiedCapabilityReceipt(
                        tvId, expectedSession, SystemClock.elapsedRealtime())
                : null;
        token = null;
        if (timeout != null) handler.removeCallbacks(timeout);
        WebSocket old = socket;
        socket = null;
        if (old != null) old.cancel();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        if (callback != null)
            handler.post(() -> callback.complete(verifiedProof != null));
        if (verifiedCallback != null)
            handler.post(() -> verifiedCallback.complete(verifiedProof));
    }

    @Override public void close() {
        finish(false);
    }
}
