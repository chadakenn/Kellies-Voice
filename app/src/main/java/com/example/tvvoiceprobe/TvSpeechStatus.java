package com.example.tvvoiceprobe;

/**
 * Local, privacy-safe speech diagnostics for the TV alert status screen.
 * Store only fixed status labels, never the spoken message, tokens, device IDs,
 * Home Assistant URL, or raw Android exceptions.
 */
public final class TvSpeechStatus {
    private static volatile String last = "No spoken alert attempted in this app session.";

    private TvSpeechStatus() { }

    public static void note(String status) {
        if (status != null && status.length() <= 100) last = status;
    }

    public static String latest() {
        return last;
    }
}
