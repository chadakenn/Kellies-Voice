package com.example.tvvoiceprobe;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Privacy-safe diagnostics and supported recovery for the screensaver watcher.
 * Does not assume why a background start failed. Records the actual exception
 * class and uses a user-visible notification when unattended start is blocked.
 * Never logs URLs, tokens, or camera names.
 */
final class ScreensaverWatchRecovery {
    static final String PREFS = "screensaver_watch_recovery";
    static final String KEY_LAST_ATTEMPT = "last_attempt_ms";
    static final String KEY_LAST_RESULT = "last_result";
    static final String KEY_LAST_ERROR = "last_error";
    static final String KEY_BOOT_COUNT = "boot_receiver_boot_count";
    static final String RESULT_REQUESTED = "start requested";
    static final String RESULT_RUNNING = "service confirmed RUNNING";
    static final String RESULT_DENIED = "start denied";
    static final String RESULT_SKIPPED = "skipped (feature off)";
    static final String RESULT_NOTIFICATION = "notification posted";
    static final String CHANNEL = "kellie_screensaver";
    static final int NOTICE_ID = 2245;

    private ScreensaverWatchRecovery() { }

    /** Pure decision: should we attempt a start? */
    static boolean decideStart(boolean screensaverOn, boolean haManageOn) {
        return screensaverOn || haManageOn;
    }

    /** Pure decision: what recovery action after a failed start? */
    static String decideRecovery(boolean shouldRun, boolean startAccepted) {
        if (!shouldRun) return RESULT_SKIPPED;
        if (startAccepted) return RESULT_REQUESTED;
        return RESULT_NOTIFICATION;
    }

    /** Pure decision: is this boot's receiver entry current? */
    static boolean bootMatches(int storedBootCount, int currentBootCount) {
        return storedBootCount >= 0 && storedBootCount == currentBootCount;
    }

    /** Whether the watcher service should be running at all. */
    static boolean shouldRun(Context context) {
        if (context == null) return false;
        OverlayPreferences prefs = new OverlayPreferences(context);
        return prefs.screensaverDashboard() || prefs.haManage();
    }

    static void record(Context context, String result, String error) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_LAST_ATTEMPT, System.currentTimeMillis())
                .putString(KEY_LAST_RESULT, result == null ? "" : result)
                .putString(KEY_LAST_ERROR, error == null ? "" : error)
                .apply();
        ScreensaverWatchStatus.startup(result, error);
    }

    static void noteBootReceiver(Context context) {
        if (context == null) return;
        int boot = currentBootCount(context);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY_BOOT_COUNT, boot).apply();
    }

    /** True only if the receiver ran on the current boot, not an older one. */
    static boolean bootReceiverEnteredThisBoot(Context context) {
        if (context == null) return false;
        int stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_BOOT_COUNT, -1);
        return stored >= 0 && stored == currentBootCount(context);
    }

    static int currentBootCount(Context context) {
        try {
            return android.provider.Settings.Global.getInt(
                    context.getContentResolver(),
                    android.provider.Settings.Global.BOOT_COUNT, -1);
        } catch (Exception ignored) {
            return -1;
        }
    }

    static String lastResult(Context context) {
        if (context == null) return "";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_RESULT, "");
    }

    static String lastError(Context context) {
        if (context == null) return "";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_ERROR, "");
    }

    /**
     * Attempt to start the watcher. Does not check overlay permission here;
     * the service itself gates overlay rendering. HA layout sync must still
     * start when overlay permission is missing.
     * Returns true if startForegroundService was accepted by the system.
     */
    static boolean tryStart(Context context) {
        if (!shouldRun(context)) {
            record(context, RESULT_SKIPPED, "feature off");
            cancelRecovery(context);
            return false;
        }
        try {
            context.startForegroundService(new Intent(context, ScreensaverWatchService.class)
                    .setAction(ScreensaverWatchService.ACTION_START));
            record(context, RESULT_REQUESTED, "");
            return true;
        } catch (RuntimeException e) {
            String type = e.getClass().getSimpleName();
            record(context, RESULT_DENIED, type);
            return false;
        }
    }

    /**
     * Supported recovery when an unattended start is denied.
     * Posts a user-visible notification that opens Settings so the user can
     * resume the watcher. Does not use an inexact AlarmManager to promise
     * an automatic FGS restart (not a reliable exemption).
     */
    static void postRecoveryNotification(Context context) {
        if (context == null || !shouldRun(context)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "Kellie's Voice screensaver", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Screensaver watcher recovery");
        manager.createNotificationChannel(channel);
        Intent open = new Intent(context, VoiceActivity.class)
                .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 78, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Kellie's Voice screensaver")
                .setContentText("Tap to resume the screensaver watcher")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();
        try {
            manager.notify(NOTICE_ID, notice);
            record(context, RESULT_NOTIFICATION, "user tap required");
        } catch (RuntimeException ignored) {
            record(context, RESULT_DENIED, "notification failed");
        }
    }

    /** Cancel any outstanding recovery notification when the feature is turned off. */
    static void cancelRecovery(Context context) {
        if (context == null) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTICE_ID);
    }
}
