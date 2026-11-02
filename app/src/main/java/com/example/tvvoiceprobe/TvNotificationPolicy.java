package com.example.tvvoiceprobe;

/** Local, opt-in notification filtering; all priority strings are allowlisted. */
public final class TvNotificationPolicy {
    public static final String LOW = "low";
    public static final String NORMAL = "normal";
    public static final String HIGH = "high";
    private TvNotificationPolicy() {}

    public static String priority(String value) {
        if (value == null || value.isEmpty()) return NORMAL;
        switch (value) {
            case LOW: case NORMAL: case HIGH: return value;
            default: return null;
        }
    }

    /** Quiet hours span [start, end), in device-local minutes from midnight. */
    public static boolean withinQuietHours(int minute, int start, int end) {
        if (minute < 0 || minute >= 1440 || start < 0 || start >= 1440
                || end < 0 || end >= 1440 || start == end) return false;
        if (start < end) return minute >= start && minute < end;
        return minute >= start || minute < end;
    }

    /** High priority always displays, but never bypasses Android permission gates. */
    public static boolean shouldDisplay(String priority, boolean quietEnabled,
                                        int minute, int start, int end) {
        String checked = priority(priority);
        if (checked == null) return false;
        return HIGH.equals(checked) || !quietEnabled
                || !withinQuietHours(minute, start, end);
    }

    public static boolean shouldSpeak(String priority) {
        return NORMAL.equals(priority) || HIGH.equals(priority);
    }

    public static String alertSize(String priority, String preferred) {
        if (LOW.equals(priority)) return TvAlertLayout.SMALL;
        if (HIGH.equals(priority)) return TvAlertLayout.LARGE;
        return TvAlertLayout.size(preferred);
    }
}
