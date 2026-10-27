package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.HttpUrl;

/**
 * v0.5 opt-in Home Assistant voice overlay. Starts from a visible activity;
 * no always-on microphone or wake word. Recording is explicitly tied to a
 * user-initiated overlay launch / TALK, with STOP and watchdog escape hatches.
 * An optional Okay Nabu listener lives in WakeWordService and is off by default.
 */
public final class VoiceOverlayService extends Service implements HaAssistClient.Events {
    public static final String ACTION_CLOSE = "com.example.tvvoiceprobe.CLOSE_OVERLAY";
    public static final String ACTION_OVERLAY_READY = "com.example.tvvoiceprobe.OVERLAY_READY";
    public static final String ACTION_OVERLAY_FAILED = "com.example.tvvoiceprobe.OVERLAY_FAILED";
    private static final String CHANNEL_ID = "ha_tv_voice_overlay";
    private static final int NOTIFICATION_ID = 2217;
    private static final AtomicInteger OPEN = new AtomicInteger();

    public static boolean isOpen() { return OPEN.get() > 0; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Button> actions = new ArrayList<>();
    private WindowManager windowManager;
    private VoicePanel panel;
    private TextView statusView;
    private TextView answerView;
    private TextView transcriptView;
    private ScrollView answerScroll;
    private VoiceWaveView wave;
    private HaAssistClient assist;
    private CredentialStore.Connection connection;
    private OverlayPreferences preferences;
    private MediaPlayer player;
    private SpeechFocus speechFocus;
    private int selected = 0;
    private boolean listening = false;
    private boolean speaking = false;
    private boolean firstAutoListenPending = false;
    private boolean firstAutoListenStarted = false;
    /** True only for a just-detected, opted-in wake phrase. Never persisted. */
    private boolean pendingWakeCapture = false;
    private boolean readyChime;
    private WakeWordService.Cancellation micHandoff;
    private long autoListenDeadlineMs;
    private boolean closing = false;
    private boolean countedOpen = false;
    private final FollowUpSession followUps = new FollowUpSession();
    private FollowUpEar followEar;
    private int followToken;
    private boolean followWindow;
    private final MicOwnership mics = new MicOwnership();

    private final Runnable endListeningTimeout = () -> {
        if (listening && assist != null) {
            listening = false;
            assist.finishTalking();
            mics.stopRequested();
            if (wave != null) wave.setMode(VoiceWaveView.THINKING);
            status("Listening time limit reached. Processing...");
        }
    };
    private final Runnable dismissAfterReply = this::stopSelf;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CLOSE.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        OverlayPreferences incoming = new OverlayPreferences(this);
        final boolean wakeTriggered = WakeWordSession.isDetectionRequest(
                incoming.wakeWord(), intent == null ? null : intent.getAction())
                && !incoming.microphoneDisabled();
        if (panel != null) {
            main.removeCallbacks(dismissAfterReply);
            panel.post(() -> {
                panel.requestFocus();
                focus(0);
                if (wakeTriggered && !listening) {
                    pendingWakeCapture = true;
                    firstAutoListenPending = true;
                    firstAutoListenStarted = false;
                    autoListenDeadlineMs = SystemClock.elapsedRealtime() + 10_000L;
                }
                if (assist != null && !assist.isConnected()) {
                    if (preferences != null && (preferences.autoListen() || wakeTriggered)) {
                        firstAutoListenPending = true;
                        firstAutoListenStarted = false;
                        pendingWakeCapture = wakeTriggered || pendingWakeCapture;
                        autoListenDeadlineMs = SystemClock.elapsedRealtime()
                                + (wakeTriggered ? 10_000L
                                : AssistReadinessPolicy.PENDING_USER_REQUEST_MS);
                    }
                    if (assist.canReconnect()) {
                        assist.ensureConnected();
                        status(wakeTriggered || (preferences != null && preferences.autoListen())
                                ? "Connecting to Home Assistant... listening if ready soon."
                                : AssistReadinessPolicy.waitingForConnection());
                    } else {
                        firstAutoListenPending = false;
                        pendingWakeCapture = false;
                        status("Home Assistant login is unavailable. Open SETTINGS to reconnect.");
                    }
                } else if (!listening && assist != null && assist.isConnected()
                        && preferences != null
                        && (preferences.autoListen() || wakeTriggered)) {
                    if (wakeTriggered) pendingWakeCapture = true;
                    startListening();
                }
            });
            notifyLauncher(ACTION_OVERLAY_READY);
            return START_NOT_STICKY;
        }
        if (!Settings.canDrawOverlays(this) ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            notifyLauncher(ACTION_OVERLAY_FAILED);
            stopSelf();
            return START_NOT_STICKY;
        }
        closing = false;
        preferences = new OverlayPreferences(this);
        followToken = followUps.open();
        followWindow = false;
        followEar = new FollowUpEar(this);
        pendingWakeCapture = wakeTriggered;
        firstAutoListenPending = preferences.autoListen() || wakeTriggered;
        firstAutoListenStarted = false;
        autoListenDeadlineMs = SystemClock.elapsedRealtime()
                + (wakeTriggered ? 10_000L : AssistReadinessPolicy.PENDING_USER_REQUEST_MS);
        try {
            createNotificationChannel();
            if (!publishMicState()) {
                notifyLauncher(ACTION_OVERLAY_FAILED);
                stopSelf();
                return START_NOT_STICKY;
            }

            connection = new CredentialStore(this).load();
            if (connection == null) {
                notifyLauncher(ACTION_OVERLAY_FAILED);
                stopSelf();
                return START_NOT_STICKY;
            }
            showPanel();
            markOpen();
            if (wakeTriggered) {
                // The wake listener is still recording the start of the question.
                // Stopping it here would drop those words. Capture claims them later.
                attachAssist(true);
            } else {
                status("Waiting for the microphone...");
                micHandoff = WakeWordService.requestHandoff(MicLease.OVERLAY, new WakeWordService.Handoff() {
                    @Override public void accepted(WakeWordService.Cancellation cancellation) {
                        main.post(() -> {
                            if (closing || cancellation.isCancelled()) {
                                MicLease.release(MicLease.OVERLAY);
                                if (!closing) stopSelf();
                                return;
                            }
                            attachAssist(false);
                        });
                    }

                    @Override public void denied() {
                        main.post(() -> {
                            status("The microphone is in use. Leave diagnostics or stop TALK first.");
                            notifyLauncher(ACTION_OVERLAY_FAILED);
                            stopSelf();
                        });
                    }
                });
            }
        } catch (Exception ignored) {
            // Do not print private tokens or HA URL into logs.
            notifyLauncher(ACTION_OVERLAY_FAILED);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void attachAssist(boolean fromWake) {
        try {
            // Only an explicit wake detection may claim this pre-authenticated
            // socket. Ordinary TALK keeps its existing connection behavior.
            HaAssistClient warmed = fromWake ? WakeAssistConnection.take(this) : null;
            assist = warmed == null ? new HaAssistClient(this, this) : warmed;
            assist.setLayoutSync(preferences.haManage());
            HaDiscovery.setWanted(this, preferences.haManage());
            HaDiscovery.acquire(this);
            if (warmed == null) {
                assist.connect(connection.url, connection.token);
            } else if (assist.isConnected()) {
                // Authentication may have finished before callbacks were
                // transferred from the wake service: explicitly consume it.
                ready();
            } else if (assist.canReconnect()) {
                assist.ensureConnected();
            } else {
                // A stale/failed warm session must never block a fresh login.
                assist.connect(connection.url, connection.token);
            }
            notifyLauncher(ACTION_OVERLAY_READY);
        } catch (Exception ignored) {
            notifyLauncher(ACTION_OVERLAY_FAILED);
            stopSelf();
        }
    }

    private void notifyLauncher(String action) {
        sendBroadcast(new Intent(action).setPackage(getPackageName()));
    }

    private Notification buildNotification(String message) {
        Intent openApp = new Intent(this, VoiceActivity.class);
        openApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openApp,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent close = new Intent(this, VoiceOverlayService.class);
        close.setAction(ACTION_CLOSE);
        PendingIntent closePending = PendingIntent.getService(this, 1, close,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Kellie's Voice")
                .setContentText(message)
                .setContentIntent(openPending)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel, "Close", closePending)
                        .build())
                .setOngoing(true)
                .build();
    }

    /**
     * Publishes the foreground type for the current recorder owner.
     * A follow-up preview is microphone-on even though {@code listening} is still false.
     * Returns false when a capture was required and Android rejected the microphone type.
     */
    private boolean publishMicState() {
        if (closing) return false;
        boolean capturing = mics.microphoneActive();
        int sdk = Build.VERSION.SDK_INT;
        int attempted = MicrophonePrivacy.serviceType(sdk, capturing);
        boolean rejected = false;
        if (attempted == MicrophonePrivacy.TYPE_SPECIAL_USE) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification(
                        MicrophonePrivacy.notificationText(false, MicrophonePrivacy.TYPE_SPECIAL_USE)),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                return true;
            } catch (RuntimeException ignored) {
                rejected = true;
            }
        }
        int actual = MicrophonePrivacy.afterRejection(attempted, rejected);
        Notification notification = buildNotification(MicrophonePrivacy.notificationText(
                capturing, actual, mics.followUpCapture()));
        try {
            if (sdk >= 29 && actual == MicrophonePrivacy.TYPE_MICROPHONE) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return !capturing || sdk < 29 || actual == MicrophonePrivacy.TYPE_MICROPHONE;
    }

    private void createNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Kellie's Voice", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible only while the voice overlay is open");
            manager.createNotificationChannel(channel);
        }
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setPadding(dp(2), dp(3), dp(2), dp(3));
        t.setFocusable(false);
        return t;
    }

    private void showPanel() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) throw new IllegalStateException("No window manager");
        actions.clear();
        selected = 0;
        boolean aurora = OverlayPreferences.SKIN_AURORA.equals(preferences.skin());
        boolean glass = OverlayPreferences.SKIN_GLASS.equals(preferences.skin());
        // Night Signal keeps its compact geometry, but uses warm brass accents
        // to avoid the generic cyan/violet AI appearance.
        boolean signal = OverlayPreferences.SKIN_SIGNAL.equals(preferences.skin());

        panel = new VoicePanel();
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(signal ? 20 : 22), dp(signal ? 12 : 14), dp(22), dp(14));
        panel.setFocusable(true);
        panel.setFocusableInTouchMode(true);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                signal ? new int[] {0xf21B1512, 0xf2261B15}
                        : glass ? new int[] {0xe8201513, 0xf0291a17, 0xed150f0d}
                        : new int[] {0xf02A1A16, 0xf017100e});
        background.setCornerRadius(dp(signal ? 8 : glass ? 22 : 14));
        background.setStroke(dp(1), signal ? 0xff805F48 : glass ? 0x88E7A981 : 0xffD9946F);
        panel.setBackground(aurora ? new AuroraGlassDrawable(dp(18)) : background);
        panel.setElevation(dp(signal ? 10 : 14));

        if (signal) {
            View rule = new View(this);
            rule.setBackgroundColor(0xffE1B27B);
            panel.addView(rule, new LinearLayout.LayoutParams(-1, dp(3)));
        }

        TextView top = label(aurora ? "KELLIE’S VOICE" : signal ? "KELLIE'S VOICE" : glass ? "✦  KELLIE'S VOICE" : "🎙  KELLIE'S VOICE",
                signal ? 13 : glass ? 15 : 19,
                aurora ? 0xffBED6FF : signal ? 0xffE7C39B : glass ? 0xffF5D6BD : 0xffE6AB82);
        top.setLetterSpacing(signal ? 0.18f : 0.12f);
        panel.addView(top);

        statusView = label("CONNECTING...", signal ? 13 : glass ? 14 : 15,
                aurora ? 0xffCFDAF4 : signal ? 0xffF0DDC7 : 0xffF7E8DA);
        panel.addView(statusView);

        wave = new VoiceWaveView(this);
        wave.setLook(aurora ? VoiceWaveView.LOOK_AURORA : signal ? VoiceWaveView.LOOK_SIGNAL : VoiceWaveView.LOOK_COPPER);
        wave.setMode(VoiceWaveView.CONNECTING);
        panel.addView(wave, new LinearLayout.LayoutParams(-1, dp(glass ? 88 : signal ? 64 : 68)));

        // One scrolling region keeps long Assist answers readable while the
        // TALK/STOP/CLOSE/SETTINGS buttons remain visible and remote-friendly.
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        if (glass || signal) {
            words.addView(label(signal ? "You said" : "YOU SAID", 11,
                    aurora ? 0xff9CB9E9 : signal ? 0xffAF9881 : 0xffD1AC92));
        }
        transcriptView = label("Say something when listening starts...",
                signal ? 16 : glass ? 17 : 16,
                aurora ? 0xffE4ECFF : signal ? 0xffF0E2D0 : glass ? 0xffF7E9E0 : 0xffEBCDB9);
        words.addView(transcriptView);

        words.addView(label(signal ? "Home Assistant" : "HOME ASSISTANT SAYS", 11,
                aurora ? 0xff9CB9E9 : signal ? 0xffAF9881 : glass ? 0xffD1AC92 : 0xffC89A7B));
        answerView = label("Ready for your question",
                aurora ? 27 : signal ? 26 : glass ? 23 : 19, 0xffffffff);
        words.addView(answerView);

        answerScroll = new ScrollView(this);
        answerScroll.setFillViewport(true);
        answerScroll.setFocusable(true);
        answerScroll.setFocusableInTouchMode(true);
        answerScroll.setVerticalScrollBarEnabled(true);
        answerScroll.addView(words);
        float textFraction = OverlayPreferences.SIZE_SMALL.equals(preferences.size()) ? 0.28f
                : OverlayPreferences.SIZE_LARGE.equals(preferences.size()) ? 0.44f : 0.34f;
        int textHeight = Math.max(dp(110),
                (int) (getResources().getDisplayMetrics().heightPixels * textFraction));
        panel.addView(answerScroll, new LinearLayout.LayoutParams(-1, textHeight));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        addAction(toolbar, "TALK", this::startListening, signal);
        addAction(toolbar, "STOP", this::stopListening, signal);
        addAction(toolbar, "CLOSE", this::stopSelf, signal);
        addAction(toolbar, "SETTINGS", this::openMainSettings, signal);
        panel.addView(toolbar);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int baseWidthDp;
        if (signal) {
            baseWidthDp = OverlayPreferences.SIZE_SMALL.equals(preferences.size()) ? 440
                    : OverlayPreferences.SIZE_LARGE.equals(preferences.size()) ? 880 : 640;
        } else if (OverlayPreferences.SIZE_SMALL.equals(preferences.size())) {
            baseWidthDp = glass ? 470 : 385;
        } else if (OverlayPreferences.SIZE_LARGE.equals(preferences.size())) {
            baseWidthDp = glass ? 940 : 760;
        } else {
            // Preserve existing TV dimensions for current installs.
            baseWidthDp = glass ? 720 : 550;
        }
        int width = Math.max(dp(220), Math.min(dp(baseWidthDp), screenWidth - dp(40)));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);

        // Window gravity is explicit, not inferred from skin, when selected.
        // Keep the original placement until a user deliberately changes it.
        String position = preferences.position();
        params.x = 0;
        params.y = 0;
        switch (position) {
            case OverlayPreferences.POSITION_TOP_LEFT:
                params.gravity = Gravity.TOP | Gravity.START;
                params.x = dp(24);
                params.y = dp(24);
                break;
            case OverlayPreferences.POSITION_TOP_RIGHT:
                params.gravity = Gravity.TOP | Gravity.END;
                params.x = dp(24);
                params.y = dp(24);
                break;
            case OverlayPreferences.POSITION_BOTTOM_LEFT:
                params.gravity = Gravity.BOTTOM | Gravity.START;
                params.x = dp(24);
                params.y = dp(24);
                break;
            case OverlayPreferences.POSITION_BOTTOM_RIGHT:
                params.gravity = Gravity.BOTTOM | Gravity.END;
                params.x = dp(24);
                params.y = dp(24);
                break;
            case OverlayPreferences.POSITION_CENTER:
                params.gravity = Gravity.CENTER;
                break;
            case OverlayPreferences.POSITION_BOTTOM_CENTER:
                params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                params.y = dp(42);
                break;
            default:
                if (aurora) {
                    params.gravity = Gravity.BOTTOM | Gravity.START;
                    params.x = dp(36);
                    params.y = dp(36);
                } else if (signal) {
                    params.gravity = Gravity.BOTTOM | Gravity.START;
                    params.x = dp(28);
                    params.y = dp(36);
                } else {
                    params.gravity = glass ? (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL)
                            : (Gravity.TOP | Gravity.END);
                    params.y = dp(glass ? 42 : 45);
                    if (!glass) params.x = dp(18);
                }
                break;
        }
        params.setTitle("Kellie's Voice");
        windowManager.addView(panel, params);
        panel.post(() -> focus(0));
    }

    private void addAction(LinearLayout parent, String name, Runnable runnable, boolean signal) {
        final int index = actions.size();
        Button button = new Button(this);
        button.setText(name);
        button.setTextSize(signal ? 14 : 13);
        button.setAllCaps(false);
        button.setTextColor(signal ? 0xffF8E8D6 : 0xffffffff);
        button.setFocusable(true);
        button.setFocusableInTouchMode(true);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        if (signal) {
            button.setBackgroundTintList(null);
            button.setPadding(dp(6), 0, dp(6), 0);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    OverlayPreferences.SKIN_AURORA.equals(preferences.skin()) ? 0xff172844 : 0xff574039));
        }
        button.setOnClickListener(v -> runnable.run());
        button.setOnFocusChangeListener((v, focused) -> {
            if (focused) selected = index;
            if (signal) {
                GradientDrawable chip = new GradientDrawable();
                chip.setColor(focused ? 0xff433027 : 0x00000000);
                chip.setCornerRadius(dp(8));
                chip.setStroke(Math.max(1, dp(1)), focused ? 0xffE7C39B : 0xff7B5A45);
                button.setBackground(chip);
                button.setBackgroundTintList(null);
            } else {
                button.setBackgroundTintList(ColorStateList.valueOf(
                        OverlayPreferences.SKIN_AURORA.equals(preferences.skin())
                                ? (focused ? 0xff3D65B2 : 0xff172844)
                                : (focused ? 0xffBB7958 : 0xff574039)));
            }
        });
        if (signal) button.getOnFocusChangeListener().onFocusChange(button, false);
        actions.add(button);
        parent.addView(button, new LinearLayout.LayoutParams(0, dp(signal ? 40 : 42), 1f));
    }

    private void focus(int index) {
        if (actions.isEmpty()) return;
        selected = Math.max(0, Math.min(index, actions.size() - 1));
        actions.get(selected).requestFocus();
    }

    private void startListening() {
        startListening(false);
    }

    private void startListening(boolean followUpTurn) {
        boolean fromWake = !followUpTurn && pendingWakeCapture;
        if (closing || assist == null) return;
        if (!followUpTurn && followEar != null) followEar.stop();
        if (listening) {
            if (followUpTurn && followEar != null) followEar.stop();
            return;
        }
        if (!assist.isConnected()) {
            if (followUpTurn) {
                if (followEar != null) followEar.stop();
                followWindow = false;
                mics.previewReleased();
                publishMicState();
                scheduleIdleDismiss();
                return; // Never resume a follow-up mic across HA disconnect.
            }
            if (!assist.canReconnect()) {
                firstAutoListenPending = false;
                pendingWakeCapture = false;
                status("Home Assistant login is unavailable. Open SETTINGS to reconnect.");
                return;
            }
            firstAutoListenPending = true;
            firstAutoListenStarted = false;
            pendingWakeCapture = fromWake;
            autoListenDeadlineMs = SystemClock.elapsedRealtime()
                    + (fromWake ? 10_000L : AssistReadinessPolicy.PENDING_USER_REQUEST_MS);
            assist.ensureConnected();
            status("Connecting to Home Assistant... I'll listen if ready within 30 seconds.");
            return;
        }
        followWindow = false;
        if (fromWake && !MicLease.OVERLAY.equals(MicLease.holder())) {
            boolean queued = MicLease.acquire(MicLease.OVERLAY)
                    || MicLease.isWaiting(MicLease.OVERLAY);
            if (!queued) {
                pendingWakeCapture = false;
                status("The microphone is in use. Leave diagnostics or stop TALK first.");
                return;
            }
        }
        readyChime = fromWake;
        if (followUpTurn) mics.beginHandoff();
        else mics.armAssist();
        if (!publishMicState()) {
            if (followEar != null) followEar.stop();
            if (fromWake) MicLease.release(MicLease.OVERLAY);
            readyChime = false;
            mics.captureNeverStarted();
            publishMicState();
            status("Android wouldn't allow the microphone. Capture stayed off.");
            scheduleIdleDismiss();
            return;
        }
        FollowUpEar handoff = followUpTurn ? followEar : null;
        firstAutoListenPending = false;
        pendingWakeCapture = false;
        main.removeCallbacks(dismissAfterReply);
        main.removeCallbacks(endListeningTimeout);
        if (player != null) {
            player.release();
            player = null;
        }
        if (speechFocus != null) speechFocus.release();
        listening = true;
        speaking = false;
        if (transcriptView != null) transcriptView.setText("Checking microphone...");
        if (answerView != null) answerView.setText("...");
        if (wave != null) wave.setMode(VoiceWaveView.CONNECTING);
        status(followUpTurn ? "Listening for a follow-up" : "Checking microphone route...");
        assist.setRoomNoise(preferences.noise());
        assist.setWakeLeadForNextRun(fromWake);
        if (!assist.pipelineIdle() ||
                !assist.talk(WakeWordSession.stopOnSilence(
                        followUpTurn || preferences.autoListen(), fromWake), handoff)) {
            assist.setWakeLeadForNextRun(false);
            if (handoff != null) handoff.stop();
            if (fromWake) MicLease.release(MicLease.OVERLAY);
            listening = false;
            mics.captureNeverStarted();
            publishMicState();
            main.removeCallbacks(endListeningTimeout);
            if (wave != null) wave.setMode(
                    assist.isConnected() ? VoiceWaveView.IDLE : VoiceWaveView.CONNECTING);
            if (followUpTurn) {
                status("Couldn't open a follow-up. Microphone capture stopped.");
                scheduleIdleDismiss();
            }
        } else {
            mics.bindRun(assist.activeRunId());
            mics.assistListening();
            if (followUpTurn) followUps.markQuestion(followToken);
        }
    }

    private void openMainSettings() {
        // Explicit Activity intent has no MAIN/launcher action, so the optional
        // ⭐ fallback cannot trap the user in a launch-overlay loop.
        try {
            Intent open = new Intent(this, VoiceActivity.class);
            open.setAction("com.example.tvvoiceprobe.OPEN_SETTINGS");
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(open);
        } catch (Exception ignored) { }
        stopSelf();
    }

    private void stopListening() {
        pendingWakeCapture = false;
        firstAutoListenPending = false;
        boolean pipeline = listening;
        endFollowUps();
        if (!pipeline || assist == null) {
            listening = false;
            mics.releaseAll();
            publishMicState();
            if (wave != null) wave.setMode(VoiceWaveView.IDLE);
            status("Stopped. Microphone capture stopped.");
            scheduleIdleDismiss();
            return;
        }
        listening = false;
        main.removeCallbacks(endListeningTimeout);
        if (wave != null) wave.setMode(VoiceWaveView.THINKING);
        assist.finishTalking();
        mics.stopRequested();
        mics.releaseAll();
        publishMicState();
    }

    private final class VoicePanel extends LinearLayout {
        VoicePanel() { super(VoiceOverlayService.this); }
        @Override public boolean dispatchKeyEvent(KeyEvent event) {
            int code = event.getKeyCode();
            boolean down = event.getAction() == KeyEvent.ACTION_DOWN &&
                    event.getRepeatCount() == 0;
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) {
                        if (answerScroll != null && answerScroll.hasFocus()) focus(0);
                        else focus(selected - 1);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down) {
                        if (answerScroll != null && answerScroll.hasFocus()) {
                            answerScroll.smoothScrollBy(0, -dp(60));
                        } else if (selected == 0 && answerScroll != null) {
                            answerScroll.requestFocus();
                        } else {
                            focus(selected - 1);
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) {
                        if (answerScroll != null && answerScroll.hasFocus()) focus(0);
                        else focus(selected + 1);
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) {
                        if (answerScroll != null && answerScroll.hasFocus()) {
                            View child = answerScroll.getChildAt(0);
                            int height = child == null ? 0 : child.getHeight();
                            boolean more = answerScroll.getScrollY()
                                    + answerScroll.getHeight() < height - dp(8);
                            if (more) answerScroll.smoothScrollBy(0, dp(60));
                            else focus(0);
                        } else {
                            focus(selected + 1);
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down && !actions.isEmpty()) actions.get(selected).performClick();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (down) stopSelf();
                    return true;
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
    }

    @Override public void haLayout(String setting) {
        if (!HaLayout.changesPanel(setting)) return;
        main.post(() -> {
            if (!closing) refreshPanel();
        });
    }

    private void refreshPanel() {
        if (windowManager == null || panel == null) return;
        CharSequence said = transcriptView == null ? "" : transcriptView.getText();
        CharSequence answer = answerView == null ? "" : answerView.getText();
        CharSequence line = statusView == null ? "" : statusView.getText();
        boolean wasListening = listening;
        try {
            windowManager.removeView(panel);
        } catch (Exception ignored) { }
        panel = null;
        showPanel();
        if (transcriptView != null) transcriptView.setText(said);
        if (answerView != null) answerView.setText(answer);
        if (statusView != null) statusView.setText(line);
        if (wave != null) {
            wave.setMode(wasListening ? VoiceWaveView.LISTENING : (speaking ? VoiceWaveView.SPEAKING : VoiceWaveView.IDLE));
        }
    }

    @Override public void connectionLost() {
        main.post(() -> {
            if (closing) return;
            endFollowUps();
            listening = false;
            mics.stopRequested();
            mics.releaseAll();
            publishMicState();
            main.removeCallbacks(endListeningTimeout);
            main.removeCallbacks(dismissAfterReply);
            // Preserve only a recent, explicitly requested auto-listen.
            if (SystemClock.elapsedRealtime() > autoListenDeadlineMs) {
                firstAutoListenPending = false;
                pendingWakeCapture = false;
            }
            if (!speaking && wave != null) wave.setMode(VoiceWaveView.CONNECTING);
        });
    }

    @Override public void ready() {
        main.post(() -> {
            if (closing) return;
            if (wave != null) wave.setMode(VoiceWaveView.IDLE);
            boolean expiredRequest = firstAutoListenPending
                    && SystemClock.elapsedRealtime() > autoListenDeadlineMs;
            if (AssistReadinessPolicy.mayStartPendingRequest(
                    firstAutoListenPending, firstAutoListenStarted,
                    SystemClock.elapsedRealtime(), autoListenDeadlineMs)) {
                firstAutoListenStarted = true;
                startListening();
            } else {
                firstAutoListenPending = false;
                pendingWakeCapture = false;
                status(expiredRequest ? AssistReadinessPolicy.expiredRequest()
                        : "Connected. Ready. Select TALK.");
            }
        });
    }

    @Override public void listening() {
        main.post(() -> {
            if (closing || mics.phase() == MicOwnership.PREVIEW) return;
            listening = true;
            mics.assistListening();
            publishMicState();
            if (readyChime) {
                readyChime = false;
                playReadyChime();
            }
            if (transcriptView != null) transcriptView.setText("Listening to your voice...");
            if (wave != null) wave.setMode(VoiceWaveView.LISTENING);
            main.removeCallbacks(endListeningTimeout);
            main.postDelayed(endListeningTimeout, 23000);
        });
    }

    private void playReadyChime() {
        try {
            android.media.ToneGenerator tone = new android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_MUSIC, 70);
            tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 140);
            main.postDelayed(tone::release, 400);
        } catch (RuntimeException ignored) {
            // A missing tone stream must not stop the question.
        }
    }

    @Override public void processing() {
        main.post(() -> {
            listening = false;
            mics.stopRequested();
            main.removeCallbacks(endListeningTimeout);
            if (wave != null) wave.setMode(VoiceWaveView.THINKING);
        });
    }

    @Override public void localSilence() {
        main.post(() -> {
            listening = false;
            mics.stopRequested();
            main.removeCallbacks(endListeningTimeout);
            if (!closing && wave != null) wave.setMode(VoiceWaveView.THINKING);
        });
    }

    @Override public void runEnded() {
        main.post(() -> {
            if (closing) return;
            if (!speaking) {
                int token = followUps.takeDeferred();
                if (token != 0) {
                    maybeFollowUp(token);
                    return;
                }
            }
            if (!speaking && !followWindow) {
                if (wave != null) wave.setMode(VoiceWaveView.IDLE);
                scheduleIdleDismiss();
            }
        });
    }

    @Override public void speechMissing() {
        final int token = followToken;
        main.post(() -> {
            if (closing || speaking || token != followToken) return;
            maybeFollowUp(token);
        });
    }

    @Override public void status(String message) {
        main.post(() -> {
            if (!closing && statusView != null) statusView.setText(message);
        });
    }

    @Override public void transcript(String value) {
        main.post(() -> {
            if (!closing && transcriptView != null) transcriptView.setText(value);
        });
    }

    @Override public void answer(String value) {
        main.post(() -> {
            if (!closing && answerView != null) {
                answerView.setText(value);
                if (answerScroll != null) answerScroll.post(() -> answerScroll.scrollTo(0, 0));
            }
        });
    }

    @Override public void ttsUrl(String url) {
        main.post(() -> {
            main.removeCallbacks(dismissAfterReply);
            playTts(url);
        });
    }

    private void scheduleIdleDismiss() {
        if (closing || preferences == null || !preferences.autoHide()) return;
        main.removeCallbacks(dismissAfterReply);
        main.postDelayed(dismissAfterReply, preferences.hideSeconds() * 1000L);
    }

    private void playTts(String address) {
        if (connection == null || closing) return;
        try {
            // TTS may take a moment to prepare; do not dismiss while buffering.
            speaking = true;
            main.removeCallbacks(dismissAfterReply);
            HttpUrl playback = TtsUrls.allowedPlaybackUrl(connection.url, address);
            if (player != null) player.release();
            if (speechFocus == null) speechFocus = new SpeechFocus(this);
            speechFocus.release(); // Release any previous TTS focus before replay.
            speechFocus.acquire();
            final int spokenFor = followToken;
            final int playbackId = followUps.bindPlayback();
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            Map<String,String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + connection.token);
            player.setDataSource(this, Uri.parse(playback.toString()), headers);
            player.setOnPreparedListener(p -> {
                status("SPEAKING • Home Assistant is answering");
                if (wave != null) wave.setMode(VoiceWaveView.SPEAKING);
                p.start();
            });
            player.setOnCompletionListener(p -> {
                speaking = false;
                if (speechFocus != null) speechFocus.release();
                if (wave != null) wave.setMode(VoiceWaveView.IDLE);
                if (!followUps.playbackCurrent(playbackId, spokenFor)) {
                    scheduleIdleDismiss();
                    return;
                }
                maybeFollowUp(spokenFor);
            });
            player.setOnErrorListener((p, what, extra) -> {
                speaking = false;
                endFollowUps();
                mics.releaseAll();
                publishMicState();
                if (speechFocus != null) speechFocus.release();
                status("Speech playback failed; answer is still shown.");
                scheduleIdleDismiss();
                return true;
            });
            player.prepareAsync();
        } catch (Exception ignored) {
            speaking = false;
            endFollowUps();
            mics.releaseAll();
            publishMicState();
            if (speechFocus != null) speechFocus.release();
            status("Could not play speech. The answer is still shown.");
            scheduleIdleDismiss();
        }
    }

    private void endFollowUps() {
        followWindow = false;
        followUps.cancel();
        if (followEar != null) followEar.stop();
        mics.previewReleased();
    }

    private void maybeFollowUp(int token) {
        if (closing || preferences == null || assist == null) return;
        if (!FollowUpSession.captureAllowed(true,
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED,
                closing)) {
            followWindow = false;
            status("Microphone permission is missing. Capture stayed off.");
            scheduleIdleDismiss();
            return;
        }
        // Enforce the current device mic consent and route policy before
        // opening a preview recorder, not merely when Assist later takes over.
        if (MicRoutePolicy.manuallyBlocked(preferences.microphoneDisabled(),
                preferences.allowUnverifiedMic())) {
            followWindow = false;
            status(MicRoutePolicy.MANUALLY_DISABLED_MESSAGE);
            scheduleIdleDismiss();
            return;
        }
        String blockedRoute = MicrophoneRouting.preflight(
                this, preferences.allowUnverifiedMic());
        if (blockedRoute != null) {
            followWindow = false;
            status(blockedRoute);
            scheduleIdleDismiss();
            return;
        }
        if (!followUps.arm(preferences.followUp(), token)) {
            followWindow = false;
            status("Done. Press ⭐ again or choose TALK.");
            scheduleIdleDismiss();
            return;
        }
        if (!assist.pipelineIdle()) {
            followUps.deferUntilIdle(token);
            return;
        }
        followWindow = true;
        status("Listening for a follow-up");
        if (wave != null) wave.setMode(VoiceWaveView.LISTENING);
        if (followEar == null) followEar = new FollowUpEar(this);
        mics.armPreview();
        if (!publishMicState()) {
            mics.previewDidNotOpen();
            publishMicState();
            followWindow = false;
            if (wave != null) wave.setMode(VoiceWaveView.IDLE);
            status("Android wouldn't allow the microphone. Capture stayed off.");
            scheduleIdleDismiss();
            return;
        }
        boolean opened = followEar.listen(preferences.followUpSeconds() * 1000,
                preferences.noise(),
                () -> main.post(() -> followUpHeard(token)),
                () -> main.post(() -> followUpMissed(token)));
        if (!opened) {
            followWindow = false;
            mics.previewDidNotOpen();
            publishMicState();
            if (wave != null) wave.setMode(VoiceWaveView.IDLE);
            status("Microphone didn't open. Capture stayed off.");
            scheduleIdleDismiss();
        }
    }

    private void followUpHeard(int token) {
        if (closing || !followUps.same(token) || !followWindow) return;
        startListening(true);
    }

    private void followUpMissed(int token) {
        if (closing || !followUps.same(token)) return;
        followWindow = false;
        listening = false;
        mics.previewReleased();
        publishMicState();
        if (wave != null) wave.setMode(VoiceWaveView.IDLE);
        status("No follow-up. Microphone capture stopped.");
        scheduleIdleDismiss();
    }

    @Override public void captureReleased(int runId) {
        main.post(() -> {
            boolean wasOn = mics.microphoneActive();
            mics.captureReleased(runId);
            if (!closing && wasOn != mics.microphoneActive()) publishMicState();
        });
    }

    @Override public void onDestroy() {
        markClosed();
        if (micHandoff != null) micHandoff.cancel();
        closing = true;
        pendingWakeCapture = false;
        endFollowUps();
        mics.releaseAll();
        HaDiscovery.release(this);
        speaking = false;
        main.removeCallbacksAndMessages(null);
        if (assist != null) { assist.shutdown(); assist = null; }
        MicLease.release(MicLease.OVERLAY);
        if (player != null) { player.release(); player = null; }
        if (speechFocus != null) speechFocus.release();
        if (windowManager != null && panel != null) {
            try { windowManager.removeViewImmediate(panel); }
            catch (Exception ignored) { }
        }
        panel = null;
        answerScroll = null;
        wave = null;
        answerView = null;
        transcriptView = null;
        statusView = null;
        connection = null;
        actions.clear();
        stopForeground(STOP_FOREGROUND_REMOVE);
        // Reconnect for the *next* wake only if the listener is still armed.
        // The old overlay socket was shut down before this, avoiding a live
        // STT run or microphone crossing back into the wake service.
        WakeAssistConnection.preconnect(this);
        super.onDestroy();
    }

    private void markOpen() {
        if (!countedOpen) {
            countedOpen = true;
            OPEN.incrementAndGet();
        }
    }

    private void markClosed() {
        if (countedOpen) {
            countedOpen = false;
            OPEN.decrementAndGet();
        }
    }
}
