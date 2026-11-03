package com.example.tvvoiceprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Explicitly opened from a camera alert's authenticated action button.
 * Requests only read-scoped Frigate event metadata. No clip URL, password,
 * thumbnail, or media stream is ever received or played by this Activity.
 */
public final class FrigateReplayActivity extends Activity {
    public static final String EXTRA_CAMERA_ENTITY = "replay_camera_entity";
    private static final int REQUEST_ID = 743;
    private static final int COLOR_BG = 0xFF111829;
    private static final int COLOR_TEXT = 0xFFEAF1FA;
    private static final int COLOR_SUB = 0xFFB5C4D8;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private WebSocket socket;
    private TextView status;
    private LinearLayout rows;
    private String camera;
    private boolean closing;
    private boolean requestSent;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        camera = TvEventPayload.cameraEntity(getIntent().getStringExtra(EXTRA_CAMERA_ENTITY));
        if (camera == null) {
            finish();
            return;
        }
        renderShell();
        // Fetch/renew credentials off the UI thread. Renewable mode never
        // falls back to the saved owner token after revocation or expiry.
        new Thread(this::connectOnWorker, "KelliesReplayAuth").start();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int sp, int color) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(sp);
        label.setTextColor(color);
        label.setPadding(0, dp(6), 0, dp(6));
        return label;
    }

    private void renderShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(48), dp(28), dp(48), dp(24));
        root.setBackgroundColor(COLOR_BG);

        TextView heading = text("Recent camera events", 30, COLOR_TEXT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(heading);
        root.addView(text(camera, 18, COLOR_SUB));
        root.addView(text("Recorded event history · remote OK selects · Back exits",
                16, COLOR_SUB));

        status = text("Connecting securely to Home Assistant…", 20, COLOR_TEXT);
        root.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button back = new Button(this);
        back.setText("Back to TV");
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(dp(230), dp(56)));
        back.requestFocus();
        setContentView(root);
    }

    private void connectOnWorker() {
        CredentialStore.Connection connection;
        try {
            TvRenewableLoginVault vault = new TvRenewableLoginVault(this);
            if (vault.blocksManualFallback()) {
                connection = vault.connectionOnWorker();
            } else {
                connection = new CredentialStore(this).load();
            }
        } catch (Exception ignored) {
            connection = null;
        }
        if (connection == null) {
            showStatus("No authorized TV login. Reconnect Kellie's Voice in Settings.");
            return;
        }
        HttpUrl base = HttpUrl.parse(connection.url);
        if (base == null || !(base.scheme().equals("http") || base.scheme().equals("https"))
                || !base.username().isEmpty() || !base.password().isEmpty()
                || base.query() != null || base.fragment() != null) {
            showStatus("Saved Home Assistant address is invalid.");
            return;
        }
        final String bearer = connection.token;
        HttpUrl wsUrl = base.newBuilder().addPathSegments("api/websocket").build();
        synchronized (this) {
            if (closing) return;
            socket = http.newWebSocket(new Request.Builder().url(wsUrl).build(),
                    new WebSocketListener() {
                        @Override public void onMessage(WebSocket ws, String raw) {
                            handle(ws, raw, bearer);
                        }

                        @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                            showStatus("Home Assistant connection unavailable.");
                        }

                        @Override public void onClosed(WebSocket ws, int code, String reason) {
                            if (!closing && !requestSent) showStatus("Connection closed.");
                        }
                    });
        }
    }

    private void handle(WebSocket ws, String raw, String bearer) {
        if (closing) return;
        try {
            JSONObject packet = new JSONObject(raw);
            String type = packet.optString("type", "");
            if ("auth_required".equals(type)) {
                ws.send(new JSONObject().put("type", "auth")
                        .put("access_token", bearer).toString());
                return;
            }
            if ("auth_invalid".equals(type)) {
                showStatus("TV account login expired or was revoked.");
                ws.close(1000, "Authentication rejected");
                return;
            }
            if ("auth_ok".equals(type)) {
                requestSent = true;
                ws.send(new JSONObject().put("id", REQUEST_ID)
                        .put("type", "kellies_voice/replay_events")
                        .put("device_id", new OverlayPreferences(thisActivity()).deviceId())
                        .put("entity_id", camera).put("limit", 8).toString());
                return;
            }
            if (packet.optInt("id", -1) != REQUEST_ID) return;
            if ("result".equals(type) && packet.optBoolean("success", false)) {
                JSONObject result = packet.optJSONObject("result");
                if (result == null || !camera.equals(result.optString("camera_entity_id", ""))) {
                    showStatus("Camera authorization mismatch.");
                    return;
                }
                JSONArray events = result.optJSONArray("events");
                runOnUiThread(() -> showEvents(events));
            } else if ("result".equals(type)) {
                JSONObject error = packet.optJSONObject("error");
                String code = error == null ? "" : error.optString("code", "");
                if ("unauthorized".equals(code)) showStatus("No permission to read camera events.");
                else if ("replay_unavailable".equals(code)) showStatus("Frigate is not available or this camera is not mapped.");
                else showStatus("No recorded events available for this camera.");
            }
            ws.close(1000, "Event list received");
        } catch (Exception ignored) {
            showStatus("Could not read recorded-event metadata.");
        }
    }

    private Activity thisActivity() { return this; }

    private void showEvents(JSONArray events) {
        if (closing || rows == null || status == null) return;
        rows.removeAllViews();
        if (events == null || events.length() == 0) {
            status.setText("No completed recordings found for this camera.");
            return;
        }
        int count = Math.min(8, events.length());
        int shown = 0;
        for (int i = 0; i < count; i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            String id = event.optString("event_id", "");
            if (!id.matches("[A-Za-z0-9][A-Za-z0-9._-]{5,95}") || id.contains(".."))
                continue;
            String label = event.optString("label", "event");
            if (!label.matches("[a-z][a-z0-9_]{0,39}")) continue;
            double startedAt = event.optDouble("start_time", 0);
            if (!Double.isFinite(startedAt) || startedAt <= 0) continue;

            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                    Locale.getDefault()).format(new Date((long) (startedAt * 1000)));
            Button entry = new Button(this);
            entry.setText((shown + 1) + ".  " + label.replace('_', ' ') + "  ·  " + time);
            entry.setAllCaps(false);
            entry.setTextColor(COLOR_TEXT);
            entry.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            entry.setTextSize(19);
            entry.setPadding(dp(20), dp(12), dp(20), dp(12));
            entry.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Recording found")
                    .setMessage("Recorded video playback is not enabled in this development build yet. "
                            + "This screen verifies the correct camera and event permissions first.")
                    .setPositiveButton("OK", null).show());
            rows.addView(entry, new LinearLayout.LayoutParams(-1, dp(66)));
            shown++;
        }
        status.setText(shown == 0 ? "No valid recorded events returned."
                : shown + " recorded events · metadata preview only (no video player yet)");
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    private void showStatus(String text) {
        runOnUiThread(() -> {
            if (!closing && status != null) status.setText(text);
        });
    }

    @Override protected void onDestroy() {
        synchronized (this) {
            closing = true;
            if (socket != null) {
                socket.close(1000, "Replay screen closed");
                socket = null;
            }
        }
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
        super.onDestroy();
    }
}
