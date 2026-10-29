package com.example.tvvoiceprobe;

/** Spatial D-pad navigation for the main TV's two rows of controls. */
public final class TvHomeNavigation {
    private TvHomeNavigation() { }

    public static int right(int selected, int shortcuts) {
        if (shortcuts <= 0 || selected < 3) return Math.min(2, selected + 1);
        return Math.min(2 + shortcuts, selected + 1);
    }

    public static int left(int selected, int shortcuts) {
        if (shortcuts <= 0 || selected < 3) return Math.max(0, selected - 1);
        return Math.max(3, selected - 1);
    }

    public static int up(int selected, int shortcuts) {
        return shortcuts > 0 && selected < 3
                ? 3 + Math.min(selected, shortcuts - 1) : selected;
    }

    public static int down(int selected) {
        return selected >= 3 ? Math.min(2, selected - 3) : selected;
    }
}
