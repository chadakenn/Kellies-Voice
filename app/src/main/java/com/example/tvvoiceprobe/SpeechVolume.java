package com.example.tvvoiceprobe;

/** Safe, per-utterance Android TTS gain; never changes system media volume. */
final class SpeechVolume {
    static final int DEFAULT_PERCENT = 60;
    private static final int[] LEVELS = {20, 40, 60, 80, 100};

    private SpeechVolume() { }

    static int[] levels() {
        return LEVELS.clone();
    }

    static int normalizePercent(int percent) {
        for (int level : LEVELS) {
            if (percent == level) return percent;
        }
        return DEFAULT_PERCENT;
    }

    static float gain(int percent) {
        return normalizePercent(percent) / 100.0f;
    }
}
