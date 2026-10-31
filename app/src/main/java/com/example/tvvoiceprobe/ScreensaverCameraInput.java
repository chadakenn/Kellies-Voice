package com.example.tvvoiceprobe;

/** Shared strict input validation for the Compose Frigate camera entity editor. */
public final class ScreensaverCameraInput {
    private ScreensaverCameraInput() { }

    /**
     * @return normalized comma-separated camera IDs, blank for an intentional
     *         clear, or null for invalid input. Unlike the dashboard's resilient
     *         reader, an editor must never silently discard invalid selections.
     */
    public static String validatedOrNull(String raw) {
        if (raw == null) return null;
        String typed = raw.trim();
        if (typed.isEmpty()) return "";

        String[] ids = typed.split("[,\\s]+");
        if (ids.length == 0 || ids.length > ScreensaverDashboard.MAX_CAMERAS) return null;
        for (String id : ids) {
            if (TvEventPayload.cameraEntity(id) == null) return null;
        }
        return ScreensaverDashboard.cameras(typed);
    }
}
