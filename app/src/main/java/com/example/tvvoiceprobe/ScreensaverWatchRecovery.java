package com.example.tvvoiceprobe;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * Privacy-safe recovery for the screensaver watcher after reboot.
 * Records whether a start was attempted and why it failed.
 * Never logs URLs, tokens, or camera names.
 */
final class ScreensaverWatchRecovery {
    static final String PREFS = "screensaver_watch_recovery";
    static final String KEY_LAST_ATTEMPT = "last_attempt_ms";
    static final String KEY_LAST_RESULT = "last_result";
    static final String KEY_LAST_ERROR = "last_error";
    static final String RESULT_STARTED = "started";
    static final String RESULT_DENIED = "denied";
    static final String RESULT_SKIPPED = "skipped";
    static final String RESULT_SCHEDULED = "scheduled";

    private ScreensaverWatchRecovery() { }

    /** Decide whether the watcher should be running. */
    static boolean shouldRun(Context context) {
        if (context == null) return false;
        OverlayPreferences prefs = new OverlayPreferences(context);
        return prefs.screensaverDashboard() || prefs.haManage();
    }

    /** Record a privacy-safe startup outcome. */
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

    /** Try to start the watcher. Returns true if the start call succeeded. */
    static boolean tryStart(Context context) {
        if (!shouldRun(context)) {
            record(context, RESULT_SKIPPED, "feature off");
            return false;
        }
        if (!Settings.canDrawOverlays(context)) {
            record(context, RESULT_DENIED, "overlay permission missing");
            return false;
        }
        try {
            context.startForegroundService(new Intent(context, ScreensaverWatchService.class)
                    .setAction(ScreensaverWatchService.ACTION_START));
            record(context, RESULT_STARTED, "");
            return true;
        } catch (RuntimeException e) {
            String type = e.getClass().getSimpleName();
            record(context, RESULT_DENIED, type);
            return false;
        }
    }

    /** Schedule a one-shot recovery if the immediate start was denied. */
    static void scheduleRecovery(Context context) {
        if (context == null) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent retry = new Intent(context, ScreensaverWatchService.class)
                .setAction(ScreensaverWatchService.ACTION_START);
        PendingIntent pi = PendingIntent.getService(context, 77, retry,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long when = SystemClock.elapsedRealtime() + 45_000L;
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, when, pi);
            record(context, RESULT_SCHEDULED, "alarm +45s");
        } catch (RuntimeException ignored) {
            record(context, RESULT_DENIED, "alarm schedule failed");
        }
    }
}
