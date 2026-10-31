package com.example.tvvoiceprobe;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/**
 * Restores an explicitly saved wake-word choice from a VISIBLE activity only.
 *
 * Android 14+ does not permit a microphone foreground service to be created
 * directly from BOOT_COMPLETED. At boot we post a one-tap reminder, never
 * start an AudioRecord or a microphone-typed service from the receiver.
 */
public final class WakeWordStartup {
    private static final String CHANNEL = "kellie_wake_resume";
    private static final int NOTICE_ID = 2253;
    private WakeWordStartup() { }

    /** Activity must be resumed/visible. This does not create a new opt-in. */
    public static void resumeFromVisible(Activity activity) {
        OverlayPreferences prefs = new OverlayPreferences(activity);
        if (!prefs.wakeWord() || !prefs.wakeRestartConsent() || prefs.microphoneDisabled()) {
            clearReminder(activity);
            return;
        }
        boolean signedIn = WakeWordLoginGate.hasSavedLogin(activity);
        boolean routeReady = MicrophoneRouting.preflight(
                activity, prefs.allowUnverifiedMic()) == null;
        if (!WakeWordSession.mayRestore(
                prefs.wakeWord() && prefs.wakeRestartConsent(), prefs.microphoneDisabled(),
                activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED,
                Settings.canDrawOverlays(activity), signedIn,
                routeReady, WakeWordService.isListening())) {
            if (WakeWordService.isListening()) clearReminder(activity);
            return;
        }
        try {
            activity.startForegroundService(new Intent(activity, WakeWordService.class)
                    .setAction(WakeWordSession.ACTION_RESTORE));
            // Service clears any boot reminder once listening is actually running.
        } catch (RuntimeException blocked) {
            // Never override the saved ON choice merely because Android
            // rejects a foreground microphone start on this firmware.
            showResumeReminder(activity);
        }
    }

    /** Called from the already-registered BOOT_COMPLETED / update receiver. */
    public static void onDeviceRestart(Context context) {
        OverlayPreferences prefs = new OverlayPreferences(context);
        if (prefs.wakeWord() && prefs.wakeRestartConsent() && !prefs.microphoneDisabled()) {
            showResumeReminder(context);
        } else {
            clearReminder(context);
        }
    }

    static void showResumeReminder(Context context) {
        OverlayPreferences prefs = new OverlayPreferences(context);
        if (!prefs.wakeWord() || !prefs.wakeRestartConsent() || prefs.microphoneDisabled()) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        try {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Kellie's Voice hands-free wake", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Resume an already enabled wake word after Android TV restarts");
            manager.createNotificationChannel(channel);
            Intent open = new Intent(context, VoiceActivity.class)
                    .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, 2253, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification reminder = new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                    .setContentTitle("Okay Nabu is enabled")
                    .setContentText("Open Kellie's Voice to resume hands-free listening")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build();
            manager.notify(NOTICE_ID, reminder);
        } catch (RuntimeException ignored) {
            // Notifications can be blocked on Android TV. The saved choice
            // still resumes the next time the user opens the app.
        }
    }

    static void clearReminder(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTICE_ID);
    }
}
