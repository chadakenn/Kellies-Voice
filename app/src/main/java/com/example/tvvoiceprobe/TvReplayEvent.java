package com.example.tvvoiceprobe;

import java.util.regex.Pattern;

/** Strict allowlist for Frigate event summaries received over authenticated HA WS. */
public final class TvReplayEvent {
    private static final Pattern ID =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{5,95}");
    private static final Pattern LABEL =
            Pattern.compile("[a-z][a-z0-9_]{0,39}");

    private TvReplayEvent() { }

    public static boolean validId(String input) {
        return input != null && ID.matcher(input).matches() && !input.contains("..");
    }

    public static boolean validLabel(String input) {
        return input != null && LABEL.matcher(input).matches();
    }

    /** Converts Frigate epoch seconds without overflowing Java Date. */
    public static long epochMillis(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 253_402_300_799d)
            return -1L;
        return (long) (seconds * 1000d);
    }
}
