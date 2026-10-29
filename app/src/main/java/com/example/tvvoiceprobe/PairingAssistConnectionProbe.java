package com.example.tvvoiceprobe;

import android.os.Handler;
import android.os.Looper;

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
 * Home Assistant and can access an Assist pipeline.
 *
 * Never sends a microphone frame, never starts Assist, never touches a saved
 * login, never stages/promotes a credential and never marks HA pairing done.
 * Only HTTPS with normal platform TLS trust is accepted. Local plain HTTP
 * token validation requires a separately reviewed encrypted tunnel.
 */
public final class PairingAssistConnectionProbe implements AutoCloseable {
    public interface Result {
        /** Delivered on Android's main thread, at most once. */
        void complete(boolean authenticatedAndAssistAvailable);
    }

    private static final long TIMEOUT_MS = 15_000L;
    private final PairingAssistProofState gate = new PairingAssistProofState();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    private final Result callback;
    private WebSocket socket;
    private boolean finished;
    private Runnable timeout;
    private String token;

    private PairingAssistConnectionProbe(Result callback, String token) {
        this.callback = callback;
        this.token = token;
    }

    /** Never call from a live TV route until #193 consent/AEAD is reviewed. */
    public static PairingAssistConnectionProbe check(
            PairedOAuthCredentials credentials, String expectedSession,
            String expectedTvId, Result callback) {
        if (callback == null || credentials == null ||
                !credentials.matches(expectedSession, expectedTvId,
                        System.currentTimeMillis())) {
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
        PairingAssistConnectionProbe probe =
                new PairingAssistConnectionProbe(callback, credentials.accessToken);
        probe.open(base);
        return probe;
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
                    boolean verified = gate.pipelineResult(
                            message.optInt("id", -1), message.optBoolean("success", false));
                    finish(verified);
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
        token = null;
        if (timeout != null) handler.removeCallbacks(timeout);
        WebSocket old = socket;
        socket = null;
        if (old != null) old.cancel();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        handler.post(() -> callback.complete(ok));
    }

    @Override public void close() {
        finish(false);
    }
}
