package com.example.tvvoiceprobe;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Private session-only TV alert history. No database, tokens, clips, or camera URLs. */
public final class TvNotificationHistory {
    private static final int LIMIT = 30;
    private static final long MAX_AGE_MS = 24L * 60L * 60L * 1000L;
    private static final ArrayDeque<Entry> EVENTS = new ArrayDeque<>();
    private TvNotificationHistory() {}

    private static final class Entry {
        final long at;
        final String title;
        final String category;
        final String priority;
        final boolean quiet;
        Entry(long at, String title, String category, String priority, boolean quiet) {
            this.at = at;
            this.title = title;
            this.category = category;
            this.priority = priority;
            this.quiet = quiet;
        }
    }

    public static synchronized void add(String title, String category,
                                        String priority, boolean suppressed) {
        long now = System.currentTimeMillis();
        String safeTitle = TvEventPayload.cleanText(title, 65).replace('\n', ' ');
        String safeCategory = "camera".equals(category) ? "camera" : "announcement";
        String checked = TvNotificationPolicy.priority(priority);
        if (checked == null) return;
        EVENTS.addFirst(new Entry(now, safeTitle.isEmpty() ? "Notification" : safeTitle,
                safeCategory, checked, suppressed));
        prune(now);
    }

    public static synchronized List<String> recent() {
        long now = System.currentTimeMillis();
        prune(now);
        ArrayList<String> result = new ArrayList<>();
        SimpleDateFormat time = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (Entry item : EVENTS) {
            result.add(time.format(new Date(item.at)) + "  ·  "
                    + item.title + "  (" + item.priority + ", "
                    + (item.quiet ? "quiet hours" : item.category) + ")");
        }
        return result;
    }

    public static synchronized void clear() { EVENTS.clear(); }

    private static void prune(long now) {
        while (!EVENTS.isEmpty()
                && (EVENTS.size() > LIMIT
                || now - EVENTS.peekLast().at > MAX_AGE_MS)) EVENTS.removeLast();
    }
}
