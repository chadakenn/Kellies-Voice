package com.example.tvvoiceprobe;

/** Drops a Home Assistant conversation after it has been idle. */
public final class ConversationLifetime {
    public static final long IDLE_MS = 10L * 60L * 1000L;

    private ConversationLifetime() { }

    public static boolean expired(long touchedElapsedMs, long nowElapsedMs) {
        return touchedElapsedMs > 0L && nowElapsedMs - touchedElapsedMs >= IDLE_MS;
    }
}
