package com.example.tvvoiceprobe;

import java.util.Locale;

/** Safe, non-sensitive status messages for the voice connection and Assist pipeline.
 * Does not start or retry a microphone. Policy is independent of Android to test
 * startup and expired user requests without device hardware.
 */
public final class AssistReadinessPolicy {
    private AssistReadinessPolicy() { }

    public static final long PENDING_USER_REQUEST_MS = 30_000L;

    /** A new microphone session is allowed only for a recent explicit request. */
    public static boolean mayStartPendingRequest(boolean pending, boolean alreadyStarted,
            long nowMs, long requestDeadlineMs) {
        return pending && !alreadyStarted && nowMs >= 0L
                && requestDeadlineMs >= 0L && nowMs <= requestDeadlineMs;
    }

    public static String waitingForConnection() {
        return "Connecting to Home Assistant... Press TALK when Ready.";
    }

    public static String expiredRequest() {
        return "Connected. Previous voice request expired; press TALK to listen.";
    }

    /** Never display arbitrary server error messages, which may contain URLs or secrets. */
    public static String assistError(String code, String message) {
        String type = code == null ? "" : code.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_');
        String detail = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean mentionsPipeline = type.contains("pipeline") || detail.contains("pipeline");
        boolean starting = type.contains("not_loaded") || type.contains("not_ready")
                || type.contains("initializ") || type.contains("starting")
                || ((detail.contains("starting") || detail.contains("loading")
                    || detail.contains("initializ") || detail.contains("not ready"))
                    && mentionsPipeline);
        if (mentionsPipeline && starting) {
            return "Home Assistant Assist pipeline is starting. Try TALK again shortly.";
        }
        if (type.contains("pipeline") && (type.contains("not_found")
                || type.contains("not_supported") || type.contains("missing"))) {
            return "The selected Home Assistant Assist pipeline is unavailable. Check Voice assistants.";
        }
        if (type.startsWith("stt") || type.contains("speech_to_text")) {
            return "Speech recognition failed. Try TALK again or check STT in Home Assistant.";
        }
        if (type.startsWith("intent") || type.contains("conversation")
                || type.contains("agent")) {
            return "Home Assistant conversation agent failed. Check its logs or model.";
        }
        if (type.startsWith("tts") || type.contains("text_to_speech")) {
            return "Home Assistant text-to-speech failed. Check TTS settings.";
        }
        return "Home Assistant Assist request failed. Check the selected pipeline.";
    }
}
