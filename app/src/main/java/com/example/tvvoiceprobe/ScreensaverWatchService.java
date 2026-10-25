package com.example.tvvoiceprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;

/**
 * Listens for Android's screensaver and tries to place the Frigate dashboard over it.
 * Opt-in only. It never starts the microphone. Google's own screensaver can still
 * hide a normal overlay; the Kellie's Voice screensaver is the reliable path.
 */
public final class ScreensaverWatchService extends Service {
    static final String ACTION_START = "com.example.tvvoiceprobe.SCREENSAVER_START";
    static final String ACTION_STOP = "com.example.tvvoiceprobe.SCREENSAVER_STOP";
    static final String ACTION_TEST_OVERLAY = "com.example.tvvoiceprobe.SCREENSAVER_TEST_OVERLAY";
    private static final String CHANNEL = "kellie_screensaver";
    private static final int NOTICE = 2244;
    private static final String DREAMING_STARTED = "android.intent.action.DREAMING_STARTED";
    private static final String DREAMING_STOPPED = "android.intent.action.DREAMING_STOPPED";

    private WindowManager windows;
    private ScreensaverBoard board;
    private boolean registered;
    private boolean dreaming;
    private long shownAtMs;

    private final BroadcastReceiver dreams = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (DREAMING_STARTED.equals(intent.getAction())) {
                dreaming = true;
                ScreensaverWatchStatus.event("Android DREAMING_STARTED received");
                ScreensaverSignals.screensaverStopped();
                showIfWanted(false);
            } else if (DREAMING_STOPPED.equals(intent.getAction())) {
                dreaming = false;
                ScreensaverWatchStatus.event("Android DREAMING_STOPPED received");
                long now = SystemClock.elapsedRealtime();
                if (board != null && ScreensaverDashboard.keepAfterEarlyStop(shownAtMs, now)) return;
                hide();
                ScreensaverSignals.screensaverStopped();
            }
        }
    };

    static void startIfEnabled(Context context) {
        if (context == null || !new OverlayPreferences(context).screensaverDashboard()) return;
        try {
            context.startForegroundService(new Intent(context, ScreensaverWatchService.class)
                    .setAction(ACTION_START));
        } catch (RuntimeException ignored) { }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTICE, notice());
        if ((intent != null && ACTION_STOP.equals(intent.getAction()))
                || !new OverlayPreferences(this).screensaverDashboard()) {
            hide();
            ScreensaverWatchStatus.serviceRunning(false);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        ScreensaverWatchStatus.serviceRunning(true);
        windows = getSystemService(WindowManager.class);
        if (!registered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(DREAMING_STARTED);
            filter.addAction(DREAMING_STOPPED);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(dreams, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(dreams, filter);
            }
            registered = true;
            ScreensaverSignals.setListener(this::hideBecauseOurDream);
        }
        if (intent != null && ACTION_TEST_OVERLAY.equals(intent.getAction())) {
            // Explicit local test, not evidence of an ambient-mode event.
            showIfWanted(true);
        }
        // Survive service eviction only while the user has opted in.
        return START_STICKY;
    }

    private void hideBecauseOurDream() {
        if (ScreensaverSignals.ourDream()) hide();
    }

    private void showIfWanted(boolean manualTest) {
        OverlayPreferences prefs = new OverlayPreferences(this);
        boolean configured = ScreensaverDashboard.configured(
                prefs.screensaverUrl(), prefs.screensaverCameras());
        boolean overlayAllowed = Settings.canDrawOverlays(this);
        if (!ScreensaverDashboard.showOverlay(prefs.screensaverDashboard(), dreaming || manualTest,
                manualTest ? false : ScreensaverSignals.dismissed(), ScreensaverSignals.ourDream(),
                overlayAllowed, configured)) {
            if (!overlayAllowed) ScreensaverWatchStatus.overlay("Blocked: Display over other apps permission needed");
            else if (!configured) ScreensaverWatchStatus.overlay("Blocked: configure Frigate URL or cameras");
            else ScreensaverWatchStatus.overlay("Not shown: screensaver inactive or dismissed");
            return;
        }
        if (board != null || windows == null) return;
        board = new ScreensaverBoard(this, () -> {
            ScreensaverSignals.dismiss();
            hide();
        });
        int windowFlags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                windowFlags, PixelFormat.TRANSLUCENT);
        try {
            params.setTitle("Kellie's Voice Frigate overlay");
            windows.addView(board.view(), params);
            board.start();
            shownAtMs = SystemClock.elapsedRealtime();
            ScreensaverWatchStatus.overlay(manualTest
                    ? "Overlay shown (manual test, not ambient detection)"
                    : "Overlay shown after Android dream-start event");
        } catch (RuntimeException ignored) {
            ScreensaverWatchStatus.overlay("Android rejected the overlay window");
            board.stop();
            board = null;
        }
    }

    private void hide() {
        shownAtMs = 0;
        if (board == null) return;
        board.stop();
        try {
            if (windows != null) windows.removeView(board.view());
        } catch (RuntimeException ignored) { }
        board = null;
    }

    private Notification notice() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null && manager.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Kellie's Voice screensaver", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows the Frigate dashboard while the TV screensaver is on");
            manager.createNotificationChannel(channel);
        }
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Frigate screensaver armed")
                .setContentText("Opens only while the screensaver is on. The microphone stays off.")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override public void onDestroy() {
        hide();
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (RuntimeException ignored) { }
        ScreensaverSignals.setListener(null);
        ScreensaverWatchStatus.serviceRunning(false);
        if (registered) {
            try { unregisterReceiver(dreams); } catch (RuntimeException ignored) { }
            registered = false;
        }
        super.onDestroy();
    }
}
