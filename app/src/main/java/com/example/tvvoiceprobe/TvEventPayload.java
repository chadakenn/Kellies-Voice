package com.example.tvvoiceprobe;

import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

/** Strict, dependency-free validation for authenticated Home Assistant TV events. */
public final class TvEventPayload {
    public static final String ANNOUNCEMENT = "kellies_voice_announcement";
    public static final String CAMERA_POPUP = "kellies_voice_camera_popup";
    public static final String LIVE_CAMERA = "kellies_voice_live_camera";
    public static final String HIDE_CAMERA = "kellies_voice_hide_camera";
    public static final String ACTION = "kellies_voice_announcement_action";
    private static final Pattern ACTION_ID = Pattern.compile("[a-z][a-z0-9_]{0,31}");
    private static final Pattern CAMERA_ID = Pattern.compile("camera\\.[a-zA-Z0-9_]{1,80}");

    public static final class Action {
        public final String id;
        public final String label;

        private Action(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private TvEventPayload() { }

    /** Never trust unscoped or malformed popup button IDs. */
    public static String announcementId(String value) {
        return value != null && value.matches("[0-9a-f]{32}") ? value : "";
    }

    public static Action action(String id, String label) {
        if (id == null || label == null || !ACTION_ID.matcher(id).matches()) return null;
        String text = label.trim().replaceAll("\\s+", " ");
        String lower = text.toLowerCase();
        if (text.isEmpty() || text.length() > 24 || lower.contains("://")
                || lower.startsWith("http") || lower.contains("www.")) return null;
        return new Action(id, text);
    }

    public static List<Action> takeActions(Action[] candidates) {
        ArrayList<Action> kept = new ArrayList<>();
        if (candidates == null) return kept;
        for (Action candidate : candidates) {
            if (candidate == null || kept.size() >= 3) continue;
            boolean exists = false;
            for (Action other : kept) if (other.id.equals(candidate.id)) exists = true;
            if (!exists) kept.add(candidate);
        }
        return kept;
    }

    public static boolean addressedTo(String requestedId, String thisId) {
        return requestedId == null || requestedId.trim().isEmpty()
                || (thisId != null && requestedId.trim().equals(thisId));
    }

    public static String cleanText(String value, int maximum) {
        if (value == null) return "";
        String text = value.trim().replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "");
        int limit = Math.max(1, maximum);
        return text.length() <= limit ? text : text.substring(0, limit).trim();
    }

    public static int duration(int seconds) {
        return Math.min(45, Math.max(4, seconds <= 0 ? 10 : seconds));
    }

    public static int snapshotRefresh(int seconds) {
        // Fail closed for invalid or too-aggressive refresh requests.
        return seconds >= 2 && seconds <= 30 ? seconds : 0;
    }

    public static boolean fitWholeImage(String mode) {
        return "fit".equals(mode);
    }

    public static int liveDuration(int seconds) {
        return Math.min(180, Math.max(4, seconds <= 0 ? 90 : seconds));
    }

    public static boolean hlsPathAllowed(String path) {
        return path != null && path.startsWith("/api/hls/")
                && path.endsWith(".m3u8") && !path.contains("..")
                && !path.contains("\\") && !path.contains("//")
                && !path.contains("?") && !path.contains("#")
                && path.length() <= 600;
    }

    public static String cameraEntity(String value) {
        if (value == null) return null;
        String entity = value.trim();
        return CAMERA_ID.matcher(entity).matches() ? entity : null;
    }

    /** A sentence the TV may speak. Rejects empty, too-long, or web addresses. */
    public static String speak(String value) {
        if (value == null) return "";
        String text = value.trim().replaceAll("\\s+", " ");
        if (text.isEmpty() || text.length() > 180) return "";
        String lower = text.toLowerCase();
        if (lower.contains("://") || lower.startsWith("http") || lower.contains("www.")) return "";
        return text;
    }
}
