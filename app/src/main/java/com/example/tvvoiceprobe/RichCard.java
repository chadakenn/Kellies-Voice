package com.example.tvvoiceprobe;

import java.util.ArrayList;
import java.util.List;

/** Sanitized rich card for temporary overlays. */
public final class RichCard {
    public static final String EVENT = "kellies_voice_rich_card";

    public final String type;
    public final String title;
    public final String subtitle;
    public final List<Row> rows;
    public final String imageEntity;
    public final List<TvEventPayload.Action> actions;
    public final String announcementId;
    public final int seconds;

    private RichCard(String type, String title, String subtitle, List<Row> rows,
                     String imageEntity, List<TvEventPayload.Action> actions,
                     String announcementId, int seconds) {
        this.type = type;
        this.title = title;
        this.subtitle = subtitle;
        this.rows = rows;
        this.imageEntity = imageEntity;
        this.actions = actions;
        this.announcementId = announcementId;
        this.seconds = seconds;
    }

    public static final class Row {
        public final String label;
        public final String value;
        public Row(String label, String value) {
            this.label = label;
            this.value = value == null ? "" : value;
        }
    }

    public static RichCard from(String type, String title, String subtitle,
                                List<Row> rows, String imageEntity,
                                List<TvEventPayload.Action> actions,
                                String announcementId, int seconds) {
        if (!"list".equals(type) && !"card".equals(type)) return null;
        String safeTitle = TvEventPayload.cleanText(title, 65);
        if (safeTitle.isEmpty()) safeTitle = "Card";
        List<Row> kept = new ArrayList<>();
        if (rows != null) {
            for (Row r : rows) {
                if (r == null || r.label == null || r.label.trim().isEmpty()) continue;
                kept.add(new Row(TvEventPayload.cleanText(r.label, 40),
                                 TvEventPayload.cleanText(r.value, 20)));
                if (kept.size() == 8) break;
            }
        }
        if (kept.isEmpty()) return null;
        return new RichCard(type, safeTitle,
                TvEventPayload.cleanText(subtitle, 80),
                kept,
                imageEntity == null ? "" : imageEntity,
                actions == null ? new ArrayList<>() : actions,
                announcementId == null ? "" : announcementId,
                Math.min(45, Math.max(8, seconds <= 0 ? 20 : seconds)));
    }
}
