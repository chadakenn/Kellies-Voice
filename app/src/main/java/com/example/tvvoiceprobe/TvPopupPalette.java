package com.example.tvvoiceprobe;

/**
 * Presentation-only colors for Home Assistant TV alerts and forecast popups.
 * Uses the same validated OverlayPreferences skin IDs as Compose Settings.
 * The default copper values intentionally preserve the existing released UI.
 * No network, microphone, or preference changes happen here.
 */
public final class TvPopupPalette {
    public final int backgroundStart;
    public final int backgroundEnd;
    public final int border;
    public final int rule;
    public final int title;
    public final int body;
    public final int muted;
    public final int letterbox;
    public final int buttonBackground;
    public final int buttonFocused;
    public final int buttonText;
    public final int cornerRadiusDp;
    public final boolean auroraGlass;

    public final int forecastStart;
    public final int forecastEnd;
    public final int forecastBorder;
    public final int forecastTitle;
    public final int forecastHeading;
    public final int forecastText;
    public final int forecastMuted;
    public final int forecastRadiusDp;

    private TvPopupPalette(int backgroundStart, int backgroundEnd, int border,
                           int rule, int title, int body, int muted, int letterbox,
                           int buttonBackground, int buttonFocused, int buttonText,
                           int cornerRadiusDp, boolean auroraGlass,
                           int forecastStart, int forecastEnd, int forecastBorder,
                           int forecastTitle, int forecastHeading, int forecastText,
                           int forecastMuted, int forecastRadiusDp) {
        this.backgroundStart = backgroundStart;
        this.backgroundEnd = backgroundEnd;
        this.border = border;
        this.rule = rule;
        this.title = title;
        this.body = body;
        this.muted = muted;
        this.letterbox = letterbox;
        this.buttonBackground = buttonBackground;
        this.buttonFocused = buttonFocused;
        this.buttonText = buttonText;
        this.cornerRadiusDp = cornerRadiusDp;
        this.auroraGlass = auroraGlass;
        this.forecastStart = forecastStart;
        this.forecastEnd = forecastEnd;
        this.forecastBorder = forecastBorder;
        this.forecastTitle = forecastTitle;
        this.forecastHeading = forecastHeading;
        this.forecastText = forecastText;
        this.forecastMuted = forecastMuted;
        this.forecastRadiusDp = forecastRadiusDp;
    }

    /** Resolve installed community colors for alerts, cameras and forecasts.
     * Built-in palettes keep their existing, hardware-tested appearance.
     */
    public static TvPopupPalette forSkin(android.content.Context context, String skin) {
        if (context != null && CommunityThemeStore.isId(skin)) {
            CommunityThemeStore.Theme custom = CommunityThemeStore.get(context, skin);
            if (custom != null) {
                AppPalette palette = AppPalette.forSkin(context, skin);
                return new TvPopupPalette(
                        translucent(palette.menuStart), translucent(palette.menuEnd),
                        palette.stroke, palette.accent, palette.accent, palette.text,
                        palette.muted, palette.screen, palette.menuButton,
                        palette.focus, palette.text, 12, false,
                        translucent(palette.menuStart), translucent(palette.menuEnd),
                        palette.stroke, palette.accent, palette.accent,
                        palette.text, palette.muted, 12);
            }
        }
        return forSkin(skin);
    }

    /** Called when a popup is created; switching themes affects the next popup. */
    public static TvPopupPalette forSkin(String skin) {
        AppPalette palette = AppPalette.forSkin(skin);
        if (OverlayPreferences.SKIN_AURORA.equals(skin)) {
            return new TvPopupPalette(
                    translucent(palette.menuStart), translucent(palette.menuEnd),
                    palette.stroke, palette.accent, palette.accent, palette.text,
                    palette.muted, palette.screen, palette.button, palette.menuFocus,
                    palette.text, 20, true,
                    0xf514100e, 0xf5241a14, 0xffc4884a,
                    0xffedf3ff, 0xffa6bfea, 0xfff2f5ff,
                    0xffa6bfea, 14);
        }
        if (OverlayPreferences.SKIN_OBSIDIAN.equals(skin)) {
            return new TvPopupPalette(
                    translucent(palette.menuStart), translucent(palette.menuEnd),
                    palette.accent, palette.focus, palette.accent,
                    palette.text, palette.muted, palette.screen,
                    palette.button, palette.menuFocus, palette.text, 18, false,
                    translucent(palette.menuStart), translucent(palette.menuEnd),
                    palette.accent, palette.accent, palette.muted,
                    palette.text, palette.muted, 18);
        }
        if (OverlayPreferences.SKIN_TERMINAL.equals(skin)) {
            // Jade Terminal: sharp 3dp corners, phosphor green rule and border.
            return new TvPopupPalette(
                    translucent(palette.menuStart), translucent(palette.menuEnd),
                    palette.accent, palette.accent, palette.accent,
                    palette.text, palette.muted, palette.screen,
                    palette.button, palette.menuFocus, palette.text, 3, false,
                    translucent(palette.menuStart), translucent(palette.menuEnd),
                    palette.accent, palette.accent, palette.muted,
                    palette.text, palette.muted, 3);
        }
        if (OverlayPreferences.SKIN_CLASSIC.equals(skin)
                || OverlayPreferences.SKIN_SIGNAL.equals(skin)) {
            int radius = OverlayPreferences.SKIN_SIGNAL.equals(skin) ? 6 : 9;
            return new TvPopupPalette(
                    translucent(palette.menuStart), translucent(palette.menuEnd),
                    palette.stroke, palette.accent, palette.accent, palette.text,
                    palette.muted, palette.screen, palette.button, palette.menuFocus,
                    palette.text, radius, false,
                    translucent(palette.menuStart), translucent(palette.menuEnd),
                    palette.stroke, palette.accent, palette.muted,
                    palette.text, palette.muted, radius);
        }
        // Legacy copper: don't alter its published announcement and forecast looks.
        return new TvPopupPalette(
                0xf51b1512, 0xf52b1d17, 0xffad835d,
                0xffe1b27b, 0xffeac296, 0xfffae8d5,
                0xffad835d, 0xff2d211c, palette.button, palette.focus,
                0xfffae8d5, 12, false,
                0xf514100e, 0xf5241a14, 0xffc4884a,
                0xffeac296, 0xffc4884a, 0xfffae8d5,
                0xffad835d, 14);
    }

    private static int translucent(int color) {
        return 0xf5000000 | (color & 0x00ffffff);
    }
}
