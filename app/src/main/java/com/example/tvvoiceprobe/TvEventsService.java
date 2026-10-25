package com.example.tvvoiceprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Opt-in, microphone-free foreground listener for authenticated Home
 * Assistant events. Popup windows are non-focusable and never intercept the
 * remote or take audio focus away from a video.
 *
 * Android can still stop this service. If alerts were already enabled, a
 * reboot or app-update broadcast may start it again. That is not guaranteed,
 * and this service does not start the microphone or replay old events.
 */
public final class TvEventsService extends Service {
    public static final String ACTION_START = "com.example.tvvoiceprobe.TV_EVENTS_START";
    /** User-requested recovery, unlike an ordinary idempotent service START. */
    public static final String ACTION_RESUME = "com.example.tvvoiceprobe.TV_EVENTS_RESUME";
    public static final String ACTION_STOP = "com.example.tvvoiceprobe.TV_EVENTS_STOP";
    public static final String ACTION_PREVIEW = "com.example.tvvoiceprobe.TV_EVENTS_PREVIEW";

    private static final String CHANNEL = "kellie_tv_events";
    private static final int NOTICE_ID = 2242;
    private static final int ANNOUNCE_ID = 1;
    private static final int CAMERA_ID = 2;
    private static final int LIVE_EVENT_ID = 3;
    private static final int LAYOUT_EVENT_ID = 4;
    private static final int LAYOUT_STATES_ID = 5;
    private static final int HIDE_CAMERA_EVENT_ID = 6;
    private static final int FORECAST_EVENT_ID = 7;
    private static final int TIMER_EVENT_ID = 8;
    private static final int SCOPED_ALERTS_ID = 9;
    private TimerOverlayController timerOverlay;
    private static final int MAX_SNAPSHOT_BYTES = 5 * 1024 * 1024;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build();
    private final Runnable reconnect = this::connect;
    private final Runnable connectionWatchdog = this::checkConnectionHealth;
    private final Runnable recoveryMonitor = this::checkReceiverRecovery;
    private final Runnable dismiss = this::hideCard;

    private WindowManager windowManager;
    private View card;
    private ImageView photo;
    private Call snapshotCall;
    private int snapshotSerial;
    private Runnable snapshotRefreshTask;
    private String snapshotEntity;
    private String snapshotTitle;
    private boolean snapshotHeader;
    private boolean snapshotFit;
    private TextureView liveSurface;
    private boolean liveFirstFrame;
    private VideoSize liveVideoSize;
    private TextView liveStatus;
    private ExoPlayer livePlayer;
    private int requestCounter = 100;
    private int legacyPending;
    private boolean legacyDenied;
    private boolean legacyMode;
    private int liveRequestId = -1;
    private int liveRequestGeneration = -1;
    private int layoutStatesId = -1;
    private WebSocket socket;
    private CredentialStore.Connection credentials;
    private boolean stopping;
    private boolean cameraVisible;
    private int failedAttempts;
    // Kept separate from network failures: auth_ok resets failedAttempts
    // even if HA has not created this TV's alert-position select yet.
    private int notReadyAttempts;
    // Separate from ordinary network retries; auth_ok must not reset this.
    private int deniedAttempts;
    private int connectionAttempts;
    private long nextReconnectAtMs = -1L;
    private int generation;
    private boolean alertsSubscribed;
    private boolean terminalAlertError;
    private int heartbeatId = -1;
    private long connectionStartedMs = -1L;
    private long heartbeatSentMs = -1L;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            new OverlayPreferences(this).setTvEvents(false);
            TvEventsRecovery.note(TvEventsRecovery.OFF);
            stopSelf();
            return START_NOT_STICKY;
        }
        // A user must first enable this in the app, and Android requires the
        // system "Display over other apps" permission for passive popups.
        if (!new OverlayPreferences(this).tvEvents()) {
            TvEventsRecovery.note(TvEventsRecovery.OFF);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this)) {
            TvEventsRecovery.note(TvEventsRecovery.PERMISSION);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (windowManager == null) windowManager =
                (WindowManager) getSystemService(WINDOW_SERVICE);
        CredentialStore.Connection saved;
        try { saved = new CredentialStore(this).load(); }
        catch (Exception ignored) { saved = null; }
        if (saved == null) {
            credentials = null;
            TvEventsRecovery.note(TvEventsRecovery.SIGNED_OUT);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (credentials != null
                && (!credentials.url.equals(saved.url) || !credentials.token.equals(saved.token))) {
            main.removeCallbacks(reconnect);
            main.removeCallbacks(connectionWatchdog);
            nextReconnectAtMs = -1L;
            connectionAttempts = 0;
            if (socket != null) {
                socket.cancel();
                socket = null;
            }
            clearConnectionTracking();
            terminalAlertError = false;
            failedAttempts = 0;
            notReadyAttempts = 0;
            deniedAttempts = 0;
        }
        credentials = saved;
        try {
            createChannel();
            Notification notification = buildNotification("TV alerts enabled");
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTICE_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTICE_ID, notification);
            }
        } catch (RuntimeException ignored) {
            TvEventsRecovery.note(TvEventsRecovery.STOPPED);
            stopSelf();
            return START_NOT_STICKY;
        }
        stopping = false;
        main.removeCallbacks(recoveryMonitor);
        main.postDelayed(recoveryMonitor, 20_000L);
        if (timerOverlay == null) timerOverlay = new TimerOverlayController(this, windowManager);
        TvEventsRestartReceiver.clearResume(this);
        if (ACTION_PREVIEW.equals(action)) {
            showCard("Kellie's Voice", "This is how announcements will appear over your TV.", null, 10);
        }
        if (TvAlertConnectionPolicy.shouldForceRestart(
                ACTION_RESUME.equals(action), socket != null, alertsSubscribed,
                TvAlertConnectionPolicy.setupTimedOut(connectionStartedMs,
                        SystemClock.elapsedRealtime(), alertsSubscribed))) {
            // A stale OkHttp WebSocket reference must never make the Resume
            // button a no-op. Invalid-token / actual permission errors only
            // retry after an explicit user resume or changed credentials.
            if (ACTION_RESUME.equals(action) || !terminalAlertError) {
                forceReconnect();
            }
        } else if (socket == null && !terminalAlertError) {
            TvEventsRecovery.note(TvEventsRecovery.RECONNECTING);
            connect();
        }
        return START_STICKY;
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "Kellie's Voice TV alerts", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Opt-in Home Assistant announcements and camera popups");
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String message) {
        Intent open = new Intent(this, VoiceActivity.class)
                .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS");
        PendingIntent openPending = PendingIntent.getActivity(this, 30, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, TvEventsService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 31, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Kellie's Voice TV notifications")
                .setContentText(message)
                .setContentIntent(openPending)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Disable alerts", stopPending).build())
                .setOngoing(true)
                .build();
    }

    private void connectionNotice(String message) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && !stopping) manager.notify(NOTICE_ID, buildNotification(message));
    }

    private void clearConnectionTracking() {
        alertsSubscribed = false;
        heartbeatId = -1;
        heartbeatSentMs = -1L;
        connectionStartedMs = -1L;
        layoutStatesId = -1;
        legacyMode = false;
        legacyPending = 0;
        legacyDenied = false;
    }

    /** Abort even a half-open transport; late callbacks are ignored by identity. */
    private void forceReconnect() {
        main.removeCallbacks(reconnect);
        main.removeCallbacks(connectionWatchdog);
        WebSocket old = socket;
        socket = null;
        nextReconnectAtMs = -1L;
        clearConnectionTracking();
        terminalAlertError = false;
        failedAttempts = 0;
        notReadyAttempts = 0;
        deniedAttempts = 0;
        if (old != null) old.cancel();
        if (stopping) return;
        TvEventsRecovery.note(TvEventsRecovery.RECONNECTING);
        connectionNotice("Reconnecting TV alert receiver");
        connect();
    }

    /** Retry on a new socket; late messages from the old one cannot alter state. */
    private void retryAlertSubscription(WebSocket ws, long delay, String message) {
        main.removeCallbacks(connectionWatchdog);
        socket = null;
        clearConnectionTracking();
        ws.cancel();
        TvEventsRecovery.note(TvEventsRecovery.RECONNECTING);
        connectionNotice(message);
        scheduleReconnect(delay);
    }

    /** All retry paths record a deadline, including when Android loses a callback. */
    private void scheduleReconnect(long delayMs) {
        main.removeCallbacks(reconnect);
        nextReconnectAtMs = SystemClock.elapsedRealtime() + delayMs;
        main.postDelayed(reconnect, delayMs);
    }

    private void checkReceiverRecovery() {
        if (stopping) return;
        long now = SystemClock.elapsedRealtime();
        if (TvAlertConnectionPolicy.shouldMonitorReconnect(
                new OverlayPreferences(this).tvEvents(), Settings.canDrawOverlays(this),
                credentials != null, terminalAlertError, socket != null,
                nextReconnectAtMs, now)) {
            TvEventsRecovery.diagnostic("Recovery watchdog repairing an overdue retry");
            connect();
        }
        // Keep watching even when the authenticated alert socket is absent.
        main.postDelayed(recoveryMonitor, 20_000L);
    }

    private void scheduleConnectionWatchdog() {
        main.removeCallbacks(connectionWatchdog);
        if (!stopping && socket != null && !terminalAlertError) {
            main.postDelayed(connectionWatchdog, TvAlertConnectionPolicy.WATCHDOG_INTERVAL_MS);
        }
    }

    /**
     * HA responds to authenticated {id,type:"ping"} with {id,type:"pong"}.
     * No audio, events, credentials or arbitrary URL are sent by this probe.
     * Android standby can pause the Handler, so elapsedRealtime measures
     * time across sleep; the next wake tick heals a half-open socket.
     */
    private void checkConnectionHealth() {
        if (stopping || socket == null || terminalAlertError) return;
        long now = SystemClock.elapsedRealtime();
        if (TvAlertConnectionPolicy.setupTimedOut(connectionStartedMs, now, alertsSubscribed)
                || (heartbeatId != -1 && TvAlertConnectionPolicy.heartbeatTimedOut(
                        heartbeatSentMs, now))) {
            forceReconnect();
            return;
        }
        if (alertsSubscribed && heartbeatId == -1) {
            try {
                int id = ++requestCounter;
                JSONObject ping = new JSONObject().put("id", id).put("type", "ping");
                if (!socket.send(ping.toString())) {
                    forceReconnect();
                    return;
                }
                heartbeatId = id;
                heartbeatSentMs = now;
            } catch (Exception ignored) {
                forceReconnect();
                return;
            }
        }
        scheduleConnectionWatchdog();
    }

    private void connect() {
        main.removeCallbacks(reconnect);
        if (stopping || socket != null || terminalAlertError
                || !new OverlayPreferences(this).tvEvents() || credentials == null) return;
        nextReconnectAtMs = -1L;
        connectionAttempts = Math.min(connectionAttempts + 1, 1_000_000);
        TvEventsRecovery.diagnostic("Opening alert socket, attempt " + connectionAttempts);
        HttpUrl base = HttpUrl.parse(credentials.url);
        if (base == null || !(base.scheme().equals("http") || base.scheme().equals("https"))
                || !base.username().isEmpty() || !base.password().isEmpty()) {
            terminalAlertError = true;
            TvEventsRecovery.note(TvEventsRecovery.CHECK);
            TvEventsRecovery.diagnostic("Saved Home Assistant address is invalid");
            connectionNotice("Check the Home Assistant connection in Settings");
            return;
        }
        // OkHttp upgrades an HTTP(S) URL to WebSocket internally. HttpUrl
        // itself only accepts the http/https schemes.
        HttpUrl address = base.newBuilder()
                .addPathSegments("api/websocket").build();
        clearConnectionTracking();
        connectionStartedMs = SystemClock.elapsedRealtime();
        TvEventsRecovery.note(TvEventsRecovery.RECONNECTING);
        socket = http.newWebSocket(new Request.Builder().url(address).build(),
                new WebSocketListener() {
                    @Override public void onMessage(WebSocket ws, String text) {
                        main.post(() -> handleMessage(ws, text));
                    }
                    @Override public void onFailure(WebSocket ws, Throwable problem,
                            Response response) {
                        main.post(() -> lostConnection(ws));
                    }
                    @Override public void onClosed(WebSocket ws, int code, String reason) {
                        main.post(() -> lostConnection(ws));
                    }
                });
        scheduleConnectionWatchdog();
    }

    private void lostConnection(WebSocket ws) {
        if (ws != socket) return;
        socket = null;
        main.removeCallbacks(connectionWatchdog);
        clearConnectionTracking();
        if (stopping || terminalAlertError) return;
        TvEventsRecovery.note(TvEventsRecovery.RECONNECTING);
        connectionNotice("Reconnecting to Home Assistant");
        long delay = TvAlertConnectionPolicy.retryDelayMs(failedAttempts++);
        TvEventsRecovery.diagnostic("Network reconnect scheduled in " + (delay / 1000L) + "s");
        scheduleReconnect(delay);
    }

    private void handleMessage(WebSocket ws, String json) {
        if (stopping || ws != socket) return;
        try {
            JSONObject message = new JSONObject(json);
            String type = message.optString("type");
            if ("auth_required".equals(type)) {
                ws.send(new JSONObject().put("type", "auth")
                        .put("access_token", credentials.token).toString());
                return;
            }
            if ("auth_invalid".equals(type)) {
                terminalAlertError = true;
                main.removeCallbacks(connectionWatchdog);
                TvEventsRecovery.note(TvEventsRecovery.REJECTED);
                TvEventsRecovery.diagnostic("Saved Home Assistant authentication rejected");
                connectionNotice("Home Assistant rejected the saved login");
                ws.close(1000, "Authentication rejected");
                return;
            }
            if ("auth_ok".equals(type)) {
                failedAttempts = 0;
                // No admin-level custom event subscriptions. The newer HACS
                // integration checks this user's read access to the paired TV.
                legacyMode = false;
                legacyPending = 0;
                legacyDenied = false;
                ws.send(new JSONObject().put("id", SCOPED_ALERTS_ID)
                        .put("type", "kellies_voice/subscribe_alerts")
                        .put("device_id", new OverlayPreferences(this).deviceId()).toString());
                if (new OverlayPreferences(this).haManage()) {
                    layoutStatesId = LAYOUT_STATES_ID;
                    ws.send(new JSONObject().put("id", LAYOUT_EVENT_ID)
                            .put("type", "subscribe_events")
                            .put("event_type", "state_changed").toString());
                    ws.send(new JSONObject().put("id", LAYOUT_STATES_ID)
                            .put("type", "get_states").toString());
                }
                connectionNotice("Authenticated, checking TV alert permissions");
                TvEventsRecovery.note(TvEventsRecovery.RECONNECTING);
                return;
            }
            if ("pong".equals(type)) {
                if (message.optInt("id", -1) == heartbeatId) {
                    heartbeatId = -1;
                    heartbeatSentMs = -1L;
                }
                return;
            }
            if ("result".equals(type)) {
                int resultId = message.optInt("id", -1);
                if (resultId == SCOPED_ALERTS_ID) {
                    if (message.optBoolean("success", false)) {
                        alertsSubscribed = true;
                        notReadyAttempts = 0;
                        deniedAttempts = 0;
                        TvEventsRecovery.note(TvEventsRecovery.CONNECTED);
                        TvEventsRecovery.diagnostic("Authorized alert subscription on attempt " + connectionAttempts);
                        connectionNotice("Connected, waiting for TV alerts");
                        TvEventsRestartReceiver.clearResume(this);
                    } else {
                        JSONObject error = message.optJSONObject("error");
                        String code = error == null ? "" : error.optString("code");
                        if ("unknown_command".equals(code)) {
                            // Compatibility with old HACS + an admin TV account.
                            // Non-admin users receive a clear denial instead.
                            subscribeLegacy(ws);
                        } else if (TvAlertConnectionPolicy.retryableSubscriptionError(code)) {
                            // The paired TV's HA controls might not exist yet.
                            retryAlertSubscription(ws,
                                    TvAlertConnectionPolicy.retryDelayMs(notReadyAttempts++),
                                    "Waiting for Home Assistant TV controls");
                        } else if (TvAlertConnectionPolicy.isScopedPermissionDenial(code)) {
                            int attempt = deniedAttempts;
                            deniedAttempts = Math.min(deniedAttempts + 1,
                                    TvAlertConnectionPolicy.MAX_TRANSIENT_DENIAL_RETRIES);
                            long delay = TvAlertConnectionPolicy.scopedDenialRetryDelayMs(attempt);
                            TvEventsRecovery.diagnostic("Scoped unauthorized, retry " +
                                    (attempt + 1) + " in " + (delay / 1000L) + "s");
                            retryAlertSubscription(ws, delay, "Rechecking TV alert connection");
                            if (attempt >= TvAlertConnectionPolicy.MAX_TRANSIENT_DENIAL_RETRIES) {
                                TvEventsRecovery.note(TvEventsRecovery.DENIED);
                            }
                        } else if ("not_allowed".equals(code)) {
                            terminalAlertError = true;
                            main.removeCallbacks(connectionWatchdog);
                            TvEventsRecovery.note(TvEventsRecovery.DENIED);
                            connectionNotice("TV alert permissions denied; check TV alert status");
                        } else {
                            terminalAlertError = true;
                            main.removeCallbacks(connectionWatchdog);
                            TvEventsRecovery.note(TvEventsRecovery.UPDATE_HACS);
                            connectionNotice("Update the Kellie's Voice HACS integration");
                        }
                    }
                    return;
                }
                if (legacyMode && resultId >= ANNOUNCE_ID && resultId <= TIMER_EVENT_ID
                        && resultId != LAYOUT_EVENT_ID && resultId != LAYOUT_STATES_ID) {
                    if (!message.optBoolean("success", false)) legacyDenied = true;
                    if (--legacyPending <= 0) {
                        if (legacyDenied) {
                            terminalAlertError = true;
                            main.removeCallbacks(connectionWatchdog);
                            TvEventsRecovery.note(TvEventsRecovery.DENIED);
                            connectionNotice("TV alert permissions denied; update HACS");
                        } else {
                            alertsSubscribed = true;
                            TvEventsRecovery.note(TvEventsRecovery.CONNECTED);
                            connectionNotice("Connected (legacy event subscriptions)");
                            TvEventsRestartReceiver.clearResume(this);
                        }
                    }
                    return;
                }
                if (resultId == layoutStatesId) {
                    layoutStatesId = -1;
                    if (message.optBoolean("success", false)) {
                        JSONArray states = message.optJSONArray("result");
                        if (states != null) {
                            for (int i = 0; i < states.length(); i++) {
                                applyAlertState(states.optJSONObject(i));
                            }
                        }
                    }
                    return;
                }
                if (resultId == liveRequestId && liveRequestGeneration == generation) {
                    liveRequestId = -1;
                    if (message.optBoolean("success", false)) {
                        JSONObject result = message.optJSONObject("result");
                        String path = result == null ? "" : result.optString("url");
                        playLiveStream(path);
                    } else if (liveStatus != null) {
                        JSONObject err = message.optJSONObject("error");
                        String code = err == null ? "" : err.optString("code");
                        // Error codes only; never expose free-form HA errors, credentials, or URLs.
                        if (!code.matches("[a-z_]{1,40}")) code = "unknown";
                        liveStatus.setText("HA stream request failed (" + code
                                + "); showing camera snapshot.");
                    }
                    return;
                }
                return;
            }
            if (!"event".equals(type)) return;
            int subscription = message.optInt("id", -1);
            JSONObject event = message.optJSONObject("event");
            if (event == null) return;
            String eventType = event.optString("event_type");
            if (subscription == LAYOUT_EVENT_ID && "state_changed".equals(eventType)) {
                JSONObject changed = event.optJSONObject("data");
                if (changed != null) applyAlertState(changed.optJSONObject("new_state"));
                return;
            }
            JSONObject data = event.optJSONObject("data");
            if (data == null) return;
            if (!TvEventPayload.addressedTo(data.optString("device_id", ""),
                    new OverlayPreferences(this).deviceId())) return;
            // A close command never starts a new overlay or microphone.
            // Ignore it when a text announcement (or nothing) is visible.
            if ((subscription == SCOPED_ALERTS_ID || subscription == HIDE_CAMERA_EVENT_ID)
                    && TvEventPayload.HIDE_CAMERA.equals(eventType)) {
                if (cameraVisible) hideCard();
                return;
            }
            if ((subscription == SCOPED_ALERTS_ID || subscription == TIMER_EVENT_ID)
                    && "kellies_voice_timer".equals(eventType)) {
                if (timerOverlay != null) timerOverlay.command(data);
                return;
            }
            int duration = TvEventPayload.duration(data.optInt("duration_seconds", 10));
            if ((subscription == SCOPED_ALERTS_ID || subscription == ANNOUNCE_ID)
                    && TvEventPayload.ANNOUNCEMENT.equals(eventType)) {
                String body = TvEventPayload.cleanText(data.optString("text"), 500);
                if (!body.isEmpty()) showCard(
                        TvEventPayload.cleanText(data.optString("title", "Kellie's Voice"), 65),
                        body, null, duration);
            } else if ((subscription == SCOPED_ALERTS_ID || subscription == CAMERA_ID)
                    && TvEventPayload.CAMERA_POPUP.equals(eventType)) {
                String entity = TvEventPayload.cameraEntity(data.optString("entity_id"));
                if (entity != null) {
                    boolean header = data.optBoolean("show_header", true);
                    String title = TvEventPayload.cleanText(
                            data.optString("title", "Camera alert"), 65);
                    boolean fit = TvEventPayload.fitWholeImage(data.optString("image_fit"));
                    int refresh = TvEventPayload.snapshotRefresh(
                            data.optInt("refresh_seconds", 0));
                    showSnapshotCard(title, entity, duration, header, fit, refresh);
                }
            } else if ((subscription == SCOPED_ALERTS_ID || subscription == LIVE_EVENT_ID)
                    && TvEventPayload.LIVE_CAMERA.equals(eventType)) {
                String entity = TvEventPayload.cameraEntity(data.optString("entity_id"));
                if (entity != null) {
                    boolean header = data.optBoolean("show_header", true);
                    boolean fit = TvEventPayload.fitWholeImage(data.optString("image_fit"));
                    int refresh = TvEventPayload.snapshotRefresh(data.optInt("refresh_seconds", 0));
                    showCard(TvEventPayload.cleanText(data.optString("title", "Live camera"), 65),
                            "LIVE · Starting muted video…", entity,
                            TvEventPayload.liveDuration(data.optInt("duration_seconds", 90)),
                            true, header, fit, refresh);
                }
            } else if ((subscription == SCOPED_ALERTS_ID || subscription == FORECAST_EVENT_ID)
                    && ForecastCard.EVENT.equals(eventType)) {
                if (!cameraVisible) showForecast(data);
            }
        } catch (Exception ignored) {
            // Never print auth tokens, URLs, or untrusted event payloads.
        }
    }

    /** Older HACS integration compatibility, only after scoped command is absent. */
    private void subscribeLegacy(WebSocket ws) {
        legacyMode = true;
        legacyDenied = false;
        legacyPending = 6;
        String[] types = {
                TvEventPayload.ANNOUNCEMENT, TvEventPayload.CAMERA_POPUP,
                TvEventPayload.LIVE_CAMERA, TvEventPayload.HIDE_CAMERA,
                ForecastCard.EVENT, "kellies_voice_timer"
        };
        int[] ids = {
                ANNOUNCE_ID, CAMERA_ID, LIVE_EVENT_ID, HIDE_CAMERA_EVENT_ID,
                FORECAST_EVENT_ID, TIMER_EVENT_ID
        };
        for (int i = 0; i < ids.length; i++) {
            try {
                if (!ws.send(new JSONObject().put("id", ids[i])
                        .put("type", "subscribe_events")
                        .put("event_type", types[i]).toString())) {
                    legacyDenied = true;
                    legacyPending--;
                }
            } catch (Exception ignored) {
                legacyDenied = true;
                legacyPending--;
            }
        }
        if (legacyPending <= 0) {
            TvEventsRecovery.note(TvEventsRecovery.UPDATE_HACS);
            connectionNotice("Update Kellie's Voice HACS for TV alerts");
        }
    }

    /** A forecast card never replaces a camera and never opens the microphone. */
    private void showForecast(JSONObject data) {
        if (stopping || windowManager == null || !Settings.canDrawOverlays(this)) return;
        if (!ForecastCard.fresh(data.optLong("issued", 0L), System.currentTimeMillis() / 1000L)) return;
        org.json.JSONArray rows = data.optJSONArray("days");
        java.util.ArrayList<ForecastCard.Day> parsed = new java.util.ArrayList<>();
        if (rows != null) {
            for (int i = 0; i < rows.length() && parsed.size() < 7; i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                ForecastCard.Day day = ForecastCard.day(
                        row.optString("label", ""),
                        row.optString("condition", ""),
                        row.isNull("high") ? null : String.valueOf(row.opt("high")),
                        row.isNull("low") ? null : String.valueOf(row.opt("low")),
                        row.isNull("precipitation") ? null : String.valueOf(row.opt("precipitation")));
                if (day != null) parsed.add(day);
            }
        }
        int requested = data.has("duration_seconds")
                ? data.optInt("duration_seconds", 20)
                : new OverlayPreferences(this).weatherSeconds();
        ForecastCard forecast = ForecastCard.card(
                data.optString("location", "Forecast"),
                data.optString("units", ""),
                requested,
                parsed);
        if (forecast == null) return;
        hideCard();
        int current = ++generation;
        OverlayPreferences placement = new OverlayPreferences(this);
        boolean aurora = OverlayPreferences.SKIN_AURORA.equals(placement.skin());
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xf514100E, 0xf5241A14});
        background.setCornerRadius(dp(14));
        background.setStroke(dp(2), 0xffC4884A);
        panel.setBackground(aurora ? new AuroraGlassDrawable(dp(14)) : background);
        panel.addView(label(forecast.location, 18, aurora ? 0xffEDF3FF : 0xffEAC296));
        panel.addView(label(forecast.heading(), 13, aurora ? 0xffA6BFEA : 0xffC4884A));
        for (ForecastCard.Day day : forecast.days) {
            TextView row = label(day.line(forecast.units), 15, aurora ? 0xffF2F5FF : 0xffFAE8D5);
            row.setMaxLines(1);
            panel.addView(row);
        }
        panel.addView(label("Back closes this forecast", 12, aurora ? 0xffA6BFEA : 0xffAD835D));
        panel.setFocusable(true);
        panel.setOnKeyListener((view, key, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            if (key == android.view.KeyEvent.KEYCODE_BACK
                    || key == android.view.KeyEvent.KEYCODE_ESCAPE) {
                hideCard();
                return true;
            }
            return false;
        });
        int screen = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(dp(WeatherLayout.widthDp(placement.weatherSize())), screen - dp(48));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        String position = placement.weatherPosition();
        params.gravity = weatherGravity(position);
        params.x = dp(position.equals(WeatherLayout.BOTTOM_CENTER)
                || position.equals(TvAlertLayout.CENTER) ? 0 : 28);
        params.y = dp(position.equals(TvAlertLayout.CENTER) ? 0 : 36);
        params.setTitle("Kellie's Voice forecast");
        try {
            windowManager.addView(panel, params);
            panel.requestFocus();
            card = panel;
            cameraVisible = false;
            main.postDelayed(dismiss, forecast.seconds * 1000L);
            if (generation != current) hideCard();
        } catch (RuntimeException ignored) {
            card = null;
        }
    }

    private static int weatherGravity(String position) {
        switch (WeatherLayout.position(position)) {
            case TvAlertLayout.TOP_LEFT:
                return Gravity.TOP | Gravity.START;
            case TvAlertLayout.BOTTOM_LEFT:
                return Gravity.BOTTOM | Gravity.START;
            case TvAlertLayout.BOTTOM_RIGHT:
                return Gravity.BOTTOM | Gravity.END;
            case WeatherLayout.BOTTOM_CENTER:
                return Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            case TvAlertLayout.CENTER:
                return Gravity.CENTER;
            default:
                return Gravity.TOP | Gravity.END;
        }
    }

    private int dp(int pixels) {
        return Math.round(pixels * getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int points, int color) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(points);
        v.setTextColor(color);
        v.setPadding(dp(3), dp(5), dp(3), dp(5));
        v.setMaxLines(8);
        return v;
    }

    private void showCard(String title, String text, String entity, int seconds) {
        showCard(title, text, entity, seconds, false);
    }

    /** Reuse the same snapshot ImageView and window for repeated calls. */
    private void showSnapshotCard(String title, String entity, int seconds,
                                  boolean header, boolean fit, int refreshSeconds) {
        if (stopping || windowManager == null || !Settings.canDrawOverlays(this)) return;
        if (cameraVisible && liveSurface == null && card != null && photo != null
                && entity.equals(snapshotEntity) && title.equals(snapshotTitle)
                && header == snapshotHeader && fit == snapshotFit) {
            int current = generation;
            main.removeCallbacks(dismiss);
            fetchSnapshot(entity, photo, current); // replace image in-place
            scheduleSnapshotRefresh(entity, current, refreshSeconds);
            main.postDelayed(dismiss, TvEventPayload.duration(seconds) * 1000L);
            return;
        }
        showCard(title, "Home Assistant camera snapshot", entity, seconds,
                false, header, fit, refreshSeconds);
    }

    private void showCard(String title, String text, String entity, int seconds, boolean live) {
        showCard(title, text, entity, seconds, live, true, false, 0);
    }

    private void showCard(String title, String text, String entity, int seconds,
                          boolean live, boolean showHeader, boolean fitWhole, int refreshSeconds) {
        if (stopping || windowManager == null || !Settings.canDrawOverlays(this)) return;
        hideCard();
        int current = ++generation;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(13), dp(18), dp(14));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[] {0xf51B1512, 0xf52B1D17});
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), 0xffAD835D);
        panel.setBackground(background);
        panel.setElevation(dp(12));

        TextView subtitle = null;
        if (entity == null || showHeader) {
            View goldRule = new View(this);
            goldRule.setBackgroundColor(0xffE1B27B);
            panel.addView(goldRule, new LinearLayout.LayoutParams(-1, dp(3)));
            panel.addView(label(title.isEmpty() ? "Kellie's Voice" : title, 19, 0xffEAC296));
            subtitle = label(text, 16, 0xffFAE8D5);
            panel.addView(subtitle);
        }
        photo = null;
        OverlayPreferences placement = new OverlayPreferences(this);
        String alertSize = placement.alertSize();
        String cameraMode = placement.cameraMode();
        if (entity != null && live) {
            liveStatus = subtitle;
            // SurfaceView can show black in TYPE_APPLICATION_OVERLAY on some TVs.
            // TextureView is composited with the rest of this popup window.
            // Keep a real camera still behind the player until the first video frame.
            FrameLayout preview = new FrameLayout(this);
            preview.setBackgroundColor(0xff2D211C); // letterbox behind live video
            ImageView still = new ImageView(this);
            still.setScaleType(fitWhole ? ImageView.ScaleType.FIT_CENTER
                    : ImageView.ScaleType.CENTER_CROP);
            still.setBackgroundColor(0xff2D211C);
            preview.addView(still, new FrameLayout.LayoutParams(-1, -1));
            photo = still;
            TextureView surface = new TextureView(this);
            surface.setOpaque(false);
            surface.setAlpha(0f);
            surface.setKeepScreenOn(true);
            preview.addView(surface, new FrameLayout.LayoutParams(-1, -1));
            liveSurface = surface;
            liveFirstFrame = false;
            liveVideoSize = null;
            panel.addView(preview, new LinearLayout.LayoutParams(-1,
                    dp(TvAlertLayout.mediaHeightDp(alertSize, true))));
        } else if (entity != null) {
            ImageView image = new ImageView(this);
            image.setScaleType(fitWhole ? ImageView.ScaleType.FIT_CENTER
                    : ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(0xff2D211C);
            panel.addView(image, new LinearLayout.LayoutParams(-1,
                    dp(TvAlertLayout.mediaHeightDp(alertSize, false))));
            photo = image;
        }

        int screen = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(dp(TvAlertLayout.widthDp(alertSize, entity != null, live)), screen - dp(48));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        String alertPosition = placement.alertPosition();
        params.gravity = alertGravity(alertPosition);
        params.x = dp(TvAlertLayout.marginXDp(alertPosition));
        params.y = dp(TvAlertLayout.marginYDp(alertPosition));
        params.setTitle("Kellie's Voice TV alert");
        try {
            windowManager.addView(panel, params);
            card = panel;
            cameraVisible = entity != null;
            if (entity != null) {
                snapshotEntity = entity;
                snapshotTitle = title;
                snapshotHeader = showHeader;
                snapshotFit = fitWhole;
            }
            if (entity != null) {
                if (live) {
                    // Keep stills behind the player until the first actual video frame.
                    fetchSnapshot(entity, photo, current);
                    scheduleSnapshotRefresh(entity, current, refreshSeconds);
                    if (LiveCameraMode.requestsStream(cameraMode)) {
                        requestLiveStream(entity, current);
                        main.postDelayed(() -> {
                            if (generation == current && !liveFirstFrame) {
                                // Abandon late HLS responses and release a stalled decoder.
                                liveRequestId = -1;
                                liveRequestGeneration = -1;
                                releaseLivePlayer("Live video timed out; showing camera snapshot.");
                            }
                        }, 18000L);
                    } else {
                        if (liveStatus != null) liveStatus.setText(
                                "Camera snapshot · live video disabled on this TV.");
                    }
                } else {
                    fetchSnapshot(entity, photo, current);
                    scheduleSnapshotRefresh(entity, current, refreshSeconds);
                }
            }
            main.postDelayed(dismiss,
                    (live ? TvEventPayload.liveDuration(seconds)
                            : TvEventPayload.duration(seconds)) * 1000L);
        } catch (RuntimeException ignored) {
            card = null;
            photo = null;
        }
    }

    private static int alertGravity(String position) {
        switch (TvAlertLayout.position(position)) {
            case TvAlertLayout.TOP_LEFT:
                return Gravity.TOP | Gravity.START;
            case TvAlertLayout.BOTTOM_LEFT:
                return Gravity.BOTTOM | Gravity.START;
            case TvAlertLayout.BOTTOM_RIGHT:
                return Gravity.BOTTOM | Gravity.END;
            case TvAlertLayout.CENTER:
                return Gravity.CENTER;
            default:
                return Gravity.TOP | Gravity.END;
        }
    }

    /** Saves a Home Assistant alert placement. Ignores voice-overlay settings. */
    private void applyAlertState(JSONObject state) {
        if (state == null || !new OverlayPreferences(this).haManage()) return;
        JSONObject attributes = state.optJSONObject("attributes");
        if (attributes == null) return;
        OverlayPreferences prefs = new OverlayPreferences(this);
        if (!prefs.deviceId().equals(attributes.optString("kellie_device_id"))) return;
        String setting = attributes.optString("kellie_setting");
        if (!HaLayout.ALERT_POSITION.equals(setting) && !HaLayout.ALERT_SIZE.equals(setting)
                && !HaLayout.WEATHER_POSITION.equals(setting)
                && !HaLayout.WEATHER_SIZE.equals(setting)
                && !HaLayout.WEATHER_DURATION.equals(setting)) return;
        if (attributes.optInt("kellie_generation", 0) <= 0) return;
        String raw = state.optString("state");
        if ("unavailable".equals(raw) || "unknown".equals(raw) || raw.isEmpty()) return;
        HaLayout.apply(prefs, setting, raw);
    }

    private void requestLiveStream(String entity, int current) {
        if (socket == null || liveSurface == null) {
            if (liveStatus != null) liveStatus.setText("Waiting for Home Assistant connection.");
            return;
        }
        int id = ++requestCounter;
        liveRequestId = id;
        liveRequestGeneration = current;
        try {
            boolean sent = socket.send(new JSONObject().put("id", id)
                    .put("type", "camera/stream").put("entity_id", entity)
                    .put("format", "hls").toString());
            if (!sent && liveStatus != null) liveStatus.setText("Could not request live camera.");
        } catch (Exception ignored) {
            if (liveStatus != null) liveStatus.setText("Could not request live camera.");
        }
    }

    /**
     * #67: PREFER_SOFTWARE alone is not safe: Media3 may still fall through
     * to a hardware decoder and blank the integrated TV's HDMI input.
     * Only include codecs explicitly identified as software-only.
     */
    private DefaultRenderersFactory cameraRenderers(String mode) {
        DefaultRenderersFactory factory = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true);
        if (LiveCameraMode.softwareOnly(mode)) {
            factory.setMediaCodecSelector((mime, secure, tunnel) -> {
                List<MediaCodecInfo> codecs = MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mime, secure, tunnel);
                if (!MimeTypes.isVideo(mime)) return codecs;
                List<MediaCodecInfo> software = new ArrayList<>();
                for (MediaCodecInfo codec : codecs) {
                    if (codec.softwareOnly) software.add(codec);
                }
                return software; // Never fall through to a hardware video decoder.
            });
        }
        return factory;
    }

    /** Correct raw TextureView aspect ratio when the HA action requests fit. */
    private void fitLiveTexture(VideoSize size, TextureView surface) {
        if (size == null || surface == null || !snapshotFit
                || size.width <= 0 || size.height <= 0) return;
        surface.post(() -> {
            if (liveSurface != surface || !snapshotFit
                    || surface.getWidth() <= 0 || surface.getHeight() <= 0) return;
            float videoAspect = size.width * size.pixelWidthHeightRatio / size.height;
            float windowAspect = (float) surface.getWidth() / surface.getHeight();
            if (!Float.isFinite(videoAspect) || videoAspect <= 0) return;
            float x = videoAspect > windowAspect ? 1f : videoAspect / windowAspect;
            float y = videoAspect > windowAspect ? windowAspect / videoAspect : 1f;
            Matrix transform = new Matrix();
            transform.setScale(x, y, surface.getWidth() / 2f, surface.getHeight() / 2f);
            surface.setTransform(transform);
        });
    }

    private void releaseLivePlayer(String status) {
        if (liveStatus != null && status != null) liveStatus.setText(status);
        if (liveSurface != null) liveSurface.setAlpha(0f);
        if (photo != null) photo.setVisibility(View.VISIBLE);
        if (livePlayer != null) {
            ExoPlayer old = livePlayer;
            livePlayer = null;
            if (liveSurface != null) old.clearVideoTextureView(liveSurface);
            old.release();
        }
    }

    private void playLiveStream(String path) {
        // The stream URL is generated by HA over our authenticated socket, never
        // supplied by a TV event. Reject other hosts or protocols completely.
        if (liveSurface == null || credentials == null || !TvEventPayload.hlsPathAllowed(path)) {
            if (liveStatus != null) liveStatus.setText(
                    "HA returned an unsupported HLS address; showing camera snapshot.");
            return;
        }
        HttpUrl base = HttpUrl.parse(credentials.url);
        HttpUrl url = base == null ? null : base.resolve(path);
        if (base == null || url == null || !base.scheme().equals(url.scheme())
                || !base.host().equals(url.host()) || base.port() != url.port()
                || !url.encodedPath().startsWith("/api/hls/")) {
            if (liveStatus != null) liveStatus.setText("Untrusted HLS stream address rejected.");
            return;
        }
        try {
            String mode = new OverlayPreferences(this).cameraMode();
            if (!LiveCameraMode.requestsStream(mode)) return;
            ExoPlayer player = new ExoPlayer.Builder(this, cameraRenderers(mode)).build();
            player.setVolume(0f);
            // Video-only selection avoids allocating audio output over existing TV playback.
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
            player.addListener(new Player.Listener() {
                @Override public void onVideoSizeChanged(VideoSize videoSize) {
                    if (player == livePlayer && liveSurface != null) {
                        liveVideoSize = videoSize;
                        fitLiveTexture(videoSize, liveSurface);
                    }
                }
                @Override public void onRenderedFirstFrame() {
                    // Headerless live popups intentionally have no subtitle.
                    // Lack of a label must never prevent playback from becoming visible.
                    if (player != livePlayer || liveSurface == null) return;
                    liveFirstFrame = true;
                    if (liveVideoSize != null) fitLiveTexture(liveVideoSize, liveSurface);
                    stopSnapshotRefresh();
                    if (photo != null) photo.setVisibility(View.INVISIBLE);
                    liveSurface.setAlpha(1f);
                    if (liveStatus != null) liveStatus.setText("LIVE · muted");
                }
                @Override public void onPlayerError(PlaybackException error) {
                    if (player != livePlayer) return;
                    // Keep the photo, never retry on the hardware codec in safe mode.
                    String cause = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                            ? "No available video decoder (4001)" : "Live error " + error.errorCode;
                    releaseLivePlayer(cause + "; showing camera snapshot.");
                }
            });
            livePlayer = player;
            player.setVideoTextureView(liveSurface);
            player.setMediaItem(new MediaItem.Builder().setUri(url.toString())
                    .setMimeType(MimeTypes.APPLICATION_M3U8).build());
            player.prepare();
            player.play();
        } catch (RuntimeException ignored) {
            if (liveStatus != null) liveStatus.setText(
                    "Cannot start video player; showing camera snapshot.");
            releaseLivePlayer(null);
        }
    }

    /** Stop periodic stills and cancel an in-flight HTTP fetch after live video begins. */
    private void stopSnapshotRefresh() {
        if (snapshotRefreshTask != null) main.removeCallbacks(snapshotRefreshTask);
        snapshotRefreshTask = null;
        if (snapshotCall != null) snapshotCall.cancel();
        snapshotCall = null;
        ++snapshotSerial; // Discard stale responses, including a queued UI callback.
    }

    /** Every timer is bounded by the card lifetime. Never request two periodic frames at once. */
    private void scheduleSnapshotRefresh(String entity, int current, int seconds) {
        if (snapshotRefreshTask != null) main.removeCallbacks(snapshotRefreshTask);
        snapshotRefreshTask = null;
        int refresh = TvEventPayload.snapshotRefresh(seconds);
        if (refresh == 0) return;
        snapshotRefreshTask = new Runnable() {
            @Override public void run() {
                if (stopping || generation != current || photo == null
                        || !entity.equals(snapshotEntity)
                        || (liveSurface != null && liveFirstFrame)) return;
                // While one image downloads, skip the period rather than building
                // an unbounded queue of Frigate/camera proxy requests.
                if (snapshotCall == null) fetchSnapshot(entity, photo, current);
                main.postDelayed(this, refresh * 1000L);
            }
        };
        main.postDelayed(snapshotRefreshTask, refresh * 1000L);
    }

    private void fetchSnapshot(String entity, ImageView target, int current) {
        if (credentials == null) return;
        HttpUrl base = HttpUrl.parse(credentials.url);
        if (base == null) return;
        // The event may only select a validated camera entity. It may NOT
        // supply a URL, hostname, protocol, file path, or bearer token.
        HttpUrl url = base.newBuilder().addPathSegment("api")
                .addPathSegment("camera_proxy").addPathSegment(entity).build();
        Request request = new Request.Builder().url(url)
                .header("Authorization", "Bearer " + credentials.token)
                .header("Cache-Control", "no-cache").build();
        if (snapshotCall != null) snapshotCall.cancel();
        final int serial = ++snapshotSerial;
        Call call = http.newCall(request);
        snapshotCall = call;
        call.enqueue(new Callback() {
            @Override public void onFailure(Call failed, java.io.IOException e) {
                main.post(() -> { if (snapshotCall == failed) snapshotCall = null; });
            }
            @Override public void onResponse(Call completed, Response response) {
                try (Response result = response) {
                    if (!result.isSuccessful() || result.body() == null) return;
                    long declared = result.body().contentLength();
                    if (declared > MAX_SNAPSHOT_BYTES) return;
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream input = result.body().byteStream()) {
                        byte[] buf = new byte[8192];
                        int read;
                        while ((read = input.read(buf)) >= 0) {
                            if (bytes.size() + read > MAX_SNAPSHOT_BYTES) return;
                            bytes.write(buf, 0, read);
                        }
                    }
                    byte[] raw = bytes.toByteArray();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
                    int largest = Math.max(options.outWidth, options.outHeight);
                    options.inSampleSize = largest > 1920 ? 4 : largest > 960 ? 2 : 1;
                    options.inJustDecodeBounds = false;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
                    if (bitmap != null) main.post(() -> {
                        if (!stopping && generation == current && photo == target
                                && serial == snapshotSerial) {
                            target.setImageBitmap(bitmap);
                        } else bitmap.recycle();
                    });
                } catch (Exception ignored) { }
                finally {
                    main.post(() -> {
                        if (snapshotCall == completed) snapshotCall = null;
                    });
                }
            }
        });
    }

    private void hideCard() {
        main.removeCallbacks(dismiss);
        if (snapshotRefreshTask != null) main.removeCallbacks(snapshotRefreshTask);
        snapshotRefreshTask = null;
        if (snapshotCall != null) snapshotCall.cancel();
        snapshotCall = null;
        ++snapshotSerial;
        snapshotEntity = null;
        snapshotTitle = null;
        ++generation;
        liveRequestId = -1;
        liveRequestGeneration = -1;
        liveFirstFrame = false;
        liveVideoSize = null;
        releaseLivePlayer(null);
        liveSurface = null;
        liveStatus = null;
        cameraVisible = false;
        if (windowManager != null && card != null) {
            try { windowManager.removeView(card); } catch (RuntimeException ignored) { }
        }
        card = null;
        photo = null;
    }

    @Override public void onDestroy() {
        stopping = true;
        if (timerOverlay != null) { timerOverlay.close(!new OverlayPreferences(this).tvEvents()); timerOverlay = null; }
        main.removeCallbacksAndMessages(null);
        clearConnectionTracking();
        hideCard();
        if (socket != null) {
            socket.close(1000, "Receiver stopped");
            socket = null;
        }
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
        credentials = null;
        if (new OverlayPreferences(this).tvEvents()) {
            TvEventsRecovery.note(TvEventsRecovery.STOPPED);
        } else {
            TvEventsRecovery.note(TvEventsRecovery.OFF);
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
