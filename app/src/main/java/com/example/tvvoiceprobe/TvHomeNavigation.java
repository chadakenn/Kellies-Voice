package com.example.tvvoiceprobe;

/** Spatial D-pad navigation: three main controls below a 1–4 action 2x2 grid.
 * Indices 0–2 are TALK, STOP and SETTINGS. Quick Actions start at index 3.
 * The last action spans the whole row when there are three.
 */
public final class TvHomeNavigation {
    private TvHomeNavigation() { }

    private static int count(int shortcuts) {
        return Math.max(0, Math.min(4, shortcuts));
    }

    public static int right(int selected, int shortcuts) {
        int n = count(shortcuts);
        if (selected < 3 || n == 0) return Math.min(2, selected + 1);
        if (selected == 3) return n > 1 ? 4 : 3;
        if (selected == 5) return n == 4 ? 6 : 5;
        return selected;
    }

    public static int left(int selected, int shortcuts) {
        int n = count(shortcuts);
        if (selected < 3 || n == 0) return Math.max(0, selected - 1);
        if (selected == 4) return 3;
        if (selected == 6) return 5;
        return selected;
    }

    public static int up(int selected, int shortcuts) {
        int n = count(shortcuts);
        if (n == 0) return selected;
        if (selected < 3) {
            // The third/fourth shortcuts sit immediately above the toolbar;
            // skip the first row only when that is the sole row available.
            if (n == 1) return 3;
            if (n == 2) return selected == 2 ? 4 : 3 + selected;
            if (n == 3) return 5; // Full-width third button.
            return selected == 2 ? 6 : 5; // Bottom left/right shortcuts.
        }
        if (n > 2 && selected == 5) return 3;
        if (n == 4 && selected == 6) return 4;
        return selected;
    }

    public static int down(int selected, int shortcuts) {
        int n = count(shortcuts);
        if (selected < 3 || n == 0) return selected;
        if (n > 2 && (selected == 3 || selected == 4)) {
            // There really is a second action row; do not jump past it.
            return selected == 4 && n == 4 ? 6 : 5;
        }
        if (selected == 3) return 0;
        if (selected == 4) return 2;
        if (selected == 5) return n == 3 ? 1 : 0;
        if (selected == 6) return 2;
        return 0;
    }

    /** Compatibility for callers that only know the original three buttons. */
    public static int down(int selected) {
        return selected >= 3 ? Math.min(2, selected - 3) : selected;
    }
}
