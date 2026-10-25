package com.example.tvvoiceprobe;

import android.content.Context;
import android.content.Intent;

/** Apply Home Assistant settings without opening an Activity or the microphone. */
final class ScreensaverSettingsEffects {
    private ScreensaverSettingsEffects() { }

    static void apply(Context context, String changed) {
        if (!HaLayout.isScreensaver(changed)) return;
        if (HaLayout.SCREENSAVER_ENABLED.equals(changed)) {
            if (new OverlayPreferences(context).screensaverDashboard()) {
                ScreensaverWatchService.startIfEnabled(context);
            } else if (!new OverlayPreferences(context).haManage()) {
                // Keep the HA-authorized watcher alive when HA owns this setting:
                // otherwise an OFF command would make a later ON impossible remotely.
                try {
                    context.startService(new Intent(context, ScreensaverWatchService.class)
                            .setAction(ScreensaverWatchService.ACTION_STOP));
                } catch (RuntimeException ignored) {
                    context.stopService(new Intent(context, ScreensaverWatchService.class));
                }
            }
        }
        ScreensaverSignals.settingsChanged();
    }
}
