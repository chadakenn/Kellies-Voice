package com.example.tvvoiceprobe;

/** A bounded, normalized TV remote pointer. No camera IDs or web URLs involved. */
final class ScreensaverRemoteCursor {
    private float x = 0.5f;
    private float y = 0.5f;
    static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3;

    /** New browsing session starts in the center instead of an old offscreen spot. */
    void reset() { x = 0.5f; y = 0.5f; }

    /** Short presses are precise; holding a D-pad arrow accelerates movement.
     * `repeatCount` comes from Android KeyEvent and cannot be trusted to
     * remain small, so both acceleration and screen bounds are capped.
     * This is screen navigation only and never interacts with credentials.
     */
    void move(int direction, int repeatCount) {
        final int repeats = Math.max(0, Math.min(18, repeatCount));
        final float step = Math.min(0.070f, 0.022f + repeats * 0.003f);
        if (direction == UP) y -= step;
        if (direction == DOWN) y += step;
        if (direction == LEFT) x -= step;
        if (direction == RIGHT) x += step;
        x = Math.max(0.025f, Math.min(0.975f, x));
        y = Math.max(0.025f, Math.min(0.975f, y));
    }

    /** Preserve existing normal-tap behavior for callers without repeat data. */
    void move(int direction) { move(direction, 0); }

    /**
     * Moving beyond the top/bottom of a TV WebView must scroll its contents,
     * not leave the remote pointer stranded. Ordinary movement stays precise.
     * Positive wheel axis scrolls up; negative scrolls down.
     */
    float verticalWheelAtEdge(int direction, int repeatCount) {
        final boolean up = direction == UP && y <= 0.025f;
        final boolean down = direction == DOWN && y >= 0.975f;
        if (!up && !down) return 0f;
        final int repeats = Math.max(0, Math.min(16, repeatCount));
        final float magnitude = Math.min(3f, 1f + repeats * 0.12f);
        return up ? magnitude : -magnitude;
    }

    int pixelX(int width) { return Math.round(x * Math.max(1, width)); }
    int pixelY(int height) { return Math.round(y * Math.max(1, height)); }
}
