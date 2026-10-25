package com.example.tvvoiceprobe;

/**
 * One voice session's follow-up window. A newer generation makes old timers do nothing.
 * This class does not open a microphone.
 */
public final class FollowUpSession {
    public static final int MAX_TURNS = 4;
    public static final int DEFAULT_SECONDS = 8;

    private int generation;
    private int turns;
    private int playback;
    private int deferred;
    private boolean active;

    public int open() {
        turns = 0;
        deferred = 0;
        active = true;
        return ++generation;
    }

    /** Ends automatic follow-ups. The returned generation is not armable. */
    public int cancel() {
        active = false;
        turns = 0;
        deferred = 0;
        playback++;
        return ++generation;
    }

    public boolean active() { return active; }

    public int bindPlayback() {
        return ++playback;
    }

    public boolean playbackCurrent(int playbackId, int token) {
        return active && playbackId == playback && same(token);
    }

    public void deferUntilIdle(int token) {
        deferred = active && same(token) ? token : 0;
    }

    public int takeDeferred() {
        int token = deferred;
        deferred = 0;
        return active && same(token) ? token : 0;
    }

    public boolean same(int token) {
        return token != 0 && token == generation;
    }

    /** Follow-up can start only while this session is current, enabled, and under the turn cap. */
    public boolean arm(boolean enabled, int token) {
        return active && enabled && same(token) && turns < MAX_TURNS;
    }

    public void markQuestion(int token) {
        if (same(token)) turns++;
    }

    public static int seconds(String raw) {
        if (raw == null) return -1;
        switch (raw.trim()) {
            case "5":
            case "5 seconds":
                return 5;
            case "8":
            case "8 seconds":
                return 8;
            case "10":
            case "10 seconds":
                return 10;
            case "15":
            case "15 seconds":
                return 15;
            default:
                return -1;
        }
    }

    public static String label(int seconds) {
        int value = seconds == 5 || seconds == 10 || seconds == 15 ? seconds : DEFAULT_SECONDS;
        return value + " seconds";
    }

    /** Home Assistant may change the timeout only after the TV has opted in. */
    public static boolean remoteTimeoutAllowed(boolean locallyEnabled) {
        return locallyEnabled;
    }

    /** Recording starts only for an open overlay that still has permission. */
    public static boolean captureAllowed(boolean overlayOpen, boolean permissionGranted, boolean closing) {
        return overlayOpen && permissionGranted && !closing;
    }

    public static boolean voiced(byte[] pcm, int length, String noiseMode) {
        return voiced(pcm, 0, length, noiseMode);
    }

    public static boolean voiced(byte[] pcm, int offset, int length, String noiseMode) {
        if (pcm == null || length < 2 || offset < 0 || offset >= pcm.length) return false;
        double power = 0d;
        int count = 0;
        int end = Math.min(pcm.length, offset + length) & ~1;
        for (int i = offset; i + 1 < end; i += 2) {
            short sample = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8));
            double normalized = sample / 32768.0;
            power += normalized * normalized;
            count++;
        }
        if (count == 0) return false;
        double rms = Math.sqrt(power / count);
        double floor = "quiet".equals(noiseMode) ? 0.004d : "tv".equals(noiseMode) ? 0.02d : 0.008d;
        return rms > floor;
    }
}
