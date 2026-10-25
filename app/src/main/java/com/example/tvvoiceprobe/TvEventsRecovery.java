package com.example.tvvoiceprobe;

/**
 * Decides whether an already opted-in TV alert receiver may start again.
 * This never enables alerts by itself and never starts the microphone.
 */
public final class TvEventsRecovery {
    public static final String BOOT = "android.intent.action.BOOT_COMPLETED";
    public static final String REPLACED = "android.intent.action.MY_PACKAGE_REPLACED";

    public static final String SKIP = "skip";
    public static final String START = "start";
    public static final String NEEDS_USER = "needs_user";

    public static final String OFF = "off";
    public static final String CONNECTED = "connected";
    public static final String RECONNECTING = "reconnecting";
    public static final String PERMISSION = "permission";
    public static final String SIGNED_OUT = "signed_out";
    public static final String STOPPED = "stopped";
    public static final String REJECTED = "rejected";
    public static final String CHECK = "check";
    public static final String DENIED = "permission_denied";
    public static final String UPDATE_HACS = "update_hacs";

    public static final int RESUME_NOTIFICATION_ID = 2243;

    private static volatile String runtime = "";

    private TvEventsRecovery() {}

    public static boolean accepts(String action) {
        return BOOT.equals(action) || REPLACED.equals(action);
    }

    /**
     * @return START only when the owner already enabled alerts and the
     *         permission and saved login are still present.
     */
    public static String decide(boolean optedIn, boolean overlayAllowed, boolean signedIn) {
        if (!optedIn) return SKIP;
        if (!overlayAllowed || !signedIn) return NEEDS_USER;
        return START;
    }

    public static String blockedMessage(boolean overlayAllowed, boolean signedIn) {
        if (!signedIn) {
            return "Open Kellie's Voice and save the Home Assistant login to resume TV alerts.";
        }
        if (!overlayAllowed) {
            return "Allow display over other apps, then open Kellie's Voice to resume TV alerts.";
        }
        return "Android blocked automatic startup. Open Kellie's Voice to resume TV alerts.";
    }

    public static void note(String state) {
        runtime = state == null ? "" : state;
    }

    public static String runtime() {
        return runtime;
    }

    public static String describe(boolean optedIn, boolean overlayAllowed, boolean signedIn, String runtime) {
        if (!optedIn) return "Off. TV alerts stay off until you turn them on.";
        if (!signedIn) return "Signed out. Alerts stay off until Home Assistant is saved again.";
        if (!overlayAllowed) return "Display over other apps is off, so popups cannot show.";
        if (CONNECTED.equals(runtime)) {
            return "Connected. Waiting for new Home Assistant events. Older events are not replayed.";
        }
        if (RECONNECTING.equals(runtime)) {
            return "Reconnecting to Home Assistant. Alerts sent while it was disconnected are not replayed.";
        }
        if (DENIED.equals(runtime)) {
            return "Home Assistant reported TV alert permission denied. "
                    + "Kellie's Voice will automatically recheck periodically after startup, "
                    + "but will not receive alerts until Home Assistant approves access. "
                    + "If this persists, check read access to the TV's Kellie's Voice controls. "
                    + "Resume TV alerts retries immediately; no administrator token is required.";
        }
        if (UPDATE_HACS.equals(runtime)) {
            return "The Home Assistant integration does not support scoped TV alerts. "
                    + "Update Kellie's Voice in HACS and restart Home Assistant.";
        }
        if (REJECTED.equals(runtime)) return "Home Assistant rejected the saved login.";
        if (CHECK.equals(runtime)) return "The saved Home Assistant address needs to be checked in Settings.";
        if (STOPPED.equals(runtime) || runtime == null || runtime.isEmpty()) {
            return "Not running. Android tries again after a reboot or app update. If popups stay missing, open Kellie's Voice once.";
        }
        return "Not running. Open Kellie's Voice to resume TV alerts.";
    }
}
