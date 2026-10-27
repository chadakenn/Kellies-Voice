package com.example.tvvoiceprobe;

/**
 * When the opt-in wake listener may run. A reboot, an app update, or a missing
 * permission never starts it. Home Assistant is not contacted from here.
 */
public final class WakeWordSession {
    public static final String ACTION_START = "com.example.tvvoiceprobe.WAKE_WORD_START";
    public static final String ACTION_STOP = "com.example.tvvoiceprobe.WAKE_WORD_STOP";
    /** Resume from a visible activity ONLY; never from a boot broadcast. */
    public static final String ACTION_RESTORE = "com.example.tvvoiceprobe.WAKE_WORD_RESTORE";
    /** Internal one-shot capture request, never sent on boot or app update. */
    public static final String ACTION_DETECTED = "com.example.tvvoiceprobe.WAKE_WORD_DETECTED";
    /** Two seconds at 10 ms chunks, matching the Home Assistant companion cooldown. */
    public static final int COOLDOWN_CHUNKS = 200;

    private int cooldown;

    public static boolean mayStart(boolean enabled, String action,
                                   boolean micGranted, boolean overlayGranted) {
        return enabled
                && (ACTION_START.equals(action) || ACTION_RESTORE.equals(action))
                && micGranted
                && overlayGranted;
    }

    /** Preserve the owner's ON choice while enforcing every privacy gate. */
    public static boolean mayRestore(boolean optedIn, boolean manuallyDisabled,
            boolean micGranted, boolean overlayGranted, boolean signedIn,
            boolean microphoneReady, boolean alreadyListening) {
        return optedIn && !manuallyDisabled && micGranted && overlayGranted
                && signedIn && microphoneReady && !alreadyListening;
    }

    /** A detection only starts the overlay mic when the wake listener is opted in. */
    public static boolean isDetectionRequest(boolean enabled, String action) {
        return enabled && ACTION_DETECTED.equals(action);
    }

    /** A wake-initiated question must finish on silence, even with Auto-listen disabled. */
    public static boolean stopOnSilence(boolean autoListen, boolean fromWakeWord) {
        return autoListen || fromWakeWord;
    }

    /** The wake listener must not hold the microphone while the voice overlay is up. */
    public static boolean shouldCapture(boolean enabled, boolean overlayOpen) {
        return enabled && !overlayOpen;
    }

    /**
     * @param detected the engine scored this chunk as the wake word
     * @return true only for a new detection, never during the cooldown
     */
    public boolean onChunk(boolean detected) {
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        if (!detected) return false;
        cooldown = COOLDOWN_CHUNKS;
        return true;
    }
}
