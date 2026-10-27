package com.example.tvvoiceprobe;

/** Pure policy for optional reduction of local media-stream volume on wake. */
public final class WakeDuckingLevels {
    private static final int[] LEVELS = {0, 25, 50, 75};

    private WakeDuckingLevels() { }

    public static int[] levels() { return LEVELS.clone(); }

    public static int normalize(int percent) {
        for (int value : LEVELS) if (percent == value) return value;
        return 0; // Invalid or legacy values must never change media volume.
    }

    /** 0 means unchanged; 75 means retain approximately 25% of current volume. */
    public static int target(int original, int percent) {
        int safe = Math.max(0, original);
        int reduction = normalize(percent);
        if (reduction == 0 || safe == 0) return safe;
        return Math.max(1, Math.round(safe * (100 - reduction) / 100.0f));
    }
}
