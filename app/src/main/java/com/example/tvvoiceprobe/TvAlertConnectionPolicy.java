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

    /** Missing HA controls during startup always require a fresh authorization check. */
    public static boolean retryableSubscriptionError(String code) {
        return "tv_not_ready".equals(code);
    }

    // HA can temporarily report unauthorized after Core restarts, even when
    // pressing Resume later succeeds without changing permissions. Start with
    // a brief burst, then recheck infrequently instead of stopping forever.
    // Every new connection still needs explicit server-side authorization.
    public static final int MAX_TRANSIENT_DENIAL_RETRIES = 5;
    public static final long PERMISSION_RECHECK_MS = 120_000L;

    public static boolean isScopedPermissionDenial(String code) {
        return "unauthorized".equals(code);
    }

    public static boolean shouldRetryScopedDenial(String code, int attempts) {
        return isScopedPermissionDenial(code)
                && attempts >= 0 && attempts < MAX_TRANSIENT_DENIAL_RETRIES;
    }

    /** After the burst, permission errors are rechecked every two minutes. */
    public static long scopedDenialRetryDelayMs(int attempts) {
        if (attempts < 0) throw new IllegalArgumentException("Negative attempt");
        return attempts < MAX_TRANSIENT_DENIAL_RETRIES
                ? retryDelayMs(attempts) : PERMISSION_RECHECK_MS;
    }

    public static long retryDelayMs(int attempts) {
        // Retain the old bounded retry schedule; avoid a shift overflow.
        return Math.min(30_000L, 2_000L << Math.min(4, Math.max(0, attempts)));
    }
}
