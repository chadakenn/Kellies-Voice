package com.example.tvvoiceprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/**
 * Restarts the opt-in alert receiver after reboot or an app update.
 * It does not enable alerts, open the microphone, or retry in a loop.
 * Some Android TV builds still refuse a background start; then one notification
 * points at the app. Android TV may hide that notice outside the notification list.
 */
public final class TvEventsRestartReceiver extends BroadcastReceiver {
    static final String CHANNEL = "kellie_tv_events";

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !TvEventsRecovery.accepts(intent.getAction())) return;
        ScreensaverWatchService.startIfEnabled(context);
        boolean overlay = Settings.canDrawOverlays(context);
        boolean signedIn = signedIn(context);
        String decision = TvEventsRecovery.decide(
                new OverlayPreferences(context).tvEvents(), overlay, signedIn);
        if (TvEventsRecovery.SKIP.equals(decision)) return;
        if (TvEventsRecovery.START.equals(decision)) {
            try {
                context.startForegroundService(new Intent(context, TvEventsService.class)
                        .setAction(TvEventsService.ACTION_START));
                return;
            } catch (RuntimeException ignored) {
                TvEventsRecovery.note(TvEventsRecovery.STOPPED);
            }
        } else if (!signedIn) {
            TvEventsRecovery.note(TvEventsRecovery.SIGNED_OUT);
        } else {
            TvEventsRecovery.note(TvEventsRecovery.PERMISSION);
        }
        showResume(context, TvEventsRecovery.blockedMessage(overlay, signedIn));
    }

    private static boolean signedIn(Context context) {
        try {
            return new CredentialStore(context).load() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    static void showResume(Context context, String message) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "Kellie's Voice TV alerts", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Opt-in Home Assistant announcements and camera popups");
        manager.createNotificationChannel(channel);
        Intent open = new Intent(context, VoiceActivity.class)
                .setAction("com.example.tvvoiceprobe.OPEN_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(context, 32, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notice = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Resume TV alerts")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        manager.notify(TvEventsRecovery.RESUME_NOTIFICATION_ID, notice);
    }

    static void clearResume(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(TvEventsRecovery.RESUME_NOTIFICATION_ID);
    }
}
