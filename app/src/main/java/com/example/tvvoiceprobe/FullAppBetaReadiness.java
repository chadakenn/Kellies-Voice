package com.example.tvvoiceprobe;

/** Debug beta checkpoint: never reads or prints HA URL, token or credentials.
 *
 * Pairing v3 QA proves TV+owner consent ONLY. It must never be counted as a
 * real Assist session. The stable manual HA login is a separate connection.
 */
final class FullAppBetaReadiness {
    private FullAppBetaReadiness() { }

    static String describe(boolean savedLogin, boolean assistAuthenticated,
            boolean overlayAllowed, boolean micDisabled, boolean tvEventsOptIn,
            boolean tvEventsConnected, boolean screensaverOptIn) {
        String assist;
        if (!savedLogin) assist = "NOT CONFIGURED (manual HA login required)";
        else if (!assistAuthenticated) assist = "NOT CONNECTED (check HA login/network)";
        else assist = "CONNECTED AND AUTHENTICATED";

        String alerts;
        if (!tvEventsOptIn) alerts = "OFF (opt in under Alerts & cameras)";
        else if (!savedLogin) alerts = "NEEDS HA LOGIN";
        else if (!overlayAllowed) alerts = "NEEDS DISPLAY OVER APPS PERMISSION";
        else if (!tvEventsConnected) alerts = "NOT CONNECTED (open TV alert status)";
        else alerts = "CONNECTED (send an actual popup to confirm delivery)";

        return "HOME ASSISTANT: " + assist
                + "\nTV alerts & cameras: " + alerts
                + "\nOverlay permission: " + (overlayAllowed ? "GRANTED" : "NOT GRANTED")
                + "\nTV microphone: " + (micDisabled ? "MANUALLY DISABLED" : "AVAILABLE TO CHECK")
                + "\nFrigate screensaver: " + (screensaverOptIn ? "ENABLED" : "OFF")
                + "\n\nSecure v3 pairing: TEST-ONLY OWNER RECEIPT."
                + "\nThis does NOT log in or grant Assist/camera permissions."
                + "\nA real, separate HA login is required for full app tests."
                + "\n\nTest TALK, spoken reply, overlay, camera popup, "
                + "alerts, wake opt-in, screensaver, and reconnect separately."
                + "\nNo token or network credentials are displayed here.";
    }
}
