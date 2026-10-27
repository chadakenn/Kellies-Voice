package com.example.tvvoiceprobe;

/**
 * Opt-in number of simultaneous HA HLS decoders for the screensaver grid.
 * Existing installations stay on the proven snapshot grid by default.
 */
public final class ScreensaverLiveMode {
    public static final String SNAPSHOTS = "Snapshots (reliable)";
    public static final String ONE = "Live (one camera)";
    public static final String TWO = "Live (two cameras)";
    public static final String FOUR = "Live (four cameras, advanced)";

    private ScreensaverLiveMode() { }

    public static String normalize(String raw) {
        if (ONE.equals(raw) || TWO.equals(raw) || FOUR.equals(raw)) return raw;
        return SNAPSHOTS;
    }

    public static int streamLimit(String setting, String decoderMode, int selected) {
        if (!LiveCameraMode.requestsStream(decoderMode)) return 0;
        int cap;
        switch (normalize(setting)) {
            case ONE: cap = 1; break;
            case TWO: cap = 2; break;
            case FOUR: cap = 4; break;
            default: return 0;
        }
        return Math.min(Math.max(0, selected), cap);
    }
}
