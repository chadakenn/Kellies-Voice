package com.example.tvvoiceprobe;

/**
 * Pure, testable policy for the microphone-free TV alert receiver.
 * HA's authenticated WebSocket can be half-open after TV standby:
 * an OkHttp WebSocket object alone is never proof of a subscription.
 */
public final class TvAlertConnectionPolicy {
    public static final long WATCHDOG_INTERVAL_MS = 15_000L;
    public static final long SETUP_TIMEOUT_MS = 45_000L;
    public static final long HEARTBEAT_TIMEOUT_MS = 30_000L;

    private TvAlertConnectionPolicy() { }

    public static boolean shouldForceRestart(boolean explicitResume, boolean socketPresent,
                                             boolean subscribed, boolean setupExpired) {
        // Don't destroy a fresh authentication just because Activity.onStart
        // repeated ACTION_START while a WebSocket handshake was still running.
        return explicitResume || (socketPresent && !subscribed && setupExpired);
    }

    public static boolean setupTimedOut(long startedAtMs, long nowMs, boolean subscribed) {
        return !subscribed && startedAtMs >= 0
                && nowMs >= startedAtMs
                && nowMs - startedAtMs >= SETUP_TIMEOUT_MS;
    }

    public static boolean heartbeatTimedOut(long sentAtMs, long nowMs) {
        return sentAtMs >= 0 && nowMs >= sentAtMs
                && nowMs - sentAtMs >= HEARTBEAT_TIMEOUT_MS;
    }

    public static long retryDelayMs(int attempts) {
        // Retain the old bounded retry schedule; avoid a shift overflow.
        return Math.min(30_000L, 2_000L << Math.min(4, Math.max(0, attempts)));
    }
}
