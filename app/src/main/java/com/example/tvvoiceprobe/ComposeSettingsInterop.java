package com.example.tvvoiceprobe;

import android.content.Context;

/**
 * Narrow bridge from the Compose Settings preview to existing package-private
 * app logic. Keep policy and process-local state owned by their original classes.
 */
public final class ComposeSettingsInterop {
    private ComposeSettingsInterop() { }

    /** Returns a microphone route error, or null when preflight succeeds. */
    public static String microphonePreflight(Context context, boolean allowUnverified) {
        return MicrophoneRouting.preflight(context, allowUnverified);
    }

    /** Refresh any active screensaver dashboard after a settings change. */
    public static void screensaverSettingsChanged() {
        ScreensaverSignals.settingsChanged();
    }
}
