package com.example.tvvoiceprobe;

/** Spreads retries so several TVs do not hit Home Assistant on the same second. */
public final class RetryTiming {
    private RetryTiming() { }

    public static long withJitter(long baseMs, long capMs, double unitRandom) {
        long base = Math.max(0L, baseMs);
        double unit = unitRandom < 0d ? 0d : (unitRandom > 1d ? 1d : unitRandom);
        long delay = base + (long) (base * 0.2d * unit);
        if (capMs > 0L && delay > capMs) return capMs;
        return delay;
    }
}
