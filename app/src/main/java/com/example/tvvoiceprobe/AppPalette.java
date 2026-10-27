package com.example.tvvoiceprobe;

/**
 * Shared palette for TV screens and settings menus, based on the same validated
 * local appearance choice used by the floating voice overlay. No HA calls,
 * microphone permission, or other side effects.
 */
public final class AppPalette {
    public final int screen;
    public final int accent;
    public final int text;
    public final int muted;
    public final int button;
    public final int focus;
    public final int menuStart;
    public final int menuEnd;
    public final int menuButton;
    public final int menuFocus;
    public final int stroke;

    private AppPalette(int screen, int accent, int text, int muted,
                       int button, int focus, int menuStart, int menuEnd,
                       int menuButton, int menuFocus, int stroke) {
        this.screen = screen;
        this.accent = accent;
        this.text = text;
        this.muted = muted;
        this.button = button;
        this.focus = focus;
        this.menuStart = menuStart;
        this.menuEnd = menuEnd;
        this.menuButton = menuButton;
        this.menuFocus = menuFocus;
        this.stroke = stroke;
    }

    public static AppPalette forSkin(String skin) {
        if (OverlayPreferences.SKIN_AURORA.equals(skin)) {
            return new AppPalette(
                    0xff0A1225, 0xffBED6FF, 0xffE4ECFF, 0xff9CB9E9,
                    0xff172844, 0xff3F6BB6, 0xff162947, 0xff0B1428,
                    0xff213758, 0xff365AA0, 0xff709FF8);
        }
        if (OverlayPreferences.SKIN_SIGNAL.equals(skin)) {
            return new AppPalette(
                    0xff171411, 0xffE7C39B, 0xffF5E8D5, 0xffAF9881,
                    0xff332924, 0xff865E41, 0xff26201A, 0xff13110F,
                    0xff39302B, 0xff6D523E, 0xffB38A58);
        }
        if (OverlayPreferences.SKIN_CLASSIC.equals(skin)) {
            return new AppPalette(
                    0xff181512, 0xffE6AB82, 0xffF1DDC9, 0xffC89A7B,
                    0xff49382F, 0xffA66A4B, 0xff261C17, 0xff120E0C,
                    0xff493128, 0xff86533A, 0xffB88664);
        }
        // Current copper palette remains the default for existing installs.
        return new AppPalette(
                0xff1B1412, 0xffE9B390, 0xffF7E8DB, 0xffC59A80,
                0xff62453B, 0xffBA785B, 0xff2B1B16, 0xff17100E,
                0xff493128, 0xffA66A4B, 0xffB98465);
    }
}
