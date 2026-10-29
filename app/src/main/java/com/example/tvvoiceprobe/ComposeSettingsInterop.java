package com.example.tvvoiceprobe;

import android.content.Context;
import java.util.LinkedHashSet;

/**
 * Narrow bridge from the Compose Settings preview to existing package-private
 * app logic. Keep policy and process-local state owned by their original classes.
 */
public final class ComposeSettingsInterop {
    private static final LinkedHashSet<String> PENDING_LAYOUT_UPDATES = new LinkedHashSet<>();

    private static boolean updateChannelChanged;
    // Only one pending fallback navigation at a time, with an explicit
    // allowlist. No secrets or settings values cross this bridge.
    private static String pendingLegacySection;

    public static synchronized void requestLegacySection(String section) {
        if ("HOME".equals(section) || "VOICE".equals(section)
                || "OVERLAY".equals(section) || "ALERTS".equals(section)
                || "SCREEN".equals(section) || "SYSTEM".equals(section)) {
            pendingLegacySection = section;
        }
    }

    public static synchronized String takeLegacySection() {
        String section = pendingLegacySection;
        pendingLegacySection = null;
        return section;
    }


    /** Clear the owning Activity's stale updater offer after channel selection. */
    public static synchronized void notifyUpdateChannelChanged() {
        updateChannelChanged = true;
    }

    /** One-shot request; does not start an update check or installation. */
    public static synchronized boolean takeUpdateChannelChanged() {
        boolean pending = updateChannelChanged;
        updateChannelChanged = false;
        return pending;
    }

    private ComposeSettingsInterop() { }

    /**
     * A user changed a setting in the Compose preview. Save only its public
     * HaLayout key, never tokens or values. The owning VoiceActivity uses its
     * existing authenticated Assist client when it resumes.
     */
    public static synchronized void queueLayoutUpdate(String setting) {
        if (HaLayout.known(setting)) PENDING_LAYOUT_UPDATES.add(setting);
    }

    /** Called only by the owning Activity when returning from Compose. */
    public static synchronized String[] takePendingLayoutUpdates() {
        String[] updates = PENDING_LAYOUT_UPDATES.toArray(new String[0]);
        PENDING_LAYOUT_UPDATES.clear();
        return updates;
    }

    /** Returns a microphone route error, or null when preflight succeeds. */
    public static String microphonePreflight(Context context, boolean allowUnverified) {
        return MicrophoneRouting.preflight(context, allowUnverified);
    }

    /** Refresh any active screensaver dashboard after a settings change. */
    public static void screensaverSettingsChanged() {
        ScreensaverSignals.settingsChanged();
    }

    /**
     * Match existing HA-driven screensaver lifecycle behavior. This handles
     * watcher start/stop and refresh without taking ownership of Assist/mic.
     */
    public static void applyScreensaverSetting(Context context, String setting) {
        if (HaLayout.isScreensaver(setting)) {
            ScreensaverSettingsEffects.apply(context, setting);
            queueLayoutUpdate(setting);
        }
    }
}
