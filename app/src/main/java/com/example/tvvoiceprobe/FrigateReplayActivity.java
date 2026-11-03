package com.example.tvvoiceprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.content.res.ColorStateList;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
    private volatile boolean closing;
    private volatile String haOrigin;
    private volatile String haToken;
    private ExoPlayer clipPlayer;
    private FrameLayout playbackLayer;
    private TextView playbackLabel;
    private boolean requestSent;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
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

        FrameLayout host = new FrameLayout(this);
        host.addView(root, new FrameLayout.LayoutParams(-1, -1));

        playbackLayer = new FrameLayout(this);
        playbackLayer.setBackgroundColor(Color.BLACK);
        playbackLayer.setVisibility(View.GONE);
        host.addView(playbackLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(host);
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
        haOrigin = base.toString();
        haToken = bearer;
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
                        .put("device_id", new OverlayPreferences(this).deviceId())
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
            if (!TvReplayEvent.validId(id))
                continue;
            String label = event.optString("label", "event");
            if (!TvReplayEvent.validLabel(label)) continue;
            double startedAt = event.optDouble("start_time", 0);
            long epochMs = TvReplayEvent.epochMillis(startedAt);
            if (epochMs < 0) continue;

            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT,
                    Locale.getDefault()).format(new Date(epochMs));
            Button entry = new Button(this);
            entry.setText((shown + 1) + ".  " + label.replace('_', ' ') + "  ·  " + time);
            entry.setAllCaps(false);
            entry.setTextColor(COLOR_TEXT);
            entry.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            entry.setTextSize(19);
            entry.setPadding(dp(20), dp(12), dp(20), dp(12));
            entry.setOnClickListener(v -> playRecordedEvent(id));
            rows.addView(entry, new LinearLayout.LayoutParams(-1, dp(66)));
            shown++;
        }
        status.setText(shown == 0 ? "No valid recorded events returned."
                : shown + " recorded events · press OK to play, Back to exit");
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    /** Play only our own HA-authenticated per-TV clip route. No Frigate URL. */
    private void playRecordedEvent(String id) {
        if (closing || !TvReplayEvent.validId(id) || camera == null) return;
        String origin = haOrigin, bearer = haToken;
        HttpUrl base = HttpUrl.parse(origin == null ? "" : origin);
        if (base == null || bearer == null || bearer.isEmpty()) {
            showStatus("Home Assistant login is not ready.");
            return;
        }
        HttpUrl url = base.newBuilder()
                .addPathSegments("api/kellies_voice/replay_clip")
                .addPathSegment(new OverlayPreferences(this).deviceId())
                .addPathSegment(camera)
                .addPathSegment(id)
                .build();

        stopPlayer();
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + bearer);
        DefaultHttpDataSource.Factory httpSource = new DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(12000)
                .setReadTimeoutMs(30000)
                .setAllowCrossProtocolRedirects(false);
        DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(this, httpSource);
        clipPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
                .build();
        clipPlayer.setVolume(0f); // Never surprise viewers with clip audio.
        PlayerView view = new PlayerView(this);
        view.setUseController(true);
        view.setPlayer(clipPlayer);
        playbackLayer.removeAllViews();
        playbackLayer.addView(view, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setPadding(dp(24), dp(12), dp(24), dp(12));
        controls.setBackgroundColor(0xB0000000);
        Button back = new Button(this);
        back.setText("Back to events");
        back.setAllCaps(false);
        back.setOnClickListener(v -> stopPlayer());
        controls.addView(back);

        Button audio = new Button(this);
        audio.setText("Unmute clip");
        audio.setAllCaps(false);
        audio.setOnClickListener(v -> {
            if (clipPlayer == null) return;
            boolean muted = clipPlayer.getVolume() == 0f;
            clipPlayer.setVolume(muted ? 1f : 0f);
            audio.setText(muted ? "Mute clip" : "Unmute clip");
        });
        controls.addView(audio);
        FrameLayout.LayoutParams menu = new FrameLayout.LayoutParams(
                -1, dp(80), Gravity.BOTTOM);
        playbackLayer.addView(controls, menu);

        playbackLayer.setVisibility(View.VISIBLE);
        playbackLayer.bringToFront();
        back.requestFocus();

        clipPlayer.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException exception) {
                stopPlayer();
                showStatus("Recording unavailable, expired, or blocked by HA permissions.");
            }
        });
        clipPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url.toString())));
        clipPlayer.prepare();
        clipPlayer.play();
    }

    private void stopPlayer() {
        if (clipPlayer != null) {
            clipPlayer.stop();
            clipPlayer.release();
            clipPlayer = null;
        }
        if (playbackLayer != null) {
            playbackLayer.setVisibility(View.GONE);
            playbackLayer.removeAllViews();
        }
    }

    @Override public void onBackPressed() {
        if (playbackLayer != null && playbackLayer.getVisibility() == View.VISIBLE) {
            stopPlayer();
            return;
        }
        super.onBackPressed();
    }

    @Override protected void onStop() {
        // Never keep recording video playing after another app takes the screen.
        stopPlayer();
        super.onStop();
    }

    private void showStatus(String text) {
        runOnUiThread(() -> {
            if (!closing && status != null) status.setText(text);
        });
    }

    @Override protected void onDestroy() {
        stopPlayer();
        synchronized (this) {
            closing = true;
            if (socket != null) {
                socket.close(1000, "Replay screen closed");
                socket = null;
            }
        }
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
        haOrigin = null;
        haToken = null;
        super.onDestroy();
    }
}
