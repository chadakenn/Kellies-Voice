package com.example.tvvoiceprobe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Bounded HA-camera HLS streams for the opt-in screensaver grid.
 * Video stays muted. On unsupported HA streams, late frames or decoder errors
 * each tile keeps its preexisting authenticated snapshot fallback.
 */
final class ScreensaverLiveGrid {
    private static final int REQUEST_START = 4100;
    private static final long FIRST_FRAME_TIMEOUT_MS = 18000L;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CredentialStore.Connection connection;
    private final String[] cameras;
    private final TextureView[] surfaces;
    private final ImageView[] snapshots;
    private final TextView[] statuses;
    private final boolean[] playing;
    private final ExoPlayer[] players;
    private final int limit;
    private final String decoderMode;
    private final OkHttpClient http = new OkHttpClient();
    private WebSocket socket;
    private boolean stopped;

    ScreensaverLiveGrid(Context context, CredentialStore.Connection connection, String[] cameras,
            TextureView[] surfaces, ImageView[] snapshots, TextView[] statuses,
            int limit, String decoderMode) {
        this.context = context.getApplicationContext();
        this.connection = connection;
        this.cameras = cameras;
        this.surfaces = surfaces;
        this.snapshots = snapshots;
        this.statuses = statuses;
        this.limit = Math.max(0, Math.min(Math.min(cameras.length, 4), limit));
        this.decoderMode = LiveCameraMode.normalize(decoderMode);
        this.playing = new boolean[cameras.length];
        this.players = new ExoPlayer[cameras.length];
    }

    boolean isLive(int index) {
        return index >= 0 && index < playing.length && playing[index];
    }

    void start() {
        if (stopped || limit == 0 || connection == null) return;
        HttpUrl base = HttpUrl.parse(connection.url);
        if (base == null || (!"http".equals(base.scheme()) && !"https".equals(base.scheme()))) return;
        HttpUrl url = base.newBuilder().addPathSegment("api").addPathSegment("websocket").build();
        socket = http.newWebSocket(new Request.Builder().url(url).build(), new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String raw) {
                if (stopped || socket != ws) return;
                JSONObject msg;
                try { msg = new JSONObject(raw); }
                catch (Exception ignored) { return; }
                String type = msg.optString("type");
                if ("auth_required".equals(type)) {
                    try {
                        ws.send(new JSONObject().put("type", "auth")
                                .put("access_token", connection.token).toString());
                    } catch (Exception ignored) { }
                } else if ("auth_ok".equals(type)) {
                    for (int i = 0; i < limit; ++i) {
                        try {
                            ws.send(new JSONObject().put("id", REQUEST_START + i)
                                    .put("type", "camera/stream")
                                    .put("entity_id", cameras[i]).put("format", "hls").toString());
                        } catch (Exception ignored) { }
                    }
                } else if ("result".equals(type)) {
                    int slot = msg.optInt("id", 0) - REQUEST_START;
                    if (slot < 0 || slot >= limit) return;
                    JSONObject result = msg.optJSONObject("result");
                    String path = result == null ? "" : result.optString("url",
                            result.optString("path", ""));
                    if (!msg.optBoolean("success", false) || !TvEventPayload.hlsPathAllowed(path)) {
                        main.post(() -> status(slot, "Snapshot · live unavailable"));
                        return;
                    }
                    main.post(() -> play(slot, path));
                } else if ("auth_invalid".equals(type)) {
                    main.post(() -> {
                        for (int i = 0; i < limit; i++) status(i, "Snapshot · HA sign-in failed");
                    });
                }
            }
            @Override public void onFailure(WebSocket ws, Throwable error, Response response) {
                main.post(() -> {
                    if (stopped || socket != ws) return;
                    for (int i = 0; i < limit; i++) {
                        if (!playing[i]) status(i, "Snapshot · live connection unavailable");
                    }
                });
            }
        });
    }

    private DefaultRenderersFactory renderers() {
        DefaultRenderersFactory factory = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true);
        if (LiveCameraMode.softwareOnly(decoderMode)) {
            factory.setMediaCodecSelector((mime, secure, tunnel) -> {
                List<MediaCodecInfo> codecs = MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mime, secure, tunnel);
                if (!MimeTypes.isVideo(mime)) return codecs;
                List<MediaCodecInfo> allowed = new ArrayList<>();
                for (MediaCodecInfo codec : codecs) {
                    if (codec.softwareOnly) allowed.add(codec);
                }
                return allowed; // Never use the TV's potentially unsafe hardware decoder.
            });
        }
        return factory;
    }

    private void play(int slot, String path) {
        if (stopped || slot < 0 || slot >= limit || players[slot] != null) return;
        HttpUrl base = HttpUrl.parse(connection.url);
        HttpUrl resolved = base == null ? null : base.resolve(path);
        if (base == null || resolved == null || !base.scheme().equals(resolved.scheme())
                || !base.host().equals(resolved.host()) || base.port() != resolved.port()
                || !resolved.encodedPath().startsWith("/api/hls/")) {
            status(slot, "Snapshot · rejected stream address");
            return;
        }
        final TextureView surface = surfaces[slot];
        if (surface == null) return;
        try {
            ExoPlayer player = new ExoPlayer.Builder(context, renderers()).build();
            player.setVolume(0f);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
            player.addListener(new Player.Listener() {
                @Override public void onRenderedFirstFrame() {
                    if (stopped || players[slot] != player) return;
                    playing[slot] = true;
                    snapshots[slot].setVisibility(View.INVISIBLE);
                    surface.setAlpha(1f);
                    status(slot, "LIVE · muted");
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (stopped || players[slot] != player) return;
                    fallback(slot, error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                            ? "Snapshot · decoder unavailable" : "Snapshot · stream interrupted");
                }
            });
            players[slot] = player;
            surface.setAlpha(0f);
            player.setVideoTextureView(surface);
            player.setMediaItem(new MediaItem.Builder().setUri(resolved.toString())
                    .setMimeType(MimeTypes.APPLICATION_M3U8).build());
            player.prepare();
            player.play();
            status(slot, "Connecting to live…");
            main.postDelayed(() -> {
                if (!stopped && players[slot] == player && !playing[slot]) {
                    fallback(slot, "Snapshot · live timed out");
                }
            }, FIRST_FRAME_TIMEOUT_MS);
        } catch (RuntimeException ignored) {
            fallback(slot, "Snapshot · player unavailable");
        }
    }

    private void status(int slot, String value) {
        if (!stopped && statuses[slot] != null) statuses[slot].setText(value);
    }

    private void fallback(int slot, String reason) {
        playing[slot] = false;
        if (surfaces[slot] != null) surfaces[slot].setAlpha(0f);
        if (snapshots[slot] != null) snapshots[slot].setVisibility(View.VISIBLE);
        ExoPlayer old = players[slot];
        players[slot] = null;
        if (old != null) {
            if (surfaces[slot] != null) old.clearVideoTextureView(surfaces[slot]);
            old.release();
        }
        status(slot, reason);
    }

    void stop() {
        if (stopped) return;
        stopped = true;
        main.removeCallbacksAndMessages(null);
        if (socket != null) {
            socket.close(1000, "screensaver closed");
            socket = null;
        }
        for (int slot = 0; slot < players.length; ++slot) {
            ExoPlayer old = players[slot];
            players[slot] = null;
            playing[slot] = false;
            if (old != null) {
                if (surfaces[slot] != null) old.clearVideoTextureView(surfaces[slot]);
                old.release();
            }
        }
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
    }
}
