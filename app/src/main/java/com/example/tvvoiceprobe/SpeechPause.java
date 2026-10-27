package com.example.tvvoiceprobe;

/**
 * Local end-of-speech is only a fallback. Assist's stt-vad-end is the
 * normal stop, and it arrives sooner than these quiet times.
 */
public final class SpeechPause {
    private SpeechPause() { }

    public static long fallbackQuietMs(String noiseMode) {
        if ("quiet".equals(noiseMode)) return 4000L;
        if ("tv".equals(noiseMode)) return 6500L;
        return 5000L;
    }

    public static boolean fallback(boolean heardUtterance, long quietMs,
            long observedMs, String noiseMode) {
        return heardUtterance && observedMs >= 1100L
                && quietMs >= fallbackQuietMs(noiseMode);
    }
}
