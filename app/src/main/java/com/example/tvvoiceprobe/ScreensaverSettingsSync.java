package com.example.tvvoiceprobe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Opt-in screensaver setting changes from HA while the TV's voice UI is closed.
 * This never opens a microphone, exposes tokens in a URL, or reads other TVs'
 * controls. No polling or personal data logs.
 */
final class ScreensaverSettingsSync {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient();
    private WebSocket socket;
    private boolean stopped = true;
    private int attempts;
    private final Runnable reconnect = this::connect;
    private final HaNetworkWatch networks;

    ScreensaverSettingsSync(Context context) {
        this.context = context.getApplicationContext();
        this.networks = new HaNetworkWatch(this.context, main, this::onNetworkReady);
    }

    void start() {
        if (!stopped) return;
        stopped = false;
        networks.start();
        ScreensaverWatchStatus.settingsSync("Connecting to Home Assistant");
        connect();
    }

    private void connect() {
        if (stopped || !new OverlayPreferences(context).haManage()) return;
        CredentialStore.Connection saved;
        try {
            saved = new CredentialStore(context).load();
        } catch (Exception ignored) {
            saved = null;
        }
        if (saved == null || saved.url == null || saved.token == null) {
            ScreensaverWatchStatus.settingsSync("No saved Home Assistant credentials on this TV");
            return;
        }
        HttpUrl base = HttpUrl.parse(saved.url);
        if (base == null || (!"http".equals(base.scheme()) && !"https".equals(base.scheme()))) {
            ScreensaverWatchStatus.settingsSync("Invalid saved Home Assistant address");
            return;
        }
        final String token = saved.token;
        HttpUrl url = base.newBuilder().addPathSegment("api").addPathSegment("websocket").build();
        Request req = new Request.Builder().url(url).build();
        socket = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String raw) {
                if (stopped || ws != socket) return;
                JSONObject msg;
                try { msg = new JSONObject(raw); }
                catch (Exception ignored) { return; }
                String type = msg.optString("type");
                if ("auth_required".equals(type)) {
                    try {
                        JSONObject auth = new JSONObject();
                        auth.put("type", "auth");
                        auth.put("access_token", token);
                        ws.send(auth.toString());
                    } catch (Exception ignored) { }
                    return;
                }
                if ("auth_ok".equals(type)) {
                    ScreensaverWatchStatus.settingsSync("Connected to Home Assistant, reading settings");
                    attempts = 0;
                    ws.send("{\"id\":2,\"type\":\"subscribe_events\",\"event_type\":\"state_changed\"}");
                    ws.send("{\"id\":3,\"type\":\"get_states\"}");
                    return;
                }
                if ("auth_invalid".equals(type)) {
                    ScreensaverWatchStatus.settingsSync("Home Assistant rejected the saved sign-in");
                }
                if ("event".equals(type) && msg.optInt("id") == 2) {
                    JSONObject event = msg.optJSONObject("event");
                    if (event == null || !"state_changed".equals(event.optString("event_type"))) return;
                    JSONObject data = event.optJSONObject("data");
                    if (data != null) ingest(data.optJSONObject("new_state"));
                } else if ("result".equals(type) && msg.optInt("id") == 3
                        && msg.optBoolean("success", false)) {
                    JSONArray result = msg.optJSONArray("result");
                    if (result != null) for (int i = 0; i < result.length(); i++) ingest(result.optJSONObject(i));
                }
            }

            @Override public void onFailure(WebSocket ws, Throwable t, Response response) {
                ScreensaverWatchStatus.settingsSync("Home Assistant disconnected; retrying");
                scheduleReconnect(ws);
            }

            @Override public void onClosed(WebSocket ws, int code, String reason) {
                ScreensaverWatchStatus.settingsSync("Home Assistant connection closed; retrying");
                scheduleReconnect(ws);
            }
        });
    }

    private void ingest(JSONObject state) {
        if (state == null || stopped) return;
        JSONObject attrs = state.optJSONObject("attributes");
        if (attrs == null) return;
        String setting = attrs.optString("kellie_setting", "");
        if (!HaLayout.isScreensaver(setting)) return;
        String pairing = new OverlayPreferences(context).deviceId();
        if (!pairing.equals(attrs.optString("kellie_device_id", ""))) return;
        // Generation zero belongs to HA's default placeholder; never erase
        // previously configured local Frigate settings on first HA startup.
        if (attrs.optInt("kellie_generation", 0) < 1) {
            if (HaLayout.SCREENSAVER_URL.equals(setting) || HaLayout.SCREENSAVER_CAMERAS.equals(setting)) {
                ScreensaverWatchStatus.sourceSync("HA source has not been saved yet (generation 0)");
            }
            return;
        }
        String value = state.optString("state", "");
        if ("unknown".equals(value) || "unavailable".equals(value)) return;
        main.post(() -> {
            if (stopped || !new OverlayPreferences(context).haManage()) return;
            OverlayPreferences local = new OverlayPreferences(context);
            boolean changed = HaLayout.apply(local, setting, value);
            if (HaLayout.SCREENSAVER_URL.equals(setting)) {
                ScreensaverWatchStatus.sourceSync(local.screensaverUrl().isEmpty()
                        ? "HA URL received: cleared" : "HA URL received: web page selected");
            } else if (HaLayout.SCREENSAVER_CAMERAS.equals(setting)) {
                int selected = ScreensaverDashboard.cameraList(local.screensaverCameras()).length;
                ScreensaverWatchStatus.sourceSync("HA camera list received: " + selected + " selected");
            }
            ScreensaverWatchStatus.settingsSync("Connected to Home Assistant");
            if (changed) ScreensaverSettingsEffects.apply(context, setting);
        });
    }

    private void scheduleReconnect(WebSocket ended) {
        main.post(() -> {
            if (stopped || socket != ended) return;
            socket = null;
            main.removeCallbacks(reconnect);
            int capped = Math.min(5, attempts++);
            long delay = RetryTiming.withJitter(Math.min(60000L, 2000L * (1L << capped)),
                    60000L, Math.random());
            main.postDelayed(reconnect, delay);
        });
    }

    private void onNetworkReady() {
        if (stopped) return;
        WebSocket old = socket;
        socket = null;
        main.removeCallbacks(reconnect);
        if (old != null) old.cancel();
        ScreensaverWatchStatus.settingsSync("Network is back. Reconnecting");
        connect();
    }

    void stop() {
        stopped = true;
        networks.stop();
        ScreensaverWatchStatus.settingsSync("HA settings listener stopped");
        main.removeCallbacks(reconnect);
        if (socket != null) {
            socket.close(1000, "Frigate settings stopped");
            socket = null;
        }
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
