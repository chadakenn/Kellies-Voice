package com.example.tvvoiceprobe;

/** A bounded, normalized TV remote pointer. No camera IDs or web URLs involved. */
final class ScreensaverRemoteCursor {
    private float x = 0.5f;
    private float y = 0.5f;
    static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3;

    void move(int direction) {
        final float step = 0.075f;
        if (direction == UP) y -= step;
        if (direction == DOWN) y += step;
        if (direction == LEFT) x -= step;
        if (direction == RIGHT) x += step;
        x = Math.max(0.025f, Math.min(0.975f, x));
        y = Math.max(0.025f, Math.min(0.975f, y));
    }

    int pixelX(int width) { return Math.round(x * Math.max(1, width)); }
    int pixelY(int height) { return Math.round(y * Math.max(1, height)); }
}
